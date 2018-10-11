import geometry.BoundingBox;
import geometry.Point;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;



public class MYSQLTable implements Layer {
	private String name, tableName;
	private Color color;
	private boolean hidden;
	//Connection conn;
	
	private int maxZoom, minZoom;
	
	MYSQLTable() {
		//this.tableName = tableName;
		//createConnection();
		//this.conn = conn;
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
		Font old = g2d.getFont();
		Font stringFont = new Font( "SansSerif", Font.PLAIN, 20 );
		g2d.setFont(stringFont);
		
		//SELECT locality, district, province, lat, `long`, RT90N, RT90E from locality where country = "Sweden";
		
		
		//System.out.println(sqlstmt);
		try {
			
			//String sqlstmt = "SELECT RT90N, RT90E, locality FROM locality where RT90N > " +bounds.getY1()+" and RT90N < " + bounds.getY2()+ " and RT90E > "+bounds.getX1()+ " and RT90E < "+bounds.getX2()+";" ;
			String sqlstmt = "SELECT RT90N, RT90E, locality, Coordinateprecision FROM locality where RT90N > ? and RT90N < ? and RT90E > ? and RT90E < ?;" ;
			Connection conn = MYSQLConnection.getConn();
			
			PreparedStatement statement = conn.prepareStatement(sqlstmt);
			statement.setInt(1, bounds.getY1());
			statement.setInt(2, bounds.getY2());
			statement.setInt(3, bounds.getX1());
			statement.setInt(4, bounds.getX2());
			//Statement select = conn.createStatement();
			ResultSet result = statement.executeQuery();
        
			while (result.next()) { // process results one row at a time
				int north = Integer.parseInt(result.getString(1));
				int east = Integer.parseInt(result.getString(2));
				//Point p = new Point(east, north);
				//if (bounds.isInside(new Point(east, north))) {
					String name = result.getString(3);
					
					int x = (int) ((east*xScale)+xShift);
					int y = (int) ((north*yScale)+yShift);
					g2d.drawOval(x-3,y-3,6,6);
					
					try  
					  {  
						String sizestr = result.getString(4);
						int size = (int) (Integer.parseInt(sizestr)*xScale);
						g2d.drawOval(x-size,y-size,size*2,size*2);
						//System.out.println("Scale: "+xScale);
						//System.out.println("Size: "+sizestr);
						g2d.drawString(name,x,y);
					  }  
					  catch(NumberFormatException nfe)  
					  {  
						  //System.out.println("No Size");
						  g2d.setColor(Color.green);
						  g2d.drawString(name,x,y);
						  g2d.setColor(color);
					  }  
					//g2d.setColor(Color.black);
				//}
			}
			} catch (SQLException e) {
			// TODO Auto-generated catch block
				e.printStackTrace();
			}
		g2d.setFont(old);
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
	
	public int findNearest(Point p) {
		String sqlstmt = "SELECT RT90N, RT90E, ID FROM locality where Country = \"Sweden\"";
		Connection conn;
		try {
			conn = MYSQLConnection.getConn();
			PreparedStatement statement = conn.prepareStatement(sqlstmt);
			ResultSet result = statement.executeQuery();
	    
			double ndist = 700000000;
			int nID = -1;
			while (result.next()) {
				int north = Integer.parseInt(result.getString(1));
				int east = Integer.parseInt(result.getString(2));
				int ID = Integer.parseInt(result.getString(3));
				Point pc = new Point(east,north);
				double dist = p.distance(pc);
				if (dist<ndist) {
					ndist = dist;
					nID = ID;
				}
			}
			System.out.println("nearest: "+nID + " dist: "+ndist);
			return nID;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return -1;
	}
	
	public int findNearest(Point p, int limit) {
		String sqlstmt = "SELECT RT90N, RT90E, ID FROM locality where RT90N > ? and RT90N < ? and RT90E > ? and RT90E < ?;";
		Connection conn;
		try {
			conn = MYSQLConnection.getConn();
			PreparedStatement statement = conn.prepareStatement(sqlstmt);
			statement.setInt(1, p.getY()-limit);
			statement.setInt(2, p.getY()+limit);
			statement.setInt(3, p.getX()-limit);
			statement.setInt(4, p.getX()+limit);
			ResultSet result = statement.executeQuery();
	    
			double ndist = 700000000;
			int nID = -1;
			while (result.next()) {
				int north = Integer.parseInt(result.getString(1));
				int east = Integer.parseInt(result.getString(2));
				int ID = Integer.parseInt(result.getString(3));
				Point pc = new Point(east,north);
				double dist = p.distance(pc);
				if (dist<ndist) {
					ndist = dist;
					nID = ID;
				}
			}
			//System.out.println("nearest: "+nID + " dist: "+ndist);
			return nID;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return -1;
	}
	
	/*
	public TNGPointFile find(int provinsNr, String value) {
		value = value.trim();
		ArrayList<Point> ans = new ArrayList<Point>();
		ArrayList<String> names = new ArrayList<String>();
		
		String sqlstmt = "SELECT NORTH, EAST, DETALJTYP, SOCKEN FROM "+tableName+" where Ortnamn = '"+value+"' and FPNUMMER = "+provinsNr+" Order by SOCKEN";
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
		}*/
}