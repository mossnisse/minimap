package coords;

import java.util.Locale;
// Todo: write an valid checker

public class UTM {
    public final String gzd; // Grid Zone Designation (e.g., "33V")
    public final double easting;
    public final double northing;

    // Standard UTM constants
    private static final double WGS84_AXIS = 6378137.0;
    private static final double WGS84_FLATTENING = 1.0 / 298.257222101;
    private static final double UTM_SCALE = 0.9996;

    public UTM(String gzd, double easting, double northing) {
        this.gzd = gzd;
        this.easting = easting;
        this.northing = northing;
    }

    /**
     * Static Factory: Create UTM from WGS84 Lat/Lon
     */
    public static UTM fromWGS84(double lat, double lon) {
        if (lat < -80 || lat > 84) return null;

        String gzd = calculateGZD(lat, lon);
        int zone = Integer.parseInt(gzd.replaceAll("[^0-9]", ""));

        double centralMeridian = (zone * 6.0) - 183.0;
        double falseNorthing = (lat < 0) ? 10000000.0 : 0.0;

        // Reuse your TransverseMercatorStrategy
        TransverseMercatorStrategy strategy = new TransverseMercatorStrategy(
                falseNorthing, 500000.0, centralMeridian,
                UTM_SCALE, WGS84_AXIS, WGS84_FLATTENING
        );

        Coordinate projected = strategy.project(lat, lon);
        return new UTM(gzd, projected.getEast(), projected.getNorth());
    }

    public static UTM fromWGS84(Coordinate wgs84) {
        return fromWGS84(wgs84.getNorth(), wgs84.getEast());
    }

    /**
     * Instance Method: Convert this UTM coordinate back to WGS84
     */
    public Coordinate toWGS84() {
        int zone = Integer.parseInt(gzd.replaceAll("[^0-9]", ""));
        char band = gzd.charAt(gzd.length() - 1);

        double centralMeridian = (zone * 6.0) - 183.0;
        // Bands C-M are Southern Hemisphere, N-X are Northern
        double falseNorthing = (band < 'N') ? 10000000.0 : 0.0;

        TransverseMercatorStrategy strategy = new TransverseMercatorStrategy(
                falseNorthing, 500000.0, centralMeridian,
                UTM_SCALE, WGS84_AXIS, WGS84_FLATTENING
        );

        return strategy.unproject(this.northing, this.easting);
    }

    private static String calculateGZD(double lat, double lon) {
        int zn = (int) Math.ceil((lon + 180) / 6.0);
        if (lon == 180) zn = 60;

        char zl;
        if (lat >= 72) zl = 'X';
        else if (lat < -80) zl = 'C';
        else {
            int index = (int) Math.ceil((lat + 80) / 8.0);
            zl = utmNumToAlpha(index);
        }

        // Norway/Svalbard Exceptions
        if (lat > 56 && lat < 64 && lon > 3 && lon < 6) return "32V";
        if (lat > 72) {
            if (lon >= 0 && lon < 9) return "31X";
            if (lon >= 9 && lon < 21) return "33X";
            if (lon >= 21 && lon < 33) return "35X";
            if (lon >= 33 && lon < 42) return "37X";
        }
        return String.valueOf(zn) + zl;
    }

    private static char utmNumToAlpha(int num) {
        int code = num + 66; // 1 -> 'C'
        if (code > 72) code++; // Skip 'I'
        if (code > 78) code++; // Skip 'O'
        return (char) code;
    }

    public String toMGRS() {
        return MGRS.fromUTM(this);
    }

    public static UTM fromMGRS(String mgrs) {
        return MGRS.toUTM(mgrs);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s %.0f %.0f", gzd, easting, northing);
    }
}