package coords;

public interface ProjectionStrategy {
    // Converts Lat/Lon (WGS84) to Projected (E/N)
    Coordinate project(double lat, double lon);

    // Converts Projected (E/N) to Lat/Lon (WGS84)
    Coordinate unproject(double northing, double easting);
}