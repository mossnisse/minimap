package main.layers;

import main.coords.*;
import main.core.Canvas;
import main.core.Layer;
import main.geometry.Extent;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class RubinLayer extends Layer {
	private String rubin;
	private final Canvas canvas;
	static final Stroke LINE_STROKE = new BasicStroke(2);

	// Store corners in projected (Sweref) coordinates
	private final List<Coordinate> corners = new ArrayList<>();

	public RubinLayer(String rubin, Canvas canvas, String name, Color c) {
		super(name, false, CoordSystem.RT90);
		this.canvas = canvas;
		setColor(c);
		setRubin(rubin);
	}

	public void setRubin(String rubin) {
		this.rubin = rubin;
		this.corners.clear();
		int[][] rt90Corners = RUBIN.getCorners(rubin);

		if (rt90Corners != null) {
			for (int[] corner : rt90Corners) {
				// Set current corner in RT90
				Coordinate c = new Coordinate(corner[0], corner[1]);
				// Store the Sweref coordinates
				corners.add(getCRS().convertTo(c, canvas.getCRS()));
			}
		}
	}

	public Coordinate getMiddle() {
		Coordinate m = RUBIN.toRT90(rubin);
		return getCRS().convertTo(m, canvas.getCRS());
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
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, Extent bounds) {
		if (isHidden() || corners.size() < 4) return;

		g2d.setColor(getColor());
		Stroke originalStroke = g2d.getStroke();
		g2d.setStroke(LINE_STROKE);

		// Convert the 4 Sweref corners to screen pixel paths
		int[] xPoints = new int[4];
		int[] yPoints = new int[4];

		for (int i = 0; i < 4; i++) {
			Coordinate pt = corners.get(i);
			xPoints[i] = (int) ((pt.getEast() * xScale) + xShift);
			yPoints[i] = (int) ((pt.getNorth() * yScale) + yShift);
		}

		// This handles cases where the grid might be slightly rotated/skewed after conversion
		g2d.drawPolygon(xPoints, yPoints, 4);

		g2d.setStroke(originalStroke);
	}
}