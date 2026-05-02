package main.core;

import main.coords.*;
import main.dialogs.DistanceTool;
import main.geometry.Extent;

import java.awt.*;
import java.io.Serial;
import javax.swing.*;

public class Canvas extends JPanel {
	@Serial
	private static final long serialVersionUID = 1L;
	private CoordSystem cs;
	private Extent bounds;
	private volatile Coordinate coord;
	public final LayerManager layerManager;

	
	public Canvas() {
		cs = CoordSystem.SWEREF99TM;
		bounds = cs.getBoundaries();
		coord = null;
		layerManager = new LayerManager(this);
		DistanceTool tool = new DistanceTool(this);
		addMouseListener(tool);
	}

	// todo use the MYSQLTableLayer concurently with the UI thread. check for null?
	public void setCoordinate(Coordinate c) {
		coord = c;
		//MYSQLTableLayer ort = (MYSQLTableLayer) getLayer("LokalDB");
		//ort.selectNearest(p);
		repaint();
	}
	
	public Coordinate getCoordinate() {
		return coord;
	}
	
	public void hideCoordinate() {
		coord = null;
		repaint();
	}

	public void zoom(double step) {
		Coordinate middle = bounds.getMidlePoint();
		double halfW = (bounds.getWidth() * step) / 2.0;
		double halfH = (bounds.getHeight() * step) / 2.0;

		double xMin = middle.getEast() - halfW;
		double xMax = middle.getEast() + halfW;
		double yMin = middle.getNorth() - halfH;
		double yMax = middle.getNorth() + halfH;

		bounds = new Extent(yMin, xMin, yMax, xMax);
		repaint();
	}
	
	public void setBounds(Extent b) {
		bounds = b;
		repaint();
	}
	
	public Extent getBoundingBox() {
		return bounds;
	}

	public void panPixel(int dx, int dy) {
		Dimension size = getSize();
		if (size.width <= 0 || size.height <= 0 || bounds == null) return;

		double scale = Math.min(size.width / bounds.getWidth(), size.height / bounds.getHeight());

		// Perform calculations in double
		double meterDX = dx / scale;
		double meterDY = dy / scale;

		this.bounds = new Extent(
				bounds.c1.getNorth() + meterDY,
				bounds.c1.getEast() - meterDX,
				bounds.c2.getNorth() + meterDY,
				bounds.c2.getEast() - meterDX
		);
		repaint();
	}
	
	public void focus(Coordinate coord) {
		bounds.focus(coord);
		repaint();
	}

	public Coordinate translatePoint(Point p) {
		Dimension size = getSize();
		if (size.width <= 0 || size.height <= 0 || bounds == null) return null;

		double scale = Math.min(size.width / bounds.getWidth(), size.height / bounds.getHeight());
		Coordinate m = bounds.getMidlePoint();

		double xShift = (size.width / 2.0) - (m.getEast() * scale);
		double yShift = (size.height / 2.0) - (m.getNorth() * -scale);

		// Inverse of the logic above
		double east = (p.x - xShift) / scale;
		double north = (p.y - yShift) / -scale;

		return new Coordinate(north, east);
	}

	public Point toScreenSpace(Coordinate c) {
		Dimension size = getSize();
		if (size.width <= 0 || size.height <= 0 || bounds == null) return new Point(0,0);

		double scale = Math.min(size.width / bounds.getWidth(), size.height / bounds.getHeight());
		Coordinate m = bounds.getMidlePoint();

		// Use the exact same shift logic as paintComponent
		double xShift = (size.width / 2.0) - (m.getEast() * scale);
		double yShift = (size.height / 2.0) - (m.getNorth() * -scale);

		int x = (int) (c.getEast() * scale + xShift);
		int y = (int) (c.getNorth() * -scale + yShift); // Note the -scale for Y

		return new Point(x, y);
	}

	public CoordSystem getCRS() {
		return cs;
	}

	public void setCRS(CoordSystem cs) {
		this.cs = cs;
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D) g;
		Dimension size = getSize();

		// Safety check for invisible components
		if (size.width <= 0 || size.height <= 0 || bounds == null) return;

		double h = bounds.getHeight();
		double w = bounds.getWidth();

		// Calculate the scales for both axes
		double rawXScale = size.width / w;
		double rawYScale = size.height / h;

		// Pick the uniform scale (ensures 1m X = 1m Y)
		double scale = Math.min(rawXScale, rawYScale);

		// Create "Draw Bounds" (The actual area visible in the window)
		double drawWidth = size.width / scale;
		double drawHeight = size.height / scale;
		Coordinate m = bounds.getMidlePoint();

		Extent drawBounds = new Extent(
				m.getNorth() - drawHeight / 2.0,
				m.getEast() - drawWidth / 2.0,
				m.getNorth() + drawHeight / 2.0,
				m.getEast() + drawWidth / 2.0
		);

		// Calculate Shifts to center the map in the window
		double xShift = (size.width / 2.0) - (m.getEast() * scale);
		double yShift = (size.height / 2.0) - (m.getNorth() * -scale);

		// zoomL is meters per pixel (approximate)
		int zoomL = (int) (1.0 / scale);

		// Draw Layers using the LOCAL drawBounds
		//ArrayList<Layer> layers = layerManager.getLayers();
		for (Layer l : layerManager.getLayers()) {
			if (!l.isHidden() && l.isInZoomLevel(zoomL)) {
				try {
					l.draw(g2d, xShift, scale, yShift, -scale, drawBounds);
				} catch (Exception e) {
					System.err.println("Error drawing layer: " + l.getName());
				}
			}
		}

		//Draw the Marker
		if (coord != null) {
			g2d.setColor(Color.RED);
			int x = (int) (coord.getEast() * scale + xShift);
			int y = (int) (coord.getNorth() * -scale + yShift);
			g2d.drawOval(x - 10, y - 10, 20, 20);
			g2d.drawLine(x - 10, y - 10, x + 10, y + 10);
			g2d.drawLine(x - 10, y + 10, x + 10, y - 10);
		}
	}
}