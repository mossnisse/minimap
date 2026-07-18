package gis.geometry;

import gis.coords.CoordSystem;
import gis.coords.Coordinate;

public class Extent {
    public Coordinate c1, c2;

    public Extent(double nStart, double eStart, double nEnd, double eEnd) {
        this.c1 = new Coordinate(nStart, eStart);
        this.c2 = new Coordinate(nEnd, eEnd);
    }

    public Extent(Coordinate c1, Coordinate c2) {
        this.c1 = c1;
        this.c2 = c2;
    }

    public boolean isInside(Coordinate c) {
        return c1.getEast() <= c.getEast() && c.getEast() <= c2.getEast()
                && c1.getNorth()  <= c.getNorth()  && c.getNorth()  <= c2.getNorth() ;
    }

    public boolean isInside(Extent b) {
        return c1.getEast() <= b.c1.getEast() && c2.getEast() >= b.c2.getEast()
                && c1.getNorth() <= b.c1.getNorth() && c2.getNorth() >= b.c2.getNorth();
    }

    public boolean intersects(Extent b) {
        // Standard AABB (Axis-Aligned Bounding Box) intersection check
        // Logic: They overlap if (Left1 < Right2) AND (Right1 > Left2) AND (Top1 > Bottom2) AND (Bottom1 < Top2)

        double minE1 = Math.min(c1.getEast(), c2.getEast());
        double maxE1 = Math.max(c1.getEast(), c2.getEast());
        double minN1 = Math.min(c1.getNorth(), c2.getNorth());
        double maxN1 = Math.max(c1.getNorth(), c2.getNorth());

        double minE2 = Math.min(b.c1.getEast(), b.c2.getEast());
        double maxE2 = Math.max(b.c1.getEast(), b.c2.getEast());
        double minN2 = Math.min(b.c1.getNorth(), b.c2.getNorth());
        double maxN2 = Math.max(b.c1.getNorth(), b.c2.getNorth());

        return (minE1 <= maxE2 && maxE1 >= minE2) &&
                (minN1 <= maxN2 && maxN1 >= minN2);
    }

    public Coordinate getMidlePoint() {
        return new Coordinate(
                c1.getNorth() + (c2.getNorth() - c1.getNorth()) / 2,
                c1.getEast() + (c2.getEast() - c1.getEast()) / 2
        );
    }

    public Extent expand(double amount) {
        return new Extent(
                c1.getNorth() - amount,
                c1.getEast() - amount,
                c2.getNorth() + amount,
                c2.getEast() + amount
        );
    }

    public Extent grow(double percentage) {
        double width = Math.abs(c2.getEast() - c1.getEast());
        double height = Math.abs(c2.getNorth() - c1.getNorth());

        double xBuffer = width * percentage;
        double yBuffer = height * percentage;

        return new Extent(
                c1.getNorth() - yBuffer,
                c1.getEast() - xBuffer,
                c2.getNorth() + yBuffer,
                c2.getEast() + xBuffer
        );
    }

    public double getWidth() {
        return Math.abs(c2.getEast() - c1.getEast());
    }

    public double getHeight() {
        return Math.abs(c2.getNorth() - c1.getNorth());
    }

    public void focus(Coordinate coord) {
        Coordinate m = getMidlePoint();
        double sx = m.getEast() - coord.getEast();
        double sy = m.getNorth() - coord.getNorth();
        c1 = new Coordinate(c1.getNorth() - sy, c1.getEast() - sx);
        c2 = new Coordinate(c2.getNorth() - sy, c2.getEast() - sx);
    }

    /**
     * Cheap 4-corner conversion with a small safety margin, for callers that
     * tolerate slack (e.g. computing a tile fetch range every repaint).
     * Projection curves can push an edge extremum slightly outside the corner
     * box, hence the margin; use {@link #convertCRS} when the envelope must be
     * exact.
     */
    public Extent convertCRSApprox(CoordSystem fromCRS, CoordSystem toCRS) {
        if (fromCRS == toCRS) return convertCRS(fromCRS, toCRS);

        double minN = Double.POSITIVE_INFINITY;
        double maxN = Double.NEGATIVE_INFINITY;
        double minE = Double.POSITIVE_INFINITY;
        double maxE = Double.NEGATIVE_INFINITY;
        Coordinate[] corners = {
                fromCRS.convertTo(new Coordinate(c1.getNorth(), c1.getEast()), toCRS),
                fromCRS.convertTo(new Coordinate(c1.getNorth(), c2.getEast()), toCRS),
                fromCRS.convertTo(new Coordinate(c2.getNorth(), c1.getEast()), toCRS),
                fromCRS.convertTo(new Coordinate(c2.getNorth(), c2.getEast()), toCRS)
        };
        for (Coordinate point : corners) {
            if (!Double.isFinite(point.getNorth()) || !Double.isFinite(point.getEast())) continue;
            minN = Math.min(minN, point.getNorth());
            maxN = Math.max(maxN, point.getNorth());
            minE = Math.min(minE, point.getEast());
            maxE = Math.max(maxE, point.getEast());
        }
        if (!Double.isFinite(minN)) {
            // No finite corner: fall back to the exact edge-sampled conversion
            return convertCRS(fromCRS, toCRS);
        }
        return new Extent(minN, minE, maxN, maxE).grow(0.05);
    }

    public Extent convertCRS(CoordSystem fromCRS, CoordSystem toCRS) {
        if (fromCRS == toCRS) {
            return new Extent(
                    Math.min(c1.getNorth(), c2.getNorth()),
                    Math.min(c1.getEast(), c2.getEast()),
                    Math.max(c1.getNorth(), c2.getNorth()),
                    Math.max(c1.getEast(), c2.getEast()));
        }

        // Projection curves can put an edge extremum outside the box formed by
        // two opposite transformed corners. Sample all four edges and derive a
        // normalized target-CRS envelope from every finite result.
        final int edgeSegments = 32;
        double minN = Double.POSITIVE_INFINITY;
        double maxN = Double.NEGATIVE_INFINITY;
        double minE = Double.POSITIVE_INFINITY;
        double maxE = Double.NEGATIVE_INFINITY;

        double n1 = c1.getNorth(), n2 = c2.getNorth();
        double e1 = c1.getEast(), e2 = c2.getEast();
        for (int i = 0; i <= edgeSegments; i++) {
            double t = i / (double) edgeSegments;
            double n = n1 + (n2 - n1) * t;
            double e = e1 + (e2 - e1) * t;
            Coordinate[] edgePoints = {
                    fromCRS.convertTo(new Coordinate(n1, e), toCRS),
                    fromCRS.convertTo(new Coordinate(n2, e), toCRS),
                    fromCRS.convertTo(new Coordinate(n, e1), toCRS),
                    fromCRS.convertTo(new Coordinate(n, e2), toCRS)
            };
            for (Coordinate point : edgePoints) {
                if (!Double.isFinite(point.getNorth()) || !Double.isFinite(point.getEast())) continue;
                minN = Math.min(minN, point.getNorth());
                maxN = Math.max(maxN, point.getNorth());
                minE = Math.min(minE, point.getEast());
                maxE = Math.max(maxE, point.getEast());
            }
        }

        if (!Double.isFinite(minN)) {
            throw new IllegalArgumentException("Extent has no finite representation in " + toCRS);
        }
        return new Extent(minN, minE, maxN, maxE);
    }
}
