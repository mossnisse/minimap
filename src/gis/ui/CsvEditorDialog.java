package gis.ui;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.Serial;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.filechooser.FileNameExtensionFilter;

import gis.coords.CoordSystem;
import gis.csv.CsvTableModel;
import gis.core.MapCanvas;
import gis.layers.CsvPointLayer;

/**
 * Non-modal table editor for a {@link CsvPointLayer}. The user picks which
 * columns hold the northing and easting (plus an optional label column) and
 * which CRS the values are in; the layer's points follow every table edit.
 * Rows whose coordinates don't parse are tinted and skipped.
 *
 * The CSV data, mapping and dirty state live on the layer, so the editor can
 * be closed and reopened (via the Layer Manager) without losing edits; the
 * layer keeps showing them until it is removed from the map.
 */
public class CsvEditorDialog extends JDialog {
	@Serial
	private static final long serialVersionUID = 1L;

	private static final String UNSELECTED = "(select)";
	private static final String NO_LABEL = "(none)";
	private static final Color BAD_ROW_COLOR = new Color(255, 220, 220);

	private final MapCanvas mapCanvas;
	private final CsvPointLayer layer;
	private final CsvTableModel model;
	private final JTable table;
	private final JComboBox<String> northCombo = new JComboBox<>();
	private final JComboBox<String> eastCombo = new JComboBox<>();
	private final JComboBox<String> labelCombo = new JComboBox<>();
	private final JComboBox<CoordSystem> crsCombo;
	private final JLabel statusLabel = new JLabel(" ");

	private boolean updatingCombos = false;
	// Set only while a column rename is in flight, so fillCombos can carry the
	// combo selections over to the new name instead of dropping the mapping
	private String renamedFrom, renamedTo;

	/** Opens the editor for the layer, or brings an already open one to front. */
	public static void open(Frame owner, MapCanvas mapCanvas, CsvPointLayer layer) {
		Window editor = layer.getOpenEditor();
		if (editor != null) {
			editor.toFront();
			editor.requestFocus();
			return;
		}
		new CsvEditorDialog(owner, mapCanvas, layer);
	}

	private CsvEditorDialog(Frame owner, MapCanvas mapCanvas, CsvPointLayer layer) {
		super(owner, layer.getFile().getName(), false);
		this.mapCanvas = mapCanvas;
		this.layer = layer;
		this.model = new CsvTableModel(layer.getCsv());
		layer.setOpenEditor(this);

		table = new JTable(model) {
			@Override
			public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
				Component c = super.prepareRenderer(renderer, row, column);
				if (!isRowSelected(row)) {
					c.setBackground(layer.getBadRows().contains(row) ? BAD_ROW_COLOR : getBackground());
				}
				return c;
			}
		};
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

		crsCombo = new JComboBox<>(CoordSystem.values());
		crsCombo.setSelectedItem(layer.getCRS());

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
		selectFromLayerMapping();

		model.addTableModelListener(e -> {
			layer.setDirty(true);
			updateTitle();
			if (e.getFirstRow() == TableModelEvent.HEADER_ROW) {
				fillCombos(); // structure changed: recreate combo items, keep selection by name
				refresh();
			} else if (affectsMapping(e.getColumn())) {
				refresh();
			}
		});
		northCombo.addActionListener(e -> mappingChanged());
		eastCombo.addActionListener(e -> mappingChanged());
		labelCombo.addActionListener(e -> mappingChanged());
		crsCombo.addActionListener(e -> crsChanged());

		// If the layer is removed in the Layer Manager, the editor has nothing
		// left to edit: offer to save unsaved changes, then close.
		final Runnable layersChangedListener = () -> {
			if (!mapCanvas.getLayerManager().getLayers().contains(layer)) {
				if (layer.isDirty()) {
					int choice = JOptionPane.showConfirmDialog(this,
							"The layer was removed from the map. Save changes to "
									+ layer.getFile().getName() + "?",
							"Layer removed", JOptionPane.YES_NO_OPTION);
					if (choice == JOptionPane.YES_OPTION) save();
				}
				dispose();
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
				layer.setOpenEditor(null);
			}
		});

		refresh();
		updateTitle();
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

	/** Sets the combos to the layer's stored mapping (kept from a previous editor). */
	private void selectFromLayerMapping() {
		updatingCombos = true;
		try {
			if (layer.getNorthColumn() != null) northCombo.setSelectedItem(layer.getNorthColumn());
			if (layer.getEastColumn() != null) eastCombo.setSelectedItem(layer.getEastColumn());
			if (layer.getLabelColumn() != null) labelCombo.setSelectedItem(layer.getLabelColumn());
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

	/** The selected column name, or null for "(select)" / "(none)". */
	private String selectedName(JComboBox<String> combo) {
		return selectedColumn(combo) < 0 ? null : (String) combo.getSelectedItem();
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
		refresh();
	}

	private void crsChanged() {
		if (updatingCombos) return;
		CoordSystem crs = (CoordSystem) crsCombo.getSelectedItem();
		if (crs != null) {
			layer.setCRS(crs); // refresh below revalidates rows and refetches
		}
		refresh();
	}

	// ---- table model -> map layer ----

	/** Pushes the combo mapping to the layer, rebuilds its points and updates the UI. */
	private void refresh() {
		layer.setNorthColumn(selectedName(northCombo));
		layer.setEastColumn(selectedName(eastCombo));
		layer.setLabelColumn(selectedName(labelCombo));
		layer.rebuildPoints();
		table.repaint();

		if (layer.isMapped()) {
			int bad = layer.getBadRows().size();
			statusLabel.setText(layer.getPointCount() + " points on map"
					+ (bad == 0 ? "" : ", " + bad + " rows skipped (bad coordinates)"));
		} else {
			statusLabel.setText("Select the northing and easting columns to show the points on the map");
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
		setTitle((layer.isDirty() ? "*" : "") + layer.getFile().getName());
	}

	/** @return true when the file was written */
	private boolean save() {
		stopEditing();
		try {
			layer.save();
			updateTitle();
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Can't save: " + layer.getFile().getPath() + "\n" + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}

	private void saveAs() {
		stopEditing();
		File file = layer.getFile();
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
		layer.setFile(selected);
		if (save()) {
			mapCanvas.getLayerManager().notifyListeners(); // layer was renamed
		}
	}

	private void closeRequested() {
		stopEditing();
		if (layer.isDirty()) {
			int choice = JOptionPane.showConfirmDialog(this,
					"Save changes to " + layer.getFile().getName() + "?\n"
							+ "(No keeps the edits on the map layer; reopen it from the Layer Manager to save later.)",
					"Close", JOptionPane.YES_NO_CANCEL_OPTION);
			if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) return;
			if (choice == JOptionPane.YES_OPTION && !save()) return;
		}
		dispose(); // the layer deliberately stays on the map
	}
}
