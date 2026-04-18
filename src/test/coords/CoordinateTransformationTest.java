package test.coords;

import main.coords.CoordSystem;
import main.coords.Coordinate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class CoordinateTransformationTest {

    private static final double TOLERANCE_METERS = 0.001; // 1mm precision
    private static final double TOLERANCE_DEGREES = 0.0000001;

    @ParameterizedTest(name = "SWEREF99TM: {0}, {1} -> {2}, {3}")
    // sweref99 TM controll points
    // https://www.lantmateriet.se/sv/geodata/gps-geodesi-och-swepos/Referenssystem/Tvadimensionella-system/SWEREF-99-projektioner/contentassets/kontrollpunkter_sweref99tm.pdf
    @CsvSource({
            // Lat, Lon, Expected North, Expected East
            "55.0000, 12.7500, 6097106.672, 356083.438",
            "55.0000, 14.25, 6095048.642, 452024.069",
            "57.0000, 12.7500, 6319636.937, 363331.554",
            "57.0000, 19.5000, 6326392.707, 773251.054",
            "59.0000, 11.25000, 6546096.724, 284626.066",
            "69.0000, 21.000, 7666089.698, 739639.195"
    })
    void testSwerefForward(double lat, double lon, double expectedN, double expectedE) {
        Coordinate result = CoordSystem.SWEREF99TM.toProjected(lat, lon);
        assertEquals(expectedN, result.getNorth(), TOLERANCE_METERS);
        assertEquals(expectedE, result.getEast(), TOLERANCE_METERS);
    }

    @ParameterizedTest(name = "RT90/SWEREF Roundtrip")
    @CsvSource({
            "59.3293, 18.0686", // Stockholm
            "55.6050, 13.0038",  // Malmö
            "67.856107, 20.233727" // Kiruna
    })
    void testRoundTrip(double lat, double lon) {
        // Lat/Lon -> Sweref -> Lat/Lon
        Coordinate sweref = CoordSystem.SWEREF99TM.toProjected(lat, lon);
        Coordinate back = CoordSystem.SWEREF99TM.toWGS84(sweref);

        assertEquals(lat, back.getNorth(), TOLERANCE_DEGREES);
        assertEquals(lon, back.getEast(), TOLERANCE_DEGREES);
    }
}
