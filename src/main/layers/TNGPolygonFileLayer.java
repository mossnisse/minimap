package main.layers;

import main.coords.*;
import main.core.Canvas;
import main.core.Layer;
import main.geometry.CPolygon;
import main.geometry.Extent;

import java.awt.*;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import main.shapeFile.DataInputStreamSE;

public class TNGPolygonFileLayer extends Layer {
	private final String fileName;
	private final Canvas canvas;
	private int nameLength;
	private Province[] provinces;
	static final Stroke LINE_STROKE = new BasicStroke(1.5f);

	public static class Province extends CPolygon {
		private final String name;
		private final Extent box;

		Province (String name, Extent box, int[] parts, Coordinate[] points) {
			super(parts, points);
			this.name = name;
			this.box = box;
		}

		public String getName() {
			return name;
		}

		//public Extent getBoundingBox() {return box;}

		public Extent getBoundingBox() {
			return box;
		}
		
		public boolean isInside(Coordinate c) {
			if (box.isInside(c)) {
				//System.out.println("inside Box: "+name);
				return super.isInside(c);
			} 
			return false;
		}
	}
	
	public TNGPolygonFileLayer(String fileName, Canvas canvas) {
		super(fileName, false, CoordSystem.SWEREF99TM);
		this.fileName = fileName;
		this.canvas = canvas;
		readFile();
	}
	
	private void readFile() {
		try {
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
				Coordinate c1 = new Coordinate(y1, x1);
				Coordinate cc1 = getCRS().convertTo(c1, canvas.getCRS());
				Coordinate c2 = new Coordinate(y2, x2);
				Coordinate cc2 = getCRS().convertTo(c2, canvas.getCRS());

				Extent box = new Extent(cc1, cc2);
				int numParts = in.readInt();
				int numPoints = in.readInt();
				int[] parts = new int[numParts];
				for (int j = 0; j < numParts; j++) {
					parts[j] = in.readInt();
				}
				Coordinate[] points = new Coordinate[numPoints];
				for (int j = 0; j < numPoints; j++) {
					int px = in.readInt();
					int py = in.readInt();
					Coordinate swer = new Coordinate(py, px);
					points[j] = getCRS().convertTo(swer, canvas.getCRS());
				}
				provinces[i] = new Province(name, box, parts, points);
			}
			in.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
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
	public void invalidateCache() {

	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, Extent bounds) {
		if (isHidden() || provinces == null) return;

		// Use the guaranteed non-null color
		g2d.setColor(getColor());

		Stroke oldStroke = g2d.getStroke();
		g2d.setStroke(LINE_STROKE);

		for (Province pr : provinces) {
			if (bounds.intersects(pr.getBoundingBox())) {
				Coordinate[] pts = pr.getPoints();
				int[] parts = pr.getParts();

				for (int i = 0; i < parts.length; i++) {
					int start = parts[i];
					int end = (i == parts.length - 1) ? pts.length : parts[i + 1];

					// Performance Tip: Use drawPolyline for faster rendering of rings
					int[] xPoints = new int[end - start];
					int[] yPoints = new int[end - start];

					for (int j = 0; j < (end - start); j++) {
						Coordinate p = pts[start + j];
						xPoints[j] = (int) (p.getEast() * xScale + xShift);
						yPoints[j] = (int) (p.getNorth() * yScale + yShift);
					}
					g2d.drawPolyline(xPoints, yPoints, xPoints.length);
				}
			}
		}
		g2d.setStroke(oldStroke);
	}
}