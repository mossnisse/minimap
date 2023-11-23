import geometry.BoundingBox;
import geometry.Point;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import shapeFile.DataInputStreamSE;
import shapeFile.PointESRI;

public class TNGPointFile implements Layer{
	private String fileName, name;
	private Color color;
	private Locality[] localities;
	private boolean hidden;
	private CoordSystem cs;
	private int nameLength;
	
	public class Locality extends Point{
		public String name;
		
		public Locality(int x, int y, String name) {
			super(x,y);
			this.name = name;
		}
		
		public Locality(Point p, String name) {
			super(p);
			this.name = name;
		}
		
		private double distance(double x, double y) {
			return Math.sqrt(Math.pow(getX()-x,2) + Math.pow(getY()-y,2));
		}
		
		public double dist(int x, int y) {
			return  Math.round(distance(x, y)/100)/10.0; //Math.round(
		}
		
		public String riktning(double x, double y) {
			double v = Math.atan2(getY()-y, getX()-x);
			String rikt = "";
			if 	    (v>7*Math.PI/8) rikt = "V";
			else if (v>5*Math.PI/8) rikt = "NV";
			else if (v>3*Math.PI/8) rikt = "N";
			else if (v>1*Math.PI/8) rikt = "NE";
			else if (v>-1*Math.PI/8) rikt = "E";
			else if (v>-3*Math.PI/8) rikt = "SE";
			else if (v>-5*Math.PI/8) rikt = "S";
			else if (v>-7*Math.PI/8) rikt = "SV";
	 		return rikt;
		}
		
		public String getName() {
			return name;
		}
		
		public String toString() {
			String xs = Integer.toString(getX());
			String ys = Integer.toString(getY());
			return new String("("+xs+", "+ys+", "+name+")");
		}
		
		public Point getPoint() {
			return new Point(this.getX(), this.getY());
		}
	}
	
	public TNGPointFile(String fileName) throws IOException {
		this.fileName = fileName;
		this.name = fileName;
		this.cs = CoordSystem.Sweref99TM;
		readFile();
	}
	
	public TNGPointFile(ArrayList<Point> loca, ArrayList<String> names, String name) {
		this.name = name;
		localities = new Locality[loca.size()];
		int i=0;
		for(Point loci:loca) {
			localities[i]=new Locality(loci,names.get(i));
			i++;
		}
	}
	
	private void readFile() throws IOException {
		 DataInputStreamSE in =
			        new DataInputStreamSE(
			          new BufferedInputStream(
			            new FileInputStream(fileName)));
		in.readInt();
		int nrRecords = in.readInt();

		nameLength = in.readInt();
		localities = new Locality[nrRecords];
		for (int i = 0; i < nrRecords; i++) {
			String name = in.readString(nameLength).trim();
			int x = in.readInt();
			int y = in.readInt();
			localities[i] = new Locality(x,y,name);
		}
		in.close();
	}
	
	public Locality[] getLocalities() {
		return localities;
	}

	@Override
	public void setColor(Color color) {
		this.color=color;
		
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

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale,double yShift, double yScale, BoundingBox bounds) {
		if (!hidden) {
			Stroke s = g2d.getStroke();
			g2d.setStroke(new BasicStroke(2));
			for(Locality koord:localities) {
				int x = (int) ((koord.getY()*xScale)+xShift);
				int y = (int) ((koord.getX()*yScale)+yShift);
					g2d.drawOval(x-6, y-6, 12, 12);
					g2d.drawLine(x,y+10,x,y-10);
					g2d.drawLine(x+10,y,x-10,y);
				//System.out.println("name:" + koord.name +" long: "+koord.getY()+" lat: "+koord.getX()+" x:"+x+" y:"+y);
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
	
	public BoundingBox getBounds() {
		int maxX = -500000000;
		int maxY = -500000000;
		int minX =  500000000;
		int minY =  500000000;
		for(Locality koord:localities) {
			if (koord.getY()<minY) minY=koord.getY();
			if (koord.getY()>maxY) maxY=koord.getY();
			if (koord.getX()<minX) minX=koord.getX();
			if (koord.getX()>maxX) maxX=koord.getX();
		}
		return new BoundingBox(minY,minX,maxY,maxX);
	}
	
	public String toString() {
		String ans = new String(name+": ");
		for(Locality koord:localities) {
			ans=ans+koord.toString();
		}
		return ans;
	}
	
	public int size() {
		return localities.length;
	}

	@Override
	public void setCRS(CoordSystem cs) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public CoordSystem getCRS() {
		return cs;
	}
	
	public void saveFileConvert(String filename) {
		DataOutputStream out;
		try {
			out = new DataOutputStream(new FileOutputStream(filename));
			out.writeInt(1);  // shape type == Polygon
			out.writeInt(localities.length); // nr reccords
			out.writeInt(nameLength);
			for(Locality koord:localities) {
				out.writeBytes(koord.name);
				// System.out.println(record.getField(nameField));
				out.writeInt(koord.getX());
				out.writeInt(koord.getY());
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void main(String[] args) {
		TNGPointFile pf;
		try {
			pf = new TNGPointFile("orter.tng");

			pf.saveFileConvert("orterSWEREF99TM.tng");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
