package gis.layers;

import gis.core.MapCanvas;
import gis.core.Layer;
import gis.coords.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

import gis.geometry.Extent;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class GPXFileLayer extends Layer {
	private final String fileName;
	private GPXCoordinate[] coordinates = new GPXCoordinate[0];
	private static final Stroke LINE_STROKE = new BasicStroke(2);
	private final MapCanvas mapCanvas;
	private Extent cachedExtent = null;

	public static class GPXCoordinate {
		double latitude, longitude, elevation;
		String dateTime, name;
		Coordinate projectedPoint; // Store the result here!
	}

	public GPXFileLayer(String fileName, MapCanvas mapCanvas) throws IOException {
		super(fileName, false, CoordSystem.WGS84);
		this.fileName = fileName;
		this.mapCanvas = mapCanvas;
		readFile();
	}

	private void readFile() throws IOException {
		try {
			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			docBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			docBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			docBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			docBuilderFactory.setXIncludeAware(false);
			docBuilderFactory.setExpandEntityReferences(false);
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(new File(fileName));
			NodeList waypoints = doc.getElementsByTagName("wpt");

			GPXCoordinate[] parsed = new GPXCoordinate[waypoints.getLength()];

			for (int s = 0; s < waypoints.getLength(); s++) {
				Element el = (Element) waypoints.item(s);
				GPXCoordinate k = new GPXCoordinate();

				k.latitude = Double.parseDouble(el.getAttribute("lat"));
				k.longitude = Double.parseDouble(el.getAttribute("lon"));

				// Safe tag reading helper
				k.elevation = getSafeTagDouble(el, "ele");
				k.dateTime = getSafeTagString(el, "time");
				k.name = getSafeTagString(el, "name");

				parsed[s] = k;
			}

			projectCoordinates(parsed);
			coordinates = parsed; // Publish only after the complete file succeeded.
			cachedExtent = calculateExtent(parsed);
		} catch(Exception e) {
			if (e instanceof IOException io) throw io;
			throw new IOException("Invalid GPX file " + fileName + ": " + e.getMessage(), e);
		}
	}

	private void projectCoordinates(GPXCoordinate[] points) {
		for (GPXCoordinate point : points) {
			Coordinate wgs = new Coordinate(point.latitude, point.longitude);
			point.projectedPoint = getCRS().convertTo(wgs, mapCanvas.getCRS());
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

	public String getSourcePath() {
		return fileName;
	}

	private Extent calculateExtent(GPXCoordinate[] points) {
		// If there are no coordinates, we can't define a boundary
		if (points.length == 0) {
			return null;
		}

		// Initialize with the values from the first coordinate
		double minE = points[0].projectedPoint.getEast();
		double maxE = points[0].projectedPoint.getEast();
		double minN = points[0].projectedPoint.getNorth();
		double maxN = points[0].projectedPoint.getNorth();

		// Iterate through all coordinates to expand the boundaries
		for (GPXCoordinate coord : points) {
			if (coord.projectedPoint.getEast() < minE) minE = coord.projectedPoint.getEast();
			if (coord.projectedPoint.getEast() > maxE) maxE = coord.projectedPoint.getEast();
			if (coord.projectedPoint.getNorth() < minN) minN = coord.projectedPoint.getNorth();
			if (coord.projectedPoint.getNorth() > maxN) maxN = coord.projectedPoint.getNorth();
		}

		// Return a new BoundingBox representing the full extent of the GPX data
		return new Extent(minN, minE, maxN, maxE);
	}

	@Override
	public Extent getBoundaries() {
		return cachedExtent;
	}

	@Override
	public void invalidateCache() {
		projectCoordinates(coordinates);
		cachedExtent = calculateExtent(coordinates);
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, Extent bounds) {
		if (!isHidden()) {
			g2d.setColor(getColor());
			Stroke originalStroke = g2d.getStroke();
			g2d.setStroke(LINE_STROKE);
			for(GPXCoordinate coord : coordinates) {
				// Using the pre-calculated point is much faster!
				int x = (int) ((coord.projectedPoint.getEast() * xScale) + xShift);
				int y = (int) ((coord.projectedPoint.getNorth() * yScale) + yShift);
				g2d.drawOval(x - 8, y - 8, 16, 16); // Center the oval on the point
				g2d.drawLine(x - 10, y - 10,  x + 10, y + 10);
				g2d.drawLine(x - 10, y + 10,  x + 10, y - 10);
			}
			g2d.setStroke(originalStroke);
		}
	}
}
