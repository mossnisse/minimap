package main.layers;

import main.coords.*;
import main.core.Canvas;
import main.core.DBConnection;
import main.core.Layer;
import main.geometry.Extent;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/*
It's wierd that find TNGPointFileLayer. Try to come up with some better structure
 */

public class H2TableLayer extends Layer {
	private final String tableName;
	private final Canvas canvas;
	private final ArrayList<Locality> cache = new ArrayList<>();
	private Extent lastQueryBounds;

	private static record Locality(Coordinate c, String name) {}
	
	public H2TableLayer(String tableName, Canvas canvas) {
		super(tableName, false, CoordSystem.SWEREF99TM);
		this.tableName = tableName;
		this.canvas = canvas;
	}

	private void updateCache(Extent bounds) {
		cache.clear();
		try {
			Connection conn = DBConnection.getH2Conn();
			String sql = "SELECT NORTH, EAST, Ortnamn FROM " + tableName +
					" WHERE NORTH BETWEEN ? AND ? AND EAST BETWEEN ? AND ?";

			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setInt(1, (int) bounds.c1.getNorth());
				pstmt.setInt(2, (int) bounds.c2.getNorth());
				pstmt.setInt(3, (int) bounds.c1.getEast());
				pstmt.setInt(4, (int) bounds.c2.getEast());

				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
						// Cache the raw coordinates and name
						Coordinate c =  new Coordinate (rs.getInt(1), rs.getInt(2));
						cache.add(new Locality(getCRS().convertTo(c, canvas.getCRS()), rs.getString(3)));
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public TNGPointFileLayer find(int provinsNr, String value, String district) {
		value = value.trim().replace("*", "%");
		district = district.trim().replace("*", "%");

		try {
			Connection conn = DBConnection.getH2Conn();
			ArrayList<Coordinate> ans = new ArrayList<>();
			ArrayList<String> names = new ArrayList<>();

			// Build the Dynamic SQL
			StringBuilder sql = new StringBuilder("SELECT NORTH, EAST, DETALJTYP, SOCKEN FROM " + tableName + " WHERE Ortnamn ILIKE ?");

			if (provinsNr != -1) {
				sql.append(" AND FPNUMMER = ?");
			}

			// Only add district filter if it's not a global wildcard
			boolean useDistrict = !district.equals("%") && !district.isEmpty();
			if (useDistrict) {
				sql.append(" AND SOCKEN ILIKE ?");
			}

			sql.append(" ORDER BY SOCKEN LIMIT 500");

			try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
				int idx = 1;
				pstmt.setString(idx++, value);

				if (provinsNr != -1) {
					pstmt.setInt(idx++, provinsNr);
				}

				if (useDistrict) {
					pstmt.setString(idx++, district);
				}

				try (ResultSet result = pstmt.executeQuery()) {
					while (result.next()) {
						int north = result.getInt(1);
						int east = result.getInt(2);
						ans.add(new Coordinate(north, east));
						names.add(result.getString(3) + ", " + result.getString(4));
					}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return new TNGPointFileLayer(ans, names, "Search Results");
		} catch(Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public String findNearest(Coordinate c, int limit) {
		// Coordinate uses geographical North/East logic[cite: 7, 8]
		int eastVal = (int) c.getEast();
		int northVal = (int) c.getNorth();

		// Use a Bounding Box approach for the initial SQL filter
		String sql = "SELECT NORTH, EAST, Ortnamn FROM " + tableName +
				" WHERE NORTH BETWEEN ? AND ? AND EAST BETWEEN ? AND ?";

		try {
			Connection conn = DBConnection.getH2Conn();
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

				// Define the search square[cite: 7]
				pstmt.setInt(1, northVal - limit);
				pstmt.setInt(2, northVal + limit);
				pstmt.setInt(3, eastVal - limit);
				pstmt.setInt(4, eastVal + limit);

				try (ResultSet result = pstmt.executeQuery()) {
					double ndist = Double.MAX_VALUE;
					String nearest = "";

					// Create a reference point for distance calculation
					Point p = new Point(eastVal, northVal);

					while (result.next()) {
						int north = result.getInt(1);
						int east = result.getInt(2);
						String name = result.getString(3);

						// Calculate precise Euclidean distance
						Point pc = new Point(east, north);
						double dist = p.distance(pc);

						if (dist < ndist) {
							ndist = dist;
							nearest = name;
						}
					}
					return nearest;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return "";
	}

	@Override
	public Extent getBoundaries() {
		return getCRS().getBoundaries();
	}

	@Override
	public void invalidateCache() {

	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, Extent bounds) {
		if (isHidden()) return;

		Coordinate c1 = bounds.c1;
		Coordinate c2 = bounds.c2;
		Extent queryBounds = new Extent( canvas.getCRS().convertTo(c1, getCRS()), canvas.getCRS().convertTo(c2, getCRS()));

		// Refresh if we don't have a cache, or if the new view is not fully contained in the old one
		if (lastQueryBounds == null || !lastQueryBounds.isInside(queryBounds)) {
			// Optimization: Fetch a slightly larger area than needed (25% bigger)
			// to prevent constant database hits during small pans.
			Extent bufferedBounds = queryBounds.grow(0.25);
			updateCache(bufferedBounds);
			lastQueryBounds = bufferedBounds;
		}

		g2d.setColor(getColor());
		// Draw logic remains the same
		for (Locality loc : cache) {
			int x = (int) ((loc.c.getEast() * xScale) + xShift);
			int y = (int) ((loc.c.getNorth() * yScale) + yShift);
			g2d.drawOval(x - 3, y - 3, 6, 6);
			g2d.drawString(loc.name, x + 5, y);
		}
	}
}