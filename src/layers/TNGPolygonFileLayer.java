package layers;

import coords.*;
import core.Layer;
import geometry.BoundingBox;
import geometry.Polygon;
import java.awt.*;
import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import shapeFile.DataInputStreamSE;

public class TNGPolygonFileLayer implements Layer {
	private final String fileName;
	private String name;
	private int nameLength;
	private Color color;
	private int maxZoom, minZoom;
	private Province[] provinces;
	private boolean hidden;
	private CoordSystem cs;
	
	public static class Province extends Polygon{
		private String name;
		private final BoundingBox box;
		
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
	
	public TNGPolygonFileLayer(String fileName) throws IOException {
		this.fileName=fileName;
		this.name = fileName;
		readFile();
		cs = CoordSystem.SWEREF99TM;
	}
	
	private void readFile() throws IOException {
		 DataInputStreamSE in =
			        new DataInputStreamSE(
			          new BufferedInputStream(
			            new FileInputStream(fileName)));
		in.readInt();
		int nrRecords = in.readInt();
		System.out.println("fileName: "+fileName);
		System.out.println("Read nrRecords: "+nrRecords);
		nameLength = in.readInt();
		System.out.println("read nameLenght: "+nameLength);
		provinces = new Province[nrRecords];
		for (int i = 0; i < nrRecords; i++) {
			//System.out.println("ReccordNr: "+i);
			String name = in.readStringUTF8(nameLength).trim();  // length +2 stupid java adds a couple of bytes
			//System.out.println("nR: "+i+" Reads name: "+name);
			int x1 = in.readInt();
			int y1 = in.readInt();
			int x2 = in.readInt();
			int y2 = in.readInt();
			BoundingBox box = new BoundingBox(x1, y1, x2, y2);
			//System.out.println("BoundingBox: "+box);
			int numParts = in.readInt();
			int numPoints = in.readInt();
			//System.out.println("numParts: "+numParts+" numPoints: "+numPoints);
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
				out.writeInt(point.x);
				out.writeInt(point.y);
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
		if (hidden || provinces == null) return;

		Stroke s = g2d.getStroke();
		g2d.setStroke(new BasicStroke(1.5f));
		g2d.setColor(color);

		for (Province pr : provinces) {
			// Spatial Clipping: Only draw if the province is actually visible on screen
			if (bounds.intersects(pr.getBoundingBox())) {
				Point[] pts = pr.getPoints();
				int[] parts = pr.getParts();

				// Loop through each "part" (ring) of the polygon
				for (int i = 0; i < parts.length; i++) {
					int start = parts[i];
					int end = (i == parts.length - 1) ? pts.length : parts[i + 1];

					// Draw the lines for this part
					for (int j = start; j < end - 1; j++) {
						Point p1 = pts[j];
						Point p2 = pts[j + 1];

						int x1 = (int) (p1.getX() * xScale + xShift);
						int y1 = (int) (p1.getY() * yScale + yShift);
						int x2 = (int) (p2.getX() * xScale + xShift);
						int y2 = (int) (p2.getY() * yScale + yShift);

						g2d.drawLine(x1, y1, x2, y2);
					}
				}
			}
		}
		g2d.setStroke(s);
	}

	@Override
	public void setMinZoomL(int zoomLevel) { this.minZoom = zoomLevel; }

	@Override
	public void setMaxZoomL(int zoomLevel) { this.maxZoom = zoomLevel; }

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
	public void setHidden(boolean hidden) { this.hidden = hidden; }

	@Override
	public void setCRS(CoordSystem cs) {
		this.cs = cs;
	}

	@Override
	public CoordSystem getCRS() {
		return cs;
	}
}