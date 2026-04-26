package main.core;

import main.coords.*;
import main.dialogs.DistanceTool;
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
	Extent bounds;
	Coordinate coord;
	private final ArrayList<Layer> layers;
	
	public Canvas() {
		cs = CoordSystem.SWEREF99TM;
		bounds = cs.getBoundaries();
		coord = null;
		layers = new ArrayList<Layer>();

		DistanceTool tool = new DistanceTool(this);
		addMouseListener(tool);
		initialize();
	}

	private void initialize() {
		try {
			TopowebLayer tb = new TopowebLayer(this);
			tb.setName("TopoWeb");
			tb.setHidden(false);
			addLayerBottom(tb);

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

			H2TableLayer od = new H2TableLayer("ortnamnSWTM");
			od.setColor(Color.BLACK);
			od.setName("Ortnamnsdb");
			od.setHidden(false);
			od.setMaxZoomL(5);
			addLayerBottom(od);

			TNGPolygonFileLayer socFile = new TNGPolygonFileLayer("socknarSWEREF99TM.tng");
			socFile.setColor(Color.RED);
			socFile.setName("socknar");
			socFile.setHidden(false);
			addLayerBottom(socFile);

			TNGPolygonFileLayer prFile = new TNGPolygonFileLayer("provinserSWEREF99TM.tng");
			prFile.setColor(Color.BLACK);
			prFile.setName("provinser");
			addLayerBottom(prFile);

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
		synchronized (layers) {
			layers.addFirst(l);
		}
		repaint();
	}
	
	public void addLayerTop(Layer l) {
		synchronized (layers) {
			layers.add(l);
		}
		repaint();
	}

	public void delLayer(String name) {
		synchronized (layers) {
			layers.removeIf(l -> l != null && name.equals(l.getName()));
		}
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
		int xMin = (int) (bounds.c1.getEast() - meterDX);
		int xMax = (int) (bounds.c2.getEast() - meterDX);

		// Since Swing Y is down and Map Y is up, dragging "down" (positive dy)
		// means we want to see higher Y coordinates (North). So we ADD dy.
		int yMin = (int) (bounds.c1.getNorth() + meterDY);
		int yMax = (int) (bounds.c2.getNorth() + meterDY);

		bounds = new Extent(yMin, xMin, yMax, xMax);
		repaint();
	}
	
	public void focus(Coordinate coord) {
		bounds.focus(coord);
		repaint();
	}

	public Coordinate translatePoint(Point p) {
		Dimension size = getSize();
		if (size.width <= 0 || size.height <= 0 || bounds == null) return null;

		double scale = Math.min(size.width / (double)bounds.getWidth(), size.height / (double)bounds.getHeight());
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

		double scale = Math.min(size.width / (double)bounds.getWidth(), size.height / (double)bounds.getHeight());
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

		BoundingBox drawBounds = new BoundingBox(
				(int)(m.getEast() - drawWidth / 2.0),
				(int)(m.getNorth() - drawHeight / 2.0),
				(int)(m.getEast() + drawWidth / 2.0),
				(int)(m.getNorth() + drawHeight / 2.0)
		);

		// Calculate Shifts to center the map in the window
		double xShift = (size.width / 2.0) - (m.getEast() * scale);
		double yShift = (size.height / 2.0) - (m.getNorth() * -scale);

		// zoomL is meters per pixel (approximate)
		int zoomL = (int) (1.0 / scale);

		// Draw Layers using the LOCAL drawBounds
		synchronized (layers) {
			for (int i = layers.size() - 1; i >= 0; i--) {
				Layer l = layers.get(i);
				if (!l.isHidden() && l.isInZoomLevel(zoomL)) {
					try {
						//g2d.setColor(l.getColor());
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