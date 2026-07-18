package gis.layers;

import gis.coords.CoordSystem;
import gis.coords.Coordinate;
import gis.core.Layer;
import gis.core.MapCanvas;
import gis.geometry.Extent;
import gis.geopackage.GeoPackageReader;
import gis.geopackage.GeoPackageWriter;
import gis.geopackage.GpkgGeometry;
import gis.shapefile.ShapeType;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws one feature table of a GeoPackage (.gpkg) file: points and
 * multipoints as circles, linestrings as open line strings and polygons
 * (including multipolygons) as closed outlines.
 *
 * The layer keeps the table rows in memory (geometry in the file CRS plus
 * all attribute columns) so the attribute table can be edited after loading
 * and saved back with {@link #save()}, which applies the changes with SQL in
 * one transaction. Point tables get editable coordinates too; other geometry
 * types are attribute-only, but rows can be added (without geometry) and
 * deleted for every type. The fid (INTEGER PRIMARY KEY) column is shown but
 * not editable, since it identifies the rows being updated.
 */
public class GeoPackageLayer extends Layer implements EditableTableLayer {
	private static final Stroke LINE_STROKE = new BasicStroke(1.5f);
	private static final int POINT_RADIUS = 3;

	/** One feature row, projected to the canvas CRS. */
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

	/** One table row: the editable state save() writes back. */
	private static class Row {
		Long rowid; // null until the row is inserted on save
		GpkgGeometry geometry; // file CRS; null = no geometry
		final List<Object> values;
		final BitSet dirtyColumns = new BitSet();
		boolean geometryDirty;

		Row(Long rowid, GpkgGeometry geometry, List<Object> values) {
			this.rowid = rowid;
			this.geometry = geometry;
			this.values = values;
		}
	}

	private final String fileName;
	private final String tableName;
	private final MapCanvas mapCanvas;

	private GeoPackageReader.FeatureTable table;
	private final List<String> columns = new ArrayList<>();
	private final List<Integer> columnTypes = new ArrayList<>();
	private int pkColumn = -1; // index into columns of the fid, -1 when none
	private final List<Row> rows = new ArrayList<>();
	private final List<Long> deletedRowids = new ArrayList<>();
	// Ordered schema-change log ({"add",name} / {"rename",from,to} / {"drop",name}),
	// replayed on save so a rename-then-drop sequence uses the right names
	private final List<String[]> columnOps = new ArrayList<>();
	private boolean dirty;
	private Window openEditor;

	// Projected drawing state, derived from the raw rows
	private List<Feature> features = new ArrayList<>();
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
		try (GeoPackageReader reader = new GeoPackageReader(fileName)) {
			for (GeoPackageReader.FeatureTable t : reader.getFeatureTables()) {
				if (t.tableName.equals(tableName)) {
					table = t;
					break;
				}
			}
			if (table == null) {
				throw new IOException("No feature table '" + tableName + "' in " + fileName);
			}
			columns.addAll(Arrays.asList(reader.getFieldNames(table)));
			for (int type : reader.getFieldTypes(table)) columnTypes.add(type);
			String pkName = reader.getPrimaryKeyColumn(table);
			pkColumn = (pkName == null) ? -1 : columns.indexOf(pkName);
			for (GeoPackageReader.Row r : reader.readRows(table)) {
				rows.add(new Row(r.rowid, r.geometry,
						new ArrayList<Object>(Arrays.asList(r.attributes))));
			}
		}
		rebuildProjection();
	}

	/** Re-derives the drawn features from the raw rows (file CRS -> canvas CRS). */
	private void rebuildProjection() {
		List<Feature> projected = new ArrayList<>(rows.size());
		for (Row row : rows) {
			if (row.geometry != null && !row.geometry.isEmpty()) {
				projected.add(project(row.geometry));
			}
		}
		features = projected;
		cachedExtent = calculateExtent();
	}

	private Feature project(GpkgGeometry g) {
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
		return new Feature(g.getType(), parts, points, box);
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
		return columns.toArray(new String[0]);
	}

	// ---- EditableTableLayer: attribute table ----

	@Override
	public String getSourceName() {
		return new File(fileName).getName() + ":" + tableName;
	}

	@Override
	public String getSourcePath() {
		return fileName + ":" + tableName;
	}

	public String getFilePath() {
		return fileName;
	}

	public String getTableName() {
		return tableName;
	}

	@Override
	public int getFeatureCount() {
		return rows.size();
	}

	@Override
	public int getColumnCount() {
		return columns.size();
	}

	@Override
	public String getColumnName(int column) {
		return columns.get(column);
	}

	@Override
	public boolean isCellEditable(int row, int column) {
		return column != pkColumn && !isBinaryType(columnTypes.get(column));
	}

	@Override
	public String getAttribute(int row, int column) {
		Object value = rows.get(row).values.get(column);
		if (value == null) return "";
		if (value instanceof byte[] bytes) return "[BLOB " + bytes.length + " bytes]";
		return String.valueOf(value);
	}

	@Override
	public void setAttribute(int row, int column, String value) {
		Row r = rows.get(row);
		r.values.set(column, value == null ? "" : value);
		r.dirtyColumns.set(column);
		dirty = true;
	}

	private static boolean isBinaryType(int type) {
		return type == Types.BINARY || type == Types.VARBINARY
				|| type == Types.LONGVARBINARY || type == Types.BLOB;
	}

	@Override
	public void addColumn(String name) {
		columnOps.add(new String[]{"add", name});
		columns.add(name);
		columnTypes.add(Types.VARCHAR);
		for (Row row : rows) {
			row.values.add(null);
		}
		dirty = true;
	}

	@Override
	public void renameColumn(int column, String name) {
		columnOps.add(new String[]{"rename", columns.get(column), name});
		columns.set(column, name);
		dirty = true;
	}

	@Override
	public void deleteColumn(int column) {
		columnOps.add(new String[]{"drop", columns.get(column)});
		columns.remove(column);
		columnTypes.remove(column);
		for (Row row : rows) {
			row.values.remove(column);
			removeDirtyColumn(row.dirtyColumns, column);
		}
		if (column < pkColumn) {
			pkColumn--;
		}
		dirty = true;
	}

	private static void removeDirtyColumn(BitSet dirtyColumns, int removedColumn) {
		int length = dirtyColumns.length();
		if (removedColumn >= length) return;
		BitSet following = dirtyColumns.get(removedColumn + 1, length);
		dirtyColumns.clear(removedColumn, length);
		for (int bit = following.nextSetBit(0); bit >= 0; bit = following.nextSetBit(bit + 1)) {
			dirtyColumns.set(removedColumn + bit);
		}
	}

	@Override
	public String validateNewColumnName(String name) {
		return null; // any name works quoted; duplicates are caught by the editor
	}

	@Override
	public String validateColumnRename(int column) {
		return column == pkColumn
				? "The primary key column identifies the rows being saved and can't be renamed here."
				: null;
	}

	@Override
	public String validateColumnDelete(int column) {
		return column == pkColumn
				? "The primary key column identifies the rows being saved and can't be deleted."
				: null;
	}

	// ---- EditableTableLayer: geometry ----

	@Override
	public boolean isPointEditable() {
		return "POINT".equalsIgnoreCase(table.geometryTypeName);
	}

	@Override
	public Double getPointX(int row) {
		GpkgGeometry g = rows.get(row).geometry;
		return (g == null || g.getNumPoints() == 0) ? null : g.getX(0);
	}

	@Override
	public Double getPointY(int row) {
		GpkgGeometry g = rows.get(row).geometry;
		return (g == null || g.getNumPoints() == 0) ? null : g.getY(0);
	}

	@Override
	public void setPoint(int row, Double x, Double y) {
		Row r = rows.get(row);
		r.geometry = (x == null || y == null) ? null : GpkgGeometry.point(x, y);
		r.geometryDirty = true;
		dirty = true;
		rebuildProjection();
		mapCanvas.repaint();
	}

	@Override
	public boolean canAddDeleteRows() {
		return true; // deleting works for every type; new rows start without geometry
	}

	@Override
	public void addRow() {
		List<Object> values = new ArrayList<>(columns.size());
		for (int i = 0; i < columns.size(); i++) {
			values.add(null);
		}
		rows.add(new Row(null, null, values));
		dirty = true;
	}

	@Override
	public void deleteRows(int[] rowIndexes) {
		int[] sorted = rowIndexes.clone();
		Arrays.sort(sorted);
		for (int i = sorted.length - 1; i >= 0; i--) {
			if (i < sorted.length - 1 && sorted[i] == sorted[i + 1]) continue;
			Row removed = rows.remove(sorted[i]);
			if (removed.rowid != null) {
				deletedRowids.add(removed.rowid);
			}
		}
		dirty = true;
		rebuildProjection();
		mapCanvas.repaint();
	}

	// ---- EditableTableLayer: save / bookkeeping ----

	@Override
	public String getStatusText() {
		String text = rows.size() + " features (" + table.geometryTypeName
				+ ") in table " + tableName;
		if (isPointEditable()) {
			return text + " — X/Y are in the layer's CRS; blank X or Y = no geometry";
		}
		return text + " — attributes editable, shapes are not";
	}

	@Override
	public boolean isDirty() {
		return dirty;
	}

	@Override
	public Window getOpenEditor() {
		return openEditor;
	}

	@Override
	public void setOpenEditor(Window openEditor) {
		this.openEditor = openEditor;
	}

	/** Applies all unsaved edits to the GeoPackage in one transaction. */
	@Override
	public void save() throws IOException {
		Map<Row, Long> insertedRowids = new IdentityHashMap<>();
		try (GeoPackageWriter writer = new GeoPackageWriter(fileName)) {
			for (String[] op : columnOps) {
				switch (op[0]) {
					case "add" -> writer.addColumn(tableName, op[1]);
					case "rename" -> writer.renameColumn(tableName, op[1], op[2]);
					case "drop" -> writer.dropColumn(tableName, op[1]);
				}
			}
			for (Long rowid : deletedRowids) {
				writer.deleteRow(tableName, rowid);
			}

			for (Row row : rows) {
				if (row.rowid == null) {
					List<String> insertColumns = new ArrayList<>();
					List<Object> insertValues = new ArrayList<>();
					for (int column = 0; column < columns.size(); column++) {
						if (column != pkColumn && row.values.get(column) != null) {
							insertColumns.add(columns.get(column));
							insertValues.add(row.values.get(column));
						}
					}
					long rowid = writer.insertRow(tableName, insertColumns, insertValues,
							table.geometryColumn, geometryBlob(row));
					insertedRowids.put(row, rowid);
				} else {
					List<String> updateColumns = new ArrayList<>();
					List<Object> updateValues = new ArrayList<>();
					for (int column = row.dirtyColumns.nextSetBit(0); column >= 0;
							column = row.dirtyColumns.nextSetBit(column + 1)) {
						if (column != pkColumn) {
							updateColumns.add(columns.get(column));
							updateValues.add(row.values.get(column));
						}
					}
					writer.updateAttributes(tableName, row.rowid, updateColumns, updateValues);
					if (row.geometryDirty) {
						writer.updateGeometry(tableName, table.geometryColumn, row.rowid, geometryBlob(row));
					}
				}
			}
			writer.touchLastChange(tableName);
			writer.commit();
		}
		for (Map.Entry<Row, Long> inserted : insertedRowids.entrySet()) {
			Row row = inserted.getKey();
			row.rowid = inserted.getValue();
			if (pkColumn >= 0) row.values.set(pkColumn, inserted.getValue());
		}
		for (Row row : rows) {
			row.dirtyColumns.clear();
			row.geometryDirty = false;
		}
		deletedRowids.clear();
		columnOps.clear();
		dirty = false;
	}

	private byte[] geometryBlob(Row row) {
		return (row.geometry == null || row.geometry.getNumPoints() == 0) ? null
				: GpkgGeometry.encodePoint(row.geometry.getX(0), row.geometry.getY(0), table.srsId);
	}

	// ---- drawing ----

	@Override
	public Extent getBoundaries() {
		return cachedExtent;
	}

	@Override
	public void invalidateCache() {
		// Re-project the in-memory data when e.g. the canvas CRS changes.
		// No re-read from the file: that would throw away unsaved table edits.
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
