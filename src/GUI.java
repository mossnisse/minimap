import geometry.BoundingBox;
import geometry.Point;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.Box;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

public class GUI implements ActionListener, ItemListener, MouseListener, MouseWheelListener, NActionListener {
	static JFrame frame;
	public static Canvas canvas;
	static Point coord;
	int mouseDownX, mouseDownY;
	ShapePointFile orter;
	static JFrame sframe;
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
		canvas.addMouseListener(gui);
		canvas.addMouseWheelListener(gui);
		//coord = new Point(0, 0);
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
		JMenuItem menuItem0, menuItem1, menuItem2, menuItem3, menuItem4, menuItem5, menuItem6, menuItem7, menuItem8, menuItem9, menuItem10, menuItem11;
		menuBar = new JMenuBar();

		// Build the first menu.
		menu = new JMenu("File");
		// menu.setMnemonic(KeyEvent.VK_A);
		menu.getAccessibleContext().setAccessibleDescription(
				"The only menu in this program that has menu items");
		menuBar.add(menu);

		menuItem0 = new JMenuItem("Open File", KeyEvent.VK_O);
		// menuItem.setMnemonic(KeyEvent.VK_T); //used constructor instead
		menuItem0.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,
				ActionEvent.CTRL_MASK));
		menuItem0.getAccessibleContext().setAccessibleDescription(
				"This doesn't really do anything");
		menuItem0.addActionListener(this);
		menu.add(menuItem0);

		menuItem1 = new JMenuItem("Open .gpx File", KeyEvent.VK_G);
		// menuItem.setMnemonic(KeyEvent.VK_T); //used constructor instead
		menuItem1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G,
				ActionEvent.CTRL_MASK));
		menuItem1.getAccessibleContext().setAccessibleDescription(
				"This doesn't really do anything");
		menuItem1.addActionListener(this);
		menu.add(menuItem1);

		menuItem2 = new JMenuItem("Save as .csv", KeyEvent.VK_S);
		menuItem2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
				ActionEvent.CTRL_MASK));
		menuItem2.getAccessibleContext().setAccessibleDescription(
				"This doesn't really do anything");
		menuItem2.addActionListener(this);
		menu.add(menuItem2);

		menuItem2 = new JMenuItem("Set user", KeyEvent.VK_I);
		menuItem2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I,
				ActionEvent.CTRL_MASK));
		menuItem2.getAccessibleContext().setAccessibleDescription(
				"It sets the name for the registrator");
		menuItem2.addActionListener(this);
		menu.add(menuItem2);

		menuItem3 = new JMenuItem("Exit", KeyEvent.VK_Q);
		menuItem3.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q,
				ActionEvent.CTRL_MASK));
		menuItem3.getAccessibleContext().setAccessibleDescription(
				"This doesn't really do anything");
		menuItem3.addActionListener(this);
		menu.add(menuItem3);

		menu2 = new JMenu("View");
		menuBar.add(menu2);
		menuItem4 = new JMenuItem("Zoom in", KeyEvent.VK_P);
		// menuItem.setMnemonic(KeyEvent.VK_T); //used constructor instead
		menuItem4.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P,
				ActionEvent.CTRL_MASK));
		menuItem4.addActionListener(this);
		menu2.add(menuItem4);

		menuItem5 = new JMenuItem("Zoom out", KeyEvent.VK_M);
		// menuItem.setMnemonic(KeyEvent.VK_T); //used constructor instead
		menuItem5.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M,
				ActionEvent.CTRL_MASK));
		menuItem5.addActionListener(this);
		menu2.add(menuItem5);

		menuItem6 = new JMenuItem("View Coordinate", KeyEvent.VK_K);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem6.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_K,
				ActionEvent.CTRL_MASK));
		menuItem6.addActionListener(this);
		menu2.add(menuItem6);

		menuItem8 = new JMenuItem("View Rubin", KeyEvent.VK_R);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem8.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R,
				ActionEvent.CTRL_MASK));
		menuItem8.addActionListener(this);
		menu2.add(menuItem8);

		menuItem7 = new JMenuItem("Search localities", KeyEvent.VK_F);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem7.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F,
				ActionEvent.CTRL_MASK));
		//menuItem7.addActionListener(this);
		menu2.add(menuItem7);

		menuItem8 = new JMenuItem("Distance and Direction", KeyEvent.VK_D);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem8.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D,
				ActionEvent.CTRL_MASK));
		menuItem8.addActionListener(this);
		menu2.add(menuItem8);
		
		menuItem9 = new JMenuItem("Search specimens", KeyEvent.VK_E);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem9.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E,
				ActionEvent.CTRL_MASK));
		menuItem9.addActionListener(this);
		menu2.add(menuItem9);
		
		menuItem10 = new JMenuItem("Show lokality at marker", KeyEvent.VK_T);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem10.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T,
				ActionEvent.CTRL_MASK));
		menuItem10.addActionListener(this);
		menu2.add(menuItem10);
		
		menuItem11 = new JMenuItem("Create lokality at marker", KeyEvent.VK_Y);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem11.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y,
				ActionEvent.CTRL_MASK));
		menuItem11.addActionListener(this);
		menu2.add(menuItem11);

		menuBar.add(Box.createHorizontalGlue());

		menu3 = new JMenu("Help");
		menuBar.add(menu3);

		menuItem9 = new JMenuItem("About");
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem9.addActionListener(this);
		menu3.add(menuItem9);
		
		menuItem11 = new JMenuItem("Shortcuts");
		menuItem11.addActionListener(this);
		menu3.add(menuItem11);

		menuItem10 = new JMenuItem("Layers", KeyEvent.VK_L);
		// menuItem.setMnemonic(KeyEvent.VK_K); //used constructor instead
		menuItem10.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L,
				ActionEvent.CTRL_MASK));
		menuItem10.addActionListener(this);
		menu2.add(menuItem10);

		return menuBar;
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		// TODO Auto-generated method stub
		/*
		 * JMenuItem source = (JMenuItem)(e.getSource()); String s =
		 * "Item event detected." + newline + "    Event source: " +
		 * source.getText() + " (an instance of " + getClassName(source) + ")" +
		 * newline + "    New state: " + ((e.getStateChange() ==
		 * ItemEvent.SELECTED) ? "selected":"unselected"); output.append(s +
		 * newline); output.setCaretPosition(output.getDocument().getLength());
		 */
		//System.out.println("changed");
	}


	public static void setCursorWait() {
		//getComponent();
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
			/*
			 * try { GPXFile l; l = new GPXFile(file.getCanonicalPath());
			 * l.setColor(Color.ORANGE); l.setName(file.getName());
			 * canvas.addLayer(l); } catch (ParserConfigurationException e)
			 * { // TODO Auto-generated catch block e.printStackTrace(); }
			 * catch (SAXException e) { // TODO Auto-generated catch block
			 * e.printStackTrace(); } catch (IOException e) { // TODO
			 * Auto-generated catch block e.printStackTrace(); }
			 */

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
			// This is where a real application would open the file.
			// log.append("Opening: " + file.getName() + "." + newline);
		} else {
			// log.append("Open command cancelled by user." + newline);
			System.out.println("Save cancelled");
		}
	}

	public void search() {
		
		if (sList == null) {
			SearchDialog d = new SearchDialog(frame, this, "");
			d.setVisible(true);
		} else {
			SearchDialog d = new SearchDialog(frame, this, sList.getSelectedText());
			d.setVisible(true);
		}

		//d.setModalityType(JDialog.ModalityType.MODELESS);

		

		/*if(d.isEntered()) {
						//String provins = d.getProvins();
						String lokal = d.getLokal();
						int prNr = d.getProvinsNr();
						//String provins = d.getProvins();
						//String socken = d.getSocken();
						search(prNr, "", lokal);

						//System.out.println("lokal: " + lokal);
					}*/
	}

	public void viewCoordinate() {
		CoordinateDialog d = new CoordinateDialog(frame, canvas.getCoordinate(),
				(TNGPolygonFile) canvas.getLayer("provinser"),
				(TNGPolygonFile) canvas.getLayer("socknar"));
		d.setVisible(true);
		coord = d.getCoordinate();
		canvas.focus(coord);
		canvas.setCoordinate(coord);
	}

	public void viewRubin() {
		String s = (String) JOptionPane.showInputDialog(frame, "Rubin", "Customized Dialog", JOptionPane.PLAIN_MESSAGE, null, null,"");
		if ((s != null) && (s.length() > 0)) {
			Rubin r = new Rubin(s, "Rubin", Color.green);
			canvas.delLayer("Rubin");
			canvas.addLayerTop(r);
			Point p = r.getMiddle();
			canvas.focus(p);
		}

	}

	public void distance() {
		DistanceDialog d = new DistanceDialog(frame);
		d.setVisible(true);
		String distance = d.getDistance();
		String direction = d.getDirection();
		System.out.println("Distance: "+distance+" Direction: "+direction);
		Distance dist = new Distance("dist", canvas.getCoordinate(), Integer.parseInt(distance), direction);
		dist.setColor(Color.red);
		dist.setHidden(false);
		canvas.delLayer("dist");
		canvas.addLayerTop(dist);
	}

	public void userDialog() {
		SettUserDialog l = new SettUserDialog();
		l.setVisible(true);
	}

	@Override
	public void actionPerformed(ActionEvent arg0) {
		JMenuItem source = (JMenuItem) (arg0.getSource());
		switch (source.getText()) {
			case "Open File":
				openFile();
				break;
			case "Open .gpx File":
				openGPXFile();
				break;
			case "Save as .csv":
				saveCSV();
				break;
			case "Exit":
				System.exit(0);
				break;
			case "Search":
				search();
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
				JOptionPane.showMessageDialog(frame, "Minimap, written by Nils Ericson 2013");
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
			case "Search specimens":
				searchSpecimens();
				break;
			case "Show lokality at marker":
				showLokalAtCoord();
				break;
			case "Create lokality at marker":
				createLokalAtCoord();
				break;
			case "Shortcuts":
				String message = "Press s and click on the map to create a new Locality\nPress a and click on the map to edit Locality information\n"
						+ "Press c and click on the map to show info about the coordinate\nPress r and click on the map to show the 5x5 km RUBIN ruta";
				 JOptionPane.showMessageDialog(frame, message, "Shortcuts", JOptionPane.INFORMATION_MESSAGE);
				break;
		}
	}

	public void search(int prNr, String socken, String lokal) {
		H2Table od = (H2Table) canvas.getLayer("Ortnamnsdb");
		TNGPointFile ans = od.find(prNr, lokal);
		if (ans.size() != 0) {
			ans.setColor(Color.blue);
			// System.out.println(ans);
			BoundingBox b = ans.getBounds();

			if (b.getWidth() < 5000) {
				Point middle = b.getMidlePoint();
				b.setX1(middle.getX() - 2500);
				b.setY1(middle.getY() - 2500);
				b.setX2(middle.getX() + 2500);
				b.setY2(middle.getY() + 2500);
			}
			// System.out.println(b);
			canvas.setBounds(b);
			canvas.delLayer("ans");
			canvas.addLayerTop(ans);
		} else {
			System.out.println("no hits");
		}
	}

	public Container createContentPane() {
		// Create the content-pane-to-be.
		JPanel contentPane = new JPanel(new BorderLayout());
		contentPane.setOpaque(true);
		return contentPane;
	}

	public void showCoordDialog(MouseEvent arg0) {
		coord = canvas.translatePoint2(new Point(arg0.getX(), arg0.getY()));
		canvas.setCoordinate(coord);
		CoordinateDialog d = new CoordinateDialog(frame, coord,
				(TNGPolygonFile) canvas.getLayer("provinser"),
				(TNGPolygonFile) canvas.getLayer("socknar"));
		d.cancel.requestFocusInWindow();
		d.setVisible(true);
		d.cancel.requestFocusInWindow();
		coord = d.getCoordinate();
		canvas.focus(coord);
		canvas.setCoordinate(coord);
	}

	public void showRubin(MouseEvent arg0) {
		System.out.print("show RUBIN: ");
		Point p = canvas.translatePoint2(new Point(arg0.getX(), arg0.getY()));
		 String s = Coordinates.getRUBINfromSweref99TM(p);
		//Coordinates sweref99TM = new Coordinates(p);
		//System.out.print(" sweref99tm "+sweref99TM+" ");
		//String s =  sweref99TM.getRUBINfromSweref99TM();
		System.out.println(s);
		Rubin r = new Rubin(s, "Rubin", Color.green);
		canvas.delLayer("Rubin");
		canvas.addLayerTop(r);
		//Point p2 = r.getMiddle();
		//canvas.focus(p2);
	}

	public void showLokal(MouseEvent arg0) {
		System.out.println("show Locality");
		Point p = canvas.translatePoint2(new Point(arg0.getX(), arg0.getY()));

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
		
		/*
		CreateLocalityD d = new CreateLocalityD(frame, Integer.toString(coord.getY()), Integer.toString(coord.getX()), provins, socken);
		d.setVisible(true);*/
		
		JFrame lframe = new JFrame();
		CreateLocalityDialog diag = new CreateLocalityDialog(lframe, Integer.toString(coord.getY()), Integer.toString(coord.getX()), provins, socken);
		lframe.add(diag);
		diag.cancel.requestFocusInWindow();
		//canvas.repaint();
	}
	
	public static void searchSpecimens() {
		sframe = new JFrame("Specimens");
		//sframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		sframe.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		sframe.addWindowListener(new WindowAdapter() {
	        @Override
	        public void windowClosing(WindowEvent event) {
	        	try {
					Settings.setValue("specimen dialog", "closed");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        	sframe.setVisible(false);
	        	sframe.dispose();
	        }
	    });
		sList = new SpecimenList();
		sframe.add(sList);
		sframe.setSize(400, 1000);
		try {
			Settings.setValue("specimen dialog", "open");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		sframe.setVisible(true);
	}


	private void createLocalityDialog(MouseEvent me) {
		System.out.println("skapa lokal");
		//Point p = canvas.translatePoint2(new Point(me.getX(), me.getY()));
		coord = canvas.translatePoint2(new Point(me.getX(), me.getY()));
		canvas.setCoordinate(coord);
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
		//CreateLocalityD d = new CreateLocalityD(frame, Integer.toString(coord.getY()), Integer.toString(coord.getX()), provins, socken);
		//d.setVisible(true);
		JFrame lframe = new JFrame();
		CreateLocalityDialog diag = new CreateLocalityDialog(lframe, Integer.toString(coord.getY()), Integer.toString(coord.getX()), provins, socken);
		lframe.add(diag);
		diag.cancel.requestFocusInWindow();
		canvas.repaint();
		canvas.repaint();
	}
	
	

	@Override
	public void mouseClicked(MouseEvent arg0) {
		//System.out.println("W-key down: "+IsKeyPressed.isWPressed());
		System.out.println("mouse clicked: " + arg0.getX() + "key: " +Keyboard.getKeys() );


		if(Keyboard.isKeyDown(65)) {
			showLokal(arg0);
		} else if (Keyboard.isKeyDown(82)) {
			showRubin(arg0);
		} else if (Keyboard.isKeyDown(67)) {
			showCoordDialog(arg0);
		} else if (Keyboard.isKeyDown(83)) {
			createLocalityDialog(arg0);
		} else {
			Point p  = canvas.translatePoint2(new Point(arg0.getX(), arg0.getY()));
			canvas.setCoordinate(p);
		}
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseExited(MouseEvent arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void mousePressed(MouseEvent arg0) {
		mouseDownX = arg0.getX();
		mouseDownY = arg0.getY();
		canvas.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

	}

	@Override
	public void mouseReleased(MouseEvent arg0) {
		int x = arg0.getX();
		int y = arg0.getY();
		int dx = x - mouseDownX;
		int dy = y - mouseDownY;
		canvas.panPixel(dx, dy);
		canvas.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent arg0) {
		int rot = arg0.getWheelRotation();
		double step = 1;
		if (rot > 0)
			step = 1.2;
		else if (rot < 0)
			step = 0.8;
		canvas.zoom(step);
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
		// TODO Auto-generated method stub
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
			search();
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
	}
	}
}
