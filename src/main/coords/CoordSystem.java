package main.coords;

import main.geometry.BoundingBox;

import java.awt.*;

public enum CoordSystem {

    //1 128 550
    SWEREF99TM("SWEREF99 TM",
            new TransverseMercatorStrategy(0.0, 500000.0, 15.00, 0.9996, 6378137.0, 1.0 / 298.257222101),
            6_000_000, 7_700_000, 200_000, 960_000),
    // parameters to compensate for the Bessel ellipsoid, to convert direct to the "wgs84" ellipsoid
    RT90("RT90 2.5 gon V 0:-15.",
            new TransverseMercatorStrategy(-667.711, 1500064.274, 15.0 + 48.0 / 60.0 + 22.624306 / 3600.0, 1.00000561024, 6378137.0, 1.0 / 298.257222101),
            6100000, 7700000, 1200000, 1900000),
    WEB_MERCATOR("Web Mercator / EPSG:3857",
            new WebMercatorStrategy(),
            -20037508.34, 20037508.34, -20037508.34, 20037508.34),
    WGS84("WGS84",
            new LatLongStrategy(),
        -90,90,-180,180);

    private final String label;
    private final ProjectionStrategy strategy;
    public final double nMin, nMax, eMin, eMax;

    CoordSystem(String label, ProjectionStrategy strategy, double nMin, double nMax, double eMin, double eMax) {
        this.label = label;
        this.strategy = strategy;
        this.nMin = nMin;
        this.nMax = nMax;
        this.eMin = eMin;
        this.eMax = eMax;
    }

    public Coordinate convertTo(Coordinate c, CoordSystem cs) {
        if (cs.equals(this) ) { return c; }
        Coordinate wgs84 = toWGS84(c);
        return cs.toProjected(wgs84);
    }

    public Coordinate toProjected(double lat, double lon) {
        return strategy.project(lat, lon);
    }

    public Coordinate toProjected(Coordinate c) {
        return strategy.project(c.getNorth(), c.getEast());
    }

    public Coordinate toWGS84(double n, double e) {
        return strategy.unproject(n, e);
    }

    public Coordinate toWGS84(Coordinate c) {
        return strategy.unproject(c.getNorth(), c.getEast());
    }

    public Coordinate toWGS84(Point p) {
        return strategy.unproject(p.y, p.x);
    }

    public boolean isValid(double n, double e) {
        return n >= nMin && n <= nMax && e >= eMin && e <= eMax;
    }

    public boolean isValid(Point p) {
        return p.y >= nMin && p.y <= nMax && p.x >= eMin && p.x <= eMax;
    }

    public boolean isValid(Coordinate c) {
        return c.getNorth() >= nMin && c.getNorth() <= nMax && c.getEast() >= eMin && c.getEast() <= eMax;
    }

    public boolean canProject(double lat, double lon) {
        Coordinate projected = toProjected(lat, lon);
        return isValid(projected.getNorth(), projected.getEast());
    }

    public String getLabel() {
        return label;
    }

    public BoundingBox getBoundingBox() {
        return new BoundingBox((int) Math.floor(eMin), (int) Math.ceil(eMax), (int) Math.floor(nMin), (int) Math.ceil(nMax));
    }

    public Extent getBoundaries() {
        return new Extent(nMax, eMax, nMin, eMax);
    }
}