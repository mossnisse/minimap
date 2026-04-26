package main.layers;

import main.coords.*;
import main.core.Layer;
import main.geometry.BoundingBox;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class RubinLayer implements Layer {
	private String name, rubin;
	private Color color = Color.BLACK;
	private int maxZoom = 0; // 0 indicates unset
	private int minZoom = 0;
	private boolean hidden;
	private CoordSystem cs;
	static final Stroke LINE_STROKE = new BasicStroke(2);

	// Store corners in projected (Sweref) coordinates
	private List<Coordinate> swerefCorners = new ArrayList<>();

	public RubinLayer(String rubin, String name, Color c) {
		this.name = name;
		this.color = c;
		setRubin(rubin);
	}

	public void setRubin(String rubin) {
		this.rubin = rubin;
		this.swerefCorners.clear();

		int[][] rt90Corners = RUBIN.getCorners(rubin);

		if (rt90Corners != null) {
			for (int[] corner : rt90Corners) {
				// Set current corner in RT90
				Coordinate wgs84 = CoordSystem.RT90.toWGS84(corner[0], corner[1]);
				Coordinate sweref = CoordSystem.SWEREF99TM.toProjected(wgs84);

				// Store the Sweref coordinates
				swerefCorners.add(sweref);
			}
		}
	}

	public Coordinate getMiddle() {
		return RUBIN.toSweref99TM(rubin);
	}

	@Override
	public void setColor(Color color) {
		this.color = (color != null) ? color : Color.BLACK;
	}

	@Override
	public Color getColor() {
		return color;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public boolean isHidden() { return hidden; }

	@Override
	public void setHidden(boolean hidden) { this.hidden = hidden; }

	@Override
	public void setCRS(CoordSystem cs) { this.cs = cs; }

	@Override
	public CoordSystem getCRS() { return cs; }

	@Override
	public void setMinZoomL(int zoomLevel) { minZoom = zoomLevel; }

	@Override
	public void setMaxZoomL(int zoomLevel) { maxZoom = zoomLevel; }

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		boolean meetsMin = (minZoom == 0 || zoomLevel >= minZoom);
		boolean meetsMax = (maxZoom == 0 || zoomLevel <= maxZoom);
		return meetsMin && meetsMax;
	}

	@Override
	public Extent getBoundaries() {
		//Todo: implement the method
		return null;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		if (hidden || swerefCorners.size() < 4) return;

		g2d.setColor(color);
		Stroke originalStroke = g2d.getStroke();
		g2d.setStroke(LINE_STROKE);

		// Convert the 4 Sweref corners to screen pixel paths
		int[] xPoints = new int[4];
		int[] yPoints = new int[4];

		for (int i = 0; i < 4; i++) {
			Coordinate pt = swerefCorners.get(i);
			xPoints[i] = (int) ((pt.getEast() * xScale) + xShift);
			yPoints[i] = (int) ((pt.getNorth() * yScale) + yShift);
		}

		// This handles cases where the grid might be slightly rotated/skewed after conversion
		g2d.drawPolygon(xPoints, yPoints, 4);

		g2d.setStroke(originalStroke);
	}
}