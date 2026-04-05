import geometry.Point;
import coords.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;
import java.awt.Desktop;
import java.net.URI;

public class GUI implements NActionListener {
	static JFrame frame;
	public static Canvas canvas;
	static Point coord;
	static SpecimenList sList;

	public GUI() {
	}

	private static void createAndShowGUI() {
		// Create and set up the window.
		GUI gui = new GUI();
		frame = new JFrame("Minimap");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setJMenuBar(gui.createMenuBar());
		frame.setContentPane(gui.createContentPane());
		canvas = new Canvas();
		frame.add(canvas);
		final java.awt.Point pressPt = new java.awt.Point();

		canvas.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				pressPt.setLocation(e.getPoint()); // Store the point in the holder
				canvas.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				int dx = e.getX() - pressPt.x;
				int dy = e.getY() - pressPt.y;
				canvas.panPixel(dx, dy);
				canvas.setCursor(Cursor.getDefaultCursor());
			}

			@Override
			public void mouseClicked(MouseEvent arg0) {
				if(Keyboard.isKeyDown(KeyEvent.VK_A)) {
					gui.showLokal(arg0);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_R)) {
					gui.showRubin(arg0);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_C)) {
					gui.showCoordDialog(arg0);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_S)) {
					gui.createLocalityDialog(arg0);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_K)) {  //K
					gui.OpenKartbildcom(arg0);
				} else {
					Point p  = canvas.translatePoint(new Point(arg0.getX(), arg0.getY()));
					canvas.setCoordinate(p);
				}
			}
		});

		canvas.addMouseWheelListener(e -> {
			int rot = e.getWheelRotation();
			double step = (rot > 0) ? 1.2 : 0.8;
			canvas.zoom(step);
		});

		// Display the window.
		frame.setSize(1000, 1000);
		frame.setVisible(true);
		
		try {
			if (Settings.getValue("specimen dialog").equals("open")){
				searchSpecimens();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Keyboard.activate();
		Keyboard.addActionListener(gui);
	}

	public JMenuBar createMenuBar() {
		JMenuBar menuBar;
		JMenu menu, menu2, menu3;
		JMenuItem menuItem0, menuItem1, menuItem2, menuItem3, menuItem4, menuItem5, menuItem6, menuItem7, menuItem8, menuItem9, menuItem10, menuItem11, menuItem12, menuItem13;
		menuBar = new JMenuBar();

		// Build the first menu.
		menu = new JMenu("File");
		// menu.setMnemonic(KeyEvent.VK_A);
		menu.getAccessibleContext().setAccessibleDescription("The only menu in this program that has menu items");
		menuBar.add(menu);

		menuItem0 = new JMenuItem("Open File", KeyEvent.VK_O);
		// menuItem.setMnemonic(KeyEvent.VK_T); //used constructor instead
		menuItem0.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
		menuItem0.getAccessibleContext().setAccessibleDescription("This doesn't really do anything");
		menuItem0.addActionListener(e -> openFile());
		menu.add(menuItem0);

		menuItem1 = new JMenuItem("Open .gpx File", KeyEvent.VK_G);
		// menuItem.setMnemonic(KeyEvent.VK_T); //used constructor instead
		menuItem1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G,
				ActionEvent.CTRL_MASK));
		menuItem1.getAccessibleContext().setAccessibleDescription(
				"This doesn't really do anything");
		menuItem1.addActionListener(e->openGPXFile());
		menu.add(menuItem1);

		menuItem2 = new JMenuItem("Save as .csv", KeyEvent.VK_S);
		menuItem2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
				ActionEvent.CTRL_MASK));
		menuItem2.getAccessibleContext().setAccessibleDescription(
				"This doesn't really do anything");
		menuItem2.addActionListener(e->saveCSV());
		menu.add(menuItem2);

		menuItem2 = new JMenuItem("Set user", KeyEvent.VK_I);
		menuItem2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I,
				ActionEvent.CTRL_MASK));
		menuItem2.getAccessibleContext().setAccessibleDescription(
				"It sets the name for the registrator");
		menuItem2.addActionListener(e->userDialog());
		menu.add(menuItem2);

		menuItem3 = new JMenuItem("Exit", KeyEvent.VK_Q);
		menuItem3.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q,
				ActionEvent.CTRL_MASK));
		menuItem3.getAccessibleContext().setAccessibleDescription(
				"This doesn't really do anything");
		menuItem3.addActionListener(e->System.exit(0));
		menu.add(menuItem3);

		menu2 = new JMenu("View");
		menuBar.add(menu2);
		menuItem4 = new JMenuItem("Zoom in", KeyEvent.VK_P);
		// menuItem.setMnemonic(KeyEvent.VK_T); //used constructor instead
		menuItem4.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P,
				ActionEvent.CTRL_MASK));
		menuItem4.addActionListener(e->canvas.zoom(0.5));
		menu2.add(menuItem4);

		menuItem5 = new JMenuItem("Zoom out", KeyEvent.VK_M);
		// menuItem.setMnemonic(KeyEvent.VK_T); //used constructor instead
		menuItem5.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M,
				ActionEvent.CTRL_MASK));
		menuItem5.addActionListener(e->canvas.zoom(2));
		menu2.add(menuItem5);

		
		menuItem12 = new JMenuItem("Mark/Find Coordinate", KeyEvent.VK_U);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem12.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U,
				ActionEvent.CTRL_MASK));
		menuItem12.addActionListener(e->MarkCoordDialog());
		menu2.add(menuItem12);
		
		menuItem6 = new JMenuItem("View Coordinate", KeyEvent.VK_K);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem6.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_K,
				ActionEvent.CTRL_MASK));
		menuItem6.addActionListener(e->viewCoordinate());
		menu2.add(menuItem6);

		menuItem8 = new JMenuItem("View Rubin", KeyEvent.VK_R);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem8.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R,
				ActionEvent.CTRL_MASK));
		menuItem8.addActionListener(e->viewRubin());
		menu2.add(menuItem8);

		menuItem7 = new JMenuItem("Search localities", KeyEvent.VK_F);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem7.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F,
				ActionEvent.CTRL_MASK));
		menuItem7.addActionListener(e->searchLocality());
		menu2.add(menuItem7);

		menuItem8 = new JMenuItem("Distance and Direction", KeyEvent.VK_D);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem8.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D,
				ActionEvent.CTRL_MASK));
		menuItem8.addActionListener(e->distance());
		menu2.add(menuItem8);
		
		menuItem9 = new JMenuItem("Search specimens", KeyEvent.VK_E);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem9.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E,
				ActionEvent.CTRL_MASK));
		menuItem9.addActionListener(e->searchSpecimens());
		menu2.add(menuItem9);

		menuItem10 = new JMenuItem("Show lokality at marker", KeyEvent.VK_T);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem10.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T,
				ActionEvent.CTRL_MASK));
		menuItem10.addActionListener(e->showLokalAtCoord());
		menu2.add(menuItem10);
		
		menuItem11 = new JMenuItem("Create lokality at marker", KeyEvent.VK_Y);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem11.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y,
				ActionEvent.CTRL_MASK));
		menuItem11.addActionListener(e->createLokalAtCoord());
		menu2.add(menuItem11);

		menuBar.add(Box.createHorizontalGlue());

		menu3 = new JMenu("Help");
		menuBar.add(menu3);

		menuItem9 = new JMenuItem("About");
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem9.addActionListener(e->JOptionPane.showMessageDialog(frame, "Minimap, written by Nils Ericson 2013"));
		menu3.add(menuItem9);
		
		menuItem11 = new JMenuItem("Shortcuts");
		menuItem11.addActionListener(e->showShortcuts());
		menu3.add(menuItem11);

		menuItem10 = new JMenuItem("Layers", KeyEvent.VK_L);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem10.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L,
				ActionEvent.CTRL_MASK));
		//menuItem10.addActionListener(this);
		menu2.add(menuItem10);
		
		menuItem13 = new JMenuItem("Search Ortnamnsregistret", KeyEvent.VK_B);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem13.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B,
				ActionEvent.CTRL_MASK));
		menuItem13.addActionListener(e->SearchOrtReg());
		menu2.add(menuItem13);

		return menuBar;
	}

	public static void setCursorWait() {
		frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		for( Window window : frame.getOwnedWindows() ){
            if( window.isVisible() ){
                window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
        }
		System.out.println("Changing cursor wait");
	}
	
	public static void setCursorDefault() {
		frame.setCursor(Cursor.getDefaultCursor());
		for( Window window : frame.getOwnedWindows() ){
            if( window.isVisible() ){
                window.setCursor(Cursor.getDefaultCursor());
            }
        }
		System.out.println("Changing cursor default");
	}
	
	private void openFile() {
		//System.out.println("Open File");
		final JFileChooser fc = new JFileChooser();

		FileNameExtensionFilter filter = new FileNameExtensionFilter(
				"Map Files", ".shp", ".SHP", "tif", "TIF", "tng", "TNG",
				"gpx", "GPX");
		fc.setFileFilter(filter);
		int returnVal = fc.showOpenDialog(GUI.canvas);

		if (returnVal == JFileChooser.APPROVE_OPTION) {
			setCursorWait();
			File file = fc.getSelectedFile();
			System.out.println("Open: " + file.getName());
			
			try {
				
				RasterFil rFile = new RasterFil(file.getPath());
				canvas.addLayerBotom(rFile);
				
				//JOptionPane.showMessageDialog(null, "Öppnar2: "+file.getPath(), "InfoBox", JOptionPane.INFORMATION_MESSAGE);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				JOptionPane.showMessageDialog(null, "kan inte öppna: "+file.getPath(), "InfoBox", JOptionPane.INFORMATION_MESSAGE);
			}
			setCursorDefault();

		} else {
			System.out.println("Open cancelled");
		}
	}

	public void openGPXFile() {
		System.out.println("Open");
		final JFileChooser fc = new JFileChooser();

		FileNameExtensionFilter filter = new FileNameExtensionFilter(
				"Waypoint Files", "gpx", "GPX");
		fc.setFileFilter(filter);
		int returnVal = fc.showOpenDialog(GUI.canvas);

		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File file = fc.getSelectedFile();
			System.out.println("Open: " + file.getName());

			try {
				GPXFile l;
				l = new GPXFile(file.getCanonicalPath());
				l.setColor(Color.ORANGE);
				l.setName(file.getName());
				canvas.addLayerTop(l);
				canvas.repaint();
			} catch (ParserConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} else {
			System.out.println("Open cancelled");
		}
	}

	public void saveCSV() {
		System.out.println("Save");
		final JFileChooser fc = new JFileChooser();
		int returnVal = fc.showSaveDialog(GUI.canvas);

		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File file = fc.getSelectedFile();
			System.out.println("Save: " + file.getName());
			// log.append("Opening: " + file.getName() + "." + newline);
		} else {
			// log.append("Open command cancelled by user." + newline);
			System.out.println("Save cancelled");
		}
	}

	public void searchLocality() {
		if (sList == null) {
			SearchLocalityDialog d = new SearchLocalityDialog(frame, "");
			d.setVisible(true);
		} else {
			SearchLocalityDialog d = new SearchLocalityDialog(frame, sList.getSelectedText());
			d.setVisible(true);
		}
	}

	public void viewCoordinate() {
		CoordinateDialog d = new CoordinateDialog(frame, canvas.getCoordinate(),
				(TNGPolygonFile) canvas.getLayer("provinser"),
				(TNGPolygonFile) canvas.getLayer("socknar"));
		d.setVisible(true);
		coord = d.getCoordinateSweref99TM();
		canvas.focus(coord);
		canvas.setCoordinate(coord);
	}

	public void viewRubin() {
		String s = (String) JOptionPane.showInputDialog(frame, "Enter RUBIN (e.g. 12H5j):",
				"Search Grid Square", JOptionPane.PLAIN_MESSAGE, null, null, "");
		if (s != null && !s.trim().isEmpty()) {
			Rubin r = new Rubin(s.trim(), "Rubin", Color.green);
			canvas.delLayer("Rubin");
			canvas.addLayerTop(r);
			Point p = r.getMiddle();
			if (p != null) {
				canvas.focus(p);
				canvas.repaint(); // Ensure the green box appears immediately
			} else {
				JOptionPane.showMessageDialog(frame, "Invalid RUBIN format.", "Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	public void distance() {
		DistanceDialog dlg = new DistanceDialog(frame);
		dlg.setVisible(true);

		if (!dlg.wasCancelled()) {
			String direction = dlg.getDirection();
			String distStr = dlg.getDistance();

			try {
				int distVal = Integer.parseInt(distStr);
				Distance dist = new Distance("dist", canvas.getCoordinate(), distVal, direction, CoordSystem.SWEREF99TM);
				dist.setColor(Color.red);
				dist.setHidden(false);
				canvas.delLayer("dist");
				canvas.addLayerTop(dist);
				canvas.repaint(); // Don't forget to repaint!
			} catch (NumberFormatException e) {
				JOptionPane.showMessageDialog(frame, "Please enter a valid numeric distance.");
			}
		} else {
			System.out.println("User cancelled.");
		}
		dlg.dispose();
	}

	public void userDialog() {
		SettUserDialog l = new SettUserDialog();
		l.setVisible(true);
	}

	public void showShortcuts() {
		String message = "Press s and click on the map to create a new Locality\nPress a and click on the map to edit Locality information\n"
				+ "Press c and click on the map to show info about the coordinate\nPress r and click on the map to show the 5x5 km RUBIN ruta";
		JOptionPane.showMessageDialog(frame, message, "Shortcuts", JOptionPane.INFORMATION_MESSAGE);
	}

	public Container createContentPane() {
		// Create the content-pane-to-be.
		JPanel contentPane = new JPanel(new BorderLayout());
		contentPane.setOpaque(true);
		return contentPane;
	}

	public void showCoordDialog(MouseEvent arg0) {
		coord = canvas.translatePoint(new Point(arg0.getX(), arg0.getY()));
		canvas.setCoordinate(coord);
		CoordinateDialog d = new CoordinateDialog(frame, coord,
				(TNGPolygonFile) canvas.getLayer("provinser"),
				(TNGPolygonFile) canvas.getLayer("socknar"));
		d.cancel.requestFocusInWindow();
		d.setVisible(true);
		d.cancel.requestFocusInWindow();
		coord = d.getCoordinateSweref99TM();
		canvas.focus(coord);
		canvas.setCoordinate(coord);
	}

	public void showRubin(MouseEvent arg0) {
		System.out.print("show RUBIN: ");
		Point p = canvas.translatePoint(new Point(arg0.getX(), arg0.getY()));
		Coordinates c = new Coordinates(p.getY(), p.getX());
		String s = c.toRUBIN(true);
		System.out.println(s);
		Rubin r = new Rubin(s, "Rubin", Color.green);
		canvas.delLayer("Rubin");
		canvas.addLayerTop(r);
		//Point p2 = r.getMiddle();
		//canvas.focus(p2);
	}

	public void showLokal(MouseEvent arg0) {
		Point p = canvas.translatePoint(new Point(arg0.getX(), arg0.getY()));
		MYSQLTable ldb = (MYSQLTable) canvas.getLayer("LokalDB");
		int localityID = ldb.findNearest(p, 1000);

		if (localityID != -1) {
			JFrame lframe = new JFrame("Locality");
			lframe.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			LocalityDialog diag = new LocalityDialog(localityID, lframe);
			lframe.add(diag);
			lframe.pack();
			lframe.setLocationRelativeTo(frame);
			lframe.setVisible(true);
		}
	}
	
	public void showLokalAtCoord() {
		System.out.println("show Locality at");
		//Point p = canvas.translatePoint2(new Point(arg0.getX(), arg0.getY()));
		Point p = canvas.getCoordinate();

		MYSQLTable ldb = (MYSQLTable) canvas.getLayer("LokalDB");
		int localityID = ldb.findNearest(p,1000);
		if (localityID != -1) {

			JFrame lframe = new JFrame("Locality");
			LocalityDialog diag = new LocalityDialog(localityID,lframe);
			//lframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			lframe.add(diag);
			diag.cancel.requestFocusInWindow();
		}
		//canvas.repaint();
	}
	
	private void createLokalAtCoord() {
		System.out.println("create Locality at");  // TODO trace print

		coord =  canvas.getCoordinate();
		String provins = "", socken = "";
		TNGPolygonFile provinces = (TNGPolygonFile)canvas.getLayer("provinser");
		TNGPolygonFile districts = (TNGPolygonFile)canvas.getLayer("socknar");
		TNGPolygonFile.Province pr = provinces.inPolygon(coord);
		if (pr != null) {
			provins = pr.getName();
		} else {
			provins ="utanför lager";
		}
		TNGPolygonFile.Province so = districts.inPolygon(coord);
		if (so != null) {
			socken = so.getName();
		} else {
			socken = "utanför lager";
		}
		
		JFrame lframe = new JFrame();
		CreateLocalityDialog diag = new CreateLocalityDialog(lframe, Integer.toString(coord.getY()), Integer.toString(coord.getX()), provins, socken);
		lframe.add(diag);
		diag.cancel.requestFocusInWindow();
		//canvas.repaint();
	}

	public static void searchSpecimens() {
		SpecimenService service = new SpecimenService();

		// Open the Bridge Dialog first
		SpecimenBridgeDialog bridgeDialog = new SpecimenBridgeDialog(frame, service);

		// Standard settings management
		bridgeDialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				try {
					Settings.setValue("specimen dialog", "closed");
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		});

		try {
			Settings.setValue("specimen dialog", "open");
		} catch (IOException e) {
			e.printStackTrace();
		}

		bridgeDialog.setVisible(true);
	}

	private void createLocalityDialog(MouseEvent me) {
		System.out.println("skapa lokal");

		// Get coordinates and update canvas marker
		coord = canvas.translatePoint(new Point(me.getX(), me.getY()));
		canvas.setCoordinate(coord);

		// Fetch Province and District info
		String provins = "utanför lager", socken = "utanför lager";
		TNGPolygonFile provinces = (TNGPolygonFile)canvas.getLayer("provinser");
		TNGPolygonFile districts = (TNGPolygonFile)canvas.getLayer("socknar");

		if (provinces != null) {
			TNGPolygonFile.Province pr = provinces.inPolygon(coord);
			if (pr != null) provins = pr.getName();
		}

		if (districts != null) {
			TNGPolygonFile.Province so = districts.inPolygon(coord);
			if (so != null) socken = so.getName();
		}

		// Initialize the Frame
		JFrame lframe = new JFrame("Create new Locality");
		lframe.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		// Create the Dialog
		CreateLocalityDialog diag = new CreateLocalityDialog(
				lframe,
				Integer.toString(coord.getY()),
				Integer.toString(coord.getX()),
				provins,
				socken
		);

		// Focus the cancel button (or OK button)
		diag.cancel.requestFocusInWindow();

		canvas.repaint();
	}
	
	private void OpenKartbildcom(MouseEvent arg0) {
		System.out.println("Open Kartbild.com");
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
		    try {
		    	int map = 40000;
		    	int zoomLevel = 12;
		    	Point point = canvas.translatePoint(new Point(arg0.getX(), arg0.getY()));
		    	
		    	Coordinates sweref99TM = new Coordinates(point.getY(), point.getX());
		    	Coordinates wgs84 = sweref99TM.toWGS84(CoordSystem.SWEREF99TM);
		    	System.out.println("coord: "+wgs84);
		    	String uri = "https://kartbild.com/#"+String.valueOf(zoomLevel)+"/"+String.valueOf(wgs84.getNorth())+"/"+String.valueOf(wgs84.getEast())+"/0x"+String.valueOf(map);
		    	System.out.println("URI: "+uri);
		    	Desktop.getDesktop().browse(new URI(uri));
		    } catch(Exception e) {
		    		
		    }
		} else {
			System.out.println("open browser not supported");
		}
	}
	
	private void SearchOrtReg() {
		System.out.println("Try search Ortnamnsregistret");
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
		    try {
		    	String placeName;
		    	if (sList == null) {
		    		placeName = "";
				} else {
					placeName = sList.getSelectedText();
				}
		    	String url = "https://ortnamnsregistret.isof.se/place-names?place-name="+placeName;
		    	Desktop.getDesktop().browse(new URI(url));
		    } catch(Exception e) {
		    	
		    }
		} else {
		    System.out.println("open browser not supported");
		}
		
	}
	
	public void MarkCoordDialog() {
		//coord = canvas.translatePoint2(new Point(arg0.getX(), arg0.getY()));
		//canvas.setCoordinate(coord);
		System.out.println("MarkDialog");
		MarkDialog d = new MarkDialog(frame, canvas, coord,
				(TNGPolygonFile) canvas.getLayer("provinser"),
				(TNGPolygonFile) canvas.getLayer("socknar"));
		//d.cancel.requestFocusInWindow();
		d.setVisible(true);
		//d.cancel.requestFocusInWindow();
		//coord = d.getCoordinate();
		//canvas.focus(coord);
		//canvas.setCoordinate(coord);
	}

	public static void main(String[] args) {
		// Schedule a job for the event-dispatching thread:
		// creating and showing this application's GUI.
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				createAndShowGUI();
			}
		});
	}

	public void nActionPerformed(String a) {
		switch (a) {
		case "Open File":
			openFile();
			break;
		case  "Open .gpx File":
			openGPXFile();
			break;
		case "Save as .csv":
			saveCSV();
			break;
		case "Exit":
			System.exit(0);
			break;
		case "Search":
			searchLocality();
			break;
		case "Zoom in":
			canvas.zoom(0.5);
			break;
		case "Zoom out":
			canvas.zoom(2);
			break;
		case "View Coordinate":
			viewCoordinate();
			break;
		case "View Rubin":
			viewRubin();
			break;
		case "About":
			JOptionPane.showMessageDialog(frame, "Minimap 0.1, written by Nils Ericson 2017-04-13");
			break;
		case "Distance and Direction":
			distance();
			break;
		case "Layers":
			LayerDialog l = new LayerDialog(frame, canvas.getLayers());
			l.setVisible(true);
			break;
		case "Set user":
			userDialog();
			break;
		case "Show lokality at marker":
			showLokalAtCoord();
			break;
		case "Create lokality at marker":
			createLokalAtCoord();
			break;
		case "Search localities":
			searchLocality();
			break;
		case "Search Ortnamnsregistret":
			SearchOrtReg();
			break;
	}
	}
}