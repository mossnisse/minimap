import coords.*;
import geometry.BoundingBox;
import geometry.Point;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;

public class RubinLayer implements Layer {
	private String name, rubin;
	private Color color;
	private boolean hidden;
	private CoordSystem cs;
	private double westBoundary;
	private double northBoundary;
	
	public RubinLayer(String rubin, String name, Color c) {
		this.rubin = rubin;
		this.name = name;
		this.color = c;
		setRubin(rubin);
	}

	public void setRubin(String rubin) {
		this.rubin = rubin;
		// Pre-calculate the grid boundaries
		Coordinates c = new Coordinates(0,0);
		c.setFromRUBIN(rubin, true);

		// A standard RUBIN square is 5000m x 5000m
		this.westBoundary = c.getEast() - 2500;
		this.northBoundary = c.getNorth() + 2500;
	}
	
	public Point getMiddle() {
		Coordinates c = new Coordinates(0,0);
		c.setFromRUBIN(rubin, true);
		return new Point((int) c.getEast(), (int) c.getNorth());
	}
	
	@Override
	public void setColor(Color c) {
		this.color = c;
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
	public void setMinZoomL(int zoomLevel) {
	}

	@Override
	public void setMaxZoomL(int zoomLevel) {
	}

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		return true;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		if (hidden) return;

		g2d.setColor(color);
		Stroke originalStroke = g2d.getStroke();
		g2d.setStroke(new BasicStroke(2));

		// Convert map coordinates to screen coordinates
		int x = (int) ((westBoundary * xScale) + xShift);
		int y = (int) ((northBoundary * yScale) + yShift);

		// Calculate width and height in pixels
		int w = (int) (5000 * xScale);
		int h = (int) Math.abs(5000 * yScale); // Height must be positive for drawRect

		// If your map Y is inverted (North is up),
		// the northBoundary is actually the top (smallest Y in screen space)
		g2d.drawRect(x, y, w, h);
		g2d.setStroke(originalStroke);
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
	public void setCRS(CoordSystem cs) {
		this.cs = cs;
	}

	@Override
	public CoordSystem getCRS() {
		return cs;
	}
}
