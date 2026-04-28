package main.coords;

public class Coordinate3d {
    private final double lat;   // Decimal Degrees
    private final double lon;   // Decimal Degrees
    private final double height; // Ellipsoidal height (meters)
    private final Ellipsoid ellipsoid;

    public static class Cartesian {
        public final double x;
        public final double y;
        public final double z;

        public Cartesian(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public Coordinate3d(double lat, double lon, double height, Ellipsoid ellipsoid) {
        this.lat = lat;
        this.lon = lon;
        this.height = height;
        this.ellipsoid = ellipsoid;
    }

    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public double getHeight() { return height; }
    public Ellipsoid getEllipsoid() { return ellipsoid; }

    public Cartesian toCartesian() {
        double latRad = Math.toRadians(this.lat);
        double lonRad = Math.toRadians(this.lon);

        double a = ellipsoid.a;
        double e2 = ellipsoid.e2;
        double h = this.height;

        // Radius of curvature in the prime vertical
        double sinLat = Math.sin(latRad);
        double cosLat = Math.cos(latRad);

        double n = a / Math.sqrt(1 - e2 * sinLat * sinLat);

        double x = (n + h) * cosLat * Math.cos(lonRad);
        double y = (n + h) * cosLat * Math.sin(lonRad);
        double z = (n * (1 - e2) + h) * sinLat;

        return new Cartesian(x, y, z);
    }

    public static Coordinate3d fromCartesian(Cartesian cart, Ellipsoid ellip) {
        double x = cart.x;
        double y = cart.y;
        double z = cart.z;
        double a = ellip.a;
        double b = ellip.b;
        double e2 = ellip.e2;

        // Second eccentricity squared
        double ep2 = (Math.pow(a, 2) - Math.pow(b, 2)) / Math.pow(b, 2);
        double p = Math.sqrt(x * x + y * y);
        double th = Math.atan2(a * z, b * p);

        double lonRad = Math.atan2(y, x);
        double latRad = Math.atan2(
                (z + ep2 * b * Math.pow(Math.sin(th), 3)),
                (p - e2 * a * Math.pow(Math.cos(th), 3))
        );

        double n = a / Math.sqrt(1 - e2 * Math.pow(Math.sin(latRad), 1));
        double height = (p / Math.cos(latRad)) - n;

        return new Coordinate3d(
                Math.toDegrees(latRad),
                Math.toDegrees(lonRad),
                height,
                ellip
        );
    }

    public static Cartesian shiftToWGS84(Cartesian local, Ellipsoid from) {
        // Convert rotation from arc-seconds to radians
        double rx = Math.toRadians(from.rx / 3600.0);
        double ry = Math.toRadians(from.ry / 3600.0);
        double rz = Math.toRadians(from.rz / 3600.0);

        // Convert scale from parts-per-million to a multiplier
        double s = 1.0 + (from.ds / 1_000_000.0);

        // 3. Apply the 7-parameter transformation (Matrix Multiplication)
        // X' = DX + S * (X + Rz*Y - Ry*Z)
        // Y' = DY + S * (-Rz*X + Y + Rx*Z)
        // Z' = DZ + S * (Ry*X - Rx*Y + Z)

        double xWGS84 = from.dx + s * (local.x + (rz * local.y) - (ry * local.z));
        double yWGS84 = from.dy + s * ((-rz * local.x) + local.y + (rx * local.z));
        double zWGS84 = from.dz + s * ((ry * local.x) - (rx * local.y) + local.z);

        return new Cartesian(xWGS84, yWGS84, zWGS84);
    }

    public Coordinate3d transformToWGS84() {
        if (this.ellipsoid == Ellipsoid.WGS84) {
            return this; // Already there
        }

        // Geographic -> Cartesian (Local)
        Cartesian localCart = this.toCartesian();

        // Helmert Shift (Local Cartesian -> WGS84 Cartesian)
        Cartesian wgs84Cart = shiftToWGS84(localCart, this.ellipsoid);

        // Cartesian -> Geographic (WGS84)
        // We pass the WGS84 ellipsoid definition for the return trip
        return Coordinate3d.fromCartesian(wgs84Cart, Ellipsoid.WGS84);
    }
}
