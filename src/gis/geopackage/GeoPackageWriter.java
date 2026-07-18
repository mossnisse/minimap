package gis.geopackage;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.sqlite.Function;

/**
 * Writes table edits back to a GeoPackage file. All changes run in one
 * transaction: nothing is committed until {@link #commit()}, and closing
 * without committing rolls everything back.
 *
 * GeoPackages with a spatial index carry RTree triggers that call the
 * ST_IsEmpty/ST_MinX/... SQL functions of the gpkg extension, which plain
 * SQLite doesn't have; the constructor registers implementations of them
 * (backed by {@link GpkgGeometry#parse}) so those triggers keep working and
 * the spatial index stays in sync with the edits.
 */
public class GeoPackageWriter implements Closeable {

	private final Connection conn;
	private boolean committed = false;

	public GeoPackageWriter(String fileName) throws IOException {
		// sqlite would silently create a missing file, so check first
		if (!new File(fileName).isFile()) {
			throw new IOException("File not found: " + fileName);
		}
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			throw new IOException("SQLite JDBC driver not on the classpath;"
					+ " add lib/sqlite-jdbc-*.jar to the run configuration", e);
		}
		try {
			conn = DriverManager.getConnection("jdbc:sqlite:" + fileName);
			registerGeometryFunctions(conn);
			conn.setAutoCommit(false);
		} catch (SQLException e) {
			throw new IOException("Can't open " + fileName + " for writing: " + e.getMessage(), e);
		}
	}

	/** The ST_* functions the standard GeoPackage RTree triggers call. */
	private static void registerGeometryFunctions(Connection conn) throws SQLException {
		Function.create(conn, "ST_IsEmpty", new GeometryFunction() {
			@Override
			protected double compute(GpkgGeometry g) {
				return g.isEmpty() ? 1 : 0;
			}
		});
		Function.create(conn, "ST_MinX", new GeometryFunction() {
			@Override
			protected double compute(GpkgGeometry g) {
				return g.getMinX();
			}
		});
		Function.create(conn, "ST_MaxX", new GeometryFunction() {
			@Override
			protected double compute(GpkgGeometry g) {
				return g.getMaxX();
			}
		});
		Function.create(conn, "ST_MinY", new GeometryFunction() {
			@Override
			protected double compute(GpkgGeometry g) {
				return g.getMinY();
			}
		});
		Function.create(conn, "ST_MaxY", new GeometryFunction() {
			@Override
			protected double compute(GpkgGeometry g) {
				return g.getMaxY();
			}
		});
	}

	private abstract static class GeometryFunction extends Function {
		protected abstract double compute(GpkgGeometry g);

		@Override
		protected void xFunc() throws SQLException {
			byte[] blob = value_blob(0);
			if (blob == null) {
				result(); // SQL NULL
				return;
			}
			try {
				result(compute(GpkgGeometry.parse(blob)));
			} catch (IOException e) {
				result();
			}
		}
	}

	// ---- schema changes ----

	public void addColumn(String table, String name) throws IOException {
		execute("ALTER TABLE " + quote(table) + " ADD COLUMN " + quote(name) + " TEXT");
	}

	public void renameColumn(String table, String from, String to) throws IOException {
		execute("ALTER TABLE " + quote(table) + " RENAME COLUMN " + quote(from) + " TO " + quote(to));
	}

	public void dropColumn(String table, String name) throws IOException {
		execute("ALTER TABLE " + quote(table) + " DROP COLUMN " + quote(name));
	}

	// ---- row changes ----

	public void deleteRow(String table, long rowid) throws IOException {
		try (PreparedStatement stmt = conn.prepareStatement(
				"DELETE FROM " + quote(table) + " WHERE rowid = ?")) {
			stmt.setLong(1, rowid);
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw wrap(e);
		}
	}

	/** Updates the given columns while preserving their supplied JDBC values. */
	public void updateAttributes(String table, long rowid, List<String> columns,
	                             List<?> values) throws IOException {
		if (columns.isEmpty()) return;
		StringBuilder sql = new StringBuilder("UPDATE ").append(quote(table)).append(" SET ");
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) sql.append(", ");
			sql.append(quote(columns.get(i))).append(" = ?");
		}
		sql.append(" WHERE rowid = ?");
		try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
			for (int i = 0; i < values.size(); i++) {
				bind(stmt, i + 1, values.get(i));
			}
			stmt.setLong(values.size() + 1, rowid);
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw wrap(e);
		}
	}

	/** Replaces the row's geometry BLOB; null removes the geometry. */
	public void updateGeometry(String table, String geometryColumn, long rowid,
	                           byte[] blob) throws IOException {
		try (PreparedStatement stmt = conn.prepareStatement(
				"UPDATE " + quote(table) + " SET " + quote(geometryColumn) + " = ? WHERE rowid = ?")) {
			stmt.setBytes(1, blob);
			stmt.setLong(2, rowid);
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw wrap(e);
		}
	}

	/**
	 * Inserts a row with the given attributes and geometry BLOB (may be null).
	 * @return the new row's rowid
	 */
	public long insertRow(String table, List<String> columns, List<?> values,
	                      String geometryColumn, byte[] blob) throws IOException {
		StringBuilder sql = new StringBuilder("INSERT INTO ").append(quote(table)).append(" (");
		for (String column : columns) {
			sql.append(quote(column)).append(", ");
		}
		sql.append(quote(geometryColumn)).append(") VALUES (");
		sql.append("?, ".repeat(columns.size())).append("?)");
		try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
			for (int i = 0; i < values.size(); i++) {
				bind(stmt, i + 1, values.get(i));
			}
			stmt.setBytes(values.size() + 1, blob);
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw wrap(e);
		}
		try (Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
			rs.next();
			return rs.getLong(1);
		} catch (SQLException e) {
			throw wrap(e);
		}
	}

	/** Stamps the table's gpkg_contents row with the current time. */
	public void touchLastChange(String table) throws IOException {
		try (PreparedStatement stmt = conn.prepareStatement(
				"UPDATE gpkg_contents SET last_change = strftime('%Y-%m-%dT%H:%M:%fZ','now')"
						+ " WHERE table_name = ?")) {
			stmt.setString(1, table);
			stmt.executeUpdate();
		} catch (SQLException e) {
			throw wrap(e);
		}
	}

	public void commit() throws IOException {
		try {
			conn.commit();
			committed = true;
		} catch (SQLException e) {
			throw wrap(e);
		}
	}

	@Override
	public void close() throws IOException {
		try {
			if (!committed) {
				conn.rollback();
			}
		} catch (SQLException e) {
			throw wrap(e);
		} finally {
			try {
				conn.close();
			} catch (SQLException e) {
				// closing after rollback failed too; nothing left to do
			}
		}
	}

	private void execute(String sql) throws IOException {
		try (Statement stmt = conn.createStatement()) {
			stmt.execute(sql);
		} catch (SQLException e) {
			throw wrap(e);
		}
	}

	private static void bind(PreparedStatement stmt, int index, Object value) throws SQLException {
		if (value == null) {
			stmt.setObject(index, null);
		} else if (value instanceof byte[] bytes) {
			stmt.setBytes(index, bytes);
		} else {
			stmt.setObject(index, value);
		}
	}

	private static IOException wrap(SQLException e) {
		return new IOException(e.getMessage(), e);
	}

	private static String quote(String identifier) {
		return "\"" + identifier.replace("\"", "\"\"") + "\"";
	}
}
