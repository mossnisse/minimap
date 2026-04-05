import coords.*;
import geometry.BoundingBox;
import java.awt.Color;
import java.awt.Graphics2D;

public interface Layer {
	void setColor(Color c);
	Color getColor();
	String getName();
	void setMinZoomL(int zoomLevel);
	void setMaxZoomL(int zoomLevel);
	boolean isInZoomLevel(int zoomLevel);
	void setName(String name);
	void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) throws Exception;
	boolean isHidden();
	void setHidden(boolean hidden);
	void setCRS(CoordSystem cs);
	CoordSystem getCRS();
}
