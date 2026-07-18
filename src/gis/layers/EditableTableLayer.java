package gis.layers;

import java.awt.Window;
import java.io.IOException;

/**
 * A layer whose attribute table (and, for point data, coordinates) can be
 * edited in the generic table editor and saved back to its source. The layer
 * owns the data and the dirty state, so the editor can be closed and reopened
 * from the Layer Manager without losing unsaved edits.
 *
 * Attribute column indexes are 0-based and exclude any geometry column; the
 * editor renders its own X/Y columns in front when {@link #isPointEditable()}.
 */
public interface EditableTableLayer {

	String getName();

	/** Short source name (file, or file:table) for dialog prompts. */
	String getSourceName();

	/** Full source path for error messages. */
	String getSourcePath();

	int getFeatureCount();

	int getColumnCount();

	String getColumnName(int column);

	boolean isCellEditable(int row, int column);

	String getAttribute(int row, int column);

	void setAttribute(int row, int column, String value);

	/** True when the rows are single points whose X/Y can be edited and saved. */
	boolean isPointEditable();

	/** The row's easting/longitude in the layer CRS, or null when it has no point. */
	Double getPointX(int row);

	/** The row's northing/latitude in the layer CRS, or null when it has no point. */
	Double getPointY(int row);

	/** Moves the row's point; either coordinate null removes the geometry. */
	void setPoint(int row, Double x, Double y);

	boolean canAddDeleteRows();

	/** Appends a row with empty attributes and no geometry yet. */
	void addRow();

	/** Deletes the given rows (any order, duplicates tolerated), geometry included. */
	void deleteRows(int[] rows);

	void addColumn(String name);

	void renameColumn(int column, String name);

	void deleteColumn(int column);

	/** @return an error message, or null when the name is acceptable. */
	String validateNewColumnName(String name);

	/** @return an error message, or null when the column may be renamed. */
	String validateColumnRename(int column);

	/** @return an error message, or null when the column may be deleted. */
	String validateColumnDelete(int column);

	/** One line for the editor's status bar (feature count, type, hints). */
	String getStatusText();

	boolean isDirty();

	/** Writes all unsaved edits back to the source. */
	void save() throws IOException;

	/** The table editor currently showing this layer, or null. */
	Window getOpenEditor();

	void setOpenEditor(Window editor);
}
