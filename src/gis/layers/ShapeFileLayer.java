package gis.layers;

import gis.coords.CoordSystem;
import gis.coords.Coordinate;
import gis.core.Layer;
import gis.core.MapCanvas;
import gis.geometry.Extent;
import gis.shapefile.ShapeType;
import gis.shapefile.ShapefileReader;
import gis.shapefile.ShpGeometry;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws any ESRI shapefile: points and multipoints as circles, polylines
 * as open line strings and polygons (including multi-ring/multipolygon
 * records) as closed outlines. Coordinates are projected to the map
 * canvas CRS once, at load time.
 */
public class ShapeFileLayer extends Layer {
	private static final Stroke LINE_STROKE = new BasicStroke(1.5f);
	private static final int POINT_RADIUS = 3;

	/** One shapefile record, projected to the canvas CRS. */
	private static class Feature {
		final ShapeType baseType;
		final int[] parts;
		final Coordinate[] points;
		final Extent box;
		final String[] attributes;

		Feature(ShapeType baseType, int[] parts, Coordinate[] points, Extent box, String[] attributes) {
			this.baseType = baseType;
			this.parts = parts;
			this.points = points;
			this.box = box;
			this.attributes = attributes;
		}
	}

	private final String fileName;
	private final MapCanvas mapCanvas;
	private List<Feature> features = new ArrayList<Feature>();
	private String[] fieldNames = new String[0];
	private Extent cachedExtent;

	public ShapeFileLayer(String fileName, MapCanvas mapCanvas, CoordSystem fileCRS) throws IOException {
		super(fileName, false, fileCRS);
		this.fileName = fileName;
		this.mapCanvas = mapCanvas;
		readFile();
	}

	private void readFile() throws IOException {
		List<Feature> loaded = new ArrayList<Feature>();
		try (ShapefileReader reader = new ShapefileReader(fileName)) {
			fieldNames = new String[reader.getFields().size()];
			for (int i = 0; i < fieldNames.length; i++) {
				fieldNames[i] = reader.getFields().get(i).name;
			}
			for (ShapefileReader.Feature f : reader) {
				loaded.add(project(f));
			}
		} catch (java.io.UncheckedIOException e) {
			throw e.getCause();
		}
		features = loaded;
		cachedExtent = calculateExtent();
	}

	private Feature project(ShapefileReader.Feature f) {
		ShpGeometry g = f.geometry;
		Coordinate[] points = new Coordinate[g.getNumPoints()];
		double minE = Double.MAX_VALUE, maxE = -Double.MAX_VALUE;
		double minN = Double.MAX_VALUE, maxN = -Double.MAX_VALUE;
		for (int i = 0; i < points.length; i++) {
			// Shapefile X is easting/longitude, Y is northing/latitude
			Coordinate c = getCRS().convertTo(new Coordinate(g.getY(i), g.getX(i)), mapCanvas.getCRS());
			points[i] = c;
			minE = Math.min(minE, c.getEast());
			maxE = Math.max(maxE, c.getEast());
			minN = Math.min(minN, c.getNorth());
			maxN = Math.max(maxN, c.getNorth());
		}
		int[] parts = new int[g.getNumParts()];
		for (int i = 0; i < parts.length; i++) {
			parts[i] = g.partStart(i);
		}
		Extent box = (points.length > 0) ? new Extent(minN, minE, maxN, maxE) : null;
		return new Feature(g.getType().base(), parts, points, box, f.attributes);
	}

	private Extent calculateExtent() {
		double minE = Double.MAX_VALUE, maxE = -Double.MAX_VALUE;
		double minN = Double.MAX_VALUE, maxN = -Double.MAX_VALUE;
		boolean any = false;
		for (Feature f : features) {
			if (f.box == null) continue;
			any = true;
			minE = Math.min(minE, f.box.c1.getEast());
			maxE = Math.max(maxE, f.box.c2.getEast());
			minN = Math.min(minN, f.box.c1.getNorth());
			maxN = Math.max(maxN, f.box.c2.getNorth());
		}
		return any ? new Extent(minN, minE, maxN, maxE) : null;
	}

	public String[] getFieldNames() {
		return fieldNames;
	}

	public int getFeatureCount() {
		return features.size();
	}

	@Override
	public Extent getBoundaries() {
		return cachedExtent;
	}

	@Override
	public void invalidateCache() {
		// Re-read to re-project when e.g. the canvas CRS changes
		try {
			readFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, Extent bounds) {
		if (isHidden()) return;

		g2d.setColor(getColor());
		Stroke oldStroke = g2d.getStroke();
		g2d.setStroke(LINE_STROKE);

		for (Feature f : features) {
			if (f.points.length == 0) continue;
			if (f.box != null && bounds != null && !bounds.intersects(f.box)) continue;

			switch (f.baseType) {
				case POINT:
				case MULTIPOINT:
					for (Coordinate c : f.points) {
						int x = (int) (c.getEast() * xScale + xShift);
						int y = (int) (c.getNorth() * yScale + yShift);
						g2d.drawOval(x - POINT_RADIUS, y - POINT_RADIUS, 2 * POINT_RADIUS, 2 * POINT_RADIUS);
					}
					break;
				case POLYLINE:
				case POLYGON:
					drawParts(g2d, f, xShift, xScale, yShift, yScale);
					break;
				default:
					break;
			}
		}
		g2d.setStroke(oldStroke);
	}

	private void drawParts(Graphics2D g2d, Feature f, double xShift, double xScale, double yShift, double yScale) {
		for (int i = 0; i < f.parts.length; i++) {
			int start = f.parts[i];
			int end = (i + 1 < f.parts.length) ? f.parts[i + 1] : f.points.length;
			int n = end - start;
			if (n < 2) continue;
			int[] xs = new int[n];
			int[] ys = new int[n];
			for (int j = 0; j < n; j++) {
				Coordinate c = f.points[start + j];
				xs[j] = (int) (c.getEast() * xScale + xShift);
				ys[j] = (int) (c.getNorth() * yScale + yShift);
			}
			if (f.baseType == ShapeType.POLYGON) {
				g2d.drawPolygon(xs, ys, n); // closes each ring
			} else {
				g2d.drawPolyline(xs, ys, n);
			}
		}
	}
}