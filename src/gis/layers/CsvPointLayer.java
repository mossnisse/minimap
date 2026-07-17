package gis.layers;

import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import gis.coords.CoordSystem;
import gis.coords.Coordinate;
import gis.core.MapCanvas;
import gis.csv.CsvFile;

/**
 * A {@link PointTableLayer} backed by an editable CSV file. The layer owns the
 * parsed CSV, the column mapping (northing/easting/label) and the dirty flag,
 * so the table can be reopened for editing (from the Layer Manager) after the
 * original editor closed, and unsaved edits stay visible on the map until the
 * layer is removed.
 */
public class CsvPointLayer extends PointTableLayer {

	private static final List<String> NORTH_NAMES = List.of("north", "northing", "n", "lat", "latitude", "y", "nord");
	private static final List<String> EAST_NAMES = List.of("east", "easting", "e", "lon", "lng", "longitude", "x", "ost", "öst");

	private final MapCanvas mapCanvas;
	private final CsvFile csv;
	private File file;
	// Header names, not indexes, so the mapping survives column add/delete; null = unmapped
	private String northColumn, eastColumn, labelColumn;
	private boolean dirty;
	private final AtomicReference<List<LabeledPoint>> points;
	// Row indexes the last rebuild skipped (unparseable or out-of-range coordinates)
	private final Set<Integer> badRows = new HashSet<>();
	private Window openEditor;

	public CsvPointLayer(File file, CsvFile csv, CoordSystem crs, MapCanvas mapCanvas) {
		this(file, csv, crs, mapCanvas, new AtomicReference<>(List.of()));
	}

	// Split constructor so the PointSource lambda passed to super() captures
	// only the AtomicReference, which `this` can't be referenced before
	private CsvPointLayer(File file, CsvFile csv, CoordSystem crs, MapCanvas mapCanvas,
	                      AtomicReference<List<LabeledPoint>> points) {
		super(file.getName(), crs, mapCanvas, bounds -> points.get(), 1.0, false);
		this.mapCanvas = mapCanvas;
		this.file = file;
		this.csv = csv;
		this.points = points;
	}

	public CsvFile getCsv() {
		return csv;
	}

	public File getFile() {
		return file;
	}

	/** Repoints the layer at a new file (Save As) and renames it to match. */
	public void setFile(File file) {
		this.file = file;
		setName(file.getName());
	}

	public boolean isDirty() {
		return dirty;
	}

	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	public String getNorthColumn() {
		return northColumn;
	}

	public void setNorthColumn(String northColumn) {
		this.northColumn = northColumn;
	}

	public String getEastColumn() {
		return eastColumn;
	}

	public void setEastColumn(String eastColumn) {
		this.eastColumn = eastColumn;
	}

	public String getLabelColumn() {
		return labelColumn;
	}

	public void setLabelColumn(String labelColumn) {
		this.labelColumn = labelColumn;
	}

	/** True when both coordinate columns resolve to existing header columns. */
	public boolean isMapped() {
		return columnIndex(northColumn) >= 0 && columnIndex(eastColumn) >= 0;
	}

	/** Rows the last {@link #rebuildPoints()} skipped. Read on the EDT only. */
	public Set<Integer> getBadRows() {
		return badRows;
	}

	public int getPointCount() {
		return points.get().size();
	}

	/** The CSV editor currently showing this layer, or null. */
	public Window getOpenEditor() {
		return openEditor;
	}

	public void setOpenEditor(Window openEditor) {
		this.openEditor = openEditor;
	}

	/** Picks coordinate columns by common names, and the first other column as label. */
	public void guessMapping() {
		List<String> header = csv.getHeader();
		for (String column : header) {
			if (NORTH_NAMES.contains(column.trim().toLowerCase())) {
				northColumn = column;
				break;
			}
		}
		for (String column : header) {
			if (EAST_NAMES.contains(column.trim().toLowerCase())) {
				eastColumn = column;
				break;
			}
		}
		for (String column : header) {
			if (!column.equals(northColumn) && !column.equals(eastColumn)) {
				labelColumn = column;
				break;
			}
		}
	}

	/**
	 * Re-derives the map points from the CSV rows and current mapping, records
	 * the rows that don't parse in {@link #getBadRows()}, and repaints. Call
	 * after any edit to the CSV data, the mapping or the CRS.
	 */
	public void rebuildPoints() {
		int northIdx = columnIndex(northColumn);
		int eastIdx = columnIndex(eastColumn);
		int labelIdx = columnIndex(labelColumn);
		CoordSystem crs = getCRS();

		badRows.clear();
		List<LabeledPoint> rebuilt = new ArrayList<>();
		if (northIdx >= 0 && eastIdx >= 0) {
			List<List<String>> rows = csv.getRows();
			for (int i = 0; i < rows.size(); i++) {
				List<String> row = rows.get(i);
				Double north = parseCoordinate(row.get(northIdx));
				Double east = parseCoordinate(row.get(eastIdx));
				if (north == null || east == null || !crs.isValid(north, east)) {
					badRows.add(i);
					continue;
				}
				String label = labelIdx >= 0 ? row.get(labelIdx) : "";
				rebuilt.add(new LabeledPoint(new Coordinate(north, east), label, 0));
			}
		}
		points.set(List.copyOf(rebuilt));
		invalidateCache();
		mapCanvas.repaint();
	}

	public void save() throws IOException {
		csv.write(file.toPath());
		dirty = false;
	}

	private int columnIndex(String name) {
		return name == null ? -1 : csv.getHeader().indexOf(name);
	}

	/** Accepts European comma decimals and embedded spaces; null when unparseable. */
	private static Double parseCoordinate(String s) {
		if (s == null || s.isBlank()) return null;
		try {
			return Double.parseDouble(s.trim().replace(" ", "").replace(',', '.'));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
