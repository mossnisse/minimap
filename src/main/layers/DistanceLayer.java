package main.layers;

import main.coords.*;
import main.core.Layer;
import main.core.Canvas;
import main.geometry.Extent;

import java.awt.*;

public class DistanceLayer extends Layer {
	Canvas canvas;
	private final Coordinate c1, c2;
	static final Stroke LINE_STROKE = new BasicStroke(2);

	public DistanceLayer(Canvas canvas, String name, Coordinate c, int distance, String direction) {
		super(name, false, canvas.getCRS());
		this.canvas = canvas;
		this.c1 = c;
		double bearing = Coordinate.getBearingFromDirection(direction);
	 	c2 = c.move(distance, bearing, getCRS());
	}

	@Override
	public Extent getBoundaries() {
		// Calculate the absolute min and max to ensure a valid Extent
		double minE = Math.min(c1.getEast(), c2.getEast());
		double maxE = Math.max(c1.getEast(), c2.getEast());
		double minN = Math.min(c1.getNorth(), c2.getNorth());
		double maxN = Math.max(c1.getNorth(), c2.getNorth());
		return new Extent(minN, minE, maxN, maxE);
	}

	@Override
	public void invalidateCache() {

	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, Extent bounds) {
		if (isHidden()) return;

		g2d.setColor(getColor());
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