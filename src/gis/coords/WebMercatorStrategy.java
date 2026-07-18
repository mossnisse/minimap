package gis.coords;

public class WebMercatorStrategy implements ProjectionStrategy {
    private static final double R = 6378137.0; // WGS84 semi-major axis
    private static final double MAX_LATITUDE = 85.0511287798066;

    @Override
    public Coordinate project(double lat, double lon) {
        lat = Math.clamp(lat, -MAX_LATITUDE, MAX_LATITUDE);
        double x = R * Math.toRadians(lon);
        double y = R * Math.log(Math.tan(Math.PI / 4.0 + Math.toRadians(lat) / 2.0));
        return new Coordinate(y, x); // Returning Northing (y), Easting (x)
    }

    @Override
    public Coordinate unproject(double northing, double easting) {
        double lon = Math.toDegrees(easting / R);
        double lat = Math.toDegrees(Math.PI / 2.0 - 2.0 * Math.atan(Math.exp(-northing / R)));
        return new Coordinate(lat, lon);
    }

    @Override
    public double getScaleFactor(Coordinate c) {
        Coordinate wgs = CoordSystem.WEB_MERCATOR.toWGS84(c);
        double latRad = Math.toRadians(wgs.getNorth());
        return 1.0 / Math.cos(latRad);
    }
}
