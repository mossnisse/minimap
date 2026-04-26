package main.core;

import java.awt.Color;
import java.awt.Graphics2D;

import main.coords.*;
import main.geometry.BoundingBox;

public interface Layer {
	String getName();
	void setName(String name);
	Color getColor();
	void setColor(Color c);
	boolean isHidden();
	void setHidden(boolean hidden);
	CoordSystem getCRS();
	void setCRS(CoordSystem cs);
	void setMinZoomL(int zoomLevel);
	void setMaxZoomL(int zoomLevel);
	boolean isInZoomLevel(int zoomLevel);
	Extent getBoundaries();
	void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) throws Exception;
}
