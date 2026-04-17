package coords;

import java.awt.*;
import java.util.Locale;

public class Coordinate {
    private double north, east; // Can represent Lat/Lon or N/E meters

    public Coordinate(double north, double east) {
        this.north = north;
        this.east = east;
    }

    public Coordinate(Point p) {
        this.north = p.y;
        this.east = p.x;
    }

    public Coordinate(Coordinate c) {
        this.north = c.north;
        this.east = c.east;
    }

    public double getNorth() {
        return north;
    }

    public double getEast() {
        return east;
    }

    // should only be used for projected coordinates where whole numbers has an acceptable precision
    public Point getPoint() {
        return new Point((int)Math.round(east), (int)Math.round(north));
    }

    public void set(double north, double east) {
        this.north = north;
        this.east = east;
    }

    public void set(Point p) {
        this.north = p.y;
        this.east = p.x;
    }

    public void set(Coordinate c) {
        this.north = c.north;
        this.east = c.east;
    }

    // lat long degrees, minutes conversion functions
    /**
     * Sets coordinates from Degrees, Minutes, Seconds (DMS).
     * Use 0 for any missing components (e.g., if you only have DM).
     */
    public void setFromDMS(double latDeg, double latMin, double latSec, String latDir,
                           double lonDeg, double lonMin, double lonSec, String lonDir) {

        this.north = dmsToDecimal(latDeg, latMin, latSec, latDir);
        this.east = dmsToDecimal(lonDeg, lonMin, lonSec, lonDir);
    }

    /**
     * String-based overload for convenience.
     * Handles cleaning up spaces and different decimal separators.
     */
    public void setFromDMS(String latD, String latM, String latS, String latDir,
                           String lonD, String lonM, String lonS, String lonDir) {
        setFromDMS(
                parseDouble(latD), parseDouble(latM), parseDouble(latS), latDir,
                parseDouble(lonD), parseDouble(lonM), parseDouble(lonS), lonDir
        );
    }

    public static String formatDMS(String latD, String latM, String latS, String latDir,
                                   String lonD, String lonM, String lonS, String lonDir) {
        return String.format(Locale.US, "%s\u00B0 %s' %s\" %s %s\u00B0 %s' %s\" %s", latD, latM, latS, latDir, lonD, lonM, lonS, lonDir);
    }

    /**
     * Core logic to convert Degrees Minutes Seconds to Decimal Degrees.
     */
    private double dmsToDecimal(double deg, double min, double sec, String direction) {
        double decimal = Math.abs(deg) + (min / 60.0) + (sec / 3600.0);

        // Normalize direction string
        String dir = (direction == null) ? "" : direction.trim().toUpperCase();

        if (dir.equals("S") || dir.equals("W") || deg < 0 || Double.doubleToRawLongBits(deg) == 0x8000000000000000L) {
            return -decimal;
        }
        return decimal;
    }

    /**
     * Improved parser that handles European comma decimals and spaces.
     */
    private double parseDouble(String str) {
        if (str == null || str.isBlank()) return 0.0;
        try {
            return Double.parseDouble(str.replace(',', '.').replace(" ", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Returns the latitude in DMS format: 57° 42' 31.9" N
     */
    public String getLatDMS() {
        return toDMS(this.north, "N", "S");
    }

    /**
     * Returns the longitude in DMS format: 11° 58' 20.32" E
     */
    public String getLonDMS() {
        return toDMS(this.east, "E", "W");
    }

    /**
     * Core logic to convert Decimal Degrees to a DMS String.
     */
    private String toDMS(double decimal, String posDir, String negDir) {
        String direction = decimal >= 0 ? posDir : negDir;
        double absValue = Math.abs(decimal);

        int degrees = (int) absValue;
        double remainderMinutes = (absValue - degrees) * 60.0;

        int minutes = (int) remainderMinutes;
        double seconds = (remainderMinutes - minutes) * 60.0;

        // The \u00B0 is the Unicode for the degree symbol °
        return String.format(Locale.US, "%d\u00B0 %d' %.2f\" %s", degrees, minutes, seconds, direction);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%.5f, %.5f", north, east);
    }

    public String toPString() {
        return Math.round(north) + ", " + Math.round(east);
    }
}