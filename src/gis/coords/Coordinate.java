package gis.coords;

import java.awt.*;
import java.util.Locale;

public class Coordinate {
    private double north, east; // Can represent Lat/Lon or N/E meters
    public static String[] directions = { "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW" };

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

        String dir = (direction == null) ? "" : direction.trim().toUpperCase();

        boolean isNegative;

        if (dir.equals("S") || dir.equals("W")) {
            isNegative = true;
        } else if (dir.equals("N") || dir.equals("E")) {
            isNegative = false;
        } else {
            isNegative = (deg < 0 || Double.compare(deg, -0.0) == 0);
        }

        return isNegative ? -decimal : decimal;
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

    // needs to be an TM coordinate system or similar for it to be reasonably accurate
    public double distanceTM(Coordinate c) {
        return Math.hypot(this.north - c.north, this.east - c.east);
    }

    public double distanceWGS84(Coordinate c) {
        double R = 6371000; // Mean Earth radius in meters

        double lat1 = Math.toRadians(this.north);
        double lat2 = Math.toRadians(c.getNorth());
        double lon1 = Math.toRadians(this.east);
        double lon2 = Math.toRadians(c.getEast());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        // --- The Fix for the 180/-180 line --- This ensures dLon is always between -PI and PI
        while (dLon > Math.PI)  dLon -= 2 * Math.PI;
        while (dLon < -Math.PI) dLon += 2 * Math.PI;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double centralAngle = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * centralAngle;
    }

    // Great Circle: Initial bearing for shortest distance, but curved on a map and bearing changes.
    public Coordinate moveWGS84(double distance, double bearingDegrees) {
        double R = 6371000; // Mean Earth radius in meters

        double lat1 = Math.toRadians(this.north); // Assuming this.north is Latitude
        double lon1 = Math.toRadians(this.east);  // Assuming this.east is Longitude
        double brng = Math.toRadians(bearingDegrees);

        // Angular distance in radians
        double angularDist = distance / R;

        // Calculate new Latitude
        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(angularDist) +
                Math.cos(lat1) * Math.sin(angularDist) * Math.cos(brng));

        // Calculate new Longitude
        double lon2 = lon1 + Math.atan2(Math.sin(brng) * Math.sin(angularDist) * Math.cos(lat1),
                Math.cos(angularDist) - Math.sin(lat1) * Math.sin(lat2));

        // Normalize longitude to -180 to +180 degrees
        double lon2Degrees = Math.toDegrees(lon2);
        lon2Degrees = (lon2Degrees + 540) % 360 - 180;

        return new Coordinate(Math.toDegrees(lat2), lon2Degrees);
    }

    public Coordinate moveTM(double distance, double bearingDegrees) {
        double brng = Math.toRadians(bearingDegrees);

        // 2Calculate the offsets In navigation (North = 0), Easting uses Sin and Northing uses Cos
        double dEast = distance * Math.sin(brng);
        double dNorth = distance * Math.cos(brng);

        // Apply the offsets to the current coordinates
        double newEast = this.east + dEast;
        double newNorth = this.north + dNorth;

        return new Coordinate(newNorth, newEast);
    }

    public Coordinate move(double distance, double bearingDegrees, CoordSystem crs) {
        Coordinate wgs84 = crs.toWGS84(this);
        Coordinate movedWgs84 = wgs84.moveWGS84(distance, bearingDegrees);
        return crs.toProjected(movedWgs84);
    }

    public double getBearingWGS84(Coordinate c) {
        double lat1 = Math.toRadians(this.north);
        double lat2 = Math.toRadians(c.getNorth());
        double lon1 = Math.toRadians(this.east);
        double lon2 = Math.toRadians(c.getEast());

        double dLon = lon2 - lon1;
        // --- The Fix for the 180/-180 line --- This ensures dLon is always between -PI and PI
        while (dLon > Math.PI)  dLon -= 2 * Math.PI;
        while (dLon < -Math.PI) dLon += 2 * Math.PI;

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        double brng = Math.atan2(y, x);

        // Convert to degrees and normalize to 0-360
        return (Math.toDegrees(brng) + 360) % 360;
    }

    public double getBearingTM(Coordinate c) {
        double dEast = c.getEast() - this.east;
        double dNorth = c.getNorth() - this.north;
        double brng = Math.atan2(dEast, dNorth);

        return (Math.toDegrees(brng) + 360) % 360;
    }

    public static double getBearingFromDirection(String dir) {
        return switch (dir.toUpperCase()) {
            case "N"   -> 0.0;
            case "NNE" -> 22.5;
            case "NE"  -> 45.0;
            case "ENE" -> 67.5;
            case "E"   -> 90.0;
            case "ESE" -> 112.5;
            case "SE"  -> 135.0;
            case "SSE" -> 157.5;
            case "S"   -> 180.0;
            case "SSW" -> 202.5;
            case "SW"  -> 225.0;
            case "WSW" -> 247.5;
            case "W"   -> 270.0;
            case "WNW" -> 292.5;
            case "NW"  -> 315.0;
            case "NNW" -> 337.5;
            default    -> 0.0;
        };
    }

    public static String getDirectionFromBearing(double bearing) {
        double normalized = (bearing % 360 + 360) % 360;
        int index = (int) Math.round(normalized / 22.5) % 16;

        return directions[index];
    }

    public String directionTM(Coordinate c) {
        return getDirectionFromBearing(getBearingTM(c));
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%.5f, %.5f", north, east);
    }

    public String toPString() {
        return Math.round(north) + ", " + Math.round(east);
    }
}