import geometry.BoundingBox;
import geometry.Point;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;

public class Distance implements Layer {
	private Color color;
	private String name;
	private final String direction;
	private boolean hidden;
	private final Point c;
	private final int dist;
	private CoordSystem cs;

	Distance(String name, Point c, int dist, String direction, CoordSystem cs) {
		this.name = name;
		this.c=c;
		this.dist = dist;
		this.direction = direction;
		this.color = Color.orange;
		hidden = false;
		this.cs = cs;
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
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setMaxZoomL(int zoomLevel) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale,
	                 double yShift, double yScale, BoundingBox bounds) {
		if (hidden) return;

		g2d.setColor(color);
		Stroke originalStroke = g2d.getStroke();
		g2d.setStroke(new BasicStroke(2));

		// Calculate screen start point
		int x1 = (int) ((c.getX() * xScale) + xShift);
		int y1 = (int) ((c.getY() * yScale) + yShift);

		// Convert distance to screen pixels
		double ds = dist * xScale;

		// Get angle based on direction string
		double angleDegrees = getAngleFromDirection(direction);
		double angleRadians = Math.toRadians(angleDegrees);

		// Standard Trig: X uses Cos, Y uses Sin
		// Note: We subtract Sin because Y-axis is inverted in Swing
		int x2 = x1 + (int) (ds * Math.cos(angleRadians));
		int y2 = y1 - (int) (ds * Math.sin(angleRadians));

		g2d.drawLine(x1, y1, x2, y2);

		// Optional: Draw a small cross or circle at the end point
		g2d.drawOval(x2-2, y2-2, 4, 4);

		g2d.setStroke(originalStroke);
	}

	private double getAngleFromDirection(String dir) {
		return switch (dir) {
			case "E"   -> 0.0;
			case "ENE" -> 22.5;
			case "NE"  -> 45.0;
			case "NNE" -> 67.5;
			case "N"   -> 90.0;
			case "NNW" -> 112.5;
			case "NW"  -> 135.0;
			case "WNW" -> 157.5;
			case "W"   -> 180.0;
			case "WSW" -> 202.5;
			case "SW"  -> 225.0;
			case "SSW" -> 247.5;
			case "S"   -> 270.0;
			case "SSE" -> 292.5;
			case "SE"  -> 315.0;
			case "ESE" -> 337.5;
			default    -> 0.0;
		};
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