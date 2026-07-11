package gis.coords;

public class LatLongStrategy implements ProjectionStrategy {
    @Override
    public Coordinate project(double lat, double lon) {
        return new Coordinate(lat, lon);
    }

    @Override
    public Coordinate unproject(double northing, double easting) {
        return new Coordinate(northing, easting);
    }

    // Canvas units (degrees) per meter. Latitude and longitude differ; use the
    // longitude factor since callers scale horizontal (east-west) distances.
    @Override
    public double getScaleFactor(Coordinate c) {
        double metersPerDegree = 111_320.0 * Math.cos(Math.toRadians(c.getNorth()));
        return (metersPerDegree > 1) ? 1.0 / metersPerDegree : 0;
    }
}
