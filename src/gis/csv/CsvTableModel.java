package gis.csv;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

/**
 * Mutable table model over a {@link CsvFile}. Every cell is an editable
 * string; all mutations go through this model so table and map listeners see
 * the change events.
 */
public class CsvTableModel extends AbstractTableModel {

	private final CsvFile csv;

	public CsvTableModel(CsvFile csv) {
		this.csv = csv;
	}

	public CsvFile getCsv() {
		return csv;
	}

	@Override
	public int getRowCount() {
		return csv.getRows().size();
	}

	@Override
	public int getColumnCount() {
		return csv.getHeader().size();
	}

	@Override
	public String getColumnName(int column) {
		return csv.getHeader().get(column);
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return String.class;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return true;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		return csv.getRows().get(rowIndex).get(columnIndex);
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		csv.getRows().get(rowIndex).set(columnIndex, aValue == null ? "" : aValue.toString());
		fireTableCellUpdated(rowIndex, columnIndex);
	}

	public void addRow() {
		List<String> row = new ArrayList<>();
		for (int i = 0; i < csv.getHeader().size(); i++) {
			row.add("");
		}
		csv.getRows().add(row);
		int index = csv.getRows().size() - 1;
		fireTableRowsInserted(index, index);
	}

	/** Deletes the given model rows (any order, duplicates tolerated) in one model event. */
	public void deleteRows(int[] modelRows) {
		int[] sorted = modelRows.clone();
		java.util.Arrays.sort(sorted);
		boolean removed = false;
		for (int i = sorted.length - 1; i >= 0; i--) {
			if (i < sorted.length - 1 && sorted[i] == sorted[i + 1]) continue;
			csv.getRows().remove(sorted[i]);
			removed = true;
		}
		// One event for the whole (possibly non-contiguous) delete, so listeners
		// don't do a full rebuild per row
		if (removed) fireTableDataChanged();
	}

	public void addColumn(String name) {
		csv.getHeader().add(name);
		for (List<String> row : csv.getRows()) {
			row.add("");
		}
		fireTableStructureChanged();
	}

	public void renameColumn(int column, String name) {
		csv.getHeader().set(column, name);
		fireTableStructureChanged();
	}

	public void deleteColumn(int column) {
		csv.getHeader().remove(column);
		for (List<String> row : csv.getRows()) {
			row.remove(column);
		}
		fireTableStructureChanged();
	}
}
