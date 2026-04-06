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

public class GUI  {
	private JFrame frame;
	private Canvas canvas;
	private Point coord;
	private SpecimenBridgeDialog bridgeDialog;

	public GUI() {
	}

	private void createAndShowGUI() {
		// Setup the frame and canvas
		frame = new JFrame("Minimap");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		canvas = new Canvas();

		// Setup Menus and Content
		frame.setJMenuBar(createMenuBar());
		frame.setContentPane(createContentPane());
		frame.add(canvas);

		// Mouse Interaction Logic
		final java.awt.Point pressPt = new java.awt.Point();
		canvas.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				pressPt.setLocation(e.getPoint());
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
			public void mouseClicked(MouseEvent e) {
				// Use 'GUI.this' to call instance methods from inside the anonymous listener
				if (Keyboard.isKeyDown(KeyEvent.VK_A)) {
					showLokal(e);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_R)) {
					showRubin(e);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_C)) {
					showCoordDialog(e);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_S)) {
					createLocalityDialog(e);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_K)) {
					OpenKartbildcom(e);
				} else {
					Point p = canvas.translatePoint(new Point(e.getX(), e.getY()));
					canvas.setCoordinate(p);
				}
			}
		});

		canvas.addMouseWheelListener(e -> {
			int rot = e.getWheelRotation();
			double step = (rot > 0) ? 1.2 : 0.8;
			canvas.zoom(step);
		});

		// Final Display Setup
		frame.setSize(1000, 1000);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		// App State Recovery
		try {
			if ("open".equals(Settings.getValue("specimen dialog"))) {
				searchSpecimens();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		Keyboard.activate();
	}

	public JMenuBar createMenuBar() {
		JMenuBar menuBar;
		JMenu menu, menu2, menu3;
		JMenuItem menuItem0, menuItem1, menuItem2, menuItem3, menuItem4, menuItem5, menuItem6, menuItem7, menuItem8, menuItem9, menuItem10, menuItem11, menuItem12;
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

		return menuBar;
	}

	public void setCursorWait() {
		frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		for (Window window : frame.getOwnedWindows() ){
            if (window.isVisible()) {
                window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
        }
	}
	
	public void setCursorDefault() {
		frame.setCursor(Cursor.getDefaultCursor());
		for (Window window : frame.getOwnedWindows()) {
            if (window.isVisible()) {
                window.setCursor(Cursor.getDefaultCursor());
            }
        }
	}
	
	private void openFile() {
		//System.out.println("Open File");
		final JFileChooser fc = new JFileChooser();

		FileNameExtensionFilter filter = new FileNameExtensionFilter(
				"Map Files", ".shp", ".SHP", "tif", "TIF", "tng", "TNG",
				"gpx", "GPX");
		fc.setFileFilter(filter);
		int returnVal = fc.showOpenDialog(canvas);

		if (returnVal == JFileChooser.APPROVE_OPTION) {
			setCursorWait();
			File file = fc.getSelectedFile();
			//System.out.println("Open: " + file.getName());
			try {
				RasterFilLayer rFile = new RasterFilLayer(file.getPath());
				canvas.addLayerBotom(rFile);
				//JOptionPane.showMessageDialog(null, "Öppnar2: "+file.getPath(), "InfoBox", JOptionPane.INFORMATION_MESSAGE);
			} catch (IOException e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null, "kan inte öppna: "+file.getPath(), "InfoBox", JOptionPane.INFORMATION_MESSAGE);
			} finally {
				setCursorDefault();
			}
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
		int returnVal = fc.showOpenDialog(canvas);

		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File file = fc.getSelectedFile();
			System.out.println("Open: " + file.getName());

			try {
				GPXFileLayer l;
				l = new GPXFileLayer(file.getCanonicalPath());
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
		int returnVal = fc.showSaveDialog(canvas);

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
		SearchLocalityDialog d = new SearchLocalityDialog(frame, "", canvas);
		d.setVisible(true);
	}

	public void viewCoordinate() {
		CoordinateDialog d = new CoordinateDialog(frame, canvas.getCoordinate(),
				(TNGPolygonFileLayer) canvas.getLayer("provinser"),
				(TNGPolygonFileLayer) canvas.getLayer("socknar"));
		d.setVisible(true);
		coord = d.getCoordinateSweref99TM();
		canvas.focus(coord);
		canvas.setCoordinate(coord);
	}

	public void viewRubin() {
		String s = (String) JOptionPane.showInputDialog(frame, "Enter RUBIN (e.g. 12H5j):",
				"Search Grid Square", JOptionPane.PLAIN_MESSAGE, null, null, "");
		if (s != null && !s.trim().isEmpty()) {
			RubinLayer r = new RubinLayer(s.trim(), "Rubin", Color.green);
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
				DistanceLayer dist = new DistanceLayer("dist", canvas.getCoordinate(), distVal, direction, CoordSystem.SWEREF99TM);
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
		SetUserDialog l = new SetUserDialog();
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
				(TNGPolygonFileLayer) canvas.getLayer("provinser"),
				(TNGPolygonFileLayer) canvas.getLayer("socknar"));
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
		RubinLayer r = new RubinLayer(s, "Rubin", Color.green);
		canvas.delLayer("Rubin");
		canvas.addLayerTop(r);
		//Point p2 = r.getMiddle();
		//canvas.focus(p2);
	}

	public void showLokal(MouseEvent arg0) {
		Point p = canvas.translatePoint(new Point(arg0.getX(), arg0.getY()));
		MYSQLTableLayer ldb = (MYSQLTableLayer) canvas.getLayer("LokalDB");
		int localityID = ldb.findNearest(p, 1000);

		if (localityID != -1) {
			JFrame lframe = new JFrame("Locality");
			lframe.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			EditLocalityDialog diag = new EditLocalityDialog(localityID, lframe, bridgeDialog, canvas);
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

		MYSQLTableLayer ldb = (MYSQLTableLayer) canvas.getLayer("LokalDB");
		int localityID = ldb.findNearest(p,1000);
		if (localityID != -1) {

			JFrame lframe = new JFrame("Locality");
			EditLocalityDialog diag = new EditLocalityDialog(localityID, lframe, bridgeDialog, canvas);
			//lframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			lframe.add(diag);
			diag.cancel.requestFocusInWindow();
		}
		//canvas.repaint();
	}

	private void createLokalAtCoord() {
		System.out.println("create Locality at");  // TODO trace print

		coord =  canvas.getCoordinate();
		String provins, socken;
		TNGPolygonFileLayer provinces = (TNGPolygonFileLayer)canvas.getLayer("provinser");
		TNGPolygonFileLayer districts = (TNGPolygonFileLayer)canvas.getLayer("socknar");
		TNGPolygonFileLayer.Province pr = provinces.inPolygon(coord);
		if (pr != null) {
			provins = pr.getName();
		} else {
			provins ="utanför lager";
		}
		TNGPolygonFileLayer.Province so = districts.inPolygon(coord);
		if (so != null) {
			socken = so.getName();
		} else {
			socken = "utanför lager";
		}

		JFrame lframe = new JFrame();
		CreateLocalityDialog diag = new CreateLocalityDialog(this, canvas, bridgeDialog, lframe, Integer.toString(coord.getY()), Integer.toString(coord.getX()), provins, socken);
			diag.cancel.requestFocusInWindow();
		//canvas.repaint();
	}

	public void searchSpecimens() {
		SpecimenService service = new SpecimenService();

		// Assign to the CLASS FIELD instead of a local variable
		bridgeDialog = new SpecimenBridgeDialog(frame, service, canvas);

		bridgeDialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				try {
					Settings.setValue("specimen dialog", "closed");
					// Clear the reference when closed to prevent memory leaks
					bridgeDialog = null;
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
		TNGPolygonFileLayer provinces = (TNGPolygonFileLayer)canvas.getLayer("provinser");
		TNGPolygonFileLayer districts = (TNGPolygonFileLayer)canvas.getLayer("socknar");

		if (provinces != null) {
			TNGPolygonFileLayer.Province pr = provinces.inPolygon(coord);
			if (pr != null) provins = pr.getName();
		}

		if (districts != null) {
			TNGPolygonFileLayer.Province so = districts.inPolygon(coord);
			if (so != null) socken = so.getName();
		}

		// Initialize the Frame
		JFrame lframe = new JFrame("Create new Locality");
		lframe.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		// Create the Dialog
		CreateLocalityDialog diag = new CreateLocalityDialog(this, canvas, bridgeDialog, lframe, Integer.toString(coord.getY()), Integer.toString(coord.getX()), provins, socken);

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
	
	public void MarkCoordDialog() {
		//coord = canvas.translatePoint2(new Point(arg0.getX(), arg0.getY()));
		//canvas.setCoordinate(coord);
		System.out.println("MarkDialog");
		MarkDialog d = new MarkDialog(frame, canvas, coord,
				(TNGPolygonFileLayer) canvas.getLayer("provinser"),
				(TNGPolygonFileLayer) canvas.getLayer("socknar"));
		//d.cancel.requestFocusInWindow();
		d.setVisible(true);
		//d.cancel.requestFocusInWindow();
		//coord = d.getCoordinate();
		//canvas.focus(coord);
		//canvas.setCoordinate(coord);
	}

	public static void main(String[] args) {
			javax.swing.SwingUtilities.invokeLater(() -> {
				GUI app = new GUI(); // Create the object
				app.createAndShowGUI(); // Call the setup on that specific object
			});
	}
}