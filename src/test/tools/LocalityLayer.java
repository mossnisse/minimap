package test.tools;

/*

public class LocalityLayer implements core.Layer {
	private Color color;
	private boolean hidden;
	private int maxZoom, minZoom;
	private CoordSystem cs = CoordSystem.RT90;
	private String name;
	
	LocalityLayer() {
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
			double yShift, double yScale, BoundingBox bounds) throws SQLException {
		if (!hidden) {
		g2d.setColor(color);
		Font old = g2d.getFont();
		Font stringFont = new Font( "SansSerif", Font.PLAIN, 20 );
		g2d.setFont(stringFont);
			String sqlstmt = "SELECT SWTMN, SWTME, locality, Coordinateprecision, zoomLevel FROM locality where SWTMN > ? and SWTMN < ? and SWTME > ? and SWTME < ?;" ;
			Connection conn = core.DBConnection.getConn();
			
			PreparedStatement statement = conn.prepareStatement(sqlstmt);
			statement.setInt(1, bounds.getY1());
			statement.setInt(2, bounds.getY2());
			statement.setInt(3, bounds.getX1());
			ResultSet result = statement.executeQuery();
        
			while (result.next()) { // process results one row at a time
				int zl = result.getInt(5);
				//System.out.println( "xScale: "+xScale);
				if ((zl<5 & zl !=0 & zl !=-1) | xScale >0.03) {
				int north = Integer.parseInt(result.getString(1));
				int east = Integer.parseInt(result.getString(2));
				//Point p = new Point(east, north);
				//if (bounds.isInside(new Point(east, north))) {
					String name = result.getString(3)+" ("+zl+")";
					
					int x = (int) ((east*xScale)+xShift);
					int y = (int) ((north*yScale)+yShift);
					g2d.drawOval(x-3,y-3,6,6);
					
					try  
					  {  
						if(zl<5 & zl!=0 & zl!=-1) g2d.setColor(Color.blue);
						String sizestr = result.getString(4);
						int size = (int) (Integer.parseInt(sizestr)*xScale);
						g2d.drawOval(x-size,y-size,size*2,size*2);
						//System.out.println("Scale: "+xScale);
						//System.out.println("Size: "+sizestr);
						g2d.drawString(name,x,y);
						g2d.setColor(color);
					  }  
					  catch(NumberFormatException nfe)  
					  {  
						  g2d.setColor(Color.green);
						  //System.out.println("No Size");
						  g2d.drawString(name,x,y);
						  g2d.setColor(color);
					  }
				}
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
		String sqlstmt = "SELECT SWTMN, SWTME, ID FROM locality where Country = \"Sweden\"";
		Connection conn;
		try {
			conn = core.DBConnection.getConn();
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
		String sqlstmt = "SELECT SWTMN, SWTME, ID FROM locality where SWTMN > ? and SWTMN < ? and SWTME > ? and SWTME < ?;";
		Connection conn;
		try {
			conn = core.DBConnection.getConn();
			PreparedStatement statement = conn.prepareStatement(sqlstmt);
			statement.setInt(1, p.y-limit);
			statement.setInt(2, p.y+limit);
			statement.setInt(3, p.x-limit);
			statement.setInt(4, p.x+limit);
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

	@Override
	public void setCRS(CoordSystem cs) { this.cs = cs; }

	@Override
	public CoordSystem getCRS() {
		return cs;
	}

}*/