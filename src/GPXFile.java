import geometry.BoundingBox;
import geometry.Point;

import java.awt.Color;
import java.awt.Graphics2D;
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


public class GPXFile implements Layer{
	
	public class GPXKoordinat {
		double latitude, longitude, elevation;
		String dateTime, name;
		
		private Double atanh(Double z) {
			return Math.log((1+z)/(1-z))/2;
		}
		
		public Point convertRT90() {
			Double k0xa = 6.3674848719179137e6;
			Double FN = -667.711;
			Double FE = 1.500064274e6;
			Double A = 0.006694380021;
			Double B = 0.00003729560209;
			Double C = 2.592527517e-7;
			Double Dp = 1.971698945e-9;
			Double lambdanoll = 0.27587170754507245;
			Double beta1 = 0.0008377318249;
			Double beta2 = 7.608527793e-7;
			Double beta3 = 1.197638020e-9;
			Double beta4 = 2.443376245e-12;
			
			Double Phi = (latitude/180)*Math.PI;
			Double deltalambda= (longitude/180)*Math.PI-lambdanoll;
			
			Double Phistar = Phi-Math.sin(Phi)*Math.cos(Phi)*(A+B*Math.sin(2*Phi)+C*Math.sin(4*Phi)+Dp*Math.sin(6*Phi));

			Double xifjutt = Math.atan(Math.tan(Phistar)/Math.cos(deltalambda));
			Double etafjutt = atanh(Math.cos(Phistar)*Math.sin(deltalambda));
					
			Double rnorth =  k0xa*(xifjutt+beta1*Math.sin(2*xifjutt)*Math.cosh(2*etafjutt)+beta2*Math.sin(4*xifjutt)*Math.cosh(4*etafjutt)
							+beta3*Math.sin(6*xifjutt)*Math.cosh(6*etafjutt)+beta4*Math.sin(8*xifjutt)*Math.cosh(8*etafjutt))+FN;
			
			Double reast = k0xa*(etafjutt+beta1*Math.cos(2*xifjutt)*Math.sinh(2*etafjutt)+beta2*Math.cos(4*xifjutt)*Math.sinh(4*etafjutt)
						+beta3*Math.cos(6*xifjutt)*Math.sinh(6*etafjutt)+beta4*Math.cos(8*xifjutt)*Math.sinh(8*etafjutt))+FE;
			return new Point((int) Math.round(rnorth), (int)Math.round(reast));
		}
	}

	private String fileName, name;
	private GPXKoordinat[] koordinates;
	private boolean hidden;

	public GPXFile(String fileName) throws IOException, ParserConfigurationException, SAXException {
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
				Point l = koord.convertRT90();
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
}