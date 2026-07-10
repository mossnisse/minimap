package test.coords;

import main.coords.CoordSystem;
import main.coords.Coordinate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.awt.Point;
import java.util.stream.Stream;

class RT90ToSwerefTest {

    // 1m tolerance is acceptable for cross-datum transformations
    private static final double TOLERANCE = 1.0;

    /**
     * The single source of truth for our test data.
     * Format: Name, RT90 North, RT90 East, Sweref99TM North, Sweref99TM East
     */
    static Stream<Arguments> coordinateProvider() {
        return Stream.of(
                // Data from Lantmäteriet official control points
                Arguments.of("A", 7453389.762, 1727060.905, 7454204.710, 761811.285),
                Arguments.of("B", 7047738.415, 1522128.637, 7046077.551, 562140.294),
                Arguments.of("C", 6671665.273, 1441843.186, 6669189.356, 486557.060),
                Arguments.of("D", 6249111.351, 1380573.079, 6246136.483, 430374.825)
        );
    }

    @ParameterizedTest(name = "Point {0}: RT90 -> SWEREF99TM")
    @MethodSource("coordinateProvider")
    void testRT90ToSweref99TM(String name, double rt90N, double rt90E, double swerefN, double swerefE) {
        // Arrange
        Coordinate rt90Coord = new Coordinate(rt90N, rt90E);

        // Act: RT90 -> WGS84 -> Sweref99TM
        Coordinate wgs84 = CoordSystem.RT90.toWGS84(rt90Coord);
        Coordinate result = CoordSystem.SWEREF99TM.toProjected(wgs84);

        // Assert
        assertEquals(swerefN, result.getNorth(), TOLERANCE, "Forward mismatch at Point " + name);
        assertEquals(swerefE, result.getEast(), TOLERANCE, "Forward mismatch at Point " + name);
    }

    @ParameterizedTest(name = "Point {0}: SWEREF99TM -> RT90")
    @MethodSource("coordinateProvider")
    void testSweref99TMToRT90(String name, double rt90N, double rt90E, double swerefN, double swerefE) {
        // Arrange
        Coordinate swerefCoord = new Coordinate(swerefN, swerefE);

        // Act: Sweref99TM -> WGS84 -> RT90
        Coordinate wgs84 = CoordSystem.SWEREF99TM.toWGS84(swerefCoord);
        Coordinate result = CoordSystem.RT90.toProjected(wgs84);

        // Assert
        assertEquals(rt90N, result.getNorth(), TOLERANCE, "Backward mismatch at Point " + name);
        assertEquals(rt90E, result.getEast(), TOLERANCE, "Backward mismatch at Point " + name);
    }

    @ParameterizedTest(name = "Point {0}: Round-Trip Consistency (RT90 -> SWEREF -> RT90)")
    @MethodSource("coordinateProvider")
    void testRoundTripConsistency(String name, double rt90N, double rt90E, double swerefN, double swerefE) {
        // Arrange
        Coordinate start = new Coordinate(rt90N, rt90E);

        // Act: Convert out and back
        Coordinate wgs84 = CoordSystem.RT90.toWGS84(start);
        Coordinate sweref = CoordSystem.SWEREF99TM.toProjected(wgs84);

        Coordinate swerefBackToWgs = CoordSystem.SWEREF99TM.toWGS84(sweref);
        Coordinate end = CoordSystem.RT90.toProjected(swerefBackToWgs);

        // Assert: For round-trips, tolerance should be even tighter (e.g., 0.01m)
        // because we are testing mathematical stability, not just datum shifts.
        assertEquals(rt90N, end.getNorth(), TOLERANCE, "Round-trip stability fail (North) at Point " + name);
        assertEquals(rt90E, end.getEast(), TOLERANCE, "Round-trip stability fail (East) at Point " + name);
    }
}