package layers;

import core.Layer;
import geometry.BoundingBox;
import coords.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class GPXFileLayer implements Layer {
	private CoordSystem cs = CoordSystem.RT90;
	private Color color = Color.BLACK;
	private int maxZoom = 0; // 0 indicates unset
	private int minZoom = 0;
	
	public class GPXKoordinat {
		double latitude, longitude, elevation;
		String dateTime, name;
	}

	private String fileName, name;
	private GPXKoordinat[] koordinates;
	private boolean hidden;

	public GPXFileLayer(String fileName) throws IOException, ParserConfigurationException, SAXException {
		this.fileName = fileName;
		this.name = fileName;
		readFile();
	}

	private void readFile() throws IOException, ParserConfigurationException, SAXException {
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
		Document doc = docBuilder.parse(new File(fileName));
		doc.getDocumentElement().normalize();
		NodeList waypoints = doc.getElementsByTagName("wpt");
		koordinates = new GPXKoordinat[waypoints.getLength()];
		for (int s = 0; s < waypoints.getLength(); s++) {
			Node waypoint = waypoints.item(s);
			Element waypointElement = (Element) waypoint;
			koordinates[s] = new GPXKoordinat();
			koordinates[s].latitude = Double.valueOf(waypointElement.getAttribute("lat"));
			koordinates[s].longitude = Double.valueOf(waypointElement.getAttribute("lon"));
			koordinates[s].elevation = Double.valueOf(waypointElement.getElementsByTagName("ele").item(0).getTextContent());
			koordinates[s].dateTime = waypointElement.getElementsByTagName("time").item(0).getTextContent();
			koordinates[s].name = waypointElement.getElementsByTagName("name").item(0).getTextContent();
		}
	}

	public GPXKoordinat[] getKoordinates() {
		return koordinates;
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
	public void setName(String name) {
		this.name=name;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		if (!hidden) {
			g2d.setColor(color);
			for(GPXKoordinat koord:koordinates) {
				Coordinate sweref = CoordSystem.SWEREF99TM.toProjected(koord.latitude, koord.longitude);
				Point l = sweref.getPoint();
				int x = (int) ((l.getY()*xScale)+xShift);
				int y = (int) ((l.getX()*yScale)+yShift);
					g2d.drawOval(x, y, 5, 5);
					//System.out.println("name:" + koord.name +" long: "+l.getY()+" lat: "+l.getX()+" x:"+x+" y:"+y);
			}
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
	public boolean isHidden() {
		return hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	@Override
	public void setCRS(CoordSystem cs) { this.cs = cs; }

	@Override
	public CoordSystem getCRS() {
		return cs;
	}
}