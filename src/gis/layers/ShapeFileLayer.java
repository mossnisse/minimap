package gis.layers;

import gis.coords.CoordSystem;
import gis.coords.Coordinate;
import gis.core.Layer;
import gis.core.MapCanvas;
import gis.geometry.Extent;
import gis.shapefile.DbfField;
import gis.shapefile.ShapeType;
import gis.shapefile.ShapefileReader;
import gis.shapefile.ShapefileWriter;
import gis.shapefile.ShpGeometry;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Draws any ESRI shapefile: points and multipoints as circles, polylines
 * as open line strings and polygons (including multi-ring/multipolygon
 * records) as closed outlines.
 *
 * The layer keeps the raw file data (geometry in the file CRS plus the .dbf
 * attribute rows) so the attribute table can be edited after loading and
 * saved back with {@link #save()}. For plain point files the coordinates are
 * editable too; other shape types are attribute-only. Drawing uses a
 * projected copy rebuilt from the raw data whenever it changes.
 */
public class ShapeFileLayer extends Layer implements EditableTableLayer {
	private static final Stroke LINE_STROKE = new BasicStroke(1.5f);
	private static final int POINT_RADIUS = 3;

	/** One record projected to the canvas CRS. */
	private static class Feature {
		final ShapeType baseType;
		final int[] parts;
		final Coordinate[] points;
		final Extent box;

		Feature(ShapeType baseType, int[] parts, Coordinate[] points, Extent box) {
			this.baseType = baseType;
			this.parts = parts;
			this.points = points;
			this.box = box;
		}
	}

	private final String fileName;
	private final MapCanvas mapCanvas;

	// Raw file data (geometry in the file CRS): the editable state save() writes back
	private ShapeType fileType = ShapeType.NULL;
	private final List<ShpGeometry> geometries = new ArrayList<>();
	private final List<DbfField> fields = new ArrayList<>();
	private final List<List<String>> rows = new ArrayList<>();
	private final List<Boolean> deletedFlags = new ArrayList<>();
	private boolean dirty;
	private boolean geometryDirty; // a .shp/.shx rewrite is needed, not just the .dbf
	private Window openEditor;

	// Projected drawing state, derived from the raw data
	private List<Feature> features = new ArrayList<>();
	private Extent cachedExtent;

	public ShapeFileLayer(String fileName, MapCanvas mapCanvas, CoordSystem fileCRS) throws IOException {
		super(fileName, false, fileCRS);
		this.fileName = fileName;
		this.mapCanvas = mapCanvas;
		readFile();
	}

	private void readFile() throws IOException {
		try (ShapefileReader reader = new ShapefileReader(fileName)) {
			fileType = reader.getShapeType();
			fields.addAll(reader.getFields());
			for (ShapefileReader.Feature f : reader) {
				geometries.add(f.geometry);
				List<String> row = new ArrayList<>(fields.size());
				for (int i = 0; i < fields.size(); i++) {
					row.add(i < f.attributes.length ? f.attributes[i] : "");
				}
				rows.add(row);
				deletedFlags.add(f.deleted);
			}
		} catch (java.io.UncheckedIOException e) {
			throw e.getCause();
		}
		rebuildProjection();
	}

	/** Re-derives the drawn features from the raw geometry (file CRS -> canvas CRS). */
	private void rebuildProjection() {
		List<Feature> projected = new ArrayList<>(geometries.size());
		for (ShpGeometry g : geometries) {
			projected.add(project(g));
		}
		features = projected;
		cachedExtent = calculateExtent();
	}

	private Feature project(ShpGeometry g) {
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
		return new Feature(g.getType().base(), parts, points, box);
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

	// ---- attribute table editing ----

	public String getFileName() {
		return fileName;
	}

	@Override
	public String getSourceName() {
		return new File(fileName).getName();
	}

	@Override
	public String getSourcePath() {
		return fileName;
	}

	@Override
	public int getColumnCount() {
		return fields.size();
	}

	@Override
	public String getColumnName(int column) {
		return fields.get(column).name;
	}

	@Override
	public boolean isCellEditable(int row, int column) {
		return true;
	}

	@Override
	public String validateNewColumnName(String name) {
		return name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 10
				? "dBASE column names can be at most 10 bytes long." : null;
	}

	@Override
	public String validateColumnRename(int column) {
		return null;
	}

	@Override
	public String validateColumnDelete(int column) {
		return fields.size() == 1
				? "A shapefile attribute table needs at least one column." : null;
	}

	@Override
	public String getStatusText() {
		String text = geometries.size() + " features (" + fileType + ")";
		if (isEditablePointFile()) {
			return text + " — X/Y are in the file's CRS; blank X or Y = no geometry";
		}
		return text + " — attributes editable, shapes are not";
	}

	public String[] getFieldNames() {
		String[] names = new String[fields.size()];
		for (int i = 0; i < names.length; i++) {
			names[i] = fields.get(i).name;
		}
		return names;
	}

	public int getFeatureCount() {
		return geometries.size();
	}

	public ShapeType getShapeType() {
		return fileType;
	}

	/** True for plain point files, whose coordinates can be edited and saved. */
	public boolean isEditablePointFile() {
		return fileType == ShapeType.POINT;
	}

	@Override
	public boolean isPointEditable() {
		return isEditablePointFile();
	}

	@Override
	public boolean canAddDeleteRows() {
		// Row changes rewrite the .shp, which only point records support
		return isEditablePointFile();
	}

	public List<DbfField> getFields() {
		return Collections.unmodifiableList(fields);
	}

	public String getAttribute(int row, int column) {
		return rows.get(row).get(column);
	}

	public void setAttribute(int row, int column, String value) {
		rows.get(row).set(column, value == null ? "" : value);
		dirty = true;
	}

	/** Adds a character column; its stored width grows with the values on save. */
	public void addColumn(String name) {
		fields.add(new DbfField(name, 'C', 1, 0));
		for (List<String> row : rows) {
			row.add("");
		}
		dirty = true;
	}

	public void renameColumn(int column, String name) {
		DbfField old = fields.get(column);
		fields.set(column, new DbfField(name, old.type, old.length, old.decimalCount));
		dirty = true;
	}

	public void deleteColumn(int column) {
		fields.remove(column);
		for (List<String> row : rows) {
			row.remove(column);
		}
		dirty = true;
	}

	// ---- point geometry editing (plain point files only) ----

	/** The row's easting/longitude in the file CRS, or null for a null shape. */
	public Double getPointX(int row) {
		ShpGeometry g = geometries.get(row);
		return (g.isNull() || g.getNumPoints() == 0) ? null : g.getX(0);
	}

	/** The row's northing/latitude in the file CRS, or null for a null shape. */
	public Double getPointY(int row) {
		ShpGeometry g = geometries.get(row);
		return (g.isNull() || g.getNumPoints() == 0) ? null : g.getY(0);
	}

	/** Moves the row's point (file CRS); either coordinate null makes it a null shape. */
	public void setPoint(int row, Double x, Double y) {
		geometries.set(row, (x == null || y == null) ? ShpGeometry.nullShape() : ShpGeometry.point(x, y));
		markGeometryChanged();
	}

	/** Appends a row with empty attributes and no geometry yet. */
	@Override
	public void addRow() {
		geometries.add(ShpGeometry.nullShape());
		List<String> row = new ArrayList<>(fields.size());
		for (int i = 0; i < fields.size(); i++) {
			row.add("");
		}
		rows.add(row);
		deletedFlags.add(false);
		markGeometryChanged();
	}

	/** Deletes the given rows (any order, duplicates tolerated), geometry included. */
	public void deleteRows(int[] rowIndexes) {
		int[] sorted = rowIndexes.clone();
		Arrays.sort(sorted);
		for (int i = sorted.length - 1; i >= 0; i--) {
			if (i < sorted.length - 1 && sorted[i] == sorted[i + 1]) continue;
			geometries.remove(sorted[i]);
			rows.remove(sorted[i]);
			deletedFlags.remove(sorted[i]);
		}
		markGeometryChanged();
	}

	private void markGeometryChanged() {
		dirty = true;
		geometryDirty = true;
		rebuildProjection();
		mapCanvas.repaint();
	}

	// ---- save / editor bookkeeping ----

	public boolean isDirty() {
		return dirty;
	}

	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	/** The table editor currently showing this layer, or null. */
	public Window getOpenEditor() {
		return openEditor;
	}

	public void setOpenEditor(Window openEditor) {
		this.openEditor = openEditor;
	}

	/**
	 * Writes the attribute table back to the .dbf (as UTF-8 with a .cpg
	 * sidecar), and the .shp/.shx too when point geometry changed.
	 */
	public void save() throws IOException {
		String base = ShapefileReader.stripExtension(fileName);
		ShapefileWriter.writeAtomically(
				fields.isEmpty() ? null : new File(base + ".dbf"),
				fields.isEmpty() ? null : new File(base + ".cpg"),
				fields, rows, deletedFlags,
				geometryDirty ? new File(base + ".shp") : null,
				geometryDirty ? new File(base + ".shx") : null,
				geometryDirty ? geometries : null);
		dirty = false;
		geometryDirty = false;
	}

	// ---- drawing ----

	@Override
	public Extent getBoundaries() {
		return cachedExtent;
	}

	@Override
	public void invalidateCache() {
		// Re-project the in-memory data when e.g. the canvas CRS changes.
		// No disk re-read: that would throw away unsaved table edits.
		rebuildProjection();
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
