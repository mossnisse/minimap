package main.core;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;

import main.dialogs.*;
import main.layers.*;
import main.coords.*;

public class GUI  {
	private JFrame frame;
	private MapCanvas mapCanvas;
	private AppContext ctx;
	private Coordinate coord;
	private SpecimenBridgeDialog bridgeDialog;
	private EditLocalityDialog moveTarget = null;

	public GUI() {
	}

	private void createAndShowGUI() {
		// Wire databases and repositories before anything can touch the DB
		ctx = AppContext.create();

		// Set up the frame and canvas
		frame = new JFrame("Minimap");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mapCanvas = new MapCanvas();
		MapLayers.installDefaultLayers(mapCanvas, ctx);

		// Setup Menus and Content
		frame.setJMenuBar(createMenuBar());
		frame.setContentPane(createContentPane());
		frame.add(mapCanvas);

		// Interactive distance measuring (G + click twice)
		mapCanvas.addMouseListener(new DistanceTool(mapCanvas, MapLayers.DISTANCE_OVERLAY));

		// Mouse Interaction Logic
		final java.awt.Point pressPt = new java.awt.Point();
		mapCanvas.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				pressPt.setLocation(e.getPoint());
				mapCanvas.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				int dx = e.getX() - pressPt.x;
				int dy = e.getY() - pressPt.y;
				mapCanvas.panPixel(dx, dy);
				mapCanvas.setCursor(Cursor.getDefaultCursor());
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (moveTarget != null) {
					leaveMoveMode(e);
				}
				if (Keyboard.isKeyDown(KeyEvent.VK_A)) {
					showLocality(e);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_R)) {
					showRubin(e);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_C)) {
					showCoordinateInfo(e);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_S)) {
					createLocalityDialog(e);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_K)) {
					OpenKartbildcom(e);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_D)) {
					distance(e);
				} else {
					Coordinate c = mapCanvas.translatePoint(new Point(e.getX(), e.getY()));
					mapCanvas.setCoordinate(c);
				}
			}
		});

		mapCanvas.addMouseWheelListener(e -> {
			int rot = e.getWheelRotation();
			double step = (rot > 0) ? 1.2 : 0.8;
			mapCanvas.zoom(step);
		});

		// Final Display Setup
		frame.setSize(1000, 1000);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		
		if ("open".equals(Settings.getValue("specimen dialog"))) {
			searchSpecimens();
		}

		Keyboard.activate();
	}

	public Container createContentPane() {
		// Create the content-pane-to-be.
		JPanel contentPane = new JPanel(new BorderLayout());
		contentPane.setOpaque(true);
		return contentPane;
	}

	public JMenuBar createMenuBar() {
		JMenuBar menuBar;
		JMenu menu, menu2, menu3, menu4;
		JMenuItem menuItem0, menuItem1, menuItem2, menuItem3, menuItem4, menuItem5, menuItem6, menuItem7, menuItem8, menuItem9, menuItem10, menuItem11, menuItem12;
		menuBar = new JMenuBar();

		// Build the first menu.
		menu = new JMenu("File");
		// menu.setMnemonic(KeyEvent.VK_A);
		menu.getAccessibleContext().setAccessibleDescription("The only menu in this program that has menu items");
		menuBar.add(menu);

		menuItem0 = new JMenuItem("Open File", KeyEvent.VK_O);
		// menuItem.setMnemonic(KeyEvent.VK_T); //used constructor instead
		menuItem0.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		menuItem0.getAccessibleContext().setAccessibleDescription("This doesn't really do anything");
		menuItem0.addActionListener(e->openFile());
		menu.add(menuItem0);

		menuItem2 = new JMenuItem("Save as .csv", KeyEvent.VK_S);
		menuItem2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
		menuItem2.getAccessibleContext().setAccessibleDescription(
				"This doesn't really do anything");
		menuItem2.addActionListener(e->saveCSV());
		menu.add(menuItem2);

		menuItem2 = new JMenuItem("Set Canvas CRS");
		menuItem2.addActionListener(e->setCanvasCRS());
		menu.add(menuItem2);

		menuItem2 = new JMenuItem("Set user", KeyEvent.VK_I);
		menuItem2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK));
		menuItem2.getAccessibleContext().setAccessibleDescription(
				"It sets the name for the registrator");
		menuItem2.addActionListener(e->userDialog());
		menu.add(menuItem2);

		menuItem3 = new JMenuItem("Exit", KeyEvent.VK_Q);
		menuItem3.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
		menuItem3.getAccessibleContext().setAccessibleDescription(
				"This doesn't really do anything");
		menuItem3.addActionListener(e->System.exit(0));
		menu.add(menuItem3);

		menu2 = new JMenu("View");
		menuBar.add(menu2);
		menuItem4 = new JMenuItem("Zoom in", KeyEvent.VK_P);
		// menuItem.setMnemonic(KeyEvent.VK_T); //used constructor instead
		menuItem4.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));
		menuItem4.addActionListener(e-> mapCanvas.zoom(0.5));
		menu2.add(menuItem4);

		menuItem5 = new JMenuItem("Zoom out", KeyEvent.VK_M);
		// menuItem.setMnemonic(KeyEvent.VK_T); //used constructor instead
		menuItem5.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK));
		menuItem5.addActionListener(e-> mapCanvas.zoom(2));
		menu2.add(menuItem5);

		menuItem10 = new JMenuItem("Layers", KeyEvent.VK_T);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem10.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK));
		menuItem10.addActionListener(e->showLayerDialog());
		menu2.add(menuItem10);

		menuItem12 = new JMenuItem("Mark/Find Coordinate", KeyEvent.VK_U);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem12.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK));
		menuItem12.addActionListener(e->MarkCoordDialog());
		menu2.add(menuItem12);

		menuItem6 = new JMenuItem("View Coordinate", KeyEvent.VK_K);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem6.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK));
		menuItem6.addActionListener(e->showCoordinateInfoAtCoord());
		menu2.add(menuItem6);

		menuItem8 = new JMenuItem("View Rubin", KeyEvent.VK_R);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem8.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK));
		menuItem8.addActionListener(e->viewRubin());
		menu2.add(menuItem8);

		menuItem7 = new JMenuItem("Search localities", KeyEvent.VK_F);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem7.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
		menuItem7.addActionListener(e->searchLocality());
		menu2.add(menuItem7);

		menuItem8 = new JMenuItem("Distance and Direction", KeyEvent.VK_D);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem8.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK));
		menuItem8.addActionListener(e->distanceAtCoord());
		menu2.add(menuItem8);
		
		menuItem9 = new JMenuItem("Search specimens", KeyEvent.VK_E);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem9.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK));
		menuItem9.addActionListener(e->searchSpecimens());
		menu2.add(menuItem9);

		menuItem10 = new JMenuItem("Edit locality at marker", KeyEvent.VK_J);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem10.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK));
		menuItem10.addActionListener(e->showLocalityAtCoord());
		menu2.add(menuItem10);
		
		menuItem11 = new JMenuItem("Create locality at marker", KeyEvent.VK_Y);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem11.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
		menuItem11.addActionListener(e->createLocalityAtCoord());
		menu2.add(menuItem11);

		menu3 = new JMenu("Layers");
		menuBar.add(menu3);

		menuItem1 = new JMenuItem("Add Topowebkartan");
		menuItem1.addActionListener(e->addTopowebkartan());
		menu3.add(menuItem1);

		menuItem1 = new JMenuItem("Add Open Street Map");
		menuItem1.addActionListener(e->addOSM());
		menu3.add(menuItem1);

		menuItem1 = new JMenuItem("Add Landskap Layer");
		menuItem1.addActionListener(e->addLandskap());
		menu3.add(menuItem1);

		menuItem1 = new JMenuItem("Add Socken Layer");
		menuItem1.addActionListener(e->addSocknar());
		menu3.add(menuItem1);

		menuItem1 = new JMenuItem("Add Lantmäteriet ortnamn Layer");
		menuItem1.addActionListener(e->addOrtnamn());
		menu3.add(menuItem1);

		menuItem1 = new JMenuItem("Add Virtual Herbarium locality Layer");
		menuItem1.addActionListener(e->addLocalityLayer());
		menu3.add(menuItem1);

		menuItem1 = new JMenuItem("Add .gpx layer", KeyEvent.VK_G);
		// menuItem.setMnemonic(KeyEvent.VK_T); //used constructor instead
		menuItem1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK));
		menuItem1.getAccessibleContext().setAccessibleDescription(
				"This doesn't really do anything");
		menuItem1.addActionListener(e->openGPXFile());
		menu3.add(menuItem1);

		menuItem1 = new JMenuItem("Add raster layer", KeyEvent.VK_G);
		menuItem1.addActionListener(e->openFile());
		menu3.add(menuItem1);

		menuBar.add(Box.createHorizontalGlue());

		menu4 = new JMenu("Help");
		menuBar.add(menu4);

		menuItem9 = new JMenuItem("About");
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem9.addActionListener(e->JOptionPane.showMessageDialog(frame, "Minimap, written by Nils Ericson 2013"));
		menu4.add(menuItem9);
		
		menuItem11 = new JMenuItem("Shortcuts");
		menuItem11.addActionListener(e->showShortcuts());
		menu4.add(menuItem11);

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

	private void setCanvasCRS() {
		new CRSDialog(this.frame, this.mapCanvas).showDialog();
	}
	
	private void openFile() {
		final JFileChooser fc = new JFileChooser();

		FileNameExtensionFilter filter = new FileNameExtensionFilter(
				"Map Files", ".shp", ".SHP", "tif", "TIF", "tng", "TNG", "png", "PNG", "jpg", "JPG",
				"gpx", "tools.GPX");
		fc.setFileFilter(filter);
		int returnVal = fc.showOpenDialog(mapCanvas);

		if (returnVal == JFileChooser.APPROVE_OPTION) {
			setCursorWait();
			File file = fc.getSelectedFile();
			//System.out.println("Open: " + file.getName());
			try {
				RasterFileLayer rFile = new RasterFileLayer(file.getPath(), mapCanvas);
				mapCanvas.getLayerManager().addLayerTop(rFile);
				//JOptionPane.showMessageDialog(null, "Öppnar2: "+file.getPath(), "InfoBox", JOptionPane.INFORMATION_MESSAGE);
			} catch (IOException e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null, "Can't open the file: "+file.getPath(), "InfoBox", JOptionPane.INFORMATION_MESSAGE);
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
				"Waypoint Files", "gpx", "tools.GPX");
		fc.setFileFilter(filter);
		int returnVal = fc.showOpenDialog(mapCanvas);

		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File file = fc.getSelectedFile();
			System.out.println("Open: " + file.getName());

			try {
				GPXFileLayer l = new GPXFileLayer(file.getCanonicalPath(), mapCanvas);
				l.setColor(Color.BLUE);
				l.setName(file.getName());
				mapCanvas.getLayerManager().addLayerTop(l);
				mapCanvas.repaint();
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	public void addTopowebkartan() {
		TopowebLayer tb = MapLayers.topoweb(mapCanvas);
		mapCanvas.setCRS(tb.getCRS());
		mapCanvas.getLayerManager().addLayerBottom(tb);
	}

	public void addOSM() {
		OSMLayer osm = MapLayers.osm(mapCanvas);
		mapCanvas.setCRS(osm.getCRS());
		mapCanvas.getLayerManager().addLayerBottom(osm);
	}

	public void addLandskap() {
		mapCanvas.getLayerManager().addLayerTop(MapLayers.PROVINSER, MapLayers.provinser(mapCanvas));
	}

	public void addSocknar() {
		mapCanvas.getLayerManager().addLayerTop(MapLayers.SOCKNAR, MapLayers.socknar(mapCanvas));
	}

	public void addOrtnamn() {
		mapCanvas.getLayerManager().addLayerTop(MapLayers.ORTNAMN, MapLayers.ortnamn(mapCanvas, ctx.placeNames));
	}

	public void addLocalityLayer() {
		mapCanvas.getLayerManager().addLayerTop(MapLayers.LOKAL_DB, MapLayers.lokalDb(mapCanvas, ctx.localities));
	}

	public void saveCSV() {
		System.out.println("Save");
		final JFileChooser fc = new JFileChooser();
		int returnVal = fc.showSaveDialog(mapCanvas);

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
		new SearchLocalityDialog(frame, this, mapCanvas, "", "", ctx.localities, ctx.placeNames).setVisible(true);
	}

	public void showLayerDialog() {
		new LayerDialog(frame, mapCanvas);
	}

	public void showCoordinateInfoAtCoord() {
		Coordinate c = mapCanvas.getCoordinate();
		new CoordinateDialog(frame, mapCanvas, c).setVisible(true);
	}

	public void showCoordinateInfo(MouseEvent me) {
		Coordinate c = mapCanvas.translatePoint(me.getPoint());
		new CoordinateDialog(frame, mapCanvas, c).setVisible(true);
	}

	public void viewRubin() {
		String s = (String) JOptionPane.showInputDialog(frame, "Enter RUBIN (e.g. 12H5j):",
				"Search Grid Square", JOptionPane.PLAIN_MESSAGE, null, null, "");

		if (s != null && !s.trim().isEmpty()) {
			RubinLayer r = new RubinLayer(s.trim(), mapCanvas, "Rubin", Color.green);
			Coordinate c = r.getMiddle();
			if (c != null) {
				mapCanvas.getLayerManager().setOverlay(MapLayers.RUBIN_MARKER, r);
				mapCanvas.focus(c);
				mapCanvas.repaint();
			} else {
				JOptionPane.showMessageDialog(frame,
						"'" + s + "' is not a valid RUBIN coordinate.\nExample format: 12H5j",
						"Invalid Format", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	public void showRubin(MouseEvent e) {
		Coordinate c = mapCanvas.translatePoint(e.getPoint());
		String rubin = RUBIN.fromSweref99TM(c);
		RubinLayer r = new RubinLayer(rubin, mapCanvas, "Rubin", Color.green);
		mapCanvas.getLayerManager().setOverlay(MapLayers.RUBIN_MARKER, r);
	}

	public void distanceAtCoord() {
		Coordinate c = mapCanvas.getCoordinate();
		if (c == null) {
			JOptionPane.showMessageDialog(mapCanvas, "Please select a point on the map first.");
			return;
		}
		new DistanceDialog(frame, mapCanvas, c, MapLayers.DISTANCE_OVERLAY).setVisible(true);
	}

	public void distance(MouseEvent me) {
		Coordinate c = mapCanvas.translatePoint(new Point(me.getX(), me.getY()));
		new DistanceDialog(frame, mapCanvas, c, MapLayers.DISTANCE_OVERLAY).setVisible(true);
	}

	public void userDialog() {
		new SetUserDialog().setVisible(true);
	}

	public void showShortcuts() {
		String message = "Press s and click on the map to create a new Locality\n" +
						"Press a and click on the map to edit Locality information\n" +
						"Press c and click on the map to show info about the coordinate\n" +
						"Press r and click on the map to show the 5x5 km RUBIN ruta\n" +
						"Press k and click on the map to open Kartbild.com at the coordinate\n" +
						"Press d and click on the map to show distance and direction\n" +
						"Press g and click on the map for the distance tool\n" +
						"in Link specimen mark text and\n" +
						"Ctr+F for search locality in the locality db and a Lantmäteriets ortnamn db\n" +
						"Ctr+B for search in Ortnamnsregistret\n" +
						"Ctr+L copy data from last saved link";

		JOptionPane.showMessageDialog(frame, message, "Shortcuts", JOptionPane.INFORMATION_MESSAGE);
	}

	public void showLocality(MouseEvent e) {
		Coordinate c = mapCanvas.translatePoint(new Point(e.getX(), e.getY()));
		editLocalityNear(c);
	}

	public void showLocalityAtCoord() {
		Coordinate c = mapCanvas.getCoordinate();
		if (c == null) return;
		editLocalityNear(c);
	}

	private void editLocalityNear(Coordinate c) {
		int localityID = ctx.localities.findNearestId(mapCanvas.getCRS().toWGS84(c), 1000);
		if (localityID != -1) {
			new EditLocalityDialog(this, frame, localityID, bridgeDialog, mapCanvas, ctx.localities).setVisible(true);
		}
	}

	public void enterMoveMode(EditLocalityDialog dialog) {
		this.moveTarget = dialog;
		Cursor crosshair = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
		frame.setCursor(crosshair);
		mapCanvas.setCursor(crosshair);
		if (dialog != null) {
			dialog.setCursor(crosshair);
		}
	}

	public void leaveMoveMode(MouseEvent me) {
		if (moveTarget == null) return;
		Coordinate mapP = mapCanvas.translatePoint(me.getPoint());
		moveTarget.updateCoordinates(mapP);

		// Reset everything back to default
		Cursor defaultCursor = Cursor.getDefaultCursor();
		frame.setCursor(defaultCursor);
		mapCanvas.setCursor(defaultCursor);
		moveTarget.setCursor(defaultCursor);

		moveTarget = null;
	}

	public void cancelMoveMode() {
		if (moveTarget != null) {
			Cursor defaultCursor = Cursor.getDefaultCursor();
			frame.setCursor(defaultCursor);
			mapCanvas.setCursor(defaultCursor);
			moveTarget.setCursor(defaultCursor);
			moveTarget.setTitle("Edit Locality: " + moveTarget.getOldName());
			moveTarget = null;
		}
	}

	private void createLocalityAtCoord() {
		coord = mapCanvas.getCoordinate();
		new CreateLocalityDialog(frame, this, mapCanvas, bridgeDialog, coord, ctx.localities, ctx.placeNames).setVisible(true);
	}

	private void createLocalityDialog(MouseEvent me) {
		coord = mapCanvas.translatePoint(me.getPoint());
		new CreateLocalityDialog(frame, this, mapCanvas, bridgeDialog, coord, ctx.localities, ctx.placeNames).setVisible(true);
	}

	public void searchSpecimens() {
		// Check if the dialog is already open
		if (bridgeDialog != null && bridgeDialog.isVisible()) {
			bridgeDialog.toFront(); // Bring the existing window to the top
			bridgeDialog.requestFocus();
			return; // Exit the method so we don't create a duplicate
		}

		bridgeDialog = new SpecimenBridgeDialog(frame, this, ctx.specimens, mapCanvas, ctx);

		bridgeDialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				try {
					Settings.setValue("specimen dialog", "closed");
					bridgeDialog = null; // Important for the "if" check above to work later
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

	private void OpenKartbildcom(MouseEvent me) {
		if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			System.err.println("Opening a browser is not supported on this system.");
			return;
		}

		try {
			int mapType = 20; // Specific layer ID for Kartbild  = generalkarta1
			int zoomLevel = 12;

			Coordinate c = mapCanvas.translatePoint(me.getPoint());
			Coordinate wgs84  = CoordSystem.SWEREF99TM.toWGS84(c);

			// Format the URI string. Format: #zoom/lat/lon/type  //https://kartbild.com/?marker=58.88545,11.02363#14/58.88545/11.02363+/0x20"
			String uriString = String.format(Locale.US, "https://kartbild.com/?marker=%f,%f#%d/%f/%f/0x%d",
					wgs84.getNorth(), wgs84.getEast(),
					zoomLevel,
					wgs84.getNorth(), wgs84.getEast(),
					mapType);

			Desktop.getDesktop().browse(new URI(uriString));
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frame,
					"Could not open the browser: " + e.getMessage(),
					"Browser Error", JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
	}

	public void MarkCoordDialog() {
		MarkCoordinateDialog d = new MarkCoordinateDialog(frame, mapCanvas);
		d.setLocationRelativeTo(frame);
		d.setVisible(true);
	}

	public static void main(String[] args) {
		javax.swing.SwingUtilities.invokeLater(() -> {
			GUI app = new GUI(); // Create the object
			app.createAndShowGUI(); // Call the setup on that specific object
		});
	}
}