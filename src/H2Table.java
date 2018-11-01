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
	private String name, tableName;
	private Color color;
	private boolean hidden;
	private Connection conn;
	private int maxZoom, minZoom;
	private CoordSystem cs = CoordSystem.RT90;
	
	H2Table(String tableName) {
		this.tableName = tableName;
		createConnection();
	}
	
	private void createConnection() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:˜/test");
        ds.setUser("sa");
        ds.setPassword("sa");
        try {
            conn = ds.getConnection();
        } catch (Exception e) {
            System.err.println("Caught IOException: " + e.getMessage());
        } finally {
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
		g2d.setColor(color);
		
		
		String sqlstmt = "SELECT NORTH, EAST, Ortnamn FROM "+tableName+" where North > " +bounds.getY1()+" and North < " + bounds.getY2()+ " and East > "+bounds.getX1()+ "and East < "+bounds.getX2() ;
		try {
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery(sqlstmt);
        
			while (result.next()) { // process results one row at a time
				int north = Integer.parseInt(result.getString(1));
				int east = Integer.parseInt(result.getString(2));
				//Point p = new Point(east, north);
				//if (bounds.isInside(new Point(east, north))) {
					String name = result.getString(3);
					int x = (int) ((east*xScale)+xShift);
					int y = (int) ((north*yScale)+yShift);
					g2d.drawOval(x-3,y-3,6,6);
					g2d.setColor(Color.black);
					g2d.drawString(name,x,y);
				//}
			}
			} catch (SQLException e) {
			// TODO Auto-generated catch block
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
		ArrayList<Point> ans = new ArrayList<Point>();
		ArrayList<String> names = new ArrayList<String>();
		String sqlstmt = "";
		if(provinsNr == -1) {
			sqlstmt = "SELECT NORTH, EAST, DETALJTYP, SOCKEN FROM "+tableName+" where Ortnamn = '"+value+"' Order by SOCKEN";
		} else {
			sqlstmt = "SELECT NORTH, EAST, DETALJTYP, SOCKEN FROM "+tableName+" where Ortnamn = '"+value+"' and FPNUMMER = "+provinsNr+" Order by SOCKEN";
		}
		try {
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery(sqlstmt);

			while (result.next()) { // process results one row at a time
				System.out.println("NORTH: "+result.getString(1)+", EAST: "+ result.getString(2)+ ", DETALJTYP: "+result.getString(3)+", SOCKEN: "+result.getString(4));
				ans.add(new Point(Integer.parseInt(result.getString(1)), Integer.parseInt(result.getString(2))));
				names.add(result.getString(3)+", "+result.getString(4));
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return new TNGPointFile(ans, names, "ans");
	}
	
	public String findNearest(Point p, int limit) {
		System.out.println("Find Nearest Ortnamn");
		
		String sqlstmt = "SELECT NORTH, EAST, Ortnamn FROM "+tableName+" where NORTH > " 
				+ Integer.toString(p.getX()-limit) + " and NORTH < " + Integer.toString(p.getX()+limit) + " and EAST > "+ Integer.toString(p.getY()-limit) + " and EAST < " + Integer.toString(p.getY()+limit) + ";";
		System.out.println(sqlstmt);
		try {
			Statement select = conn.createStatement();
			ResultSet result = select.executeQuery(sqlstmt);
			
		
	    
			double ndist = 700000000;
			String nearest = "";
			while (result.next()) {
				int north = Integer.parseInt(result.getString(1));
				int east = Integer.parseInt(result.getString(2));
				String temp = result.getString(3);
				Point pc = new Point(north,east);
				double dist = p.distance(pc);
				if (dist<ndist) {
					ndist = dist;
					nearest = temp;
				}
			}
			System.out.println("nearest: "+nearest + " dist: "+ndist);
			return nearest;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "";
	}

	@Override
	public void setCRS(CoordSystem cs) {
		// TODO Auto-generated method stub
		
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
		int batchsize=1000;
		try {
			PreparedStatement drops= conn.prepareStatement(drop);
			PreparedStatement statmt0= conn.prepareStatement(sql0);
			PreparedStatement statmt1= conn.prepareStatement(sql1);
			PreparedStatement statmt2= conn.prepareStatement(sql2);
			drops.execute();
			statmt0.execute();
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
					//System.out.println("rt90: "+rt90);
					Coordinates wgs84 = rt90.convertToWGS84FromRT90();
					//System.out.println("wgs84: "+wgs84);
					Coordinates swtm = wgs84.convertTo(CoordSystem.Sweref99TM);
					//System.out.println("swtm: "+swtm);
					//Coordinates swtm = new Coordinates(result.getDouble(1), result.getDouble(2)).convertToRT90FromSweref99TM();
					//Coordinates swtm =rt90.convertToRT90FromSweref99TM();
					//System.out.println("i "+i+", ID "+result.getInt(3));
					statmt2.setDouble(1, swtm.getNorth());
					statmt2.setDouble(2, swtm.getEast());
					statmt2.setInt(3, result.getInt(3));
					statmt2.execute();
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
	
	public static void main(String[] args) {
		H2Table h2 = new H2Table("ortnamnsDB");
		h2.saveConvert();
		//h2.showC();
	}
}