package main.core;

import main.coords.*;
import main.geometry.BoundingBox;
import main.layers.H2TableLayer;
import main.layers.MYSQLTableLayer;
import main.layers.TNGPolygonFileLayer;
import main.layers.TopowebLayer;

import java.awt.*;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import javax.swing.*;

public class Canvas extends JPanel {
	@Serial
	private static final long serialVersionUID = 1L;
	private final CoordSystem cs;
	BoundingBox bounds;
	Coordinate coord;
	private final ArrayList<Layer> layers;
	
	public Canvas() {
		cs = CoordSystem.SWEREF99TM;
		
		// Sverige Sweref99TM
		int xMin = 194181;
		int xMax = 812496;
		int yMin = 6113836;
		int yMax = 7700000;
		bounds = new BoundingBox(xMin,yMin,xMax,yMax);
		coord = null;
		layers = new ArrayList<Layer>();
		initialize();
	}

	private void initialize() {
		try {
			TNGPolygonFileLayer prFile = new TNGPolygonFileLayer("provinserSWEREF99TM.tng");
			prFile.setColor(Color.BLACK);
			prFile.setName("provinser");
			addLayerBottom(prFile);

			TNGPolygonFileLayer socFile = new TNGPolygonFileLayer("socknarSWEREF99TM.tng");
			socFile.setColor(Color.RED);
			socFile.setName("socknar");
			socFile.setHidden(false);
			addLayerBottom(socFile);

			H2TableLayer od = new H2TableLayer("ortnamnSWTM");
			od.setColor(Color.BLACK);
			od.setName("Ortnamnsdb");
			od.setHidden(false);
			od.setMaxZoomL(5);
			addLayerBottom(od);

			MYSQLTableLayer md = new MYSQLTableLayer();
			md.setColor(Color.BLACK);
			md.setName("LokalDB");
			md.setHidden(false);
			md.setMaxZoomL(40);
			md.setRepaintCallback(() -> {
				// Force the map to redraw on the Swing thread when data arrives
				SwingUtilities.invokeLater(() -> this.repaint());
			});
			addLayerBottom(md);

			TopowebLayer tb = new TopowebLayer(this);
			tb.setName("TopoWeb");
			tb.setHidden(false);
			addLayerBottom(tb);

		} catch (IOException e) {
			e.printStackTrace();
		}
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
	
	public void addLayerBottom(Layer l) {
		layers.addFirst(l);
		repaint();
	}
	
	public void addLayerTop(Layer l) {
		layers.add(l);
		repaint();
	}

	public void delLayer(String name) {
		layers.removeIf(l -> l != null && name.equals(l.getName()));
	}
	
	public Layer getLayer(String name) {
		for(Layer l: layers) {
			if (l.getName().equals(name)) {
				return l;
			}
		}
		return null;
	}
	
	public ArrayList<Layer> getLayers() {
		return layers;
	}

	public void zoom(double step) {
		Point middle = bounds.getMidlePoint();
		double halfW = (bounds.getWidth() * step) / 2.0;
		double halfH = (bounds.getHeight() * step) / 2.0;

		int xMin = (int) (middle.getX() - halfW);
		int xMax = (int) (middle.getX() + halfW);
		int yMin = (int) (middle.getY() - halfH);
		int yMax = (int) (middle.getY() + halfH);

		bounds = new BoundingBox(xMin, yMin, xMax, yMax);
		repaint();
	}
	
	public void setBounds(BoundingBox b) {
		bounds = b;
		repaint();
	}
	
	public BoundingBox getBoundingBox() {
		return bounds;
	}

	public void panPixel(int dx, int dy) {
		Dimension size = getSize();
		if (size.width <= 0 || size.height <= 0 || bounds == null) return;

		// Calculate the EXACT same scale used in paintComponent
		double rawXScale = size.width / (double)bounds.getWidth();
		double rawYScale = size.height / (double)bounds.getHeight();
		double uniformScale = Math.min(rawXScale, rawYScale);

		// Convert pixel movement to map meters
		// We divide by the scale. If scale is 0.001 px/m, 10px = 10,000m.
		double meterDX = dx / uniformScale;
		double meterDY = dy / uniformScale;

		// Shift the bounds
		// To pan the map "with" the mouse, we subtract the meter delta
		int xMin = (int) (bounds.getX1() - meterDX);
		int xMax = (int) (bounds.getX2() - meterDX);

		// Since Swing Y is down and Map Y is up, dragging "down" (positive dy)
		// means we want to see higher Y coordinates (North). So we ADD dy.
		int yMin = (int) (bounds.getY1() + meterDY);
		int yMax = (int) (bounds.getY2() + meterDY);

		bounds = new BoundingBox(xMin, yMin, xMax, yMax);
		repaint();
	}
	
	public void focus(Coordinate coord) {
		Point p = coord.getPoint();
		bounds.focus(p);
		repaint();
	}

	// translates p from pixels to world coordinates
	public Coordinate translatePoint(Point p) {
		Dimension size = getSize();
		if (size.width <= 0 || size.height <= 0 || bounds == null) return null;

		double rawXScale = size.width / (double) bounds.getWidth();
		double rawYScale = size.height / (double) bounds.getHeight();
		double scale = Math.min(rawXScale, rawYScale);

		Point m = bounds.getMidlePoint();
		double xShift = (size.width / 2.0) - (m.getX() * scale);
		double yShift = (size.height / 2.0) - (m.getY() * -scale);

		// Inverse transform (Pixels back to Meters)
		int x = (int) ((p.getX() - xShift) / scale);
		int y = (int) ((p.getY() - yShift) / -scale);

		return new Coordinate(y, x);
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
		Point m = bounds.getMidlePoint();

		BoundingBox drawBounds = new BoundingBox(
				(int)(m.getX() - drawWidth / 2.0),
				(int)(m.getY() - drawHeight / 2.0),
				(int)(m.getX() + drawWidth / 2.0),
				(int)(m.getY() + drawHeight / 2.0)
		);

		// Calculate Shifts to center the map in the window
		double xShift = (size.width / 2.0) - (m.getX() * scale);
		double yShift = (size.height / 2.0) - (m.getY() * -scale);

		// zoomL is meters per pixel (approximate)
		int zoomL = (int) (1.0 / scale);

		// Draw Layers using the LOCAL drawBounds
		synchronized (layers) {
			for (Layer l : layers) {
				if (!l.isHidden() && l.isInZoomLevel(zoomL)) {
					try {
						g2d.setColor(l.getColor());
						// Pass drawBounds here so Topoweb knows exactly which tiles to fetch
						l.draw(g2d, xShift, scale, yShift, -scale, drawBounds);
					} catch (Exception e) {
						System.err.println("Error drawing layer: " + l.getName());
					}
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