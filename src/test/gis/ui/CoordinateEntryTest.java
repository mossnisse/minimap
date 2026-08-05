package test.gis.ui;

import gis.ui.CoordinateEntry;
import gis.coords.CoordSystem;
import gis.coords.Coordinate;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.JTextField;

import static org.junit.jupiter.api.Assertions.*;

/** Typing a locality position in the system it was written down in. Headless: no window is shown. */
class CoordinateEntryTest {

    private final CoordinateEntry entry = new CoordinateEntry();
    // The row order is part of formRows()'s contract: system, northing, easting, preview.
    private final Object[] rows = entry.formRows();
    @SuppressWarnings("unchecked")
    private final JComboBox<CoordSystem> systems = (JComboBox<CoordSystem>) rows[1];
    private final JTextField north = (JTextField) rows[3], east = (JTextField) rows[5];

    @Test
    void showsAndReturnsWgs84Unchanged() {
        entry.setWGS84(57.712345, 11.973456);
        assertEquals("57.712345", north.getText());
        assertEquals("11.973456", east.getText());
        assertEquals(CoordinateEntry.Origin.LOADED, entry.origin());
        // An untouched value must come back bit for bit, not round-tripped through a projection.
        assertEquals(57.712345, entry.valueWGS84().getNorth());
        assertEquals(11.973456, entry.valueWGS84().getEast());
    }

    @Test
    void switchingSystemConvertsWhatIsAlreadyThere() {
        entry.setWGS84(57.712345, 11.973456);
        systems.setSelectedItem(CoordSystem.SWEREF99TM);
        Coordinate expected = CoordSystem.SWEREF99TM.toProjected(new Coordinate(57.712345, 11.973456));
        assertEquals(Math.round(expected.getNorth()), Long.parseLong(north.getText()));
        assertEquals(Math.round(expected.getEast()), Long.parseLong(east.getText()));
        assertEquals(57.712345, entry.valueWGS84().getNorth());

        systems.setSelectedItem(CoordSystem.RT90);
        assertTrue(CoordSystem.RT90.isValid(new Coordinate(Double.parseDouble(north.getText()), Double.parseDouble(east.getText()))));
        assertEquals(11.973456, entry.valueWGS84().getEast());
    }

    @Test
    void readsRt90AsWrittenOnPaper() {
        systems.setSelectedItem(CoordSystem.RT90);
        north.setText("6 398 152");
        east.setText("1 320 640,5");
        Coordinate wgs = entry.valueWGS84();
        assertEquals(CoordSystem.RT90.toWGS84(new Coordinate(6398152, 1320640.5)).getNorth(), wgs.getNorth(), 1e-9);
        assertEquals(CoordinateEntry.Origin.TYPED, entry.origin());
    }

    @Test void remembersThatAPositionCameFromTheMapEvenAfterConverting() {
        entry.pickWGS84(57.712345, 11.973456);
        assertEquals(CoordinateEntry.Origin.PICKED, entry.origin());
        systems.setSelectedItem(CoordSystem.SWEREF99TM);
        assertEquals(CoordinateEntry.Origin.PICKED, entry.origin());
        assertEquals(57.712345, entry.valueWGS84().getNorth());
        north.setText("6400000");
        assertEquals(CoordinateEntry.Origin.TYPED, entry.origin());
    }

    @Test
    void rejectsNumbersFromAnotherSystemAndSaysWhich() {
        systems.setSelectedItem(CoordSystem.SWEREF99TM);
        north.setText("57,712");
        east.setText("11,973");
        assertTrue(assertThrows(IllegalArgumentException.class, entry::valueWGS84).getMessage().contains("WGS84"));
        // Unconvertible input survives a system change so it can be fixed rather than retyped.
        systems.setSelectedItem(CoordSystem.WGS84);
        assertEquals("57,712", north.getText());
        assertEquals(57.712, entry.valueWGS84().getNorth(), 1e-9);
    }

    @Test
    void emptyMeansTextOnlyAndHalfFilledIsRejected() {
        assertNull(entry.valueWGS84());
        entry.setWGS84(null, null);
        assertNull(entry.valueWGS84());
        north.setText("57.7");
        assertThrows(IllegalArgumentException.class, entry::valueWGS84);
    }
}
