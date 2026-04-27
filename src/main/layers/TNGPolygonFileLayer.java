package main.layers;

import main.coords.*;
import main.core.Layer;
import main.geometry.BoundingBox;
import main.geometry.Polygon;
import java.awt.*;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import main.shapeFile.DataInputStreamSE;

public class TNGPolygonFileLayer extends Layer {
	private final String fileName;
	private int nameLength;
	private Province[] provinces;
	static final Stroke LINE_STROKE = new BasicStroke(1.5f);
	
	public static class Province extends Polygon{
		private final String name;
		private final BoundingBox box;
		
		Province (String name, BoundingBox box, int[] parts, Point[] points) {
			super(parts, points);
			this.name = name;
			this.box = box;
		}

		public String getName() {
			return name;
		}

		public BoundingBox getBoundingBox() {
			return box;
		}
		
		public boolean isInside(Coordinate c) {
			Point p = c.getPoint();
			if (box.isInside(p)) {
				//System.out.println("inside Box: "+name);
				return super.isInside(p);
			} 
			return false;
		}
	}
	
	public TNGPolygonFileLayer(String fileName) throws IOException {
		super(fileName, false, CoordSystem.SWEREF99TM);
		this.fileName = fileName;
		readFile();
	}
	
	private void readFile() throws IOException {
		 DataInputStreamSE in =
			        new DataInputStreamSE(
			          new BufferedInputStream(
			            new FileInputStream(fileName)));
		int shapeType = in.readInt();
		if (shapeType != 5) throw new IOException("wrong shape type");
		int nrRecords = in.readInt();
		nameLength = in.readInt();
		provinces = new Province[nrRecords];
		for (int i = 0; i < nrRecords; i++) {
			String name = in.readStringUTF8(nameLength).trim();  // length +2 stupid java adds a couple of bytes
			int x1 = in.readInt();
			int y1 = in.readInt();
			int x2 = in.readInt();
			int y2 = in.readInt();
			BoundingBox box = new BoundingBox(x1, y1, x2, y2);
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
		in.close();
	}
	
	public Province[] getProvinces()
	{
		return provinces;
	}

	public Province inPolygon(Coordinate c) {
		for (Province pr: provinces) {
			if(pr.isInside(c)) return pr;
		}
		return null;
	}

	@Override
	public Extent getBoundaries() {
		//Todo: implement the method
		return null;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		if (isHidden() || provinces == null) return;

		// Use the guaranteed non-null color
		g2d.setColor(getColor());

		Stroke oldStroke = g2d.getStroke();
		g2d.setStroke(LINE_STROKE);

		for (Province pr : provinces) {
			if (bounds.intersects(pr.getBoundingBox())) {
				Point[] pts = pr.getPoints();
				int[] parts = pr.getParts();

				for (int i = 0; i < parts.length; i++) {
					int start = parts[i];
					int end = (i == parts.length - 1) ? pts.length : parts[i + 1];

					// Performance Tip: Use drawPolyline for faster rendering of rings
					int[] xPoints = new int[end - start];
					int[] yPoints = new int[end - start];

					for (int j = 0; j < (end - start); j++) {
						Point p = pts[start + j];
						xPoints[j] = (int) (p.getX() * xScale + xShift);
						yPoints[j] = (int) (p.getY() * yScale + yShift);
					}
					g2d.drawPolyline(xPoints, yPoints, xPoints.length);
				}
			}
		}
		g2d.setStroke(oldStroke);
	}
}