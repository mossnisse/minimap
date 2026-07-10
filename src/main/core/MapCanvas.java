package main.core;

import main.coords.*;
import main.geometry.Extent;

import java.awt.*;
import java.io.Serial;
import javax.swing.*;

public class MapCanvas extends JPanel {
	@Serial
	private static final long serialVersionUID = 1L;
	private CoordSystem cs;
	private Extent bounds;
	private volatile Coordinate coord;
	private final LayerManager layerManager;

	
	public MapCanvas() {
		cs = CoordSystem.SWEREF99TM;
		bounds = cs.getBoundaries();
		coord = null;
		layerManager = new LayerManager(this);

		/*
		Graphics2D g2d = (Graphics2D) this.getGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		*/
	}

	public void setCoordinate(Coordinate c) {
		coord = c;
		repaint();
	}
	
	public LayerManager getLayerManager() {
		return layerManager;
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

	/**
	 * The uniform world->screen mapping for the current bounds and panel size:
	 * screenX = east * scale + xShift, screenY = north * -scale + yShift.
	 */
	private record ViewTransform(double scale, double xShift, double yShift) {
		Point toScreen(Coordinate c) {
			return new Point((int) (c.getEast() * scale + xShift),
					(int) (c.getNorth() * -scale + yShift));
		}
	}

	/** Returns the view transform for the given panel size, or null if it can't be computed. */
	private ViewTransform viewTransform(Dimension size) {
		if (size.width <= 0 || size.height <= 0 || bounds == null) return null;
		double scale = Math.min(size.width / bounds.getWidth(), size.height / bounds.getHeight());
		Coordinate m = bounds.getMidlePoint();
		double xShift = (size.width / 2.0) - (m.getEast() * scale);
		double yShift = (size.height / 2.0) - (m.getNorth() * -scale);
		return new ViewTransform(scale, xShift, yShift);
	}

	public Coordinate translatePoint(Point p) {
		ViewTransform t = viewTransform(getSize());
		if (t == null) return null;

		// Inverse of ViewTransform.toScreen
		double east = (p.x - t.xShift()) / t.scale();
		double north = (p.y - t.yShift()) / -t.scale();

		return new Coordinate(north, east);
	}

	public Point toScreenSpace(Coordinate c) {
		ViewTransform t = viewTransform(getSize());
		return (t != null) ? t.toScreen(c) : new Point(0, 0);
	}

	public CoordSystem getCRS() {
		return cs;
	}

	public void setCRS(CoordSystem newCs) {
		// Reproject the current view and marker so switching CRS keeps showing the same place
		if (newCs != cs) {
			if (bounds != null) bounds = bounds.convertCRS(cs, newCs);
			if (coord != null) coord = cs.convertTo(coord, newCs);
		}
		this.cs = newCs;
		layerManager.invalidateCache();
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D) g;
		Dimension size = getSize();

		// Safety check for invisible components
		ViewTransform t = viewTransform(size);
		if (t == null) return;

		double scale = t.scale();
		double xShift = t.xShift();
		double yShift = t.yShift();

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
					e.printStackTrace();
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