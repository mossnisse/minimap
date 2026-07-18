package gis.shapefile;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the editable parts of a shapefile set back to disk: the attribute
 * table (.dbf) for any shape type, and the geometry (.shp + .shx) for plain
 * point files. Attributes are written as UTF-8 with a .cpg sidecar saying so;
 * character columns are widened automatically when an edited value no longer
 * fits the declared field length.
 */
public final class ShapefileWriter {
	private static final Charset CHARSET = StandardCharsets.UTF_8;
	private static final int MAX_FIELD_LENGTH = 254;
	private static final int FILE_HEADER_SIZE_WORDS = 50; // 100 bytes in 16-bit words
	private static final int RECORD_HEADER_WORDS = 4;     // 8 bytes
	private static final int POINT_CONTENT_WORDS = 10;    // type + x + y = 20 bytes
	private static final int NULL_CONTENT_WORDS = 2;      // type only = 4 bytes

	private ShapefileWriter() {}

	/**
	 * Writes the attribute table and a "UTF-8" .cpg sidecar next to it.
	 * Every row must have exactly one value per field.
	 */
	public static void writeDbf(File dbfFile, File cpgFile, List<DbfField> fields,
	                            List<List<String>> rows) throws IOException {
		writeDbf(dbfFile, cpgFile, fields, rows,
				Collections.nCopies(rows.size(), Boolean.FALSE));
	}

	/** Writes a DBF while retaining each source record's deletion flag. */
	public static void writeDbf(File dbfFile, File cpgFile, List<DbfField> fields,
	                            List<List<String>> rows, List<Boolean> deletedFlags) throws IOException {
		if (fields.isEmpty()) {
			throw new IllegalArgumentException("A .dbf table needs at least one field");
		}
		if (deletedFlags.size() != rows.size()) {
			throw new IllegalArgumentException("There must be one deletion flag per .dbf row");
		}
		int n = fields.size();
		for (int row = 0; row < rows.size(); row++) {
			if (rows.get(row).size() != n) {
				throw new IllegalArgumentException("DBF row " + (row + 1) + " has "
						+ rows.get(row).size() + " values; expected " + n);
			}
		}

		// Effective byte length per column: the declared length, widened to the
		// longest edited value. Overlong values are rejected instead of truncated.
		int[] lengths = new int[n];
		for (int i = 0; i < n; i++) {
			DbfField field = fields.get(i);
			byte[] fieldName = field.name.getBytes(CHARSET);
			if (fieldName.length == 0 || fieldName.length > 10) {
				throw new IOException("dBASE column names must contain 1 to 10 UTF-8 bytes: " + field.name);
			}
			if (field.length < 1 || field.length > MAX_FIELD_LENGTH) {
				throw new IOException("Invalid width " + field.length + " for dBASE column " + field.name);
			}
			int len = field.length;
			for (int row = 0; row < rows.size(); row++) {
				String value = rows.get(row).get(i);
				if (value == null) value = "";
				int byteLength = value.getBytes(CHARSET).length;
				if (byteLength > MAX_FIELD_LENGTH) {
					throw new IOException("Value in row " + (row + 1) + ", column " + field.name
							+ " is " + byteLength + " UTF-8 bytes; dBASE allows at most "
							+ MAX_FIELD_LENGTH);
				}
				len = Math.max(len, byteLength);
			}
			lengths[i] = len;
		}
		int headerSize = 32 + 32 * n + 1;
		int recordSize = 1; // deletion flag
		for (int len : lengths) {
			recordSize += len;
		}
		if (headerSize > 0xFFFF || recordSize > 0xFFFF) {
			throw new IOException("The dBASE header or row is too large for the file format");
		}

		try (DataOutputStream out = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(dbfFile)))) {
			byte[] header = new byte[32];
			header[0] = 0x03; // dBASE III without memo
			LocalDate today = LocalDate.now();
			header[1] = (byte) (today.getYear() - 1900);
			header[2] = (byte) today.getMonthValue();
			header[3] = (byte) today.getDayOfMonth();
			writeIntLE(header, 4, rows.size());
			writeShortLE(header, 8, headerSize);
			writeShortLE(header, 10, recordSize);
			out.write(header);

			for (int i = 0; i < n; i++) {
				DbfField field = fields.get(i);
				byte[] desc = new byte[32];
				byte[] name = field.name.getBytes(CHARSET);
				System.arraycopy(name, 0, desc, 0, Math.min(name.length, 10)); // NUL-padded
				desc[11] = (byte) field.type;
				desc[16] = (byte) lengths[i];
				desc[17] = (byte) field.decimalCount;
				out.write(desc);
			}
			out.write(0x0D); // header terminator

			for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
				List<String> row = rows.get(rowIndex);
				out.write(Boolean.TRUE.equals(deletedFlags.get(rowIndex)) ? 0x2A : 0x20);
				for (int i = 0; i < n; i++) {
					String value = row.get(i) == null ? "" : row.get(i);
					out.write(pad(value, lengths[i], fields.get(i).type));
				}
			}
			out.write(0x1A); // end of file
		}
		Files.write(cpgFile.toPath(), "UTF-8".getBytes(StandardCharsets.US_ASCII));
	}

	/**
	 * Stages every changed shapefile component beside its destination and then
	 * replaces the set together. Existing files are backed up so a later
	 * replacement failure can restore components already replaced.
	 */
	public static void writeAtomically(File dbfFile, File cpgFile,
	                                  List<DbfField> fields, List<List<String>> rows,
	                                  List<Boolean> deletedFlags,
	                                  File shpFile, File shxFile,
	                                  List<ShpGeometry> geometries) throws IOException {
		if ((dbfFile == null) != (cpgFile == null)) {
			throw new IllegalArgumentException("DBF and CPG destinations must be supplied together");
		}
		if ((shpFile == null) != (shxFile == null) || (shpFile == null) != (geometries == null)) {
			throw new IllegalArgumentException("SHP, SHX and geometries must be supplied together");
		}

		Map<Path, Path> staged = new LinkedHashMap<>(); // destination -> staged file
		try {
			if (dbfFile != null) {
				Path dbfTemp = createSiblingTemp(dbfFile.toPath());
				staged.put(dbfFile.toPath().toAbsolutePath(), dbfTemp);
				Path cpgTemp = createSiblingTemp(cpgFile.toPath());
				staged.put(cpgFile.toPath().toAbsolutePath(), cpgTemp);
				writeDbf(dbfTemp.toFile(), cpgTemp.toFile(), fields, rows, deletedFlags);
			}
			if (shpFile != null) {
				Path shpTemp = createSiblingTemp(shpFile.toPath());
				staged.put(shpFile.toPath().toAbsolutePath(), shpTemp);
				Path shxTemp = createSiblingTemp(shxFile.toPath());
				staged.put(shxFile.toPath().toAbsolutePath(), shxTemp);
				writePointShp(shpTemp.toFile(), shxTemp.toFile(), geometries);
			}
			replaceAsGroup(staged);
		} finally {
			for (Path temp : staged.values()) deleteQuietly(temp);
		}
	}

	private static Path createSiblingTemp(Path destination) throws IOException {
		Path absolute = destination.toAbsolutePath();
		return Files.createTempFile(absolute.getParent(), absolute.getFileName().toString() + ".", ".tmp");
	}

	private static void replaceAsGroup(Map<Path, Path> staged) throws IOException {
		Map<Path, Path> backups = new LinkedHashMap<>();
		List<Path> replaced = new ArrayList<>();
		try {
			for (Path destination : staged.keySet()) {
				if (Files.exists(destination)) {
					Path backup = createSiblingTemp(destination);
					backups.put(destination, backup);
					Files.copy(destination, backup, StandardCopyOption.REPLACE_EXISTING,
							StandardCopyOption.COPY_ATTRIBUTES);
				}
			}
			for (Map.Entry<Path, Path> entry : staged.entrySet()) {
				moveReplace(entry.getValue(), entry.getKey());
				replaced.add(entry.getKey());
			}
		} catch (IOException failure) {
			for (int i = replaced.size() - 1; i >= 0; i--) {
				Path destination = replaced.get(i);
				try {
					Path backup = backups.get(destination);
					if (backup != null) moveReplace(backup, destination);
					else Files.deleteIfExists(destination);
				} catch (IOException rollbackFailure) {
					failure.addSuppressed(rollbackFailure);
				}
			}
			throw failure;
		} finally {
			for (Path backup : backups.values()) deleteQuietly(backup);
		}
	}

	private static void moveReplace(Path source, Path destination) throws IOException {
		try {
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// A leftover temp/backup is safer than damaging a successfully saved set.
		}
	}

	/**
	 * Writes a plain point .shp and its .shx index. Every geometry must be a
	 * single point or a null shape (a row without coordinates).
	 */
	public static void writePointShp(File shpFile, File shxFile,
	                                 List<ShpGeometry> geometries) throws IOException {
		int contentWords = 0;
		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		boolean anyPoint = false;
		for (ShpGeometry g : geometries) {
			if (g.isNull()) {
				contentWords += NULL_CONTENT_WORDS;
				continue;
			}
			if (g.getType().base() != ShapeType.POINT || g.getNumPoints() != 1) {
				throw new IllegalArgumentException("Only point or null records can be written: " + g);
			}
			contentWords += POINT_CONTENT_WORDS;
			anyPoint = true;
			minX = Math.min(minX, g.getX(0));
			maxX = Math.max(maxX, g.getX(0));
			minY = Math.min(minY, g.getY(0));
			maxY = Math.max(maxY, g.getY(0));
		}
		if (!anyPoint) {
			minX = minY = maxX = maxY = 0;
		}

		int shpWords = FILE_HEADER_SIZE_WORDS + RECORD_HEADER_WORDS * geometries.size() + contentWords;
		try (DataOutputStream out = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(shpFile)))) {
			out.write(fileHeader(shpWords, minX, minY, maxX, maxY));
			int recordNumber = 1;
			for (ShpGeometry g : geometries) {
				ByteBuffer record;
				if (g.isNull()) {
					record = ByteBuffer.allocate(8 + NULL_CONTENT_WORDS * 2);
					record.putInt(recordNumber).putInt(NULL_CONTENT_WORDS);
					record.order(ByteOrder.LITTLE_ENDIAN);
					record.putInt(ShapeType.NULL.getCode());
				} else {
					record = ByteBuffer.allocate(8 + POINT_CONTENT_WORDS * 2);
					record.putInt(recordNumber).putInt(POINT_CONTENT_WORDS);
					record.order(ByteOrder.LITTLE_ENDIAN);
					record.putInt(ShapeType.POINT.getCode());
					record.putDouble(g.getX(0)).putDouble(g.getY(0));
				}
				out.write(record.array());
				recordNumber++;
			}
		}

		int shxWords = FILE_HEADER_SIZE_WORDS + RECORD_HEADER_WORDS * geometries.size();
		try (DataOutputStream out = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(shxFile)))) {
			out.write(fileHeader(shxWords, minX, minY, maxX, maxY));
			int offsetWords = FILE_HEADER_SIZE_WORDS;
			for (ShpGeometry g : geometries) {
				int words = g.isNull() ? NULL_CONTENT_WORDS : POINT_CONTENT_WORDS;
				ByteBuffer record = ByteBuffer.allocate(8);
				record.putInt(offsetWords).putInt(words);
				out.write(record.array());
				offsetWords += RECORD_HEADER_WORDS + words;
			}
		}
	}

	private static byte[] fileHeader(int fileLengthWords, double minX, double minY,
	                                 double maxX, double maxY) {
		ByteBuffer buf = ByteBuffer.allocate(100); // big-endian by default
		buf.putInt(0, 9994);
		buf.putInt(24, fileLengthWords);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		buf.putInt(28, 1000); // version
		buf.putInt(32, ShapeType.POINT.getCode());
		buf.putDouble(36, minX);
		buf.putDouble(44, minY);
		buf.putDouble(52, maxX);
		buf.putDouble(60, maxY);
		// Z and M ranges (68..99) stay zero
		return buf.array();
	}

	/** Space-pads to the field length; numeric types are right-justified. */
	private static byte[] pad(String value, int length, char type) {
		byte[] cell = new byte[length];
		Arrays.fill(cell, (byte) ' ');
		byte[] bytes = value.getBytes(CHARSET);
		if (bytes.length > length) {
			throw new IllegalArgumentException("Value exceeds validated dBASE field width");
		}
		int copy = bytes.length;
		boolean rightJustified = type == 'N' || type == 'F';
		System.arraycopy(bytes, 0, cell, rightJustified ? length - copy : 0, copy);
		return cell;
	}

	private static void writeIntLE(byte[] b, int off, int v) {
		b[off] = (byte) v;
		b[off + 1] = (byte) (v >> 8);
		b[off + 2] = (byte) (v >> 16);
		b[off + 3] = (byte) (v >> 24);
	}

	private static void writeShortLE(byte[] b, int off, int v) {
		b[off] = (byte) v;
		b[off + 1] = (byte) (v >> 8);
	}
}
