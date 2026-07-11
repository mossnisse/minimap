package gis.ui;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.filechooser.FileNameExtensionFilter;

import gis.coords.CoordSystem;
import gis.coords.Coordinate;
import gis.csv.CsvFile;
import gis.csv.CsvTableModel;
import gis.core.MapCanvas;
import gis.layers.PointTableLayer;

/**
 * Non-modal CSV table editor. The user picks which columns hold the northing
 * and easting (plus an optional label column) and which CRS the values are
 * in; the rows then show as a {@link PointTableLayer} that follows every
 * table edit. Rows whose coordinates don't parse are tinted and skipped.
 * The layer stays on the map when the editor closes.
 */
public class CsvEditorDialog extends JDialog {
	@Serial
	private static final long serialVersionUID = 1L;

	private static final String UNSELECTED = "(select)";
	private static final String NO_LABEL = "(none)";
	private static final List<String> NORTH_NAMES = List.of("north", "northing", "n", "lat", "latitude", "y", "nord");
	private static final List<String> EAST_NAMES = List.of("east", "easting", "e", "lon", "lng", "longitude", "x", "ost", "öst");
	private static final Color BAD_ROW_COLOR = new Color(255, 220, 220);

	private final MapCanvas mapCanvas;
	private final CsvTableModel model;
	private final JTable table;
	private final JComboBox<String> northCombo = new JComboBox<>();
	private final JComboBox<String> eastCombo = new JComboBox<>();
	private final JComboBox<String> labelCombo = new JComboBox<>();
	private final JComboBox<CoordSystem> crsCombo;
	private final JLabel statusLabel = new JLabel(" ");

	private PointTableLayer layer;
	// The layer's PointSource reads this snapshot off the EDT. It is an
	// AtomicReference (not a field read from a lambda) so the lambda captures
	// only the reference: a closed editor can then be garbage collected even
	// though its layer stays on the map.
	private final AtomicReference<List<PointTableLayer.LabeledPoint>> points =
			new AtomicReference<>(List.of());
	private final Set<Integer> badRows = new HashSet<>();
	private File file;
	private boolean dirty = false;
	private boolean updatingCombos = false;
	// Set only while a column rename is in flight, so fillCombos can carry the
	// combo selections over to the new name instead of dropping the mapping
	private String renamedFrom, renamedTo;

	public CsvEditorDialog(Frame owner, MapCanvas mapCanvas, File file, CsvFile csv) {
		super(owner, file.getName(), false);
		this.mapCanvas = mapCanvas;
		this.file = file;
		this.model = new CsvTableModel(csv);

		table = new JTable(model) {
			@Override
			public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
				Component c = super.prepareRenderer(renderer, row, column);
				if (!isRowSelected(row)) {
					c.setBackground(badRows.contains(row) ? BAD_ROW_COLOR : getBackground());
				}
				return c;
			}
		};
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

		crsCombo = new JComboBox<>(CoordSystem.values());
		crsCombo.setSelectedItem(mapCanvas.getCRS());

		JPanel mappingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		mappingPanel.add(new JLabel("North:"));
		mappingPanel.add(northCombo);
		mappingPanel.add(new JLabel("East:"));
		mappingPanel.add(eastCombo);
		mappingPanel.add(new JLabel("Label:"));
		mappingPanel.add(labelCombo);
		mappingPanel.add(new JLabel("CRS:"));
		mappingPanel.add(crsCombo);
		add(mappingPanel, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setPreferredSize(new Dimension(700, 400));
		add(scrollPane, BorderLayout.CENTER);

		JPanel bottomPanel = new JPanel(new BorderLayout());
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		addButton(buttonPanel, "Add Row", this::addRow);
		addButton(buttonPanel, "Delete Row(s)", this::deleteRows);
		addButton(buttonPanel, "Add Column", this::addColumn);
		addButton(buttonPanel, "Rename Column", this::renameColumn);
		addButton(buttonPanel, "Delete Column", this::deleteColumn);
		addButton(buttonPanel, "Save", this::save);
		addButton(buttonPanel, "Save As", this::saveAs);
		addButton(buttonPanel, "Close", this::closeRequested);
		bottomPanel.add(buttonPanel, BorderLayout.CENTER);
		statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
		bottomPanel.add(statusLabel, BorderLayout.SOUTH);
		add(bottomPanel, BorderLayout.SOUTH);

		fillCombos();
		guessMapping();

		model.addTableModelListener(e -> {
			dirty = true;
			updateTitle();
			if (e.getFirstRow() == TableModelEvent.HEADER_ROW) {
				fillCombos(); // structure changed: recreate combo items, keep selection by name
				rebuildPoints();
			} else if (affectsMapping(e.getColumn())) {
				rebuildPoints();
			}
		});
		northCombo.addActionListener(e -> mappingChanged());
		eastCombo.addActionListener(e -> mappingChanged());
		labelCombo.addActionListener(e -> mappingChanged());
		crsCombo.addActionListener(e -> crsChanged());

		// If the layer is removed in the Layer Manager, forget it so the next
		// edit or mapping change adds a fresh one instead of updating a ghost.
		final Runnable layersChangedListener = () -> {
			if (layer != null && !mapCanvas.getLayerManager().getLayers().contains(layer)) {
				layer = null;
			}
		};
		mapCanvas.getLayerManager().addLayersChangedListener(layersChangedListener);

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				closeRequested();
			}

			@Override
			public void windowClosed(WindowEvent e) {
				mapCanvas.getLayerManager().removeLayersChangedListener(layersChangedListener);
			}
		});

		rebuildPoints();
		pack();
		setLocationRelativeTo(owner);
		setVisible(true);
	}

	private void addButton(JPanel panel, String label, Runnable action) {
		JButton button = new JButton(label);
		button.addActionListener(e -> action.run());
		panel.add(button);
	}

	// ---- column mapping ----

	private void fillCombos() {
		updatingCombos = true;
		try {
			String north = (String) northCombo.getSelectedItem();
			String east = (String) eastCombo.getSelectedItem();
			String label = (String) labelCombo.getSelectedItem();

			northCombo.removeAllItems();
			eastCombo.removeAllItems();
			labelCombo.removeAllItems();
			northCombo.addItem(UNSELECTED);
			eastCombo.addItem(UNSELECTED);
			labelCombo.addItem(NO_LABEL);
			for (String column : model.getCsv().getHeader()) {
				northCombo.addItem(column);
				eastCombo.addItem(column);
				labelCombo.addItem(column);
			}
			// Restore selection by column name (following a rename); falls back
			// to item 0 if the column is gone
			if (north != null) northCombo.setSelectedItem(afterRename(north));
			if (east != null) eastCombo.setSelectedItem(afterRename(east));
			if (label != null) labelCombo.setSelectedItem(afterRename(label));
		} finally {
			updatingCombos = false;
		}
	}

	private void guessMapping() {
		updatingCombos = true;
		try {
			List<String> header = model.getCsv().getHeader();
			for (String column : header) {
				if (NORTH_NAMES.contains(column.trim().toLowerCase())) {
					northCombo.setSelectedItem(column);
					break;
				}
			}
			for (String column : header) {
				if (EAST_NAMES.contains(column.trim().toLowerCase())) {
					eastCombo.setSelectedItem(column);
					break;
				}
			}
			// Label: first column that is neither coordinate
			for (String column : header) {
				if (!column.equals(northCombo.getSelectedItem()) && !column.equals(eastCombo.getSelectedItem())) {
					labelCombo.setSelectedItem(column);
					break;
				}
			}
		} finally {
			updatingCombos = false;
		}
	}

	/** Maps a saved combo selection through an in-progress column rename. */
	private String afterRename(String name) {
		return name.equals(renamedFrom) ? renamedTo : name;
	}

	private int selectedColumn(JComboBox<String> combo) {
		// Item 0 is "(select)" / "(none)"; the header columns follow in order
		int index = combo.getSelectedIndex();
		return index <= 0 ? -1 : index - 1;
	}

	/** True when a change to this model column (or ALL_COLUMNS) can move points. */
	private boolean affectsMapping(int column) {
		return column == TableModelEvent.ALL_COLUMNS
				|| column == selectedColumn(northCombo)
				|| column == selectedColumn(eastCombo)
				|| column == selectedColumn(labelCombo);
	}

	private void mappingChanged() {
		if (updatingCombos) return;
		rebuildPoints();
	}

	private void crsChanged() {
		if (updatingCombos) return;
		CoordSystem crs = (CoordSystem) crsCombo.getSelectedItem();
		if (layer != null && crs != null) {
			layer.setCRS(crs); // rebuildPoints below revalidates rows and refetches
		}
		rebuildPoints();
	}

	// ---- table model -> map layer ----

	private void rebuildPoints() {
		int northColumn = selectedColumn(northCombo);
		int eastColumn = selectedColumn(eastCombo);
		int labelColumn = selectedColumn(labelCombo);
		CoordSystem crs = (CoordSystem) crsCombo.getSelectedItem();
		boolean mapped = northColumn >= 0 && eastColumn >= 0 && crs != null;

		badRows.clear();
		List<PointTableLayer.LabeledPoint> rebuilt = new ArrayList<>();
		if (mapped) {
			List<List<String>> rows = model.getCsv().getRows();
			for (int i = 0; i < rows.size(); i++) {
				List<String> row = rows.get(i);
				Double north = parseCoordinate(row.get(northColumn));
				Double east = parseCoordinate(row.get(eastColumn));
				if (north == null || east == null || !crs.isValid(north, east)) {
					badRows.add(i);
					continue;
				}
				String label = labelColumn >= 0 ? row.get(labelColumn) : "";
				rebuilt.add(new PointTableLayer.LabeledPoint(new Coordinate(north, east), label, 0));
			}
		}
		points.set(List.copyOf(rebuilt));
		table.repaint();

		if (mapped) {
			statusLabel.setText(rebuilt.size() + " points on map"
					+ (badRows.isEmpty() ? "" : ", " + badRows.size() + " rows skipped (bad coordinates)"));
		} else {
			statusLabel.setText("Select the northing and easting columns to show the points on the map");
		}

		if (layer != null) {
			layer.invalidateCache();
			mapCanvas.repaint();
		} else if (mapped) {
			// Local so the lambda captures the reference only, not the dialog
			AtomicReference<List<PointTableLayer.LabeledPoint>> ref = points;
			layer = new PointTableLayer(file.getName(), crs, mapCanvas, bounds -> ref.get(), 1.0, false);
			layer.setColor(Color.MAGENTA);
			mapCanvas.getLayerManager().addLayerTop(layer);
		}
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

	// ---- row/column editing ----

	private void stopEditing() {
		if (table.isEditing()) {
			table.getCellEditor().stopCellEditing();
		}
	}

	private void addRow() {
		stopEditing();
		model.addRow();
	}

	private void deleteRows() {
		stopEditing();
		int[] selected = table.getSelectedRows();
		if (selected.length == 0) return;
		int[] modelRows = new int[selected.length];
		for (int i = 0; i < selected.length; i++) {
			modelRows[i] = table.convertRowIndexToModel(selected[i]);
		}
		model.deleteRows(modelRows);
	}

	private void addColumn() {
		stopEditing();
		String name = JOptionPane.showInputDialog(this, "Column name:", "Add Column", JOptionPane.QUESTION_MESSAGE);
		if (name != null && !name.isBlank()) {
			model.addColumn(name.trim());
		}
	}

	private void renameColumn() {
		stopEditing();
		int column = table.getSelectedColumn();
		if (column < 0) {
			JOptionPane.showMessageDialog(this, "Select a cell in the column to rename first.");
			return;
		}
		int modelColumn = table.convertColumnIndexToModel(column);
		String name = (String) JOptionPane.showInputDialog(this, "New name:", "Rename Column",
				JOptionPane.QUESTION_MESSAGE, null, null, model.getColumnName(modelColumn));
		if (name != null && !name.isBlank()) {
			renameColumn(modelColumn, name.trim());
		}
	}

	private void renameColumn(int modelColumn, String newName) {
		// Publish the rename so the model listener's fillCombos can carry a
		// north/east/label selection over to the new name
		renamedFrom = model.getColumnName(modelColumn);
		renamedTo = newName;
		try {
			model.renameColumn(modelColumn, newName);
		} finally {
			renamedFrom = null;
			renamedTo = null;
		}
	}

	private void deleteColumn() {
		stopEditing();
		int column = table.getSelectedColumn();
		if (column < 0) {
			JOptionPane.showMessageDialog(this, "Select a cell in the column to delete first.");
			return;
		}
		int modelColumn = table.convertColumnIndexToModel(column);
		int confirm = JOptionPane.showConfirmDialog(this,
				"Delete column: " + model.getColumnName(modelColumn) + "?",
				"Delete Column", JOptionPane.YES_NO_OPTION);
		if (confirm == JOptionPane.YES_OPTION) {
			model.deleteColumn(modelColumn);
		}
	}

	// ---- save / close ----

	private void updateTitle() {
		setTitle((dirty ? "*" : "") + file.getName());
	}

	/** @return true when the file was written */
	private boolean save() {
		stopEditing();
		try {
			model.getCsv().write(file.toPath());
			dirty = false;
			updateTitle();
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Can't save: " + file.getPath() + "\n" + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}

	private void saveAs() {
		stopEditing();
		final JFileChooser fc = new JFileChooser(file.getParentFile());
		fc.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
		fc.setSelectedFile(file);
		if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

		File selected = fc.getSelectedFile();
		if (!selected.getName().contains(".")) {
			selected = new File(selected.getParentFile(), selected.getName() + ".csv");
		}
		if (selected.exists() && !selected.equals(file)) {
			int confirm = JOptionPane.showConfirmDialog(this,
					selected.getName() + " already exists. Overwrite?",
					"Save As", JOptionPane.YES_NO_OPTION);
			if (confirm != JOptionPane.YES_OPTION) return;
		}
		file = selected;
		if (save() && layer != null) {
			layer.setName(file.getName());
			mapCanvas.getLayerManager().notifyListeners();
		}
	}

	private void closeRequested() {
		stopEditing();
		if (dirty) {
			int choice = JOptionPane.showConfirmDialog(this, "Save changes to " + file.getName() + "?",
					"Close", JOptionPane.YES_NO_CANCEL_OPTION);
			if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) return;
			if (choice == JOptionPane.YES_OPTION && !save()) return;
		}
		dispose(); // the layer deliberately stays on the map
	}
}
