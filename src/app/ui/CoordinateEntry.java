package app.ui;

import gis.coords.CoordSystem;
import gis.coords.Coordinate;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Component;
import java.util.Locale;

/**
 * Coordinate entry for forms: the position is typed in whichever system it was
 * written down in (WGS84, SWEREF99 TM or RT90) and always read back as WGS84.
 * Switching system converts what is already in the fields instead of clearing
 * them, so a SWEREF99 TM note can be checked against its WGS84 equivalent.
 *
 * <p>The rows are handed out through {@link #formRows()} so the caller can
 * splice them into its own label/field form.
 */
public final class CoordinateEntry {
    /** Where the position currently in the fields came from. */
    public enum Origin { LOADED, TYPED, PICKED }

    /** Systems offered for typing; Web Mercator is a display projection, not something anyone notes down. */
    private static final CoordSystem[] SYSTEMS = { CoordSystem.WGS84, CoordSystem.SWEREF99TM, CoordSystem.RT90 };

    private final JComboBox<CoordSystem> systems = new JComboBox<>(SYSTEMS);
    private final JTextField north = new JTextField(14), east = new JTextField(14);
    private final JLabel northLabel = new JLabel(), eastLabel = new JLabel(), preview = new JLabel();
    private final String systemLabel, emptyMessage;

    /** The system the fields currently hold values in; the combo can already be one step ahead. */
    private CoordSystem shownIn = CoordSystem.WGS84;
    /** Last value written into the fields, reused unchanged when nobody typed over it. */
    private Coordinate wgs84;
    private String shownNorth = "", shownEast = "";
    private Origin origin = Origin.LOADED;
    private boolean writing;

    public CoordinateEntry() { this("Coordinate system", "No coordinate entered."); }

    /**
     * @param systemLabel row label for the system chooser
     * @param emptyMessage shown while both fields are empty, to say what that means here
     */
    public CoordinateEntry(String systemLabel, String emptyMessage) {
        this.systemLabel = systemLabel; this.emptyMessage = emptyMessage;
        systems.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
                return super.getListCellRendererComponent(list, ((CoordSystem)value).getLabel(), index, selected, focused);
            }
        });
        systems.addActionListener(e -> systemChanged());
        DocumentListener typed = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
            private void changed() { if (!writing) { origin = Origin.TYPED; updatePreview(); } }
        };
        north.getDocument().addDocumentListener(typed);
        east.getDocument().addDocumentListener(typed);
        relabel();
        updatePreview();
    }

    /** Label/field pairs to splice into a form, in display order. */
    public Object[] formRows() {
        return new Object[]{ systemLabel, systems, northLabel, north, eastLabel, east, "", preview };
    }

    /** Shows a stored WGS84 position, or clears the fields when either part is missing. */
    public void setWGS84(Double latitude, Double longitude) {
        show(latitude == null || longitude == null ? null : new Coordinate(latitude, longitude), Origin.LOADED);
    }

    /** Fills in a position the user picked on the map. */
    public void pickWGS84(double latitude, double longitude) {
        show(new Coordinate(latitude, longitude), Origin.PICKED);
    }

    /**
     * The entered position as WGS84, or null when both fields are empty.
     *
     * @throws IllegalArgumentException with a message meant for the user when the
     *         input is incomplete, unreadable or outside the selected system
     */
    public Coordinate valueWGS84() {
        Double n = number(north.getText()), e = number(east.getText());
        if (n == null && e == null) {
            if (north.getText().isBlank() && east.getText().isBlank()) return null;
            throw new IllegalArgumentException("Could not read the coordinate as numbers.");
        }
        if (n == null || e == null) throw new IllegalArgumentException("Enter both " + northName(shownIn).toLowerCase(Locale.ROOT) + " and " + eastName(shownIn).toLowerCase(Locale.ROOT) + ".");
        // Untouched fields keep the exact stored value instead of round-tripping through a projection.
        if (wgs84 != null && north.getText().equals(shownNorth) && east.getText().equals(shownEast)) return wgs84;
        Coordinate typed = new Coordinate(n, e);
        if (!shownIn.isValid(typed)) throw new IllegalArgumentException(outOfRange(typed));
        return shownIn.toWGS84(typed);
    }

    /** Where the shown position came from; converting between systems does not change it. */
    public Origin origin() { return origin; }

    private void systemChanged() {
        CoordSystem next = (CoordSystem)systems.getSelectedItem();
        if (next == null || next == shownIn) return;
        Coordinate value;
        try { value = valueWGS84(); }
        catch (IllegalArgumentException e) { shownIn = next; relabel(); updatePreview(); return; }
        shownIn = next;
        relabel();
        show(value, origin);
    }

    private void show(Coordinate wgs, Origin newOrigin) {
        writing = true;
        try {
            wgs84 = wgs;
            Coordinate shown = wgs == null ? null : shownIn.toProjected(wgs);
            shownNorth = shown == null ? "" : format(shown.getNorth());
            shownEast = shown == null ? "" : format(shown.getEast());
            north.setText(shownNorth);
            east.setText(shownEast);
        } finally { writing = false; }
        origin = newOrigin;
        updatePreview();
    }

    private void updatePreview() {
        try {
            Coordinate wgs = valueWGS84();
            preview.setForeground(UIManager.getColor("Label.foreground"));
            preview.setText(wgs == null ? emptyMessage
                    : String.format(Locale.US, "Stored as WGS84 %.6f, %.6f", wgs.getNorth(), wgs.getEast()));
        } catch (IllegalArgumentException e) {
            preview.setForeground(Color.RED.darker());
            preview.setText(e.getMessage());
        }
    }

    private void relabel() {
        northLabel.setText(northName(shownIn) + " (" + shownIn.getLabel() + ")");
        eastLabel.setText(eastName(shownIn) + " (" + shownIn.getLabel() + ")");
    }

    /** Names the system the numbers would fit, which is nearly always the real mistake. */
    private String outOfRange(Coordinate typed) {
        for (CoordSystem other : SYSTEMS) {
            if (other != shownIn && other.isValid(typed)) return "Outside " + shownIn.getLabel() + " - those numbers look like " + other.getLabel() + ".";
        }
        return "Outside the " + shownIn.getLabel() + " range; check the order of " + northName(shownIn).toLowerCase(Locale.ROOT) + " and " + eastName(shownIn).toLowerCase(Locale.ROOT) + ".";
    }

    private static String northName(CoordSystem cs) { return cs == CoordSystem.WGS84 ? "Latitude" : "Northing"; }
    private static String eastName(CoordSystem cs) { return cs == CoordSystem.WGS84 ? "Longitude" : "Easting"; }

    private String format(double value) {
        return String.format(Locale.US, shownIn == CoordSystem.WGS84 ? "%.6f" : "%.0f", value);
    }

    /** Accepts Swedish decimal commas and grouped digits, as written in field notes. */
    private static Double number(String text) {
        if (text == null) return null;
        String cleaned = text.replaceAll("[\\s\\u00A0]", "").replace(',', '.');
        if (cleaned.isEmpty()) return null;
        try { return Double.valueOf(cleaned); } catch (NumberFormatException e) { return null; }
    }
}
