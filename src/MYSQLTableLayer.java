import coords.*;
import geometry.BoundingBox;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MYSQLTableLayer implements Layer {
	private String name;
	private Color color;
	private boolean hidden;
	private int maxZoom, minZoom;
	private CoordSystem cs;
	private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 20);
	private List<LocalityRec> cache = new ArrayList<>();
	private BoundingBox cachedBounds = null;
	private PreparedStatement activeStatement = null;

	private record LocalityRec(int n, int e, String name, int precision) {}

	MYSQLTableLayer() {}

	@Override
	public void setColor(Color c) {
		this.color =c;
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
		return !(zoomLevel > maxZoom && maxZoom != 0);
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale,
	                 double yShift, double yScale, BoundingBox bounds) throws SQLException {
		if (hidden) return;

		// Check if we need to refresh the cache
		if (shouldRefreshCache(bounds)) {
			refreshCache(bounds);
		}

		// Draw from RAM (Super Fast!)
		g2d.setColor(color);
		Font old = g2d.getFont();
		g2d.setFont(LABEL_FONT);

		for (LocalityRec rec : cache) {
			int x = (int) ((rec.e * xScale) + xShift);
			int y = (int) ((rec.n * yScale) + yShift);

			// Optimization: Only draw if actually on screen (clipping)
			if (x < -10 || x > g2d.getClipBounds().width + 10) continue;

			g2d.drawOval(x - 3, y - 3, 6, 6);
			if (rec.precision > 0) {
				int r = (int) (rec.precision * xScale);
				if (r > 1) g2d.drawOval(x - r, y - r, r * 2, r * 2);
			}
			if (xScale > 0.02) g2d.drawString(rec.name, x + 5, y);
		}
		g2d.setFont(old);
	}

	private boolean shouldRefreshCache(BoundingBox currentBounds) {
		if (cachedBounds == null || cache.isEmpty()) return true;
		// Check if current view is still inside our cached rectangle
		return !cachedBounds.isInside(currentBounds);
	}

	private void refreshCache(BoundingBox bounds) throws SQLException {
		Connection conn = DBConnection.getConn();

		// Prepare statement once or recover if connection changed
		if (activeStatement == null || activeStatement.getConnection().isClosed()) {
			String sql = "SELECT SWTMN, SWTME, locality, Coordinateprecision FROM locality " +
					"WHERE SWTMN BETWEEN ? AND ? AND SWTME BETWEEN ? AND ?;";
			activeStatement = conn.prepareStatement(sql);
		}

		// Create a BUFFER (e.g., fetch 50% more area in every direction)
		int width = Math.abs(bounds.getX1() - bounds.getX2());
		int height = Math.abs(bounds.getY1() - bounds.getY2());

		BoundingBox bufferedArea = bounds.grow(0.5);

		int yMin = Math.min(bufferedArea.getY1(), bufferedArea.getY2());
		int yMax = Math.max(bufferedArea.getY1(), bufferedArea.getY2());
		int xMin = Math.min(bufferedArea.getX1(), bufferedArea.getX2());
		int xMax = Math.max(bufferedArea.getX1(), bufferedArea.getX2());

		activeStatement.setInt(1, yMin);
		activeStatement.setInt(2, yMax);
		activeStatement.setInt(3, xMin);
		activeStatement.setInt(4, xMax);

		cache.clear();
		try (ResultSet rs = activeStatement.executeQuery()) {
			while (rs.next()) {
				cache.add(new LocalityRec(
						rs.getInt("SWTMN"),
						rs.getInt("SWTME"),
						rs.getString("locality"),
						rs.getInt("Coordinateprecision")
				));
			}
		}
		// Remember what area we now have in RAM
		this.cachedBounds = bufferedArea;
	}
	
	@Override
	public boolean isHidden() {
		return hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}
	
	public int findNearest(Point p, int limit) {
		String sqlstmt = "SELECT SWTMN, SWTME, ID FROM locality where SWTMN > ? and SWTMN < ? and SWTME > ? and SWTME < ?;";
		try (PreparedStatement statement = DBConnection.getConn().prepareStatement(sqlstmt)) {
			statement.setInt(1, p.y-limit);
			statement.setInt(2, p.y+limit);
			statement.setInt(3, p.x-limit);
			statement.setInt(4, p.x+limit);
			try (ResultSet result = statement.executeQuery()) {
				double ndist = Double.MAX_VALUE;
				int nID = -1;
				while (result.next()) {
					int north = result.getInt(1);
					int east = result.getInt(2);
					int ID = result.getInt(3);
					Point pc = new Point(east, north);
					double dist = p.distance(pc);
					if (dist < ndist) {
						ndist = dist;
						nID = ID;
					}
				}
				return nID;
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return -1;
	}

	@Override
	public void setCRS(CoordSystem cs) { this.cs = cs;  }

	@Override
	public CoordSystem getCRS() {
		return cs;
	}

	private void convCoord() {
		String sql1 = "SELECT lat, `long`, id FROM locality where country = 'Sweden' limit ?,1";
		String sql2 = "update locality set SWTMN = ?, SWTME = ? where id =?";
		try {
			Connection conn = DBConnection.getConn();
			PreparedStatement statmt1= conn.prepareStatement(sql1);
			PreparedStatement statmt2= conn.prepareStatement(sql2);
		
			for (int i=1; i< 46028; i++) {
				System.out.println("i: "+i);
				statmt1.setInt(1, i);
				ResultSet result = statmt1.executeQuery();
				result.next();
				double lat = result.getDouble(1);
				double longi = result.getDouble(2);
				int id = result.getInt(3);
				System.out.println("id: "+id);
				Coordinates c = new Coordinates(lat,longi);
				Coordinates swtm = c.toProjected(CoordSystem.SWEREF99TM);
				int swtmN = (int)Math.round(swtm.getNorth());
				int swtmE = (int)Math.round(swtm.getEast());
				statmt2.setInt(1,swtmN);
				statmt2.setInt(2,swtmE);
			
				statmt2.setInt(3,id);
				statmt2.execute();
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			
	}
	
	static void main(String[] args) {
		MYSQLTableLayer MT = new MYSQLTableLayer();
		MT.convCoord();
	}
}