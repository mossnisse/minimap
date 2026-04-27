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

public class GPXFileLayer extends Layer {
	private final String fileName;
	private GPXCoordinate[] coordinates = new GPXCoordinate[0];
	private static final Stroke LINE_STROKE = new BasicStroke(2);

	public static class GPXCoordinate {
		double latitude, longitude, elevation;
		String dateTime, name;
		Point projectedPoint; // Store the result here!
	}

	public GPXFileLayer(String fileName) throws Exception {
		super(fileName, false, CoordSystem.SWEREF99TM);
		this.fileName = fileName;
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
	public Extent getBoundaries() {
		// If there are no coordinates, we can't define a boundary
		if (coordinates == null || coordinates.length == 0) {
			return null;
		}

		// Initialize with the values from the first coordinate
		double minE = coordinates[0].projectedPoint.x;
		double maxE = coordinates[0].projectedPoint.x;
		double minN = coordinates[0].projectedPoint.y;
		double maxN = coordinates[0].projectedPoint.y;

		// Iterate through all coordinates to expand the boundaries
		for (GPXCoordinate coord : coordinates) {
			if (coord.projectedPoint.x < minE) minE = coord.projectedPoint.x;
			if (coord.projectedPoint.x > maxE) maxE = coord.projectedPoint.x;
			if (coord.projectedPoint.y < minN) minN = coord.projectedPoint.y;
			if (coord.projectedPoint.y > maxN) maxN = coord.projectedPoint.y;
		}

		// Return a new BoundingBox representing the full extent of the GPX data
		return new Extent(minN, minE, maxN, maxE);
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		if (!isHidden()) {
			g2d.setColor(getColor());
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
}