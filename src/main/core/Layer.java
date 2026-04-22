package main.core;

import java.awt.Color;
import java.awt.Graphics2D;

import main.coords.*;
import main.geometry.BoundingBox;

public interface Layer {
	void setColor(Color c);
	Color getColor();
	void setName(String name);
	String getName();
	void setMinZoomL(int zoomLevel);
	void setMaxZoomL(int zoomLevel);
	boolean isInZoomLevel(int zoomLevel);
	void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) throws Exception;
	boolean isHidden();
	void setHidden(boolean hidden);
	void setCRS(CoordSystem cs);
	CoordSystem getCRS();
}
