import coords.*;
import geometry.BoundingBox;
import geometry.Point;
import java.awt.Color;
import java.awt.Graphics2D;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import org.h2.jdbcx.JdbcDataSource;

public class H2Table implements Layer {
	private final String tableName;
	private String name;
	private Color color;
	private boolean hidden;
	private Connection conn;
	private int maxZoom, minZoom;
	private CoordSystem cs = CoordSystem.RT90;
	private BoundingBox lastBounds;
	private ArrayList<Locality> cachedPoints = new ArrayList<>();
	
	H2Table(String tableName) {
		this.tableName = tableName;
		createConnection();
	}

	private void createConnection() {
		JdbcDataSource ds = new JdbcDataSource();

		String url = "jdbc:h2:./h2/test;IFEXISTS=TRUE";
		ds.setURL(url);
		ds.setUser("sa");
		ds.setPassword("sa");

		try {
			conn = ds.getConnection();
			System.out.println("Successfully connected to: " + url);

			// DEBUG: Print every table name actually found in this file
			java.sql.DatabaseMetaData meta = conn.getMetaData();
			try (ResultSet res = meta.getTables(null, null, null, new String[]{"TABLE"})) {
				System.out.println("--- Tables found in this database ---");
				boolean found = false;
				while (res.next()) {
					System.out.println("Table: " + res.getString("TABLE_NAME"));
					found = true;
				}
				if (!found) System.out.println("WARNING: No tables found! You are likely in an empty DB.");
			}

		} catch (SQLException e) {
			System.err.println("CRITICAL H2 ERROR:");
			System.err.println("Error Code: " + e.getErrorCode());
			System.err.println("SQL State: " + e.getSQLState());
			System.err.println("Message: " + e.getMessage());

			if (e.getMessage().contains("Database \"~/test\" not found")) {
				System.err.println("HELP: The path is wrong. Check if the file is 'test.mv.db' or 'test.h2.db'.");
			}
		}
	}

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
			double yShift, double yScale, BoundingBox bounds) {
		if (!hidden) {
			/*
			if (lastBounds == null || !lastBounds.equals(bounds)) {
				updateCache(bounds);
				lastBounds = bounds;
			}*/
			g2d.setColor(color);


			String sqlstmt = "SELECT NORTH, EAST, Ortnamn FROM "+tableName+" where North > " +bounds.getY1()+" and North < " + bounds.getY2()+ " and East > "+bounds.getX1()+ "and East < "+bounds.getX2() ;
			try {
				Statement select = conn.createStatement();
				ResultSet result = select.executeQuery(sqlstmt);

				while (result.next()) { // process results one row at a time
					int north = Integer.parseInt(result.getString(1));
					int east = Integer.parseInt(result.getString(2));
					String name = result.getString(3);
					int x = (int) ((east*xScale)+xShift);
					int y = (int) ((north*yScale)+yShift);
					g2d.drawOval(x-3,y-3,6,6);
					g2d.setColor(Color.black);
					g2d.drawString(name,x,y);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
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

	public TNGPointFile find(int provinsNr, String value) {
		value = value.trim();
		if (value.contains("*")) {
			value = value.replace("*", "%");
		}
		ArrayList<Point> ans = new ArrayList<Point>();
		ArrayList<String> names = new ArrayList<String>();
		String sql = (provinsNr == -1)
				? "SELECT NORTH, EAST, DETALJTYP, SOCKEN FROM " + tableName + " WHERE Ortnamn ILIKE ? ORDER BY SOCKEN"
				: "SELECT NORTH, EAST, DETALJTYP, SOCKEN FROM " + tableName + " WHERE Ortnamn ILIKE ? AND FPNUMMER = ? ORDER BY SOCKEN";
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, value);
			if (provinsNr != -1) pstmt.setInt(2, provinsNr);

			try (ResultSet result = pstmt.executeQuery()) {
				while (result.next()) { // process results one row at a time
					//System.out.println("NORTH: " + result.getString(1) + ", EAST: " + result.getString(2) + ", DETALJTYP: " + result.getString(3) + ", SOCKEN: " + result.getString(4));
					int north = result.getInt(1);
					int east = result.getInt(2);

					ans.add(new Point(east, north)); // East=X, North=Y
					names.add(result.getString(3) + ", " + result.getString(4));
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return new TNGPointFile(ans, names, "ans");
	}

	public String findNearest(Point p, int limit) {
		int eastVal = p.getY();
		int northVal = p.getX();

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
				int north = result.getInt(2);
				int east = result.getInt(1);
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
		} catch (SQLException e) {
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
	
	public void saveConvert() {
		createConnection();
		String drop = "DROP table if exists ortnamnSWTM";
		String sql0 ="Create table ortnamnSWTM as select * FROM ortnamnsDB";
		String sql1 ="SELECT NORTH, EAST, ORT_ID from ortnamnSWTM LIMIT ?,?";
		String sql2 ="Update ortnamnSWTM set NORTH = ?, EAST = ? where ORT_ID = ?";
		int batchsize=5000;
		try {
			PreparedStatement drops= conn.prepareStatement(drop);
			drops.execute();
			PreparedStatement statmt0= conn.prepareStatement(sql0);
			statmt0.execute();
			PreparedStatement statmt1= conn.prepareStatement(sql1);
			PreparedStatement statmt2= conn.prepareStatement(sql2);
			
			statmt1.setInt(2, batchsize);
			//statmt0.execute();
			for (int i=1; i< 1000000; i++) {
				statmt1.setInt(1, i);
				ResultSet result = statmt1.executeQuery();
				while (result.next()) {
					//double north = result.getDouble(1);
					//double east = result.getDouble(2);
					//int id = result.getInt(3);
					Coordinates rt90 = new Coordinates(result.getDouble(1), result.getDouble(2));
					if (rt90.isValid(CoordSystem.RT90)) {
						Coordinates wgs84 = rt90.toWGS84(CoordSystem.RT90);
						Coordinates swtm = wgs84.toProjected(CoordSystem.SWEREF99TM);
						statmt2.setDouble(1, swtm.getNorth());
						statmt2.setDouble(2, swtm.getEast());
						statmt2.setInt(3, result.getInt(3));
						statmt2.execute();
						//System.out.println("rt90: "+rt90 +" swtm: "+swtm );
						
						//System.out.println("wgs84: "+wgs84);
						//Coordinates 
						//System.out.println("swtm: "+swtm);
						//Coordinates swtm = new Coordinates(result.getDouble(1), result.getDouble(2)).convertToRT90FromSweref99TM();
						//Coordinates swtm =rt90.convertToRT90FromSweref99TM();
						//System.out.println("i "+i+", ID "+result.getInt(3));
					} else {
						//swtm = rt90;
						System.out.println("not valid rt90: "+rt90);
					}
				}
				System.out.println("i "+i);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}	
	}
	
	public void showC() {
		try {
			String sql = "select * from ortnamnsDB where North = 123544";
			Statement select = conn.createStatement();
		
			ResultSet result = select.executeQuery(sql);
			ResultSetMetaData rsmd = result.getMetaData();
			System.out.println(result);
			System.out.println(rsmd);
			System.out.println(rsmd.getColumnName(1));
			System.out.println(rsmd.getColumnName(2));
			System.out.println(rsmd.getColumnName(3));
			System.out.println(rsmd.getColumnName(4));
			System.out.println(rsmd.getColumnName(5));
			System.out.println(rsmd.getColumnName(6));
			System.out.println(rsmd.getColumnName(7));
			while (result.next()) {
				System.out.println(result.getString(1));
				System.out.println();
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	static void main(String[] args) {
		H2Table h2 = new H2Table("ortnamnsDB");
		//String sql = "select * from ortnamnsDB where North = 123544";
		/*try {
			String sql = "Alter table ortnamnsDB ALTER COLUMN ORTNAMN varchar_ignorecase(255)";
			Statement select;
		
			select = h2.conn.createStatement();
			select.execute(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		//ResultSetMetaData rsmd = result.getMetaData();*/
		h2.saveConvert();
		//h2.showC();
	}
}