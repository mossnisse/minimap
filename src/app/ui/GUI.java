package app.ui;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;

import app.AppContext;
import app.BusyCursor;
import app.LantmaterietAccount;
import app.MapLayers;
import app.plugin.MenuContributions;
import app.plugin.Plugin;
import app.plugin.PluginContext;
import app.plugin.PluginManager;
import app.plugin.herbarium.HerbariumPlugin;
import app.plugin.collection.PrivateCollectionPlugin;
import app.project.ProjectManager;
import gis.core.*;
import gis.ui.*;
import gis.layers.*;
import gis.coords.*;
import gis.csv.CsvFile;
import gis.geopackage.GeoPackageReader;
import gis.shapefile.ShapefileReader;

public class GUI implements BusyCursor {
	private JFrame frame;
	private MapCanvas mapCanvas;
	private AppContext ctx;
	private PluginContext pluginContext;
	private PluginManager pluginManager;
	private ProjectManager projectManager;

	public GUI() {
	}

	private void createAndShowGUI() {
		ProjectManager.Bootstrap bootstrap;
		try {
			bootstrap = ProjectManager.bootstrap();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, "Could not initialize projects:\n" + e.getMessage(),
					"Startup error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		ctx = AppContext.create(() -> {
			String input = new PasswDialog().open();
			return "codeCancel".equals(input) ? null : input;
		});

		// Set up the frame and canvas
		frame = new JFrame("Minimap");
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		mapCanvas = new MapCanvas();
		mapCanvas.getLayerManager().addLayersChangedListener(this::attachLantmaterietErrorHandlers);
		MapLayers.installDefaultLayers(mapCanvas, ctx);
		pluginContext = new PluginContext(frame, mapCanvas, this, this::rebuildMenuBar, ctx,
				() -> projectManager == null ? null : projectManager.activeDirectory());
		pluginManager = new PluginManager(pluginContext);
		pluginManager.register(new HerbariumPlugin());
		pluginManager.register(new PrivateCollectionPlugin());
		projectManager = new ProjectManager(bootstrap, this::rebuildMenuBar, frame, mapCanvas, ctx, pluginManager);
		pluginManager.setEnabledChangedListener(projectManager::saveEnabledPluginsQuietly);

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
				Coordinate clicked = mapCanvas.translatePoint(e.getPoint());
				if (pluginContext.dispatchClick(e, clicked)) {
					return;
				}
				if (Keyboard.isKeyDown(KeyEvent.VK_R)) {
					showRubin(e);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_C)) {
					showCoordinateInfo(e);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_K)) {
					OpenKartbildcom(e);
				} else if (Keyboard.isKeyDown(KeyEvent.VK_D)) {
					distance(e);
				} else {
					mapCanvas.setCoordinate(clicked);
				}
			}
		});

		mapCanvas.addMouseWheelListener(e -> {
			int rot = e.getWheelRotation();
			double step = (rot > 0) ? 1.2 : 0.8;
			mapCanvas.zoom(step);
		});

		frame.addWindowListener(new WindowAdapter() {
			@Override public void windowClosing(WindowEvent e) { requestApplicationClose(); }
		});

		projectManager.restoreInitial();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		Keyboard.activate();
	}

	public Container createContentPane() {
		// Create the content-pane-to-be.
		JPanel contentPane = new JPanel(new BorderLayout());
		contentPane.setOpaque(true);
		return contentPane;
	}

	public JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();

		JMenu fileMenu = new JMenu("File");
		menuBar.add(fileMenu);
		fileMenu.add(item("Open File", KeyEvent.VK_O, e -> openFile()));
		fileMenu.add(item("Set Canvas CRS", e -> setCanvasCRS()));
		fileMenu.add(item("Exit", KeyEvent.VK_Q, e -> requestApplicationClose()));

		JMenu viewMenu = new JMenu("View");
		menuBar.add(viewMenu);
		viewMenu.add(item("Zoom in", KeyEvent.VK_P, e -> mapCanvas.zoom(0.5)));
		viewMenu.add(item("Zoom out", KeyEvent.VK_M, e -> mapCanvas.zoom(2)));
		viewMenu.add(item("Layers", KeyEvent.VK_T, e -> showLayerDialog()));
		viewMenu.add(item("Mark/Find Coordinate", KeyEvent.VK_U, e -> MarkCoordDialog()));
		viewMenu.add(item("View Coordinate", KeyEvent.VK_K, e -> showCoordinateInfoAtCoord()));
		viewMenu.add(item("View Rubin", KeyEvent.VK_R, e -> viewRubin()));
		viewMenu.add(item("Search place names", KeyEvent.VK_F, e -> searchPlaceNames()));
		viewMenu.add(item("Distance and Direction", KeyEvent.VK_D, e -> distanceAtCoord()));

		JMenu layersMenu = new JMenu("Layers");
		menuBar.add(layersMenu);
		layersMenu.add(item("Add Topowebkartan (SLU)", e -> addTopowebkartan()));
		layersMenu.add(item("Add Topowebkartan (Lantmäteriet)", e -> addLantmaterietTopowebkartan()));
		layersMenu.add(item("Add Open Street Map", e -> addOSM()));
		layersMenu.add(item("Add Landskap Layer", e -> addLandskap()));
		layersMenu.add(item("Add Socken Layer", e -> addSocknar()));
		layersMenu.add(item("Add Lantmäteriet ortnamn Layer", e -> addOrtnamn()));
		layersMenu.add(item("Add .gpx layer", KeyEvent.VK_G, e -> openGPXFile()));
		layersMenu.add(item("Add shapefile layer (.shp)", KeyEvent.VK_H, e -> openShapeFile()));
		layersMenu.add(item("Add GeoPackage layer (.gpkg)", e -> openGeoPackageFile()));
		layersMenu.add(item("Add .csv point layer (table editor)", e -> openCsvFile()));
		JMenuItem raster = item("Add raster layer", e -> openFile());
		raster.setMnemonic(KeyEvent.VK_G); // mnemonic only - Ctrl+G belongs to the .gpx item
		layersMenu.add(raster);

		for (Plugin plugin : pluginManager.all()) {
			if (pluginManager.isActive(plugin.id())) {
				plugin.contributeMenus(new MenuContributions(fileMenu, viewMenu, layersMenu));
			}
		}

		menuBar.add(buildProjectMenu());
		menuBar.add(buildPluginsMenu());
		menuBar.add(Box.createHorizontalGlue());

		JMenu helpMenu = new JMenu("Help");
		menuBar.add(helpMenu);
		helpMenu.add(item("About",
				e -> JOptionPane.showMessageDialog(frame, "Minimap, written by Nils Ericson 2013")));
		helpMenu.add(item("Shortcuts", e -> showShortcuts()));

		return menuBar;
	}

	private static JMenuItem item(String text, ActionListener action) {
		JMenuItem menuItem = new JMenuItem(text);
		menuItem.addActionListener(action);
		return menuItem;
	}

	/** A menu item whose mnemonic key doubles as its Ctrl accelerator. */
	private static JMenuItem item(String text, int key, ActionListener action) {
		JMenuItem menuItem = new JMenuItem(text, key);
		menuItem.setAccelerator(KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK));
		menuItem.addActionListener(action);
		return menuItem;
	}

	public void rebuildMenuBar() {
		if (frame == null || pluginManager == null) return;
		frame.setJMenuBar(createMenuBar());
		frame.revalidate();
		frame.repaint();
		if (projectManager != null) frame.setTitle("Minimap — " + projectManager.activeName());
	}

	private JMenu buildProjectMenu() {
		JMenu menu = new JMenu("Project");
		JMenuItem create = new JMenuItem("New…");
		create.addActionListener(e -> {
			String name = JOptionPane.showInputDialog(frame, "Project name:", "New project",
					JOptionPane.PLAIN_MESSAGE);
			if (name != null) projectManager.createBlank(name);
		});
		menu.add(create);

		JMenuItem open = new JMenuItem("Open / Switch…");
		open.addActionListener(e -> {
			java.util.List<String> names = projectManager.listProjects();
			String selected = (String) JOptionPane.showInputDialog(frame, "Open project:",
					"Projects", JOptionPane.PLAIN_MESSAGE, null, names.toArray(), projectManager.activeName());
			if (selected != null) projectManager.switchTo(selected);
		});
		menu.add(open);

		JMenuItem save = new JMenuItem("Save");
		save.addActionListener(e -> {
			try { projectManager.saveCurrent(); }
			catch (IOException ex) { JOptionPane.showMessageDialog(frame, ex.getMessage(), "Save failed", JOptionPane.ERROR_MESSAGE); }
		});
		menu.add(save);

		JMenuItem saveAs = new JMenuItem("Save As…");
		saveAs.addActionListener(e -> {
			String name = JOptionPane.showInputDialog(frame, "New project name:", "Save project as",
					JOptionPane.PLAIN_MESSAGE);
			if (name != null) projectManager.saveAs(name);
		});
		menu.add(saveAs);
		return menu;
	}

	private JMenu buildPluginsMenu() {
		JMenu menu = new JMenu("Plugins");
		for (Plugin plugin : pluginManager.all()) {
			JCheckBoxMenuItem item = new JCheckBoxMenuItem(plugin.displayName(),
					pluginManager.isActive(plugin.id()));
			item.addActionListener(e -> {
				boolean wanted = item.isSelected();
				if (!pluginManager.setEnabled(plugin.id(), wanted)) item.setSelected(!wanted);
			});
			menu.add(item);
		}
		return menu;
	}

	private void searchPlaceNames() {
		new PlaceNameSearchDialog(frame, this, mapCanvas, ctx.placeNames).setVisible(true);
	}

	private void requestApplicationClose() {
		if (projectManager != null && projectManager.prepareAndSaveForExit()) {
			frame.dispose();
			System.exit(0);
		}
	}

	@Override
	public void setCursorWait() {
		frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		for (Window window : frame.getOwnedWindows() ){
            if (window.isVisible()) {
                window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
        }
	}
	
	@Override
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
				"Raster Files", "tif", "tiff", "png", "jpg", "jpeg", "gif", "bmp");
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

	public void openShapeFile() {
		final JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter("ESRI Shapefiles", "shp"));
		if (fc.showOpenDialog(mapCanvas) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = fc.getSelectedFile();

		// Take the CRS from the .prj sidecar; ask when it is missing or unrecognized
		CoordSystem crs = ShapefileReader.guessCRS(file.getPath());
		if (crs == null) {
			crs = (CoordSystem) JOptionPane.showInputDialog(frame,
					"No .prj file found (or its projection is not supported).\n"
							+ "Which coordinate system does the shapefile use?",
					"Shapefile coordinate system", JOptionPane.QUESTION_MESSAGE,
					null, CoordSystem.values(), mapCanvas.getCRS());
			if (crs == null) {
				return; // cancelled
			}
		}

		setCursorWait();
		try {
			ShapeFileLayer layer = new ShapeFileLayer(file.getPath(), mapCanvas, crs);
			layer.setName(file.getName());
			layer.setColor(Color.RED);
			mapCanvas.getLayerManager().addLayerTop(layer);
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(frame,
					"Can't open shapefile: " + file.getPath() + "\n" + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
		} finally {
			setCursorDefault();
		}
	}

	public void openGeoPackageFile() {
		final JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter("GeoPackage files", "gpkg"));
		if (fc.showOpenDialog(mapCanvas) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = fc.getSelectedFile();

		// List the feature tables and take the CRS from the file's metadata
		GeoPackageReader.FeatureTable table;
		CoordSystem crs;
		try (GeoPackageReader reader = new GeoPackageReader(file.getPath())) {
			java.util.List<GeoPackageReader.FeatureTable> tables = reader.getFeatureTables();
			if (tables.isEmpty()) {
				JOptionPane.showMessageDialog(frame,
						"No feature tables found in: " + file.getPath(),
						"Error", JOptionPane.ERROR_MESSAGE);
				return;
			}
			if (tables.size() == 1) {
				table = tables.get(0);
			} else {
				table = (GeoPackageReader.FeatureTable) JOptionPane.showInputDialog(frame,
						"Which feature table do you want to add?",
						"GeoPackage feature table", JOptionPane.QUESTION_MESSAGE,
						null, tables.toArray(), tables.get(0));
				if (table == null) {
					return; // cancelled
				}
			}
			crs = reader.guessCRS(table);
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(frame,
					"Can't open GeoPackage: " + file.getPath() + "\n" + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		// Ask when the file's spatial reference system is unsupported
		if (crs == null) {
			crs = (CoordSystem) JOptionPane.showInputDialog(frame,
					"The layer's coordinate system is not recognized.\n"
							+ "Which coordinate system does it use?",
					"GeoPackage coordinate system", JOptionPane.QUESTION_MESSAGE,
					null, CoordSystem.values(), mapCanvas.getCRS());
			if (crs == null) {
				return; // cancelled
			}
		}

		setCursorWait();
		try {
			GeoPackageLayer layer = new GeoPackageLayer(file.getPath(), table.tableName, mapCanvas, crs);
			layer.setName(file.getName() + ":" + table.tableName);
			layer.setColor(Color.RED);
			mapCanvas.getLayerManager().addLayerTop(layer);
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(frame,
					"Can't open GeoPackage layer: " + file.getPath() + "\n" + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
		} finally {
			setCursorDefault();
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

	public void addLantmaterietTopowebkartan() {
		if (!LantmaterietAccount.isConfigured()) {
			String help = "Organizations must use a production system account for WMTS services, "
					+ "not the personal Geotorget login. Private individuals use their Geotorget login.";
			if (!promptLantmaterietCredentials("Lantmäteriet WMTS credentials", help, "Connect")) return;
		}
		addLantmaterietTopowebLayer(MapLayers.topowebLantmateriet(mapCanvas));
	}

	private boolean promptLantmaterietCredentials(String title, String message, String acceptLabel) {
		JTextField username = new JTextField(valueOrEmpty(LantmaterietAccount.username()), 24);
		// Prefilled so that correcting only the username does not force the whole
		// password to be retyped; both fields are required below.
		JPasswordField password = new JPasswordField(valueOrEmpty(LantmaterietAccount.password()), 24);
		int explanationRows = Math.clamp((message.length() / 55) + 1, 2, 7);
		JTextArea explanation = new JTextArea(message, explanationRows, 46);
		explanation.setEditable(false);
		explanation.setLineWrap(true);
		explanation.setWrapStyleWord(true);
		explanation.setOpaque(false);
		explanation.setFocusable(false);

		JPanel form = new JPanel(new GridBagLayout());
		GridBagConstraints label = new GridBagConstraints();
		label.gridx = 0;
		label.anchor = GridBagConstraints.LINE_END;
		label.insets = new Insets(3, 0, 3, 8);
		GridBagConstraints input = new GridBagConstraints();
		input.gridx = 1;
		input.weightx = 1;
		input.fill = GridBagConstraints.HORIZONTAL;
		input.insets = new Insets(3, 0, 3, 0);

		label.gridy = input.gridy = 0;
		form.add(new JLabel("Username / system account:"), label);
		form.add(username, input);
		label.gridy = input.gridy = 1;
		form.add(new JLabel("Password:"), label);
		form.add(password, input);

		JPanel fields = new JPanel(new BorderLayout(0, 8));
		fields.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		fields.add(explanation, BorderLayout.NORTH);
		fields.add(form, BorderLayout.CENTER);
		fields.add(new JLabel("Saved as plain text in " + Settings.activeSharedFile().getName()
				+ " and reused by every project."), BorderLayout.SOUTH);

		Object[] options = {acceptLabel, "Cancel"};
		if (JOptionPane.showOptionDialog(frame, fields, title, JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE, null, options, options[0]) != JOptionPane.OK_OPTION) return false;

		char[] passwordChars = password.getPassword();
		try {
			String user = username.getText().trim();
			String secret = new String(passwordChars);
			if (user.isBlank() || secret.isBlank()) {
				JOptionPane.showMessageDialog(frame, "Both username and password are required.",
						"Missing credentials", JOptionPane.WARNING_MESSAGE);
				return false;
			}
			LantmaterietAccount.save(user, secret);
			return true;
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(frame, "Could not save Lantmäteriet credentials:\n" + ex.getMessage(),
					"Settings error", JOptionPane.ERROR_MESSAGE);
			return false;
		} finally {
			java.util.Arrays.fill(passwordChars, '\0');
		}
	}

	private void addLantmaterietTopowebLayer(TopowebLayer layer) {
		layer.setHttpErrorHandler(statusCode -> handleLantmaterietHttpError(layer, statusCode));
		mapCanvas.setCRS(layer.getCRS());
		mapCanvas.getLayerManager().addLayerBottom(layer);
	}

	/** Also covers official layers restored from a saved project rather than added through the menu. */
	private void attachLantmaterietErrorHandlers() {
		for (Layer candidate : mapCanvas.getLayerManager().getLayers()) {
			if (candidate instanceof TopowebLayer layer
					&& layer.getProvider() == TopowebLayer.Provider.LANTMATERIET) {
				layer.setHttpErrorHandler(statusCode -> handleLantmaterietHttpError(layer, statusCode));
			}
		}
	}

	private void handleLantmaterietHttpError(TopowebLayer layer, int statusCode) {
		String message = "Lantmäteriet rejected the WMTS request (HTTP " + statusCode + ").\n\n"
				+ "For an organization, enter the production system account (usually four letters and "
				+ "four digits, for example abcd0001), not your personal Geotorget login. For a private "
				+ "account, use the Geotorget login. Also verify that this account has permission for "
				+ "Topografisk webbkarta Visning, översiktlig under Mitt konto > Behörigheter.\n\n"
				+ "Enter credentials to retry. Cancel removes the failed layer.";
		if (!promptLantmaterietCredentials("Lantmäteriet authentication failed", message, "Save and retry")) {
			layer.setHttpErrorHandler(null);
			mapCanvas.getLayerManager().delLayer(layer);
			return;
		}
		try {
			layer.setCredentials(LantmaterietAccount.username(), LantmaterietAccount.password());
			mapCanvas.repaint();
		} catch (IllegalArgumentException ex) {
			JOptionPane.showMessageDialog(frame, ex.getMessage(), "Credential error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private static String valueOrEmpty(String value) {
		return value == null ? "" : value;
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

	public void openCsvFile() {
		final JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter("CSV files", "csv", "txt", "tsv"));
		if (fc.showOpenDialog(mapCanvas) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = fc.getSelectedFile();

		setCursorWait();
		try {
			CsvFile csv = CsvFile.read(file.toPath());
			CsvPointLayer layer = new CsvPointLayer(file, csv, mapCanvas.getCRS(), mapCanvas);
			layer.setColor(Color.MAGENTA);
			layer.guessMapping();
			layer.rebuildPoints();
			mapCanvas.getLayerManager().addLayerTop(layer);
			CsvEditorDialog.open(frame, mapCanvas, layer);
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(frame,
					"Can't open CSV file: " + file.getPath() + "\n" + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
		} finally {
			setCursorDefault();
		}
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
		// The clicked point is in the canvas CRS, which is not necessarily SWEREF99TM
		String rubin = RUBIN.fromRT90(mapCanvas.getCRS().convertTo(c, CoordSystem.RT90));
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

	private void OpenKartbildcom(MouseEvent me) {
		if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			System.err.println("Opening a browser is not supported on this system.");
			return;
		}

		try {
			int mapType = 20; // Specific layer ID for Kartbild  = generalkarta1
			int zoomLevel = 12;

			Coordinate c = mapCanvas.translatePoint(me.getPoint());
			Coordinate wgs84  = mapCanvas.getCRS().toWGS84(c);

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
