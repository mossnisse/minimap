import geometry.Point;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class GPX {
	static TNGPointFile.Locality[] localities;
	static TNGPolygonFile.Province[] provinces;
	static TNGPolygonFile.Province[] district;
	private static boolean wPressed = false;
	
	
	public static TNGPointFile.Locality NearestLoc(int x, int y) {
		double shortest = 1E16;
		TNGPointFile.Locality nearest = null;
		for (TNGPointFile.Locality lokal : localities) {
			double distp = Math.pow(x - lokal.getX(), 2) + Math.pow(y - lokal.getY(), 2);
			if (distp < shortest) {
				shortest = distp;
				nearest = lokal;
			}

		}
		return nearest;
	}
	
	public static String inProvince(Point p) {
		for (TNGPolygonFile.Province pr: provinces) {
			if(pr.isInside(p)) return pr.getName();
		}
		return "outside shapefile";
	}
	
	public static String inDistrict(Point p) {
		for (TNGPolygonFile.Province di: district) {
			if(di.isInside(p)) return di.getName();
		}
		return "outside shapefile";
	}

	
	public static void CMDL(String[] args) {
		String GPXfile;
		String CSVfile;
		String separator;
		Boolean writeheader;

		try {
			TNGPointFile or = new TNGPointFile("..\\orter.tng");
			localities = or.getLocalities();
			System.out.println("Loaded provinces..");
			TNGPolygonFile pr = new TNGPolygonFile("..\\provinser.tng");
			provinces = pr.getProvinces();
			System.out.println("done");
			System.out.println("Loading district..");
			TNGPolygonFile so = new TNGPolygonFile("..\\socknar.tng");
			district = so.getProvinces();
			System.out.println("done");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		if (args.length > 0) {
			GPXfile = args[0];
			System.out.println("GPX file: " + GPXfile);
		} else {
			System.out.println("Inga parametrar behöver GPX fil och csv fil");
			GPXfile = "hej";
		}
		if (args.length > 1) {
			CSVfile = args[1];
			System.out.println("CSV file: " + CSVfile);
		} else {
			System.out.println("för få parametrar behöver csv fil");
			CSVfile = "hej";
		}
		if (args.length > 2) {
			separator = args[2];
		} else {
			separator = ";";
		}
		if (args.length > 3) {
			if (args[3] == "true") {
				writeheader = true;
			} else {
				writeheader = false;
			}
		} else {
			writeheader = true;
		}

		BufferedWriter bufferedWriter = null;
		// Path path = FileSystems.getDefault().getPath("test.txt");
		// Charset cs = Charset.forName("UTF-8");
		try {
			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory
					.newInstance();
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(new File(GPXfile));
			// normalize text representation
			doc.getDocumentElement().normalize();

			bufferedWriter = new BufferedWriter(new FileWriter(CSVfile));
			if (writeheader) {
				String line = "name" + separator + "north" + separator + "east"
						+ separator + "dateSE" + separator + "elevation"
						+ separator + "timeSE" + separator + "latitude"
						+ separator + "longitude" + separator + "DateTimeUTC"
						+ separator + "närmaste ort" + separator
						+ "avstånd till närmaste ort (km)" + separator
						+ "riktning"+separator+"provins"+separator+"socken";
				bufferedWriter.write(line);
				bufferedWriter.newLine();
			}

			NodeList waypoints = doc.getElementsByTagName("wpt");
			int nrWaypoints = waypoints.getLength();
			System.out.println("Number of waypoints : " + nrWaypoints);

			for (int s = 0; s < waypoints.getLength(); s++) {

				Node waypoint = waypoints.item(s);
				if (waypoint.getNodeType() == Node.ELEMENT_NODE) {
					Element waypointElement = (Element) waypoint;

					String lat = waypointElement.getAttribute("lat");
					String lon = waypointElement.getAttribute("lon");
					Coordinates c = new Coordinates(Double.valueOf(lat),
							Double.valueOf(lon)).convertRT90();
					String north = String.valueOf((int) c.getNorth());
					String east = String.valueOf((int) c.getEast());
					String elevation = waypointElement
							.getElementsByTagName("ele").item(0)
							.getTextContent();
					String datetime = waypointElement
							.getElementsByTagName("time").item(0)
							.getTextContent();
					Datum d = new Datum(datetime);
					String swDate = d.getSEDate();
					String swTime = d.getSETime();
					String name = waypointElement.getElementsByTagName("name")
							.item(0).getTextContent();
					
					//System.out.println("");
					//System.out.println(name);
					
					TNGPointFile.Locality nearest = NearestLoc((int) c.getEast(),
							(int) c.getNorth());
					double dist = nearest.dist((int)c.getEast(), (int)c.getNorth());
					String riktning = nearest.riktning(c.getEast(),
							c.getNorth());
					String province = inProvince(new Point((int) c.getEast(), (int) c.getNorth()));
					
					String district = inDistrict(new Point((int) c.getEast(), (int) c.getNorth()));
					
					String line = name + separator + north + separator + east
							+ separator + swDate + separator + elevation
							+ separator + swTime + separator + lat + separator
							+ lon + separator + datetime + separator
							+ nearest.name + separator + dist + separator
							+ riktning+separator+province+separator+district;
					bufferedWriter.write(line);
					bufferedWriter.newLine();
					
					
				}
			}// end of for loop with s var
		} catch (SAXParseException err) {
			System.out.println("** Parsing error" + ", line "
					+ err.getLineNumber() + ", uri " + err.getSystemId());
			System.out.println(" " + err.getMessage());

		} catch (SAXException e) {
			Exception x = e.getException();
			((x == null) ? e : x).printStackTrace();

		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (Throwable ex) {
			ex.printStackTrace();
		} finally {
			// Close the BufferedWriter
			try {
				if (bufferedWriter != null) {
					bufferedWriter.flush();
					bufferedWriter.close();
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		/*try {
			PrintStream out = new PrintStream(new FileOutputStream("logg.txt"));
			System.setOut(out);
			System.setErr(out);
		} catch(IOException ex) {
			ex.printStackTrace();
		}*/
		
		
		/* KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {

	            @Override
	            public boolean dispatchKeyEvent(KeyEvent ke) {
	                synchronized (IsKeyPressed.class) {
	                    switch (ke.getID()) {
	                    case KeyEvent.KEY_PRESSED:
	                        if (ke.getKeyCode() == KeyEvent.VK_W) {
	                            wPressed = true;
	                            System.out.println("w - key pressed");
	                        }
	                        break;

	                    case KeyEvent.KEY_RELEASED:
	                        if (ke.getKeyCode() == KeyEvent.VK_W) {
	                            wPressed = false;
	                            System.out.println("w - key relesead");
	                        }
	                        break;
	                    }
	                    return false;
	                }
	            }
	        });*/
		
		if (args.length > 0) {
			CMDL(args);
		} else {
			GUI.main(args);
		}
	}

}
