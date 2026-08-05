package app.plugin.herbarium;

import gis.coords.Coordinate;
import app.db.Database;
import gis.geometry.Extent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** All SQL against the MySQL {@code locality} table lives here. */
public final class LocalityRepository {
	private final Database db;

	public LocalityRepository(Database db) {
		this.db = db;
	}

	/** The editable fields of a locality, shared by the create and edit dialogs. */
	public record LocalityDetails(String locality, String alternativeNames, String district,
			String province, String country, String continent, String coordinateSource,
			String comments, int precisionMeters, String category, int zoomLevel, boolean isPlace) {}

	/**
	 * A stored locality as loaded for the edit dialog. String fields are the raw
	 * column values (may be null for empty columns).
	 */
	public record StoredLocality(String locality, String alternativeNames, String district,
			String province, String country, String continent, String coordinateSource,
			String comments, String created, String createdBy, String modified, String modifiedBy,
			String precision, String category, String zoomLevel, boolean isPlace) {}

	/** A locality position in the three projections the table stores. */
	public record StoredCoordinates(Coordinate wgs84, Coordinate sweref99tm, Coordinate rt90) {}

	/** A point for map rendering; {@code wgs84} is the stored position. */
	public record LocalityPoint(int id, String name, Coordinate wgs84, int precisionMeters) {}

	/** A search hit; the label is built by the caller. */
	public record SearchHit(int id, String locality, String district, Coordinate wgs84) {}

	/** Search filters; text fields use '*' wildcards, {@code province} of "*" means any. */
	public record SearchCriteria(String name, String country, String district, String source,
			String precision, String category, String province, boolean isPlaceOnly) {}

	/** ID of the nearest locality within {@code limitInMeters} of {@code clickWgs84}, or -1. */
	public int findNearestId(Coordinate clickWgs84, int limitInMeters) {
		// Approximate degree offset (very rough: 1 degree ~ 111km)
		// For better accuracy at high latitudes, longitude needs a cos(lat) adjustment
		double degOffset = limitInMeters / 111320.0;
		double latRad = Math.toRadians(clickWgs84.getNorth());
		double lonOffset = degOffset / Math.cos(latRad);

		String sqlstmt = "SELECT lat, `long`, ID FROM locality WHERE lat BETWEEN ? AND ? AND `long` BETWEEN ? AND ?";

		try {
			Connection conn = db.mysql();
			try (PreparedStatement statement = conn.prepareStatement(sqlstmt)) {
				statement.setDouble(1, clickWgs84.getNorth() - degOffset);
				statement.setDouble(2, clickWgs84.getNorth() + degOffset);
				statement.setDouble(3, clickWgs84.getEast() - lonOffset);
				statement.setDouble(4, clickWgs84.getEast() + lonOffset);

				try (ResultSet result = statement.executeQuery()) {
					double minDistance = limitInMeters; // Don't accept anything outside the limit
					int nearestID = -1;

					while (result.next()) {
						Coordinate recordWgs84 = new Coordinate(result.getDouble(1), result.getDouble(2));
						double actualDist = clickWgs84.distanceWGS84(recordWgs84);

						if (actualDist < minDistance) {
							minDistance = actualDist;
							nearestID = result.getInt(3);
						}
					}
					return nearestID;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return -1;
	}

	/** All localities inside {@code wgs84Bounds}, for map rendering. */
	public List<LocalityPoint> findInBounds(Extent wgs84Bounds) throws SQLException {
		List<LocalityPoint> result = new ArrayList<>();
		Connection conn = db.mysql();
		try (PreparedStatement pstmt = conn.prepareStatement(
				"SELECT lat, `long`, locality, Coordinateprecision, id FROM locality " +
						"WHERE lat BETWEEN ? AND ? AND `long` BETWEEN ? AND ?")) {

			pstmt.setDouble(1, Math.min(wgs84Bounds.c1.getNorth(), wgs84Bounds.c2.getNorth()));
			pstmt.setDouble(2, Math.max(wgs84Bounds.c1.getNorth(), wgs84Bounds.c2.getNorth()));
			pstmt.setDouble(3, Math.min(wgs84Bounds.c1.getEast(), wgs84Bounds.c2.getEast()));
			pstmt.setDouble(4, Math.max(wgs84Bounds.c1.getEast(), wgs84Bounds.c2.getEast()));

			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					result.add(new LocalityPoint(
							rs.getInt("id"),
							rs.getString("locality"),
							new Coordinate(rs.getDouble("lat"), rs.getDouble("long")),
							rs.getInt("Coordinateprecision")));
				}
			}
		}
		return result;
	}

	/** Loads a locality by id, or null if it doesn't exist. */
	public StoredLocality load(int id) throws SQLException {
		String sql = "SELECT locality, alternative_names, district, province, country, continent, " +
				"coordinate_source, lcomments, created, createdBy, modified, modifiedBy, " +
				"Coordinateprecision, category, zoomLevel, isPlace FROM locality WHERE ID = ?";
		Connection conn = db.mysql();
		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setInt(1, id);
			try (ResultSet rs = stmt.executeQuery()) {
				if (!rs.next()) return null;
				return new StoredLocality(
						rs.getString("locality"),
						rs.getString("alternative_names"),
						rs.getString("district"),
						rs.getString("province"),
						rs.getString("country"),
						rs.getString("continent"),
						rs.getString("coordinate_source"),
						rs.getString("lcomments"),
						rs.getString("created"),
						rs.getString("createdBy"),
						rs.getString("modified"),
						rs.getString("modifiedBy"),
						rs.getString("Coordinateprecision"),
						rs.getString("category"),
						rs.getString("zoomLevel"),
						rs.getInt("isPlace") == 1);
			}
		}
	}

	/** True if a locality with this name/district/province/country already exists. */
	public boolean exists(String locality, String district, String province, String country) throws SQLException {
		String sql = "SELECT 1 FROM locality WHERE locality = ? AND district = ? AND province = ? AND country = ? LIMIT 1";
		Connection conn = db.mysql();
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, locality.trim());
			ps.setString(2, district.trim());
			ps.setString(3, province.trim());
			ps.setString(4, country.trim());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	public void insert(LocalityDetails d, StoredCoordinates coords, String createdBy) throws SQLException {
		String sqlstmt = "INSERT INTO locality (locality, district, province, country, continent, lat, `long`, " +
				"RT90N, RT90E, SWTMN, SWTME, createdby, alternative_names, coordinate_source, lcomments, " +
				"Coordinateprecision, category, zoomLevel, isPlace) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

		Connection conn = db.mysql();
		try (PreparedStatement stmt = conn.prepareStatement(sqlstmt)) {
			stmt.setString(1, d.locality());
			stmt.setString(2, d.district());
			stmt.setString(3, d.province());
			stmt.setString(4, d.country());
			stmt.setString(5, d.continent());
			stmt.setDouble(6, coords.wgs84().getNorth());
			stmt.setDouble(7, coords.wgs84().getEast());
			stmt.setInt(8, (int) Math.round(coords.rt90().getNorth()));
			stmt.setInt(9, (int) Math.round(coords.rt90().getEast()));
			stmt.setInt(10, (int) Math.round(coords.sweref99tm().getNorth()));
			stmt.setInt(11, (int) Math.round(coords.sweref99tm().getEast()));
			stmt.setString(12, createdBy);
			stmt.setString(13, d.alternativeNames());
			stmt.setString(14, d.coordinateSource());
			stmt.setString(15, d.comments());
			stmt.setInt(16, d.precisionMeters());
			stmt.setString(17, d.category());
			stmt.setInt(18, d.zoomLevel());
			stmt.setBoolean(19, d.isPlace());

			stmt.executeUpdate();
		}
	}

	/** Updates a locality; {@code moved} is non-null only when the position changed. */
	public void update(int id, LocalityDetails d, StoredCoordinates moved, String modifiedBy) throws SQLException {
		StringBuilder sql = new StringBuilder("UPDATE locality SET locality=?, district=?, province=?, country=?, continent=?, " +
				"alternative_names=?, coordinate_source=?, lcomments=?, modified=NOW(), modifiedBy=?, " +
				"Coordinateprecision=?, category=?, zoomLevel=?, isPlace=?");
		if (moved != null) {
			sql.append(", lat=?, `long`=?, SWTMN=?, SWTME=?, RT90N=?, RT90E=?");
		}
		sql.append(" WHERE ID=?");

		Connection conn = db.mysql();
		try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
			int paramIdx = 1;
			stmt.setString(paramIdx++, d.locality());
			stmt.setString(paramIdx++, d.district());
			stmt.setString(paramIdx++, d.province());
			stmt.setString(paramIdx++, d.country());
			stmt.setString(paramIdx++, d.continent());
			stmt.setString(paramIdx++, d.alternativeNames());
			stmt.setString(paramIdx++, d.coordinateSource());
			stmt.setString(paramIdx++, d.comments());
			stmt.setString(paramIdx++, modifiedBy);
			stmt.setInt(paramIdx++, d.precisionMeters());
			stmt.setString(paramIdx++, d.category());
			stmt.setInt(paramIdx++, d.zoomLevel());
			stmt.setBoolean(paramIdx++, d.isPlace());

			if (moved != null) {
				stmt.setDouble(paramIdx++, moved.wgs84().getNorth());
				stmt.setDouble(paramIdx++, moved.wgs84().getEast());
				stmt.setInt(paramIdx++, (int) Math.round(moved.sweref99tm().getNorth()));
				stmt.setInt(paramIdx++, (int) Math.round(moved.sweref99tm().getEast()));
				stmt.setInt(paramIdx++, (int) Math.round(moved.rt90().getNorth()));
				stmt.setInt(paramIdx++, (int) Math.round(moved.rt90().getEast()));
			}

			stmt.setInt(paramIdx, id);
			stmt.executeUpdate();
		}
	}

	public void delete(int id) throws SQLException {
		Connection conn = db.mysql();
		try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM locality WHERE ID = ?")) {
			stmt.setInt(1, id);
			stmt.execute();
		}
	}

	/** How many specimen records reference this locality by name, or -1 on error. */
	public int countSpecimenUses(String province, String district, String locality) {
		try {
			Connection conn = db.mysql();
			String checkSql = "SELECT count(*) FROM specimens where province = ? AND district = ? AND locality = ? ";
			try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
				stmt.setString(1, province);
				stmt.setString(2, district);
				stmt.setString(3, locality);
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						return rs.getInt(1);
					}
				}
			}
		} catch (SQLException e) {
			System.err.println("Error checking specimen locality usage: " + e.getMessage());
		}
		return -1;
	}

	/** How many specimen bridges link to this locality, or -1 on error. */
	public int countBridgeUses(int localityId) {
		try {
			Connection conn = db.mysql();
			String checkSql = "SELECT count(*) from specimen_locality where specimen_locality.locality_ID = ?";
			try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
				stmt.setInt(1, localityId);
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						return rs.getInt(1);
					}
				}
			}
		} catch (SQLException e) {
			System.err.println("Error checking bridge usage: " + e.getMessage());
		}
		return -1;
	}

	public List<SearchHit> search(SearchCriteria c) throws SQLException {
		StringBuilder sql = new StringBuilder("SELECT ID, lat, `long`, locality, district FROM Locality WHERE 1=1 ");
		ArrayList<Object> params = new ArrayList<>();

		if (!c.name().isEmpty()) {
			String p = c.name().replace("*", "%");
			sql.append(" AND (locality LIKE ? OR alternative_names LIKE ? OR alternative_names LIKE ? OR alternative_names LIKE ? OR alternative_names LIKE ?)");
			params.add(p); params.add(p); params.add(p + ",%"); params.add("%, " + p); params.add("%, " + p + ",%");
		}

		addNullableLikeFilter(sql, params, "country", c.country());
		addNullableLikeFilter(sql, params, "district", c.district());
		addNullableLikeFilter(sql, params, "coordinate_source", c.source());

		String precInput = c.precision().trim();
		if (!"*".equals(precInput)) {
			if (precInput.isEmpty()) {
				sql.append(" AND (Coordinateprecision IS NULL OR Coordinateprecision = 0)");
			} else {
				try {
					int val = Integer.parseInt(precInput.replace("*", ""));
					sql.append(" AND (Coordinateprecision >= ? OR Coordinateprecision IS NULL OR Coordinateprecision = 0)");
					params.add(val);
				} catch (NumberFormatException ignored) {}
			}
		}

		addNullableLikeFilter(sql, params, "category", c.category());

		if (!"*".equals(c.province())) {
			sql.append(" AND province = ?");
			params.add(c.province());
		}

		if (c.isPlaceOnly()) sql.append(" AND isPlace = 1");
		sql.append(" LIMIT 500");

		List<SearchHit> results = new ArrayList<>();
		Connection conn = db.mysql();
		try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
			for (int i = 0; i < params.size(); i++) stmt.setObject(i + 1, params.get(i));
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					results.add(new SearchHit(
							rs.getInt("ID"),
							rs.getString("locality"),
							rs.getString("district"),
							new Coordinate(rs.getDouble("lat"), rs.getDouble("long"))));
				}
			}
		}
		return results;
	}

	private static void addNullableLikeFilter(StringBuilder sql, ArrayList<Object> params, String columnName, String input) {
		String trimmed = input.trim();
		if (!"*".equals(trimmed)) {
			if (trimmed.isEmpty()) {
				// Empty input means: match rows where the column itself is empty
				sql.append(" AND (").append(columnName).append(" IS NULL OR ").append(columnName).append(" = '')");
			} else {
				String pattern = trimmed.replace("*", "%");
				sql.append(" AND ").append(columnName).append(" LIKE ?");
				params.add(pattern);
			}
		}
	}
}
