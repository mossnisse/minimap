import geometry.BoundingBox;

import java.awt.Color;
import java.awt.Graphics2D;

public interface Layer {
	public void setColor(Color c);
	public Color getColor();
	public String getName();
	public void setMinZoomL(int zoomLevel);
	public void setMaxZoomL(int zoomLevel);
	public boolean isInZoomLevel(int zoomLevel);
	public void setName(String name);
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) throws Exception;
	public boolean isHidden();
	public void setHidden(boolean hidden);
	public void setCRS(CoordSystem cs);
	public CoordSystem getCRS();
}
