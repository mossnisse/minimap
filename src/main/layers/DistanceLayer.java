package main.layers;

import main.coords.*;
import main.core.Layer;
import main.core.Canvas;
import main.geometry.BoundingBox;
import java.awt.*;

public class DistanceLayer implements Layer {
	private Color color = Color.BLACK;
	Canvas canvas;
	private int maxZoom = 0; // 0 indicates unset
	private int minZoom = 0;
	private String name;
	private boolean hidden;
	private CoordSystem cs;
	private final Coordinate c1, c2;
	static final Stroke LINE_STROKE = new BasicStroke(2);

	public DistanceLayer(Canvas canvas, String name, Coordinate c, int distance, String direction, CoordSystem cs) {
		this.name = name;
		this.canvas = canvas;
		this.c1 = c;
		this.cs = cs;
		hidden = false;
		double bearing = Coordinate.getBearingFromDirection(direction);
	 	c2 = c.moveTM(distance, bearing);
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
	public Color getColor() {
		return color;
	}

	@Override
	public void setColor(Color color) {
		this.color = (color != null) ? color : Color.BLACK;
	}

	@Override
	public boolean isHidden() {
		return hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	@Override
	public CoordSystem getCRS() {
		return cs;
	}

	@Override
	public void setCRS(CoordSystem cs) {
		this.cs = cs;
	}

	@Override
	public void setMinZoomL(int zoomLevel) { this.minZoom = zoomLevel; }

	@Override
	public void setMaxZoomL(int zoomLevel) { this.maxZoom = zoomLevel; }

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		boolean meetsMin = (minZoom == 0 || zoomLevel >= minZoom);
		boolean meetsMax = (maxZoom == 0 || zoomLevel <= maxZoom);
		return meetsMin && meetsMax;
	}

	@Override
	public Extent getBoundaries() {
		System.out.println("C1: "+c1 +" c2: "+c2);
		// Calculate the absolute min and max to ensure a valid Extent
		double minE = Math.min(c1.getEast(), c2.getEast());
		double maxE = Math.max(c1.getEast(), c2.getEast());
		double minN = Math.min(c1.getNorth(), c2.getNorth());
		double maxN = Math.max(c1.getNorth(), c2.getNorth());
		return new Extent(minN, minE, maxN, maxE);
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		if (hidden) return;

		g2d.setColor(color);
		Stroke originalStroke = g2d.getStroke();
		g2d.setStroke(LINE_STROKE);

		Point p1 = canvas.toScreenSpace(c1);
		Point p2 = canvas.toScreenSpace(c2);

		g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
		//g2d.drawOval(p1.x - 2, p1.y - 2, 4, 4);
		g2d.drawOval(p2.x - 2, p2.y - 2, 4, 4);

		g2d.setStroke(originalStroke);
	}
}