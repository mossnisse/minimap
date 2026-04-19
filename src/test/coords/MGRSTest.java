package test.coords;

import main.coords.Coordinate;
import main.coords.MGRS;
import main.coords.UTM;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MGRSTest {

    private static final double TOLERANCE_METERS = 1; // 1mm precision
    private static final double TOLERANCE_DEGREES = 0.00002;


    static Stream<Arguments> mgrsProvider() {
        return Stream.of(
                // Standard Case (Stockholm) - 1m precision (10 digits)
                Arguments.of("Stockholm", 59.32445626, 18.07110864, "34V CL 33348 79846"),

                // New York (Zone 18) - Testing different Column/Row sets
                Arguments.of("New York", 40.710328, -74.001532, "18T WL 84339 07080"),

                // Southern Hemisphere (Sydney) - Testing 10,000,000m offset
                Arguments.of("Sydney", -33.910351, 151.082619, "56H LH 22735 46128"),

                // The "Norway Exception" (Bergen) - Crucial test for 32V
                // Bergen is in Zone 32 but far West. Easting is ~297745
                Arguments.of("Bergen", 60.394306, 5.325919, "32V KN 97582 00971"),

                Arguments.of("Dakar", 14.695236, -17.447669, "28P BB 36417 26047"),

                Arguments.of("Reykavik", 64.147202, -21.939740, "27W VM 54278 13754"),

                Arguments.of("Southern Argentina", -54.806226, -64.432755, "20F ME 07910 25830"),
                Arguments.of("Mombasa", -4.052118, 39.665108, "37M ER 73827 52081"),
                Arguments.of("near the equator", 0.703107, 111.555176, "49N EA 61773 77718"),
                Arguments.of("near the equator2", -0.032959, 36.996460, "37M BV 77010 96354")
        );
    }

    @ParameterizedTest(name = "MGRS Forward: {0}")
    @MethodSource("mgrsProvider")
    void testToMGRS(String name, double lat, double lon, String expectedMGRS) {

        UTM utm = UTM.fromWGS84(lat, lon);
        String result = MGRS.fromUTM(utm);

        assertEquals(expectedMGRS, result, "Forward conversion failed for " + name);
    }

    @ParameterizedTest(name = "MGRS Backward: {0}")
    @MethodSource("mgrsProvider")
    void testFromMGRS(String name, double lat, double lon, String mgrsStr) {
        UTM utm = MGRS.toUTM(mgrsStr);
        Coordinate wgs84 = utm.toWGS84();

        assertEquals(lat, wgs84.getNorth(), TOLERANCE_DEGREES, "Easting mismatch at " + name);
        assertEquals(lon, wgs84.getEast(), TOLERANCE_DEGREES, "Easting mismatch at " + name);
    }

    @ParameterizedTest(name = "Valid MGRS strings")
    @MethodSource("mgrsProvider")
    void testIsValidPositive(String name, double lat, double lon, String mgrsStr) {
        assertTrue(MGRS.isValid(mgrsStr), "Should be valid: " + name);
    }

    @ParameterizedTest(name = "Invalid MGRS strings")
    @ValueSource(strings = {
            "99VCL3334879846",   // Invalid Zone
            "34YCL3334879846",   // Invalid Band (Y)
            "34VIL3334879846",   // Illegal Letter I
            "34VCL333487984",    // Odd number of digits
            "34V AA 12345 12345", // Zone 34 Column Set starts at S, A is impossible
            "ABCDE",             // Not a coordinate
            "1VUC1234"           // Single digit zone (if your parser doesn't pad, check logic)
    })
    void testIsValidNegative(String invalidMgrs) {
        assertFalse(MGRS.isValid(invalidMgrs), "Should be invalid: " + invalidMgrs);
    }
}
