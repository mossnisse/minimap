package main.layers;

import main.core.Layer;
import main.geometry.BoundingBox;
import main.coords.*;
import java.awt.*;
import java.io.File;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class GPXFileLayer implements Layer {
	private CoordSystem cs = CoordSystem.SWEREF99TM;
	private Color color = Color.BLACK;
	private int maxZoom = 0; // 0 indicates unset
	private int minZoom = 0;
	private String name;
	private final String fileName;
	private GPXCoordinate[] coordinates = new GPXCoordinate[0];
	private boolean hidden;
	private static final Stroke LINE_STROKE = new BasicStroke(2);

	public static class GPXCoordinate {
		double latitude, longitude, elevation;
		String dateTime, name;
		Point projectedPoint; // Store the result here!
	}

	public GPXFileLayer(String fileName) throws Exception {
		this.fileName = fileName;
		this.name = fileName;
		readFile();
	}

	private void readFile() throws Exception {
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
		Document doc = docBuilder.parse(new File(fileName));
		NodeList waypoints = doc.getElementsByTagName("wpt");

		coordinates = new GPXCoordinate[waypoints.getLength()];

		for (int s = 0; s < waypoints.getLength(); s++) {
			Element el = (Element) waypoints.item(s);
			GPXCoordinate k = new GPXCoordinate();

			k.latitude = Double.parseDouble(el.getAttribute("lat"));
			k.longitude = Double.parseDouble(el.getAttribute("lon"));

			// Safe tag reading helper
			k.elevation = getSafeTagDouble(el, "ele");
			k.dateTime = getSafeTagString(el, "time");
			k.name = getSafeTagString(el, "name");

			// PRE-PROJECT the point so draw() is fast
			Coordinate wgs = new Coordinate(k.latitude, k.longitude);
			k.projectedPoint = CoordSystem.SWEREF99TM.toProjected(wgs).getPoint();

			coordinates[s] = k;
		}
	}

	private String getSafeTagString(Element el, String tag) {
		NodeList nl = el.getElementsByTagName(tag);
		return (nl.getLength() > 0) ? nl.item(0).getTextContent() : "";
	}

	private double getSafeTagDouble(Element el, String tag) {
		NodeList nl = el.getElementsByTagName(tag);
		return (nl.getLength() > 0) ? Double.parseDouble(nl.item(0).getTextContent()) : 0.0;
	}

	public GPXCoordinate[] getCoordinates() {
		return coordinates;
	}

	@Override
	public void setColor(Color color) {
		this.color = (color != null) ? color : Color.BLACK;
	}

	@Override
	public Color getColor() { return color; }

	@Override
	public String getName() { return name; }

	@Override
	public void setName(String name) { this.name=name; }

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		if (!hidden) {
			g2d.setColor(color);
			Stroke originalStroke = g2d.getStroke();
			g2d.setStroke(LINE_STROKE);
			for(GPXCoordinate coord : coordinates) {
				// Using the pre-calculated point is much faster!
				int x = (int) ((coord.projectedPoint.x * xScale) + xShift);
				int y = (int) ((coord.projectedPoint.y * yScale) + yShift);
				g2d.drawOval(x - 8, y - 8, 16, 16); // Center the oval on the point
				g2d.drawLine(x - 10, y - 10,  x + 10, y + 10);
				g2d.drawLine(x - 10, y + 10,  x + 10, y - 10);
			}
			g2d.setStroke(originalStroke);
		}
	}

	@Override
	public void setMinZoomL(int zoomLevel) { this.minZoom = zoomLevel; }

	@Override
	public void setMaxZoomL(int zoomLevel) { this.maxZoom = zoomLevel; }

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		boolean meetsMin = (minZoom == 0 || zoomLevel >= minZoom);
		boolean meetsMax = (maxZoom == 0 || zoomLevel <= maxZoom);
		return meetsMin && meetsMax;
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