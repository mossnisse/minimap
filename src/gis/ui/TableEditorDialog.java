package gis.ui;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.Serial;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;

import gis.core.Layer;
import gis.core.MapCanvas;
import gis.layers.EditableTableLayer;

/**
 * Non-modal attribute table editor for any {@link EditableTableLayer}
 * (shapefile and GeoPackage layers). All layers get an editable attribute
 * table; point layers additionally get editable X/Y columns, and layers that
 * allow it get row add/delete. Shapes other than single points are not
 * editable.
 *
 * Like the CSV editor, the data and dirty state live on the layer, so the
 * dialog can be closed and reopened from the Layer Manager without losing
 * unsaved edits.
 */
public class TableEditorDialog extends JDialog {
	@Serial
	private static final long serialVersionUID = 1L;

	private final MapCanvas mapCanvas;
	private final Layer layer;
	private final EditableTableLayer data;
	private final EditorTableModel model;
	private final JTable table;
	private final JLabel statusLabel = new JLabel(" ");

	/** Opens the editor for the layer, or brings an already open one to front. */
	public static <L extends Layer & EditableTableLayer> void open(Frame owner, MapCanvas mapCanvas, L layer) {
		Window editor = layer.getOpenEditor();
		if (editor != null) {
			editor.toFront();
			editor.requestFocus();
			return;
		}
		new TableEditorDialog(owner, mapCanvas, layer);
	}

	private <L extends Layer & EditableTableLayer> TableEditorDialog(Frame owner, MapCanvas mapCanvas, L layer) {
		super(owner, layer.getName(), false);
		this.mapCanvas = mapCanvas;
		this.layer = layer;
		this.data = layer;
		this.model = new EditorTableModel();
		data.setOpenEditor(this);

		table = new JTable(model);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setPreferredSize(new Dimension(700, 400));
		add(scrollPane, BorderLayout.CENTER);

		JPanel bottomPanel = new JPanel(new BorderLayout());
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		if (data.canAddDeleteRows()) {
			addButton(buttonPanel, "Add Row", this::addRow);
			addButton(buttonPanel, "Delete Row(s)", this::deleteRows);
		}
		addButton(buttonPanel, "Add Column", this::addColumn);
		addButton(buttonPanel, "Rename Column", this::renameColumn);
		addButton(buttonPanel, "Delete Column", this::deleteColumn);
		addButton(buttonPanel, "Save", this::save);
		addButton(buttonPanel, "Close", this::closeRequested);
		bottomPanel.add(buttonPanel, BorderLayout.CENTER);
		statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
		bottomPanel.add(statusLabel, BorderLayout.SOUTH);
		add(bottomPanel, BorderLayout.SOUTH);

		model.addTableModelListener(e -> {
			updateTitle();
			updateStatus();
		});

		// If the layer is removed in the Layer Manager, the editor has nothing
		// left to edit: offer to save unsaved changes, then close.
		final Runnable layersChangedListener = () -> {
			// The editor may already be disposed (e.g. by a project switch that
			// resolved unsaved work) when this fires; never prompt from a dead
			// dialog — answering Yes would re-save edits the user discarded.
			if (!isDisplayable()) return;
			if (!mapCanvas.getLayerManager().getLayers().contains(layer)) {
				if (data.isDirty()) {
					int choice = JOptionPane.showConfirmDialog(this,
							"The layer was removed from the map. Save changes to "
									+ data.getSourceName() + "?",
							"Layer removed", JOptionPane.YES_NO_OPTION);
					if (choice == JOptionPane.CLOSED_OPTION) return;
					if (choice == JOptionPane.YES_OPTION && !save()) return;
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
				data.setOpenEditor(null);
			}
		});

		updateTitle();
		updateStatus();
		pack();
		setLocationRelativeTo(owner);
		setVisible(true);
	}

	private void addButton(JPanel panel, String label, Runnable action) {
		JButton button = new JButton(label);
		button.addActionListener(e -> action.run());
		panel.add(button);
	}

	private void updateTitle() {
		setTitle((data.isDirty() ? "*" : "") + layer.getName());
	}

	private void updateStatus() {
		statusLabel.setText(data.getStatusText());
	}

	// ---- row/column editing ----

	private void stopEditing() {
		if (table.isEditing()) {
			table.getCellEditor().stopCellEditing();
		}
	}

	private void addRow() {
		stopEditing();
		data.addRow();
		int index = data.getFeatureCount() - 1;
		model.fireTableRowsInserted(index, index);
	}

	private void deleteRows() {
		stopEditing();
		int[] selected = table.getSelectedRows();
		if (selected.length == 0) return;
		int[] modelRows = new int[selected.length];
		for (int i = 0; i < selected.length; i++) {
			modelRows[i] = table.convertRowIndexToModel(selected[i]);
		}
		data.deleteRows(modelRows);
		model.fireTableDataChanged();
	}

	/** @return a validated, non-duplicate column name, or null when cancelled/invalid. */
	private String askColumnName(String title, String initial) {
		String name = (String) JOptionPane.showInputDialog(this, "Column name:",
				title, JOptionPane.QUESTION_MESSAGE, null, null, initial);
		if (name == null || name.isBlank()) return null;
		name = name.trim();
		String problem = data.validateNewColumnName(name);
		if (problem == null && !name.equals(initial)) {
			for (int i = 0; i < data.getColumnCount(); i++) {
				if (data.getColumnName(i).equalsIgnoreCase(name)) {
					problem = "There is already a column named " + data.getColumnName(i) + ".";
					break;
				}
			}
		}
		if (problem != null) {
			JOptionPane.showMessageDialog(this, problem, "Invalid name", JOptionPane.WARNING_MESSAGE);
			return null;
		}
		return name;
	}

	private void addColumn() {
		stopEditing();
		String name = askColumnName("Add Column", null);
		if (name != null) {
			data.addColumn(name);
			model.fireTableStructureChanged();
		}
	}

	/** @return the selected attribute column (index into the layer's columns), or -1. */
	private int selectedAttributeColumn(String action) {
		int column = table.getSelectedColumn();
		if (column < 0) {
			JOptionPane.showMessageDialog(this, "Select a cell in the column to " + action + " first.");
			return -1;
		}
		int modelColumn = table.convertColumnIndexToModel(column);
		int attributeColumn = modelColumn - model.geometryColumns();
		if (attributeColumn < 0) {
			JOptionPane.showMessageDialog(this, "The X/Y columns are the point geometry, not attributes.");
			return -1;
		}
		return attributeColumn;
	}

	private void renameColumn() {
		stopEditing();
		int column = selectedAttributeColumn("rename");
		if (column < 0) return;
		String blocked = data.validateColumnRename(column);
		if (blocked != null) {
			JOptionPane.showMessageDialog(this, blocked);
			return;
		}
		String name = askColumnName("Rename Column", data.getColumnName(column));
		if (name != null) {
			data.renameColumn(column, name);
			model.fireTableStructureChanged();
		}
	}

	private void deleteColumn() {
		stopEditing();
		int column = selectedAttributeColumn("delete");
		if (column < 0) return;
		String blocked = data.validateColumnDelete(column);
		if (blocked != null) {
			JOptionPane.showMessageDialog(this, blocked);
			return;
		}
		int confirm = JOptionPane.showConfirmDialog(this,
				"Delete column: " + data.getColumnName(column) + "?",
				"Delete Column", JOptionPane.YES_NO_OPTION);
		if (confirm == JOptionPane.YES_OPTION) {
			data.deleteColumn(column);
			model.fireTableStructureChanged();
		}
	}

	// ---- save / close ----

	/** @return true when the changes were written */
	private boolean save() {
		stopEditing();
		try {
			data.save();
			updateTitle();
			model.fireTableDataChanged(); // e.g. database-assigned ids for new rows
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Can't save: " + data.getSourcePath() + "\n" + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}

	private void closeRequested() {
		stopEditing();
		if (data.isDirty()) {
			int choice = JOptionPane.showConfirmDialog(this,
					"Save changes to " + data.getSourceName() + "?\n"
							+ "(No keeps the edits on the map layer; reopen it from the Layer Manager to save later.)",
					"Close", JOptionPane.YES_NO_CANCEL_OPTION);
			if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) return;
			if (choice == JOptionPane.YES_OPTION && !save()) return;
		}
		dispose(); // the layer deliberately stays on the map
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

	/**
	 * Table over the layer's attribute columns, with two leading X/Y geometry
	 * columns for point-editable layers.
	 */
	private class EditorTableModel extends AbstractTableModel {

		int geometryColumns() {
			return data.isPointEditable() ? 2 : 0;
		}

		@Override
		public int getRowCount() {
			return data.getFeatureCount();
		}

		@Override
		public int getColumnCount() {
			return geometryColumns() + data.getColumnCount();
		}

		@Override
		public String getColumnName(int column) {
			if (geometryColumns() > 0) {
				if (column == 0) return "X (east)";
				if (column == 1) return "Y (north)";
			}
			return data.getColumnName(column - geometryColumns());
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return String.class;
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			if (geometryColumns() > 0 && columnIndex < 2) {
				return true;
			}
			return data.isCellEditable(rowIndex, columnIndex - geometryColumns());
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			if (geometryColumns() > 0 && columnIndex < 2) {
				Double v = (columnIndex == 0) ? data.getPointX(rowIndex) : data.getPointY(rowIndex);
				return v == null ? "" : trimmedDouble(v);
			}
			return data.getAttribute(rowIndex, columnIndex - geometryColumns());
		}

		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			String text = (aValue == null) ? "" : aValue.toString();
			if (geometryColumns() > 0 && columnIndex < 2) {
				Double parsed = parseCoordinate(text);
				if (parsed == null && !text.isBlank()) {
					Toolkit.getDefaultToolkit().beep(); // not a number: keep the old value
					return;
				}
				if (parsed == null) {
					// Coordinate cleared: the row keeps its attributes but loses its point
					data.setPoint(rowIndex, null, null);
					fireTableRowsUpdated(rowIndex, rowIndex);
					return;
				}
				Double x = (columnIndex == 0) ? parsed : data.getPointX(rowIndex);
				Double y = (columnIndex == 1) ? parsed : data.getPointY(rowIndex);
				// First coordinate of a fresh row: park the other ordinate at 0
				// so the value is kept; the point lands right once both are set
				data.setPoint(rowIndex, x == null ? 0 : x, y == null ? 0 : y);
				fireTableRowsUpdated(rowIndex, rowIndex);
				return;
			}
			data.setAttribute(rowIndex, columnIndex - geometryColumns(), text);
			fireTableCellUpdated(rowIndex, columnIndex);
		}

		/** 59.0 shows as "59", 59.25 as "59.25". */
		private String trimmedDouble(double v) {
			return (v == Math.rint(v) && !Double.isInfinite(v))
					? String.valueOf((long) v) : String.valueOf(v);
		}
	}
}
