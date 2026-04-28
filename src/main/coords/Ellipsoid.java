package main.coords;

public class Ellipsoid {
    // Semi-major axis (radius at equator)  part of the reference ellipsoid
    public final double a;
    // Flattening  part of the reference ellipsoid
    public final double f;
    // Semi-minor axis (calculated)
    public final double b;
    // Eccentricity squared (calculated)
    public final double e2;

    // Helmert 7-parameters for transformation to WGS84, not part of the reference ellipsoid, varies over time and place if it should be exact
    public final double dx, dy, dz; // Translation (meters)
    public final double rx, ry, rz; // Rotation (arc-seconds)
    public final double ds;         // Scale factor (ppm)

    public Ellipsoid(double a, double f, double dx, double dy, double dz,
                     double rx, double ry, double rz, double ds) {
        this.a = a;
        this.f = f;
        this.b = a * (1 - f);
        this.e2 = (Math.pow(a, 2) - Math.pow(b, 2)) / Math.pow(a, 2);

        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        this.ds = ds;
    }

    // Common Ellipsoids
    // WGS84 is used as the standard
    public static final Ellipsoid WGS84 = new Ellipsoid(6378137.0, 1/298.257223563,
            0,0,0,0,0,0,0);

    // GRS 1980
    public static final Ellipsoid GRS80 = new Ellipsoid(6378137.0, 1/298.257222101,
            0,0,0,0,0,0,0);

    public static final Ellipsoid INTERNATIONAL_1924 = new Ellipsoid(6378388.0, 1/297.0,
            -87, -98, -121, 0, 0, 0, 0); // ED50 example

    // Bessel 1841 what is the Helmert 7 parameters that is good for Sweden? Like Generalstabskartan
    public static final Ellipsoid BESSEL = new Ellipsoid(6377397.155, 1 / 299.15281285, 0,0,0,0,0,0,0);

    // Sweref99 uses the GRS80 reference elipsoid and should be geocentric but drifts from WGS84 due to plate tectonic, the shift is 8-9 dm. should be added to the Helmert parameters
    public static final Ellipsoid SWEREF99 = new Ellipsoid(6378137.0, 1/298.257222101,0,0,0,0,0,0,0);

    // https://epsg.org/guidance-notes.html
    // https://github.com/OSGeo/proj-datumgrid
}
