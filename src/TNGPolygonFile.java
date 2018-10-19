import geometry.BoundingBox;
import geometry.Line;
import geometry.Point;
import geometry.Polygon;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import shapeFile.DataInputStreamSE;

public class TNGPolygonFile implements Layer{
	private String fileName, name;
	private int nameLength;
	private Color color;
	private Province[] provinces;
	private boolean hidden;
	
	public class Province extends Polygon{
		private String name;
		private BoundingBox box;
		
		Province (String name, BoundingBox box, int[] parts, Point[] points) {
			super(parts, points);
			this.name = name;
			this.box = box;
		}
		
		public BoundingBox getBoundingBox() {
			return box;
		}
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public boolean isInside(Point p) {
			if (box.isInside(p)) {
				//System.out.println("inside Box: "+name);
				return super.isInside(p);
			} 
			return false;
		}
		
	}
	
	public TNGPolygonFile(String fileName) throws IOException {
		this.fileName=fileName;
		this.name = fileName;
		readFile();
	}
	
	private void readFile() throws IOException {
		 DataInputStreamSE in =
			        new DataInputStreamSE(
			          new BufferedInputStream(
			            new FileInputStream(fileName)));
		in.readInt();
		int nrRecords = in.readInt();
		//System.out.println("Read nrRecords: "+nrRecords);
		nameLength = in.readInt();
		//System.out.println("read nameLenght: "+nameLength);
		provinces = new Province[nrRecords];
		for (int i = 0; i < nrRecords; i++) {
			String name = in.readString(nameLength).trim();
			//System.out.println("Reads name: "+name);
			int x1 = in.readInt();
			int y1 = in.readInt();
			int x2 = in.readInt();
			int y2 = in.readInt();
			BoundingBox box = new BoundingBox(x1, y1, x2, y2);
			//System.out.println("BoundingBox: "+box);
			int numParts = in.readInt();
			int numPoints = in.readInt();
			int[] parts = new int[numParts];
			for (int j = 0; j < numParts; j++) {
				parts[j] = in.readInt();
			}
			Point[] points = new Point[numPoints];
			for (int j = 0; j < numPoints; j++) {
				int px = in.readInt();
				int py = in.readInt();
				points[j] = new Point(px,py);
			}
			provinces[i] = new Province(name, box, parts, points);
		}
		//System.out.println("Read nrRecords: "+nrRecords);
		in.close();
	}
	
	
	public void saveFile(String filename) throws IOException {
		DataOutputStream out = new DataOutputStream(new FileOutputStream(filename));
		out.writeInt(5);  // shape type == Polygon
		out.writeInt(provinces.length);  // number of Polygons
		System.out.println("Save length: "+provinces.length);
		out.writeInt(nameLength);  // name field length
		System.out.println("Save namelength: "+nameLength);
		for (Province prov : provinces) {
			//int padlength = 50-prov.getName().length();
			String name = String.format("%1$-" +  nameLength + "s", prov.getName());
			//System.out.println("padded name:" + "\""+name+ "\"" + " lenght =" + prov.getName().length()+ " padlenght: "+padlength+ " padded lenght: "+  name.length());
			out.writeBytes(name);
			// System.out.println(record.getField(nameField));
			BoundingBox box = prov.getBoundingBox();
			out.writeInt(box.getX1());
			out.writeInt(box.getY1());
			out.writeInt(box.getX2());
			out.writeInt(box.getY2());
			out.writeInt(prov.getNumParts());
			out.writeInt(prov.getNumPoints());
			for (int part : prov.getParts()) {
				out.writeInt(part);
			}
			for (Point point : prov.getPoints()) {
				out.writeInt((int) Math.round(point.getX()));
				out.writeInt((int) Math.round(point.getY()));
			}
		}
		out.close();
	}
	
	public Province[] getProvinces()
	{
		return provinces;
	}

	@Override
	public void setColor(Color color) {
		this.color = color;
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
	public void setName(String name) {
		this.name = name;
	}
	
	public Province inPolygon(Point p) {
		for (Province pr: provinces) {
			if(pr.isInside(p)) return pr;
		}
		return null;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		if (!hidden) {
			Stroke s = g2d.getStroke();
			g2d.setStroke(new BasicStroke((float)1.5));
		for(TNGPolygonFile.Province pr:provinces) {
			if(bounds.intersects(pr.getBoundingBox()))
			for(Line ln:pr) {
				int x1 = (int)  ((ln.getPoint1().getX()*xScale)+xShift);
				int y1 = (int)  ((ln.getPoint1().getY()*yScale)+yShift);
				int x2 = (int)  ((ln.getPoint2().getX()*xScale)+xShift);
				int y2 = (int)  ((ln.getPoint2().getY()*yScale)+yShift);
				g2d.drawLine(x1, y1, x2, y2 );
				//System.out.println("x1:"+x1+ " y1:"+y1);
			}
		}
		g2d.setStroke(s);
		}
	}

	@Override
	public void setMinZoomL(int zoomLevel) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setMaxZoomL(int zoomLevel) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean isHidden() {
		return hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
		
	}
	
	public void calcSizes() {
		 try {
			Connection conn = MYSQLConnection.getConn();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		for(TNGPolygonFile.Province pr:provinces) {
			int xmax =0;
			int xmin = 100000000;
			int ymax = 0;
			int ymin = 100000000;
			for(Line ln:pr) {
				Point p1 = ln.getPoint1();
				Point p2 = ln.getPoint2();
				if (xmax<p1.getX()) xmax = p1.getX();
				if (xmax<p2.getX()) xmax = p2.getX();
				if (ymax<p1.getY()) ymax = p1.getY();
				if (ymax<p2.getY()) ymax = p2.getY();
				if (xmin>p1.getX()) xmin = p1.getX();
				if (xmin>p2.getX()) xmin = p2.getX();
				if (ymin>p1.getY()) ymin = p1.getY();
				if (ymin>p2.getY()) ymin = p2.getY();
			}
			System.out.println(pr.getName()+" x("+xmin+"-"+xmax+") y("+ymin+"-"+ymax+")");
		}
	}
	
	public void correctProvNames() {
		for(TNGPolygonFile.Province pr:provinces) {
			String name = pr.getName();
			name = name.toLowerCase();
			name = name.substring(0,1).toUpperCase()+name.substring(1,name.length());
			pr.setName(name);
		}
	}
	
	/*
	public void convertCoordsysrt90toSweref99TM() {
		for(Polygon pr:provinces) {
			for(Point p:pr.getPoints()) {
				Coordinates c = new Coordinates((double)p.getY(),(double)p.getX());
				Coordinates wgs84 = c.convertWGS84();
				Coordinates sweref99TM= Coordinates.convertToSweref99TMFromWGS84(wgs84);
				Point p2 = new Point((int)Math.round(sweref99TM.getEast()),(int)Math.round(sweref99TM.getNorth()));
				//System.out.println(p);
				//System.out.println(p2);
			}
		}
	}*/
	
	
// saves an .tng file with RT90 coordinates in Sweref99TM coordinates
	public void saveFileConvert(String filename) throws IOException {
		DataOutputStream out = new DataOutputStream(new FileOutputStream(filename));
		out.writeInt(5);  // shape type == Polygon
		out.writeInt(provinces.length);  // number of Polygons
		out.writeInt(nameLength);  // name field length
		System.out.println("Save length: "+provinces.length);
		System.out.println("Save namelength: "+provinces.length);
		for (Province prov : provinces) {
			//int padlength = 50-prov.getName().length();
			String name = String.format("%1$-" +  nameLength + "s", prov.getName());
			//System.out.println("padded name:" + "\""+name+ "\"" + " lenght =" + prov.getName().length()+ " padlenght: "+padlength+ " padded lenght: "+  name.length());
			//System.out.println(name);
			out.writeBytes(name);
			// System.out.println(record.getField(nameField));
			BoundingBox box = prov.getBoundingBox();
			Point p1 = box.getP1();
			Point p2 = box.getP2();
			Coordinates c1 = new Coordinates((double)p1.getY(),(double)p1.getX());
			Coordinates sweref99TM_1= c1.convertToSweref99TMFromRT90();
			Point ps1 = new Point((int)Math.round(sweref99TM_1.getEast()),(int)Math.round(sweref99TM_1.getNorth()));
			Coordinates c2 = new Coordinates((double)p2.getY(),(double)p2.getX());
			Coordinates sweref99TM_2 = c2.convertToSweref99TMFromRT90();
			Point ps2 = new Point((int)Math.round(sweref99TM_2.getEast()),(int)Math.round(sweref99TM_2.getNorth()));
			out.writeInt(ps1.getX());
			out.writeInt(ps1.getY());
			out.writeInt(ps2.getX());
			out.writeInt(ps2.getY());
			out.writeInt(prov.getNumParts());
			out.writeInt(prov.getNumPoints());
			for (int part : prov.getParts()) {
				out.writeInt(part);
			}
			for (Point p : prov.getPoints()) {
				Coordinates c = new Coordinates((double)p.getY(),(double)p.getX());
				Coordinates sweref99TM= c.convertToSweref99TMFromRT90();
				Point ps = new Point((int)Math.round(sweref99TM.getEast()),(int)Math.round(sweref99TM.getNorth()));
				out.writeInt((int) Math.round(ps.getX()));
				out.writeInt((int) Math.round(ps.getY()));
			}
		}
		System.out.println("Save length: "+provinces.length);
		out.close();
	}
	
	
	public static void main(String[] args) {
		/*TNGPolygonFile poly;
		try {
			//poly = new TNGPolygonFile("provinser.tng");

			//poly = new TNGPolygonFile("socknar.tng");
			
			//poly.saveFileConvert("provinserSWEREF99TM.tng");
			//poly.saveFileConvert("socknarSWEREF99TM.tng");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
	}
}
