package gis.layers;

import gis.coords.*;
import gis.core.MapCanvas;
import gis.core.Layer;
import gis.geometry.CPolygon;
import gis.geometry.Extent;

import java.awt.*;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import gis.shapefile.DataInputStreamSE;

public class TNGPolygonFileLayer extends Layer {
	private final String fileName;
	private final MapCanvas mapCanvas;
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

		public Extent getBoundingBox() {
			return box;
		}
		
		public boolean isInside(Coordinate c) {
			if (box.isInside(c)) {
				return super.isInside(c);
			} 
			return false;
		}
	}
	
	public TNGPolygonFileLayer(String fileName, MapCanvas mapCanvas) {
		super(fileName, false, CoordSystem.SWEREF99TM);
		this.fileName = fileName;
		this.mapCanvas = mapCanvas;
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
			int nameLength = in.readInt();
			provinces = new Province[nrRecords];
			for (int i = 0; i < nrRecords; i++) {
				String name = in.readStringUTF8(nameLength).trim();  // length +2 stupid java adds a couple of bytes
				int x1 = in.readInt();
				int y1 = in.readInt();
				int x2 = in.readInt();
				int y2 = in.readInt();
				Coordinate c1 = new Coordinate(y1, x1);
				Coordinate cc1 = getCRS().convertTo(c1, mapCanvas.getCRS());
				Coordinate c2 = new Coordinate(y2, x2);
				Coordinate cc2 = getCRS().convertTo(c2, mapCanvas.getCRS());

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
					points[j] = getCRS().convertTo(swer, mapCanvas.getCRS());
				}
				provinces[i] = new Province(name, box, parts, points);
			}
			in.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public Province inPolygon(Coordinate c) {
		for (Province pr: provinces) {
			if(pr.isInside(c)) return pr;
		}
		return null;
	}

	/** Name of the polygon that contains {@code c}, or null. */
	public String nameAt(Coordinate c) {
		Province p = inPolygon(c);
		return (p != null) ? p.getName() : null;
	}

	@Override
	public Extent getBoundaries() {
		if (provinces == null || provinces.length == 0) return null;

		double minE = Double.MAX_VALUE, maxE = -Double.MAX_VALUE;
		double minN = Double.MAX_VALUE, maxN = -Double.MAX_VALUE;

		for (Province pr : provinces) {
			Extent box = pr.getBoundingBox();
			minE = Math.min(minE, Math.min(box.c1.getEast(), box.c2.getEast()));
			maxE = Math.max(maxE, Math.max(box.c1.getEast(), box.c2.getEast()));
			minN = Math.min(minN, Math.min(box.c1.getNorth(), box.c2.getNorth()));
			maxN = Math.max(maxN, Math.max(box.c1.getNorth(), box.c2.getNorth()));
		}
		return new Extent(minN, minE, maxN, maxE);
	}

	@Override
	public void invalidateCache() {
		readFile();
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