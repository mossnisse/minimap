package gis.coords;

import java.awt.*;
import java.util.Locale;

public class RUBIN {
    private static final int RUBIN_ORIGIN_N = 6050000;
    private static final int RUBIN_ORIGIN_E = 1200000;

    private static int alphaToNum(char c) {
        if (Character.isDigit(c)) return c - '0';
        return Character.toUpperCase(c) - 'A';
    }

    private static char numToAlpha(int n, boolean uppercase) {
        return (char) ((uppercase ? 'A' : 'a') + n);
    }

    /**
     * Cleans/validates a RUBIN string and resolves it to the SW corner of the
     * square (in RT90) plus the square size in meters: {baseNorth, baseEast, size}.
     */
    private static int[] parse(String rubin) {
        // Clean the input (remove spaces, hyphens, etc.)
        String clean = rubin.replaceAll("[\\s\\u00A0-]", "");
        // Pad if first part is single digit (e.g., "7K" -> "07K")
        if (!clean.isEmpty() && !Character.isDigit(clean.charAt(1))) {
            clean = "0" + clean;
        }
        if (!(clean.length() == 3 || clean.length() == 5 || clean.length() == 7 || clean.length() == 9)) {
            throw new IllegalArgumentException("Invalid RUBIN string length");
        }

        // size = 50km  "21K"
        int n1 = Integer.parseInt(clean.substring(0, 2));
        int e1 = alphaToNum(clean.charAt(2));
        int size = 50000;
        int baseNorth = RUBIN_ORIGIN_N + (n1 * size);
        int baseEast = RUBIN_ORIGIN_E + (e1 * size);

        if (clean.length() >= 5) {
            // size = 5km  "21K8b"
            int n2 = Character.getNumericValue(clean.charAt(3));
            int e2 = alphaToNum(clean.charAt(4));
            size = 5000;
            baseNorth += (n2 * size);
            baseEast += (e2 * size);
        }
        if (clean.length() == 7) {
            // size = 1km "21K8b 4-3-" cleaned to "21K8b43"
            int n3 = Character.getNumericValue(clean.charAt(5));
            int e3 = Character.getNumericValue(clean.charAt(6));
            size = 1000;
            baseNorth += (n3 * size);
            baseEast += (e3 * size);
        } else if (clean.length() == 9) {
            // size = 100m "21K8b4335"
            int n3 = Integer.parseInt(clean.substring(5, 7));
            int e3 = Integer.parseInt(clean.substring(7, 9));
            size = 100;
            baseNorth += (n3 * size);
            baseEast += (e3 * size);
        }
        return new int[]{baseNorth, baseEast, size};
    }

    public static Coordinate toRT90(String rubin) {
        int[] p = parse(rubin);
        // Return the centre of the square
        return new Coordinate(p[0] + p[2] / 2.0, p[1] + p[2] / 2.0);
    }

    public static Coordinate toSweref99TM(String rubin) {
        Coordinate rt90 = toRT90(rubin);
        Coordinate wgs84 = CoordSystem.RT90.toWGS84(rt90);
        return CoordSystem.SWEREF99TM.toProjected(wgs84);
    }

    public static String fromRT90(Coordinate rt90) {
        int nTotal = (int) Math.round(rt90.getNorth()) - RUBIN_ORIGIN_N;
        int eTotal = (int) Math.round(rt90.getEast()) - RUBIN_ORIGIN_E;

        // Level 1: 50x50 km (e.g., "6G")
        int n1 = nTotal / 50000;
        char e1 = numToAlpha(eTotal / 50000, true);

        // Level 2: 5x5 km (e.g., "6g7e")
        int n2 = (nTotal % 50000) / 5000;
        char e2 = numToAlpha((eTotal % 50000) / 5000, false);

        // Level 3: 100x100 m (Precise index 6G7e 0420)
        int n3 = (nTotal % 5000) / 100;
        int e3 = (eTotal % 5000) / 100;

        return String.format(Locale.US, "%d%c%d%c %02d%02d", n1, e1, n2, e2, n3, e3);
    }

    public static String fromRT90(Point p) {
        return fromRT90(new Coordinate(p));
    }

    public static String fromSweref99TM(Coordinate c) {
        Coordinate wgs84 = CoordSystem.SWEREF99TM.toWGS84(c);
        return fromRT90(CoordSystem.RT90.toProjected(wgs84));
    }

    // returns the corners of the RUBIN square in RT90
    public static int[][] getCorners(String rubin) {
        int[] p = parse(rubin);
        int baseNorth = p[0], baseEast = p[1], size = p[2];

        // Return the corners [SW, NW, NE, SE] in RT90
        return new int[][]{
                {baseNorth, baseEast},
                {baseNorth + size, baseEast},
                {baseNorth + size, baseEast + size},
                {baseNorth, baseEast + size}
        };
    }

    public static boolean isValidRUBIN(String rubin, boolean strict) {
        if (rubin == null || rubin.trim().isEmpty()) {
            return false;
        }

        // Strict Mode: Compare directly against the formatting logic of toRUBIN
        if (strict) {
            // Strict matches:
            // 50km: "21K"
            // 5km:  "21K8b"
            // 1km:  "21K8b 4-3-"
            // 100m: "21K8b 4321"
            return rubin.matches("^(0[1-9]|[12]\\d|3[0-3])[A-N](\\d[a-j](\\s(\\d-\\d-|\\d{4}))?)?$");
        }

        // Loose Mode: Check if it's parsable by setFromRUBIN
        try {
            testParse(rubin);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * A helper that mirrors setFromRUBIN logic but doesn't change state.
     * Throws exceptions if parsing fails.
     */
    private static void testParse(String rubin) {
        String clean = rubin.replaceAll("[\\s\\u00A0-]", "");

        if (!clean.isEmpty() && clean.length() >= 2 && !Character.isDigit(clean.charAt(1))) {
            clean = "0" + clean;
        }

        int len = clean.length();
        // Valid lengths after cleaning: 3, 5, 7 (1km), 9 (100m)
        if (!(len == 3 || len == 5 || len == 7 || len == 9)) {
            throw new IllegalArgumentException("Invalid RUBIN length: " + len);
        }

        // Level 1 (50km): NNX
        int n1 = Integer.parseInt(clean.substring(0, 2));
        if (n1 < 1 || n1 > 33) throw new IllegalArgumentException("N1 out of bounds (1-33)");

        char e1 = clean.charAt(2);
        int e1Val = alphaToNum(e1);
        if (e1Val < 0 || e1Val > 13) throw new IllegalArgumentException("E1 out of range (A-N)");

        // Level 2 (5km): nx
        if (len >= 5) {
            char n2Char = clean.charAt(3);
            char e2Char = clean.charAt(4);

            if (!Character.isDigit(n2Char)) throw new IllegalArgumentException("n2 must be a digit");

            int e2Val = alphaToNum(e2Char);
            if (e2Val < 0 || e2Val > 9) throw new IllegalArgumentException("e2 out of range (a-j)");
        }

        // Level 3 (1km or 100m)
        if (len == 7) {
            // "21K8b 4-3-" cleaned to "21K8b43". The last two are "43"
            Integer.parseInt(clean.substring(5, 7));
        } else if (len == 9) {
            // "21K8b 4321" cleaned to "21K8b4321". The last four are "4321"
            Integer.parseInt(clean.substring(5, 9));
        }
    }
}