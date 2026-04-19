package main.coords;

import java.util.Locale;

public class UTM {
    private final int zone;
    private final boolean isNorthern; // true = N, false = S
    private final double easting;
    private final double northing;

    // Standard UTM constants
    private static final double WGS84_AXIS = 6378137.0;
    private static final double WGS84_FLATTENING = 1.0 / 298.257222101;
    private static final double UTM_SCALE = 0.9996;

    public UTM(String gzd,  double easting, double northing) {
        this.zone = Integer.parseInt(gzd.substring(0, gzd.length() - 1));
        this.isNorthern = !(gzd.charAt(gzd.length() - 1) < 'N');
        this.easting = easting;
        this.northing = northing;
    }

    public UTM(int zone, boolean isNorthern, double easting, double northing) {
        this.zone = zone;
        this.isNorthern = isNorthern;
        this.easting = easting;
        this.northing = northing;
    }

    public UTM(int zone, char hemisphere, double easting, double northing) {
        this.zone = zone;
        this.isNorthern = Character.toUpperCase(hemisphere) == 'N';
        this.easting = easting;
        this.northing = northing;
    }

    public String getGZD() {
        Coordinate wgs = this.toWGS84();
        return calculateGZD(wgs.getNorth(), wgs.getEast());
    }

    public double getNorth() {
        return northing;
    }

    public double getEast() {
        return easting;
    }

    public int getZone() {
        return zone;
    }

    public char getHemisphere() {
        return (isNorthern) ? 'N' : 'S';
    }

    public boolean isNorthern() { return isNorthern; }

    /**
     * Static Factory: Create UTM from WGS84 Lat/Lon
     */
    public static UTM fromWGS84(double lat, double lon) {
        if (lat < -80 || lat > 84) return null;
        if (lon < -180.0 || lon > 180.0) { return null; }

        int zone = (int) Math.floor((lon + 180) / 6.0) + 1;
        if (lon >= 180) zone = 60;

        // Apply Norway/Svalbard zone overrides
        zone = applyZoneExceptions(lat, lon, zone);

        double centralMeridian = (zone * 6.0) - 183.0;
        double falseNorthing = (lat < 0) ? 10000000.0 : 0.0;

        TransverseMercatorStrategy strategy = new TransverseMercatorStrategy(
                falseNorthing, 500000.0, centralMeridian,
                UTM_SCALE, WGS84_AXIS, WGS84_FLATTENING
        );

        Coordinate projected = strategy.project(lat, lon);
        return new UTM(zone, lat >= 0, projected.getEast(), projected.getNorth());
    }

    public static UTM fromWGS84(Coordinate wgs84) {
        if (wgs84 == null) return null;
        return fromWGS84(wgs84.getNorth(), wgs84.getEast());
    }

    /**
     * Instance Method: Convert this UTM coordinate back to WGS84
     */
    public Coordinate toWGS84() {
        double centralMeridian = (zone * 6.0) - 183.0;
        double falseNorthing = isNorthern ? 0.0 : 10000000.0;

        TransverseMercatorStrategy strategy = new TransverseMercatorStrategy(
                falseNorthing, 500000.0, centralMeridian,
                UTM_SCALE, WGS84_AXIS, WGS84_FLATTENING
        );

        return strategy.unproject(this.northing, this.easting);
    }

    private static int applyZoneExceptions(double lat, double lon, int zone) {
        if (lat > 56 && lat < 64 && lon > 3 && lon < 6) return 32;
        if (lat > 72) {
            if (lon >= 0 && lon < 9) return 31;
            if (lon >= 9 && lon < 21) return 33;
            if (lon >= 21 && lon < 33) return 35;
            if (lon >= 33 && lon < 42) return 37;
        }
        return zone;
    }

    // Helper to keep the calculateGZD logic for display/MGRS purposes
    private static String calculateGZD(double lat, double lon) {
        if (lat < -80 || lat > 84) return null;
        int zn = applyZoneExceptions(lat, lon, (int) Math.floor((lon + 180) / 6.0) + 1);
        if (lon >= 180) zn = 60; // Handle the 180 meridian wrap

        char zl;
        if (lat >= 72) zl = 'X';      // Band X is 12 degrees high (72 to 84)
        else if (lat < -80) zl = 'C'; // Anything south of -80 is Polar (UPS)
        else {
            // We use floor to ensure -80 to -72 is index 0, -72 to -64 is index 1, etc.
            int index = (int) Math.floor((lat + 80) / 8.0);
            zl = utmNumToAlpha(index + 1); // index + 1 because 'C' is the 1st band
        }
        return zn + String.valueOf(zl);
    }

    private static char utmNumToAlpha(int num) {
        int code = num + 66;
        if (code > 72) code++; // Skip I
        if (code > 78) code++; // Skip O
        return (char) code;
    }

    public String toMGRS() {
        return MGRS.fromUTM(this);
    }

    public static UTM fromMGRS(String mgrs) {
        return MGRS.toUTM(mgrs);
    }

    public boolean isValid(boolean simplified) {
        if (zone > 60 || zone < 1) return false;
        if (easting < 0 || easting > 1000000) return false; // relaxed limits to allow for the exceptions at Norway and svalbard
        if (northing < 0 || northing > 10000000) return false;
        if  (!simplified) {
            Coordinate wgs;
            try {
                wgs = this.toWGS84();
            } catch (Exception e) {
                return false; // Math failed (singularities)
            }

            double lat = wgs.getNorth();
            double lon = wgs.getEast();

            // Verify the Latitude is within UTM limits (-80 to 84)
            if (lat < -80 || lat > 84) return false;

            // Determine what the "Correct" zone SHOULD be for this Lat/Lon
            int expectedZone = (int) Math.floor((lon + 180) / 6.0) + 1;
            if (lon == 180) expectedZone = 60;

            // Apply the Norway/Svalbard logic to the expected zone
            expectedZone = applyZoneExceptions(lat, lon, expectedZone);

            // The coordinate is valid if its zone matches the calculated expected zone
            // and its easting is within a safe mathematical buffer (e.g., 0 to 1,000,000)
            if (this.zone != expectedZone) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%d%s %.0fE %.0fN",
                zone, isNorthern ? "N" : "S", easting, northing);
    }
}