import coords.*;
import geometry.BoundingBox;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class RubinLayer implements Layer {
	private String name, rubin;
	private Color color;
	private boolean hidden;
	private CoordSystem cs;

	// Store corners in projected (Sweref) coordinates
	private List<Point> swerefCorners = new ArrayList<>();

	public RubinLayer(String rubin, String name, Color c) {
		this.name = name;
		this.color = c;
		setRubin(rubin);
	}

	public void setRubin(String rubin) {
		this.rubin = rubin;
		this.swerefCorners.clear();

		Coordinates coordTool = new Coordinates(0, 0);
		int[][] rt90Corners = coordTool.getRUBINCorners(rubin);

		if (rt90Corners != null) {
			for (int[] corner : rt90Corners) {
				// Set current corner in RT90
				coordTool.set(corner[0], corner[1]);

				// Convert RT90 -> WGS84 -> SWEREF99TM
				Coordinates sweref = coordTool.toWGS84(CoordSystem.RT90)
						.toProjected(CoordSystem.SWEREF99TM);

				// Store the Sweref coordinates
				swerefCorners.add(new Point((int)sweref.getEast(), (int)sweref.getNorth()));
			}
		}
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		if (hidden || swerefCorners.size() < 4) return;

		g2d.setColor(color);
		Stroke originalStroke = g2d.getStroke();
		g2d.setStroke(new BasicStroke(2));

		// Convert the 4 Sweref corners to screen pixel paths
		int[] xPoints = new int[4];
		int[] yPoints = new int[4];

		for (int i = 0; i < 4; i++) {
			Point pt = swerefCorners.get(i);
			xPoints[i] = (int) ((pt.x * xScale) + xShift);
			yPoints[i] = (int) ((pt.y * yScale) + yShift);
		}

		// Use drawPolygon instead of drawRect
		// This handles cases where the grid might be slightly rotated/skewed after conversion
		g2d.drawPolygon(xPoints, yPoints, 4);

		g2d.setStroke(originalStroke);
	}

	public Point getMiddle() {
		Coordinates c = new Coordinates(0,0);
		c.setFromRUBIN(rubin, true); // true converts to Sweref inside the method
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
	public void setMinZoomL(int zoomLevel) {}

	@Override
	public void setMaxZoomL(int zoomLevel) {}

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		return true;
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
}
