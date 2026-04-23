package main.layers;

import main.coords.*;
import main.core.DBConnection;
import main.core.Layer;
import main.geometry.BoundingBox;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

public class H2TableLayer implements Layer {
	private final String tableName;
	private String name;
	private Color color = Color.BLACK;
	private int maxZoom = 0; // 0 indicates unset
	private int minZoom = 0;
	private boolean hidden;
	private CoordSystem cs = CoordSystem.SWEREF99TM;
	private final ArrayList<Locality> cache = new ArrayList<>();
	private BoundingBox lastQueryBounds;

	private static record Locality(int north, int east, String name) {}
	
	public H2TableLayer(String tableName) {
		this.tableName = tableName;
	}

	@Override
	public void setColor(Color c) {
		this.color = (color != null) ? color : Color.BLACK;
	}

	@Override
	public Color getColor() {
		return color;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setMinZoomL(int zoomLevel) {
		minZoom = zoomLevel;
	}

	@Override
	public void setMaxZoomL(int zoomLevel) {
		maxZoom = zoomLevel;
	}

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		boolean meetsMin = (minZoom == 0 || zoomLevel >= minZoom);
		boolean meetsMax = (maxZoom == 0 || zoomLevel <= maxZoom);
		return meetsMin && meetsMax;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		if (hidden) return;

		// Only query the DB if the view has moved or zoomed
		if (lastQueryBounds == null || !lastQueryBounds.equals(bounds)) {
			updateCache(bounds);
			lastQueryBounds = new BoundingBox(bounds.getX1(), bounds.getY1(), bounds.getX2(), bounds.getY2());
		}

		g2d.setColor(color);
		for (Locality loc : cache) {
			int x = (int) ((loc.east * xScale) + xShift);
			int y = (int) ((loc.north * yScale) + yShift);

			g2d.drawOval(x - 3, y - 3, 6, 6);
			g2d.drawString(loc.name, x + 5, y); // Offset text slightly
		}
	}

	private void updateCache(BoundingBox bounds) {
		cache.clear();
		try {
			Connection conn = DBConnection.getH2Conn();
			String sql = "SELECT NORTH, EAST, Ortnamn FROM " + tableName +
					" WHERE NORTH BETWEEN ? AND ? AND EAST BETWEEN ? AND ?";

			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setInt(1, bounds.getY1());
				pstmt.setInt(2, bounds.getY2());
				pstmt.setInt(3, bounds.getX1());
				pstmt.setInt(4, bounds.getX2());

				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
						// Cache the raw coordinates and name
						cache.add(new Locality(rs.getInt(1), rs.getInt(2), rs.getString(3)));
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public boolean isHidden() {
		return hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public TNGPointFileLayer find(int provinsNr, String value, String district) {
		value = value.trim().replace("*", "%");
		district = district.trim().replace("*", "%");

		try {
			Connection conn = DBConnection.getH2Conn();
			ArrayList<Coordinate> ans = new ArrayList<>();
			ArrayList<String> names = new ArrayList<>();

			// 1. Build the Dynamic SQL
			StringBuilder sql = new StringBuilder("SELECT NORTH, EAST, DETALJTYP, SOCKEN FROM " + tableName + " WHERE Ortnamn ILIKE ?");

			if (provinsNr != -1) {
				sql.append(" AND FPNUMMER = ?");
			}

			// Only add district filter if it's not a global wildcard
			boolean useDistrict = !district.equals("%") && !district.isEmpty();
			if (useDistrict) {
				sql.append(" AND SOCKEN ILIKE ?");
			}

			sql.append(" ORDER BY SOCKEN LIMIT 100");

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
		Point p = c.getPoint();
		int eastVal = p.x;
		int northVal = p.y;

		try {
			Connection conn = DBConnection.getH2Conn();

			String sqlstmt = "SELECT NORTH, EAST, Ortnamn FROM " + tableName +
					" WHERE NORTH > " + (northVal - limit) +
					" AND NORTH < " + (northVal + limit) +
					" AND EAST > " + (eastVal - limit) +
					" AND EAST < " + (eastVal + limit);

			try (Statement select = conn.createStatement();
			     ResultSet result = select.executeQuery(sqlstmt)) {

				double ndist = Double.MAX_VALUE;
				String nearest = "";

				while (result.next()) {
					int north = result.getInt(1);
					int east = result.getInt(2);
					String name = result.getString(3);

					// Ensure pc is created as (East, North) to match p
					Point pc = new Point(east, north);
					double dist = p.distance(pc);

					if (dist < ndist) {
						ndist = dist;
						nearest = name;
					}
				}
				return nearest;
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	@Override
	public void setCRS(CoordSystem cs) {
		this.cs = cs;
	}

	@Override
	public CoordSystem getCRS() {
		return cs;
	}
}