package gis.layers;

import gis.coords.CoordSystem;
import gis.coords.Coordinate;
import gis.core.Layer;
import gis.core.MapCanvas;
import gis.geometry.Extent;
import gis.geopackage.GeoPackageReader;
import gis.geopackage.GpkgGeometry;
import gis.shapefile.ShapeType;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws one feature table of a GeoPackage (.gpkg) file: points and
 * multipoints as circles, linestrings as open line strings and polygons
 * (including multipolygons) as closed outlines. Coordinates are projected
 * to the map canvas CRS once, at load time.
 */
public class GeoPackageLayer extends Layer {
	private static final Stroke LINE_STROKE = new BasicStroke(1.5f);
	private static final int POINT_RADIUS = 3;

	/** One feature row, projected to the canvas CRS. */
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
	private final String tableName;
	private final MapCanvas mapCanvas;
	private List<Feature> features = new ArrayList<Feature>();
	private String[] fieldNames = new String[0];
	private Extent cachedExtent;

	public GeoPackageLayer(String fileName, String tableName, MapCanvas mapCanvas, CoordSystem fileCRS)
			throws IOException {
		super(fileName + ":" + tableName, false, fileCRS);
		this.fileName = fileName;
		this.tableName = tableName;
		this.mapCanvas = mapCanvas;
		readFile();
	}

	private void readFile() throws IOException {
		List<Feature> loaded = new ArrayList<Feature>();
		try (GeoPackageReader reader = new GeoPackageReader(fileName)) {
			GeoPackageReader.FeatureTable table = null;
			for (GeoPackageReader.FeatureTable t : reader.getFeatureTables()) {
				if (t.tableName.equals(tableName)) {
					table = t;
					break;
				}
			}
			if (table == null) {
				throw new IOException("No feature table '" + tableName + "' in " + fileName);
			}
			fieldNames = reader.getFieldNames(table);
			for (GeoPackageReader.Feature f : reader.readFeatures(table)) {
				loaded.add(project(f));
			}
		}
		features = loaded;
		cachedExtent = calculateExtent();
	}

	private Feature project(GeoPackageReader.Feature f) {
		GpkgGeometry g = f.geometry;
		Coordinate[] points = new Coordinate[g.getNumPoints()];
		double minE = Double.MAX_VALUE, maxE = -Double.MAX_VALUE;
		double minN = Double.MAX_VALUE, maxN = -Double.MAX_VALUE;
		for (int i = 0; i < points.length; i++) {
			// WKB X is easting/longitude, Y is northing/latitude
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
		return new Feature(g.getType(), parts, points, box, f.attributes);
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
			// Better an empty layer than stale features drawn in the old CRS
			features = new ArrayList<Feature>();
			cachedExtent = null;
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
