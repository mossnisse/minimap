package main.core;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;
import org.xml.sax.SAXException;

import main.dialogs.*;
import main.layers.*;
import main.coords.*;

public class GUI  {
	private JFrame frame;
	private Canvas canvas;
	private Coordinate coord;
	private SpecimenBridgeDialog bridgeDialog;
	private EditLocalityDialog moveTarget = null;

	public GUI() {
	}

	private void createAndShowGUI() {
		// Set up the frame and canvas
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
				// Use 'core.GUI.this' to call instance methods from inside the anonymous listener
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
					Coordinate c = canvas.translatePoint(new Point(e.getX(), e.getY()));
					canvas.setCoordinate(c);
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
		menuItem0.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		menuItem0.getAccessibleContext().setAccessibleDescription("This doesn't really do anything");
		menuItem0.addActionListener(e->openFile());
		menu.add(menuItem0);

		menuItem1 = new JMenuItem("Open .gpx File", KeyEvent.VK_G);
		// menuItem.setMnemonic(KeyEvent.VK_T); //used constructor instead
		menuItem1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK));
		menuItem1.getAccessibleContext().setAccessibleDescription(
				"This doesn't really do anything");
		menuItem1.addActionListener(e->openGPXFile());
		menu.add(menuItem1);

		menuItem2 = new JMenuItem("Save as .csv", KeyEvent.VK_S);
		menuItem2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
		menuItem2.getAccessibleContext().setAccessibleDescription(
				"This doesn't really do anything");
		menuItem2.addActionListener(e->saveCSV());
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
		menuItem4.addActionListener(e->canvas.zoom(0.5));
		menu2.add(menuItem4);

		menuItem5 = new JMenuItem("Zoom out", KeyEvent.VK_M);
		// menuItem.setMnemonic(KeyEvent.VK_T); //used constructor instead
		menuItem5.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK));
		menuItem5.addActionListener(e->canvas.zoom(2));
		menu2.add(menuItem5);

		
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

		menuItem10 = new JMenuItem("Edit locality at marker", KeyEvent.VK_T);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem10.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK));
		menuItem10.addActionListener(e->showLocalityAtCoord());
		menu2.add(menuItem10);
		
		menuItem11 = new JMenuItem("Create locality at marker", KeyEvent.VK_Y);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem11.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
		menuItem11.addActionListener(e->createLocalityAtCoord());
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
		menuItem10.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));
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
		final JFileChooser fc = new JFileChooser();

		FileNameExtensionFilter filter = new FileNameExtensionFilter(
				"Map Files", ".shp", ".SHP", "tif", "TIF", "tng", "TNG",
				"gpx", "tools.GPX");
		fc.setFileFilter(filter);
		int returnVal = fc.showOpenDialog(canvas);

		if (returnVal == JFileChooser.APPROVE_OPTION) {
			setCursorWait();
			File file = fc.getSelectedFile();
			//System.out.println("Open: " + file.getName());
			try {
				RasterFilLayer rFile = new RasterFilLayer(file.getPath());
				canvas.addLayerBottom(rFile);
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
		int returnVal = fc.showOpenDialog(canvas);

		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File file = fc.getSelectedFile();
			System.out.println("Open: " + file.getName());

			try {
				GPXFileLayer l = new GPXFileLayer(file.getCanonicalPath());
				l.setColor(Color.BLUE);
				l.setName(file.getName());
				canvas.addLayerTop(l);
				canvas.repaint();
			} catch (Exception e) {
				e.printStackTrace();
			}

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
		new SearchLocalityDialog(frame, canvas, "", "").setVisible(true);
	}

	public void showCoordinateInfoAtCoord() {
		Coordinate c = canvas.getCoordinate();
		new CoordinateDialog(frame, canvas, c).setVisible(true);
	}

	public void showCoordinateInfo(MouseEvent me) {
		Coordinate c = canvas.translatePoint(me.getPoint());
		new CoordinateDialog(frame, canvas, c).setVisible(true);
	}

	public void viewRubin() {
		String s = (String) JOptionPane.showInputDialog(frame, "Enter RUBIN (e.g. 12H5j):",
				"Search Grid Square", JOptionPane.PLAIN_MESSAGE, null, null, "");

		if (s != null && !s.trim().isEmpty()) {
			RubinLayer r = new RubinLayer(s.trim(), "Rubin", Color.green);
			Coordinate c = r.getMiddle();
			if (c != null) {
				canvas.delLayer("Rubin");
				canvas.addLayerTop(r);
				canvas.focus(c);
				canvas.repaint();
			} else {
				JOptionPane.showMessageDialog(frame,
						"'" + s + "' is not a valid RUBIN coordinate.\nExample format: 12H5j",
						"Invalid Format", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	public void showRubin(MouseEvent e) {
		Coordinate c = canvas.translatePoint(e.getPoint());
		String rubin = RUBIN.fromSweref99TM(c);
		RubinLayer r = new RubinLayer(rubin, "Rubin", Color.green);
		canvas.delLayer("Rubin");
		canvas.addLayerTop(r);
	}

	public void distanceAtCoord() {
		Coordinate c = canvas.getCoordinate();
		if (c == null) {
			JOptionPane.showMessageDialog(canvas, "Please select a point on the map first.");
			return;
		}
		new DistanceDialog(frame, canvas, c).setVisible(true);
	}

	public void distance(MouseEvent me) {
		Coordinate c = canvas.translatePoint(new Point(me.getX(), me.getY()));
		new DistanceDialog(frame, canvas, c).setVisible(true);
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
						"Press d and click on the map to show distance and direction";
		JOptionPane.showMessageDialog(frame, message, "Shortcuts", JOptionPane.INFORMATION_MESSAGE);
	}

	public void showLocality(MouseEvent e) {
		Coordinate c = canvas.translatePoint(new Point(e.getX(), e.getY()));
		MYSQLTableLayer ldb = (MYSQLTableLayer) canvas.getLayer("LokalDB");
		int localityID = ldb.findNearest(c, 1000);

		if (localityID != -1) {
			new EditLocalityDialog(this, frame, localityID, bridgeDialog, canvas).setVisible(true);
		}
	}
	
	public void showLocalityAtCoord() {
		Coordinate c = canvas.getCoordinate();
		MYSQLTableLayer ldb = (MYSQLTableLayer) canvas.getLayer("LokalDB");
		int localityID = ldb.findNearest(c,1000);
		if (localityID != -1) {
			new EditLocalityDialog(this, frame, localityID, bridgeDialog, canvas).setVisible(true);
		}
	}

	public void enterMoveMode(EditLocalityDialog dialog) {
		this.moveTarget = dialog;
		Cursor crosshair = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
		frame.setCursor(crosshair);
		canvas.setCursor(crosshair);
		if (dialog != null) {
			dialog.setCursor(crosshair);
		}
	}

	public void leaveMoveMode(MouseEvent me) {
		Coordinate mapP = canvas.translatePoint(me.getPoint());
		moveTarget.updateCoordinates(mapP);

		// Reset everything back to default
		Cursor defaultCursor = Cursor.getDefaultCursor();
		frame.setCursor(defaultCursor);
		canvas.setCursor(defaultCursor);
		moveTarget.setCursor(defaultCursor);

		moveTarget = null;
	}

	private void createLocalityAtCoord() {
		coord = canvas.getCoordinate();
		new CreateLocalityDialog(frame, this, canvas, bridgeDialog, coord).setVisible(true);
	}

	private void createLocalityDialog(MouseEvent me) {
		coord = canvas.translatePoint(me.getPoint());
		new CreateLocalityDialog(frame, this, canvas, bridgeDialog, coord).setVisible(true);
	}

	public void searchSpecimens() {
		// Check if the dialog is already open
		if (bridgeDialog != null && bridgeDialog.isVisible()) {
			bridgeDialog.toFront(); // Bring the existing window to the top
			bridgeDialog.requestFocus();
			return; // Exit the method so we don't create a duplicate
		}

		SpecimenService service = new SpecimenService();
		bridgeDialog = new SpecimenBridgeDialog(frame, service, canvas);

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

			Coordinate c = canvas.translatePoint(me.getPoint());
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
		MarkCoordinateDialog d = new MarkCoordinateDialog(frame, canvas);
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