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

public class GPXFileLayer implements Layer{
	private final CoordSystem cs = CoordSystem.RT90;
	
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
	
	/*
	public static void main(String[] args) {
		GPXFile file;
		try {
			file = new GPXFile("Waypoints_12-APR-13.gpx");
			//GPXKoordinat[] k = file.getKoordinates();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}*/

	@Override
	public void setColor(Color c) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Color getColor() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return name;
	}

	@Override
	public void setName(String name) {
		this.name=name;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		if (!hidden) {
			for(GPXKoordinat koord:koordinates) {
				Coordinates c = new Coordinates(koord.latitude, koord.longitude);
				Coordinates rt90 = c.toProjected(CoordSystem.RT90);
				Point l = new Point((int)Math.round(rt90.getEast()), (int)Math.round(rt90.getNorth()));
				int x = (int) ((l.getY()*xScale)+xShift);
				int y = (int) ((l.getX()*yScale)+yShift);
					g2d.drawOval(x, y, 5, 5);
					System.out.println("name:" + koord.name +" long: "+l.getY()+" lat: "+l.getX()+" x:"+x+" y:"+y);
			}
		}
	}

	@Override
	public void setMinZoomL(int zoomLevel) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setMaxZoomL(int zoomLevel) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		// TODO Auto-generated method stub
		return true;
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
	public void setCRS(CoordSystem cs) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public CoordSystem getCRS() {
		return cs;
	}
}