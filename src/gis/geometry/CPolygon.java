package gis.geometry;

import gis.coords.Coordinate;
import java.awt.*;

public class CPolygon  {
    private final int[] parts;
    private final Coordinate[] points;

    public CPolygon(int[] parts, Coordinate[] points) {
        this.parts = parts;
        this.points = points;
    }

    public int[] getParts() {
        return parts;
    }

    public Coordinate[] getPoints() {
        return points;
    }

    public boolean isInside(Coordinate p) {
        boolean inside = false;
        double px = p.getEast();
        double py = p.getNorth();

        for (int i = 0; i < parts.length; i++) {
            int start = parts[i];
            int end = (i == parts.length - 1) ? points.length : parts[i + 1];

            for (int j = start, k = end - 1; j < end; k = j++) {
                double ix = points[j].getEast();
                double iy = points[j].getNorth();
                double kx = points[k].getEast();
                double ky = points[k].getNorth();

                // Standard W. Randolph Franklin algorithm (no object creation)
                if (((iy > py) != (ky > py)) && (px < (kx - ix) * (py - iy) / (ky - iy) + ix)) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }
}