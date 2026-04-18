package main.coords;

public class LatLongStrategy implements ProjectionStrategy {
    @Override
    public Coordinate project(double lat, double lon) {
        return new Coordinate(lat, lon);
    }

    @Override
    public Coordinate unproject(double northing, double easting) {
        return new Coordinate(northing, easting);
    }
}
