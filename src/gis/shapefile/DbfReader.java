package gis.shapefile;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Streaming reader for the attribute table (.dbf) of an ESRI shapefile.
 * Every field value is returned as a trimmed String decoded with the
 * given charset (windows-1252 when none is given, the dBASE default).
 */
public class DbfReader implements Closeable {
	private static final Charset DEFAULT_CHARSET = Charset.forName("windows-1252");

	private final DataInputStream in;
	private final Charset charset;
	private final List<DbfField> fields;
	private final int recordCount;
	private final int recordSize;
	private int recordsRead = 0;
	private boolean lastRecordDeleted;

	public DbfReader(File dbfFile, Charset charset) throws IOException {
		this.charset = (charset != null) ? charset : DEFAULT_CHARSET;
		in = new DataInputStream(new BufferedInputStream(new FileInputStream(dbfFile)));
		try {
			byte[] header = new byte[32];
			in.readFully(header);
			recordCount = readIntLE(header, 4);
			int headerSize = readShortLE(header, 8);
			recordSize = readShortLE(header, 10);
			int numFields = (headerSize - 33) / 32;
			if (numFields < 0 || recordSize < 1) {
				throw new IOException("Corrupt .dbf header in " + dbfFile);
			}
			List<DbfField> fieldList = new ArrayList<DbfField>(numFields);
			byte[] desc = new byte[32];
			for (int i = 0; i < numFields; i++) {
				in.readFully(desc);
				fieldList.add(parseFieldDescriptor(desc));
			}
			fields = Collections.unmodifiableList(fieldList);
			// Skip the header terminator (0x0D) and any vendor extras
			long extra = headerSize - 32 - 32L * numFields;
			for (long skipped = 0; skipped < extra; ) {
				long n = in.skip(extra - skipped);
				if (n <= 0) throw new IOException("Corrupt .dbf header in " + dbfFile);
				skipped += n;
			}
		} catch (IOException e) {
			in.close();
			throw e;
		}
	}

	private DbfField parseFieldDescriptor(byte[] desc) {
		int nameLen = 0;
		while (nameLen < 11 && desc[nameLen] != 0) nameLen++;
		String name = new String(desc, 0, nameLen, charset);
		char type = (char) (desc[11] & 0xFF);
		int length = desc[16] & 0xFF;
		int decimalCount = desc[17] & 0xFF;
		return new DbfField(name, type, length, decimalCount);
	}

	public List<DbfField> getFields() {
		return fields;
	}

	/** @return the column index of {@code fieldName} (case-insensitive), or -1. */
	public int fieldIndex(String fieldName) {
		for (int i = 0; i < fields.size(); i++) {
			if (fields.get(i).name.equalsIgnoreCase(fieldName)) return i;
		}
		return -1;
	}

	public int getRecordCount() {
		return recordCount;
	}

	public boolean hasNext() {
		return recordsRead < recordCount;
	}

	/**
	 * @return the next record's field values, or null at end of table.
	 *         Records flagged as deleted are returned too, to stay in
	 *         step with the .shp file's records.
	 */
	public String[] next() throws IOException {
		if (!hasNext()) {
			lastRecordDeleted = false;
			return null;
		}
		byte[] record = new byte[recordSize];
		in.readFully(record);
		recordsRead++;
		lastRecordDeleted = record[0] == 0x2A;
		String[] values = new String[fields.size()];
		int offset = 1; // skip the deletion flag
		for (int i = 0; i < fields.size(); i++) {
			int len = fields.get(i).length;
			values[i] = new String(record, offset, len, charset).trim();
			offset += len;
		}
		return values;
	}

	/** Whether the record returned by the most recent {@link #next()} was deleted. */
	public boolean wasLastRecordDeleted() {
		return lastRecordDeleted;
	}

	@Override
	public void close() throws IOException {
		in.close();
	}

	private static int readIntLE(byte[] b, int off) {
		return (b[off] & 0xFF) | (b[off + 1] & 0xFF) << 8
				| (b[off + 2] & 0xFF) << 16 | (b[off + 3] & 0xFF) << 24;
	}

	private static int readShortLE(byte[] b, int off) {
		return (b[off] & 0xFF) | (b[off + 1] & 0xFF) << 8;
	}
}
