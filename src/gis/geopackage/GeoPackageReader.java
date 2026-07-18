package gis.geopackage;

import gis.coords.CoordSystem;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Read-only access to the vector feature tables of a GeoPackage (.gpkg)
 * file: a SQLite database whose gpkg_contents/gpkg_geometry_columns tables
 * describe feature tables with a {@link GpkgGeometry} BLOB column.
 * Requires the sqlite-jdbc driver on the classpath; all SQLExceptions are
 * wrapped in IOException to match the other file readers.
 */
public class GeoPackageReader implements Closeable {

	/** One row of gpkg_contents/gpkg_geometry_columns describing a feature table. */
	public static class FeatureTable {
		public final String tableName;
		public final String identifier;
		public final String geometryColumn;
		public final String geometryTypeName;
		public final int srsId;

		FeatureTable(String tableName, String identifier, String geometryColumn,
				String geometryTypeName, int srsId) {
			this.tableName = tableName;
			this.identifier = identifier;
			this.geometryColumn = geometryColumn;
			this.geometryTypeName = geometryTypeName;
			this.srsId = srsId;
		}

		/** Shown as-is in the GUI's table picker. */
		@Override
		public String toString() {
			return tableName + " (" + geometryTypeName + ")";
		}
	}

	/** One feature row: parsed geometry plus all other columns as strings. */
	public static class Feature {
		public final GpkgGeometry geometry;
		public final String[] attributes;

		Feature(GpkgGeometry geometry, String[] attributes) {
			this.geometry = geometry;
			this.attributes = attributes;
		}
	}

	/**
	 * One row read for editing: its SQLite rowid, its geometry (null when
	 * NULL, unsupported or corrupt) and all other columns in their JDBC-native
	 * representation. Keeping the native values is important for BLOB columns
	 * and for preserving the distinction between SQL NULL and empty text.
	 */
	public static class Row {
		public final long rowid;
		public final GpkgGeometry geometry;
		public final Object[] attributes;

		Row(long rowid, GpkgGeometry geometry, Object[] attributes) {
			this.rowid = rowid;
			this.geometry = geometry;
			this.attributes = attributes;
		}
	}

	private final String fileName;
	private final Connection conn;
	private List<FeatureTable> featureTables;

	public GeoPackageReader(String fileName) throws IOException {
		// sqlite would silently create a missing file, so check first
		if (!new File(fileName).isFile()) {
			throw new IOException("File not found: " + fileName);
		}
		this.fileName = fileName;
		try {
			// Fails with a clear message when lib/sqlite-jdbc-*.jar is missing
			// from the classpath, instead of DriverManager's "no suitable driver"
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			throw new IOException("SQLite JDBC driver not on the classpath;"
					+ " add lib/sqlite-jdbc-*.jar to the run configuration", e);
		}
		try {
			Properties props = new Properties();
			props.setProperty("open_mode", "1"); // SQLITE_OPEN_READONLY
			conn = DriverManager.getConnection("jdbc:sqlite:" + fileName, props);
		} catch (SQLException e) {
			throw new IOException("Can't open " + fileName + ": " + e.getMessage(), e);
		}
	}

	/** The feature tables declared in gpkg_contents, in table-name order. */
	public List<FeatureTable> getFeatureTables() throws IOException {
		if (featureTables != null) {
			return featureTables;
		}
		List<FeatureTable> tables = new ArrayList<FeatureTable>();
		String sql = "SELECT c.table_name, c.identifier, g.column_name, g.geometry_type_name, g.srs_id"
				+ " FROM gpkg_contents c JOIN gpkg_geometry_columns g ON c.table_name = g.table_name"
				+ " WHERE c.data_type = 'features' ORDER BY c.table_name";
		try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			while (rs.next()) {
				tables.add(new FeatureTable(rs.getString(1), rs.getString(2),
						rs.getString(3), rs.getString(4), rs.getInt(5)));
			}
		} catch (SQLException e) {
			throw new IOException("Not a GeoPackage file (no readable gpkg_contents table): "
					+ fileName, e);
		}
		featureTables = tables;
		return featureTables;
	}

	/**
	 * Maps the table's spatial reference system to a supported CoordSystem
	 * via its EPSG code in gpkg_spatial_ref_sys, or null when the SRS is
	 * missing or not one we can project (the caller should ask the user).
	 */
	public CoordSystem guessCRS(FeatureTable table) throws IOException {
		String sql = "SELECT organization, organization_coordsys_id FROM gpkg_spatial_ref_sys WHERE srs_id = ?";
		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setInt(1, table.srsId);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					String organization = rs.getString(1);
					if (organization != null && organization.equalsIgnoreCase("EPSG")) {
						return epsgToCRS(rs.getInt(2));
					}
					return null;
				}
			}
		} catch (SQLException e) {
			throw new IOException("Can't read gpkg_spatial_ref_sys: " + e.getMessage(), e);
		}
		// No SRS row; files commonly use the EPSG code directly as srs_id
		return epsgToCRS(table.srsId);
	}

	private static CoordSystem epsgToCRS(int epsg) {
		switch (epsg) {
			case 4326:
				return CoordSystem.WGS84;
			case 3857:
			case 900913:
				return CoordSystem.WEB_MERCATOR;
			case 3006:
				return CoordSystem.SWEREF99TM;
			case 2400:
			case 3021:
			case 3847:
				return CoordSystem.RT90;
			default:
				return null;
		}
	}

	/** The table's column names in order, excluding the geometry column. */
	public String[] getFieldNames(FeatureTable table) throws IOException {
		try (Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT * FROM " + quote(table.tableName) + " LIMIT 0")) {
			ResultSetMetaData meta = rs.getMetaData();
			List<String> names = new ArrayList<String>();
			for (int i = 1; i <= meta.getColumnCount(); i++) {
				String name = meta.getColumnName(i);
				if (!name.equalsIgnoreCase(table.geometryColumn)) {
					names.add(name);
				}
			}
			return names.toArray(new String[0]);
		} catch (SQLException e) {
			throw new IOException("Can't read table " + table.tableName + ": " + e.getMessage(), e);
		}
	}

	/** JDBC types for {@link #getFieldNames}, in the same order. */
	public int[] getFieldTypes(FeatureTable table) throws IOException {
		try (Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT * FROM " + quote(table.tableName) + " LIMIT 0")) {
			ResultSetMetaData meta = rs.getMetaData();
			List<Integer> types = new ArrayList<Integer>();
			for (int i = 1; i <= meta.getColumnCount(); i++) {
				if (!meta.getColumnName(i).equalsIgnoreCase(table.geometryColumn)) {
					types.add(meta.getColumnType(i));
				}
			}
			int[] result = new int[types.size()];
			for (int i = 0; i < result.length; i++) result[i] = types.get(i);
			return result;
		} catch (SQLException e) {
			throw new IOException("Can't read table " + table.tableName + ": " + e.getMessage(), e);
		}
	}

	/**
	 * Reads all rows of a feature table. Rows with a NULL, empty,
	 * unsupported (e.g. GeometryCollection, curve types) or corrupt
	 * geometry are skipped, so one bad row can't fail the whole table.
	 */
	public List<Feature> readFeatures(FeatureTable table) throws IOException {
		List<Feature> features = new ArrayList<Feature>();
		int unreadable = 0;
		try (Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT * FROM " + quote(table.tableName))) {
			ResultSetMetaData meta = rs.getMetaData();
			int columns = meta.getColumnCount();
			int geomIdx = -1;
			for (int i = 1; i <= columns; i++) {
				if (meta.getColumnName(i).equalsIgnoreCase(table.geometryColumn)) {
					geomIdx = i;
					break;
				}
			}
			if (geomIdx == -1) {
				throw new IOException("Geometry column '" + table.geometryColumn
						+ "' not found in table " + table.tableName);
			}
			while (rs.next()) {
				byte[] blob = rs.getBytes(geomIdx);
				if (blob == null) {
					continue;
				}
				GpkgGeometry geometry;
				try {
					geometry = GpkgGeometry.parse(blob);
				} catch (IOException e) {
					unreadable++;
					continue;
				}
				if (geometry.isEmpty()) {
					continue;
				}
				String[] attributes = new String[columns - 1];
				int a = 0;
				for (int i = 1; i <= columns; i++) {
					if (i == geomIdx) {
						continue;
					}
					String value = rs.getString(i);
					attributes[a++] = (value != null) ? value : "";
				}
				features.add(new Feature(geometry, attributes));
			}
		} catch (SQLException e) {
			throw new IOException("Can't read table " + table.tableName + ": " + e.getMessage(), e);
		}
		if (unreadable > 0) {
			System.err.println("GeoPackage " + fileName + ", table " + table.tableName
					+ ": skipped " + unreadable + " unsupported or corrupt geometries");
		}
		return features;
	}

	/**
	 * The table's INTEGER PRIMARY KEY column name (the GeoPackage fid), or
	 * null when the table has none. Its values alias the SQLite rowid, so
	 * editors should treat the column as read-only.
	 */
	public String getPrimaryKeyColumn(FeatureTable table) throws IOException {
		try (Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + quote(table.tableName) + ")")) {
			while (rs.next()) {
				if (rs.getInt("pk") == 1 && "INTEGER".equalsIgnoreCase(rs.getString("type"))) {
					return rs.getString("name");
				}
			}
			return null;
		} catch (SQLException e) {
			throw new IOException("Can't read table info for " + table.tableName + ": " + e.getMessage(), e);
		}
	}

	/**
	 * Reads all rows of a feature table for editing: unlike
	 * {@link #readFeatures}, rows with a NULL, unsupported or corrupt
	 * geometry are included (with a null geometry), and every row carries
	 * its rowid so edits can be written back.
	 */
	public List<Row> readRows(FeatureTable table) throws IOException {
		List<Row> rows = new ArrayList<Row>();
		try (Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(
						"SELECT rowid AS __rid, * FROM " + quote(table.tableName))) {
			ResultSetMetaData meta = rs.getMetaData();
			int columns = meta.getColumnCount();
			int geomIdx = -1;
			for (int i = 2; i <= columns; i++) { // column 1 is __rid
				if (meta.getColumnName(i).equalsIgnoreCase(table.geometryColumn)) {
					geomIdx = i;
					break;
				}
			}
			if (geomIdx == -1) {
				throw new IOException("Geometry column '" + table.geometryColumn
						+ "' not found in table " + table.tableName);
			}
			while (rs.next()) {
				long rowid = rs.getLong(1);
				GpkgGeometry geometry = null;
				byte[] blob = rs.getBytes(geomIdx);
				if (blob != null) {
					try {
						GpkgGeometry parsed = GpkgGeometry.parse(blob);
						if (!parsed.isEmpty()) {
							geometry = parsed;
						}
					} catch (IOException e) {
						// Corrupt blob: keep the row, just without geometry
					}
				}
				Object[] attributes = new Object[columns - 2];
				int a = 0;
				for (int i = 2; i <= columns; i++) {
					if (i == geomIdx) {
						continue;
					}
					attributes[a++] = rs.getObject(i);
				}
				rows.add(new Row(rowid, geometry, attributes));
			}
		} catch (SQLException e) {
			throw new IOException("Can't read table " + table.tableName + ": " + e.getMessage(), e);
		}
		return rows;
	}

	private static String quote(String identifier) {
		return "\"" + identifier.replace("\"", "\"\"") + "\"";
	}

	@Override
	public void close() throws IOException {
		try {
			conn.close();
		} catch (SQLException e) {
			throw new IOException(e);
		}
	}
}
