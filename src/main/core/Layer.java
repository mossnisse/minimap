package main.core;

import java.awt.Color;
import java.awt.Graphics2D;

import main.coords.*;
import main.geometry.BoundingBox;
import main.geometry.Extent;

public abstract class Layer {
	private String name;
	private Color color = Color.BLACK;
	private boolean hidden;
	private CoordSystem cs;
	private int maxZoom = 0; // 0 indicates unset
	private int minZoom = 0;

	public Layer(String name, Boolean hidden, CoordSystem cs) {
		this.name = name;
		this.hidden = hidden;
		this.cs = cs;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = (color != null) ? color : Color.BLACK;
	}

	public boolean isHidden() {
		return hidden;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public CoordSystem getCRS() {
		return cs;
	}

	public void setCRS(CoordSystem cs) {
		this.cs = cs;
	}

	public void setMinZoomL(int zoomLevel) { this.minZoom = zoomLevel; }

	public void setMaxZoomL(int zoomLevel) { this.maxZoom = zoomLevel; }

	public boolean isInZoomLevel(int zoomLevel) {
		boolean meetsMin = (minZoom == 0 || zoomLevel >= minZoom);
		boolean meetsMax = (maxZoom == 0 || zoomLevel <= maxZoom);
		return meetsMin && meetsMax;
	}

	public abstract Extent getBoundaries();

	public abstract void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) throws Exception;
}
