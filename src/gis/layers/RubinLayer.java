package gis.layers;

import gis.coords.*;
import gis.core.MapCanvas;
import gis.core.Layer;
import gis.geometry.Extent;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class RubinLayer extends Layer {
	private String rubin;
	private final MapCanvas mapCanvas;
	static final Stroke LINE_STROKE = new BasicStroke(2);
	private int[][] rt90Corners = null;
	private final List<Coordinate> corners = new ArrayList<>();

	public RubinLayer(String rubin, MapCanvas mapCanvas, String name, Color c) {
		super(name, false, CoordSystem.RT90);
		this.mapCanvas = mapCanvas;
		setColor(c);
		setRubin(rubin);
	}

	public void setRubin(String rubin) {
		this.rubin = rubin;
		rt90Corners = RUBIN.getCorners(rubin);
		convCorners();
	}

	private void convCorners() {
		if (rt90Corners != null) {
			corners.clear();
			for (int[] corner : rt90Corners) {
				// Set current corner in RT90
				Coordinate c = new Coordinate(corner[0], corner[1]);
				// Store the converted coordinates
				corners.add(getCRS().convertTo(c, mapCanvas.getCRS()));
			}
		}
	}

	public Coordinate getMiddle() {
		Coordinate m = RUBIN.toRT90(rubin);
		return getCRS().convertTo(m, mapCanvas.getCRS());
	}

	@Override
	public Extent getBoundaries() {
		if (corners.isEmpty()) return null;
		double minE = Double.MAX_VALUE, maxE = -Double.MAX_VALUE;
		double minN = Double.MAX_VALUE, maxN = -Double.MAX_VALUE;

		for (Coordinate p : corners) {
			minE = Math.min(minE, p.getEast());
			maxE = Math.max(maxE, p.getEast());
			minN = Math.min(minN, p.getNorth());
			maxN = Math.max(maxN, p.getNorth());
		}
		return new Extent(minN, minE, maxN, maxE);
	}

	@Override
	public void invalidateCache() {
		convCorners();
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, Extent bounds) {
		if (isHidden() || corners.size() < 4) return;

		g2d.setColor(getColor());
		Stroke originalStroke = g2d.getStroke();
		g2d.setStroke(LINE_STROKE);

		// Convert the 4 Sweref corners to screen pixel paths
		int n = corners.size();
		int[] xPoints = new int[n];
		int[] yPoints = new int[n];

		for (int i = 0; i < n; i++) {
			Coordinate pt = corners.get(i);
			xPoints[i] = (int) ((pt.getEast() * xScale) + xShift);
			yPoints[i] = (int) ((pt.getNorth() * yScale) + yShift);
		}

		// This handles cases where the grid might be slightly rotated/skewed after conversion
		g2d.drawPolygon(xPoints, yPoints, n);

		g2d.setStroke(originalStroke);
	}
}