package test.coords;

import main.coords.Coordinate;
import main.coords.UTM;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UTMTest {

    // 1cm tolerance is expected for UTM math
    private static final double TOLERANCE_METERS = 0.01; // 1mm precision
    private static final double TOLERANCE_DEGREES = 0.0000001;

    static Stream<Arguments> utmProvider() {
        return Stream.of(
                // Format: Name, Lat, Lon, Expected Zone, Expected Hemisphere, Expected North, Expected East
                // Stockholm (Zone 34N)
                Arguments.of("Stockholm", 59.3293, 18.0686, "34V", 6580391.32, 333230.06),
                // New York (Zone 18N)
                Arguments.of("New York", 40.7128, -74.0060, "18T", 4507351.00, 583959.37),
                // Sydney, Australia (Zone 56S - Note the False Northing for Southern Hemisphere)
                Arguments.of("Sydney", -33.8688, 151.2093, "56H", 6250948.35, 334368.63),
                // Rio de Janeiro (Zone 23S)
                Arguments.of("Rio", -22.9068, -43.1729, "23K", 7465634.13, 687394.59),
                // Bergen, exception in Western Norway
                Arguments.of("Bergen", 60.39129694033072, 5.322101567304192, "32V", 6700648, 297354)
        );
    }

    @ParameterizedTest(name = "{0}: WGS84 to UTM")
    @MethodSource("utmProvider")
    void testWgs84ToUTM(String name, double lat, double lon, String gzd, double expN, double expE) {
        Coordinate wgs = new Coordinate(lat, lon);
        UTM result = UTM.fromWGS84(wgs);

        assertEquals(gzd, result.getGZD(), "Zone mismatch at " + name);
        assertEquals(expN, result.getNorth(), TOLERANCE_METERS, "North mismatch at " + name);
        assertEquals(expE, result.getEast(), TOLERANCE_METERS, "East mismatch at " + name);
    }

    @ParameterizedTest(name = "{0}: UTM to WGS84 (Round-trip)")
    @MethodSource("utmProvider")
    void testUTMToWgs84(String name, double lat, double lon, String gzd, double n, double e) {
        UTM utm = new UTM(gzd, e, n);
        Coordinate result = utm.toWGS84();

        // Degrees tolerance: roughly 0.00001 degrees is ~1 meter
        assertEquals(lat, result.getNorth(), TOLERANCE_DEGREES, "Latitude mismatch at " + name);
        assertEquals(lon, result.getEast(), TOLERANCE_DEGREES , "Longitude mismatch at " + name);
    }
}