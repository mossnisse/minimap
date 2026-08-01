package app.repo;

import gis.coords.Coordinate;
import app.db.Database;
import gis.geometry.Extent;

import java.awt.Point;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL against the embedded H2 place-name table (Lantmäteriet ortnamn,
 * SWEREF99TM coordinates) lives here.
 */
public final class PlaceNameRepository {
	private static final String TABLE = "ortnamnSWTM";
	private final Database db;

	public PlaceNameRepository(Database db) {
		this.db = db;
	}

	/** A named place; {@code sweref} is the stored SWEREF99TM position. */
	public record PlaceName(Coordinate sweref, String name) {}
	public record NearestPlace(String name, Coordinate sweref, double distanceMeters,
	                           double bearingDegrees, String direction) {}

	/** A search hit with the place's type and district (socken). */
	public record PlaceHit(Coordinate sweref, String type, String district) {}

	/** Name of the nearest place within {@code limitMeters} of {@code sweref}, or "". */
	public String findNearestName(Coordinate sweref, int limitMeters) {
		NearestPlace place = findNearest(sweref, limitMeters);
		return place == null ? "" : place.name();
	}

	/** Full nearest-place result, or {@code null} when nothing is within the limit. */
	public NearestPlace findNearest(Coordinate sweref, int limitMeters) {
		int eastVal = (int) sweref.getEast();
		int northVal = (int) sweref.getNorth();

		// Use a Bounding Box approach for the initial SQL filter
		String sql = "SELECT NORTH, EAST, Ortnamn FROM " + TABLE +
				" WHERE NORTH BETWEEN ? AND ? AND EAST BETWEEN ? AND ?";

		try {
			Connection conn = db.h2();
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setInt(1, northVal - limitMeters);
				pstmt.setInt(2, northVal + limitMeters);
				pstmt.setInt(3, eastVal - limitMeters);
				pstmt.setInt(4, eastVal + limitMeters);

				try (ResultSet result = pstmt.executeQuery()) {
					double ndist = Double.MAX_VALUE;
					String nearest = "";
					Coordinate nearestCoordinate = null;

					// SWEREF99TM is planar in meters, so Euclidean distance is fine here
					Point p = new Point(eastVal, northVal);

					while (result.next()) {
						int north = result.getInt(1);
						int east = result.getInt(2);
						String name = result.getString(3);

						Point pc = new Point(east, north);
						double dist = p.distance(pc);

						if (dist <= limitMeters && dist < ndist) {
							ndist = dist;
							nearest = name;
							nearestCoordinate = new Coordinate(north, east);
						}
					}
					if (nearestCoordinate == null) return null;
					// Label directions describe where the locality lies relative to the
					// named place, not the direction one would travel to reach the place.
					double bearing = nearestCoordinate.getBearingTM(sweref);
					return new NearestPlace(nearest, nearestCoordinate, ndist, bearing,
							Coordinate.getDirectionFromBearing(bearing));
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	/** All places inside {@code swerefBounds}, for map rendering. */
	public List<PlaceName> findInBounds(Extent swerefBounds) throws SQLException {
		List<PlaceName> result = new ArrayList<>();
		String sql = "SELECT NORTH, EAST, Ortnamn FROM " + TABLE +
				" WHERE NORTH BETWEEN ? AND ? AND EAST BETWEEN ? AND ?";
		Connection conn = db.h2();
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setInt(1, (int) swerefBounds.c1.getNorth());
			pstmt.setInt(2, (int) swerefBounds.c2.getNorth());
			pstmt.setInt(3, (int) swerefBounds.c1.getEast());
			pstmt.setInt(4, (int) swerefBounds.c2.getEast());

			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					result.add(new PlaceName(new Coordinate(rs.getInt(1), rs.getInt(2)), rs.getString(3)));
				}
			}
		}
		return result;
	}

	/**
	 * Search by name pattern ('%' wildcards, case-insensitive). {@code provNr} is the
	 * Lantmäteriet FPNUMMER, -1 for any province; a district pattern of "%" or "" means any.
	 */
	public List<PlaceHit> search(String namePattern, int provNr, String districtPattern) throws SQLException {
		List<PlaceHit> results = new ArrayList<>();

		StringBuilder sql = new StringBuilder("SELECT NORTH, EAST, DETALJTYP, SOCKEN FROM " + TABLE + " WHERE Ortnamn ILIKE ?");

		if (provNr != -1) {
			sql.append(" AND FPNUMMER = ?");
		}

		boolean useDistrict = !districtPattern.equals("%") && !districtPattern.isEmpty();
		if (useDistrict) {
			sql.append(" AND SOCKEN ILIKE ?");
		}

		sql.append(" ORDER BY SOCKEN LIMIT 500");

		Connection conn = db.h2();
		try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
			int idx = 1;
			pstmt.setString(idx++, namePattern);
			if (provNr != -1) pstmt.setInt(idx++, provNr);
			if (useDistrict) pstmt.setString(idx, districtPattern);

			try (ResultSet result = pstmt.executeQuery()) {
				while (result.next()) {
					Coordinate c = new Coordinate(result.getInt(1), result.getInt(2));
					results.add(new PlaceHit(c, result.getString(3), result.getString(4)));
				}
			}
		}
		return results;
	}
}
