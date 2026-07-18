package gis.shapefile;

import gis.coords.CoordSystem;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

/**
 * Reads an ESRI shapefile as a stream of features: geometry from the .shp
 * file paired with attributes from the .dbf table. The attribute charset
 * is taken from a .cpg sidecar file when present. Any path into the
 * shapefile set (base name, .shp, .dbf, ...) can be given.
 *
 * <pre>
 * try (ShapefileReader r = new ShapefileReader("borders.shp")) {
 *     for (ShapefileReader.Feature f : r) { ... }
 * }
 * </pre>
 */
public class ShapefileReader implements Closeable, Iterable<ShapefileReader.Feature> {

	/** One shapefile record: its geometry and its attribute row (never null, possibly empty). */
	public static class Feature {
		private static final String[] NO_ATTRIBUTES = {};
		public final ShpGeometry geometry;
		public final String[] attributes;
		public final boolean deleted;

		Feature(ShpGeometry geometry, String[] attributes, boolean deleted) {
			this.geometry = geometry;
			this.attributes = (attributes != null) ? attributes : NO_ATTRIBUTES;
			this.deleted = deleted;
		}

		public String toString() {
			return geometry + " " + java.util.Arrays.toString(attributes);
		}
	}

	private final ShpReader shp;
	private final DbfReader dbf; // null when there is no .dbf file

	public ShapefileReader(String filename) throws IOException {
		String base = stripExtension(filename);
		shp = new ShpReader(new File(base + ".shp"));
		File dbfFile = new File(base + ".dbf");
		if (dbfFile.exists()) {
			DbfReader reader;
			try {
				reader = new DbfReader(dbfFile, readCpgCharset(base));
			} catch (IOException e) {
				shp.close();
				throw e;
			}
			dbf = reader;
		} else {
			dbf = null;
		}
	}

	public ShapeType getShapeType() {
		return shp.getShapeType();
	}

	public double getMinX() { return shp.getMinX(); }
	public double getMinY() { return shp.getMinY(); }
	public double getMaxX() { return shp.getMaxX(); }
	public double getMaxY() { return shp.getMaxY(); }

	/** Attribute column descriptors; empty when there is no .dbf file. */
	public List<DbfField> getFields() {
		return (dbf != null) ? dbf.getFields() : Collections.<DbfField>emptyList();
	}

	/** @return the column index of {@code fieldName} (case-insensitive), or -1. */
	public int fieldIndex(String fieldName) {
		return (dbf != null) ? dbf.fieldIndex(fieldName) : -1;
	}

	public boolean hasNext() {
		return shp.hasNext();
	}

	/** @return the next feature, or null at end of file. */
	public Feature next() throws IOException {
		ShpGeometry geometry = shp.next();
		if (geometry == null) return null;
		String[] attributes = (dbf != null) ? dbf.next() : null;
		boolean deleted = dbf != null && dbf.wasLastRecordDeleted();
		return new Feature(geometry, attributes, deleted);
	}

	@Override
	public void close() throws IOException {
		try {
			shp.close();
		} finally {
			if (dbf != null) dbf.close();
		}
	}

	@Override
	public Iterator<Feature> iterator() {
		return new Iterator<Feature>() {
			@Override
			public boolean hasNext() {
				return ShapefileReader.this.hasNext();
			}

			@Override
			public Feature next() {
				try {
					Feature f = ShapefileReader.this.next();
					if (f == null) throw new NoSuchElementException();
					return f;
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
		};
	}

	/** Reads the whole shapefile into memory and closes it. */
	public static List<Feature> readAll(String filename) throws IOException {
		List<Feature> features = new ArrayList<Feature>();
		try (ShapefileReader reader = new ShapefileReader(filename)) {
			Feature f;
			while ((f = reader.next()) != null) {
				features.add(f);
			}
		}
		return features;
	}

	/**
	 * Guesses the coordinate system from the .prj sidecar file.
	 * @return the matching CoordSystem, or null when there is no .prj
	 *         file or its projection is not one the app supports.
	 */
	public static CoordSystem guessCRS(String filename) {
		File prj = new File(stripExtension(filename) + ".prj");
		if (!prj.exists()) return null;
		String wkt;
		try {
			wkt = new String(Files.readAllBytes(prj.toPath()), "US-ASCII")
					.toUpperCase(Locale.ROOT);
		} catch (IOException e) {
			return null;
		}
		if (wkt.contains("SWEREF99_TM") || wkt.contains("SWEREF99 TM")) {
			return CoordSystem.SWEREF99TM;
		}
		if (wkt.contains("RT90")) {
			return CoordSystem.RT90;
		}
		if (wkt.contains("3857") || wkt.contains("PSEUDO-MERCATOR") || wkt.contains("WEB_MERCATOR")) {
			return CoordSystem.WEB_MERCATOR;
		}
		// Plain geographic WGS84 (no projection)
		if (wkt.startsWith("GEOGCS") && (wkt.contains("WGS_1984") || wkt.contains("WGS 84") || wkt.contains("WGS84"))) {
			return CoordSystem.WGS84;
		}
		return null;
	}

	private static Charset readCpgCharset(String base) {
		File cpg = new File(base + ".cpg");
		if (!cpg.exists()) return null;
		try {
			String name = new String(Files.readAllBytes(cpg.toPath()), "US-ASCII").trim();
			String upper = name.toUpperCase(Locale.ROOT);
			if (upper.contains("UTF-8") || upper.contains("UTF8")) return Charset.forName("UTF-8");
			if (upper.contains("8859")) return Charset.forName("ISO-8859-1");
			if (upper.contains("1252")) return Charset.forName("windows-1252");
			return Charset.forName(name);
		} catch (Exception e) {
			return null; // unknown charset name: fall back to the dBASE default
		}
	}

	/** Strips a shapefile-set extension (.shp, .dbf, ...) to get the base path. */
	public static String stripExtension(String filename) {
		String lower = filename.toLowerCase(Locale.ROOT);
		for (String ext : new String[]{".shp", ".dbf", ".shx", ".prj", ".cpg"}) {
			if (lower.endsWith(ext)) {
				return filename.substring(0, filename.length() - ext.length());
			}
		}
		return filename;
	}
}
