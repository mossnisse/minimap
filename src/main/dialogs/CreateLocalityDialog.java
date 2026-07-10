package main.dialogs;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.Serial;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.swing.*;

import main.coords.*;
import main.core.*;
import main.core.MapCanvas;
import main.layers.MapLayers;

public class CreateLocalityDialog extends JDialog implements ActionListener {
	@Serial
	private static final long serialVersionUID = 5999128550024317489L;
	private final GUI gui;
	private final MapCanvas mapCanvas;
	private final SpecimenBridgeDialog bridgeDialog;
	Coordinate c;
	private JTextField localityT, districtT, provinceT, countryT, continentT, alternativeT, coordsourceT, locSizeT, categoryT, zoomLevelT;
	private JTextArea commentsT;
	private JCheckBox isPlaceT;
	private JButton cancel, ok;

	public CreateLocalityDialog(Frame owner, GUI gui, MapCanvas mapCanvas, SpecimenBridgeDialog bridge, Coordinate c) {
		super(owner, "Create New Locality", false);
		this.gui = gui;
		this.mapCanvas = mapCanvas;
		this.bridgeDialog = bridge;
		this.c = c;

		// Use Content Pane for Layout
		Container content = this.getContentPane();
		content.setLayout(new SpringLayout());

		initComponents(content);

		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentShown(ComponentEvent ce) {
				// Request focus on the cancel button to swallow stray keystrokes
				cancel.requestFocusInWindow();
			}
		});

		this.pack();
		this.setLocationRelativeTo(owner);
		this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
	}

	private void initComponents(Container content) {
		SpringLayout layout = (SpringLayout) content.getLayout();

		String continent = "";
		String country = "";
		String province = "";
		String district = "";

		String provName = MapLayers.provinceAt(mapCanvas, c);
		if (provName != null) province = provName;
		String distName = MapLayers.districtAt(mapCanvas, c);
		if (distName != null) district = distName;

		// Logic for Suggesting Name
		String suggestName = mapCanvas.layerManager.get(MapLayers.ORTNAMN)
				.map(odb -> odb.findNearest(mapCanvas.getCRS().convertTo(c, CoordSystem.SWEREF99TM), 1000))
				.orElse("");

		if (!"".equals(province)) {
			continent = "Europe";
			country = "Sweden";
		}

		// Initialize Components
		localityT = new JTextField(suggestName, 20);
		alternativeT = new JTextField(20);
		coordsourceT = new JTextField(20);
		commentsT = new JTextArea(4, 35);
		commentsT.setLineWrap(true);
		commentsT.setWrapStyleWord(true);
		JScrollPane commentScroll = new JScrollPane(commentsT);
		locSizeT = new JTextField(10);
		categoryT = new JTextField(15);
		zoomLevelT = new JTextField(5);
		isPlaceT = new JCheckBox("Is Place");
		continentT = new JTextField(continent, 15);
		countryT = new JTextField(country, 15);
		provinceT = new JTextField(province, 15);
		districtT = new JTextField(district, 15);
		cancel = new JButton("Cancel");
		ok = new JButton("OK");

		// Layout Flow using the addField helper
		JLabel lLoc = addField("Locality:", localityT, content, layout, 10, content);
		JLabel lAlt = addField("Alt Names:", alternativeT, content, layout, 10, lLoc);
		JLabel lSrc = addField("Coord Source:", coordsourceT, content, layout, 10, lAlt);

		// Comments Manual Layout
		JLabel lComm = new JLabel("Comments:");
		content.add(lComm);
		content.add(commentScroll);
		layout.putConstraint(SpringLayout.WEST, lComm, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, lComm, 10, SpringLayout.SOUTH, lSrc);
		layout.putConstraint(SpringLayout.WEST, commentScroll, 120, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, commentScroll, 0, SpringLayout.NORTH, lComm);

		JLabel lSize = addField("Size:", locSizeT, content, layout, 10, commentScroll);
		JLabel lCat = addField("Category:", categoryT, content, layout, 10, lSize);
		JLabel lZoom = addField("Zoom Level:", zoomLevelT, content, layout, 10, lCat);

		content.add(isPlaceT);
		layout.putConstraint(SpringLayout.WEST, isPlaceT, 120, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, isPlaceT, 10, SpringLayout.SOUTH, lZoom);

		JLabel lCont = addField("Continent:", continentT, content, layout, 10, isPlaceT);
		JLabel lCoun = addField("Country:", countryT, content, layout, 10, lCont);
		JLabel lProv = addField("Province:", provinceT, content, layout, 10, lCoun);
		JLabel lDist = addField("District:", districtT, content, layout, 10, lProv);

		// Buttons
		content.add(cancel);
		content.add(ok);
		layout.putConstraint(SpringLayout.WEST, cancel, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, cancel, 20, SpringLayout.SOUTH, lDist);
		layout.putConstraint(SpringLayout.WEST, ok, 10, SpringLayout.EAST, cancel);
		layout.putConstraint(SpringLayout.NORTH, ok, 0, SpringLayout.NORTH, cancel);

		// Final Anchors
		layout.putConstraint(SpringLayout.EAST, content, 20, SpringLayout.EAST, commentScroll);
		layout.putConstraint(SpringLayout.SOUTH, content, 10, SpringLayout.SOUTH, cancel);

		// Listeners
		cancel.addActionListener(this);
		ok.addActionListener(this);
		cancel.setActionCommand("cancel");
		ok.setActionCommand("ok");
	}

	private JLabel addField(String labelText, JComponent field, Container container, SpringLayout layout, int margin, Component topAnchor) {
		return SpringForm.addRow(labelText, field, container, layout, margin, topAnchor);
	}

	private void createLocality() {
		// Immediate UI Validation (No DB needed)
		// todo validate continent
		String localityName = localityT.getText().trim();
		if (localityName.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Locality name is required.");
			return;
		}

		final String sizeText = locSizeT.getText().trim();
		final int size;
		try {
			size = Integer.parseInt(sizeText);
			if (size < 0) throw new NumberFormatException();
		} catch (NumberFormatException nfe) {
			JOptionPane.showMessageDialog(this, "Size must be a positive integer.");
			return;
		}

		// Capture all other fields so doInBackground doesn't touch the UI
		final String distr = districtT.getText().trim();
		final String prov = provinceT.getText().trim();
		final String coun = countryT.getText().trim();
		final String cont = continentT.getText().trim();
		final String alt = alternativeT.getText().trim();
		final String src = coordsourceT.getText().trim();
		final String comm = commentsT.getText().trim();
		final String cat = categoryT.getText().trim();
		final String zlStr = zoomLevelT.getText().trim();
		final boolean isPlace = isPlaceT.isSelected();

		// Start Background Worker
		new SwingWorker<Boolean, Void>() {
			@Override
			protected Boolean doInBackground() throws Exception {
				gui.setCursorWait();

				Connection conn = DBConnection.getConn();
				if (localityExists(conn, localityName, distr, prov, coun)) {
					return false;
				}

				// Perform transformations in background
				Coordinate wgs84c = mapCanvas.getCRS().toWGS84(c);
				Coordinate swerefc = CoordSystem.SWEREF99TM.toProjected(wgs84c);
				Coordinate rt90c = CoordSystem.RT90.toProjected(wgs84c);

				int zli;
				try { zli = Integer.parseInt(zlStr); } catch (NumberFormatException e) { zli = -1; }

				executeInsert(conn, localityName, distr, prov, coun, cont, wgs84c, swerefc, rt90c,
						alt, src, comm, size, cat, zli, isPlace);
				return true;
			}

			@Override
			protected void done() {
				try {
					if (get()) {
						refreshMapsAndLists();
						dispose();
					} else {
						JOptionPane.showMessageDialog(CreateLocalityDialog.this, "Locality already exists.");
					}
				} catch (Exception e) {
					JOptionPane.showMessageDialog(CreateLocalityDialog.this, "Error: " + e.getMessage());
				} finally {
					gui.setCursorDefault();
				}
			}
		}.execute();
	}

	private void executeInsert(Connection conn, String localityName, String districtName, String provinceName,
							   String countryVal, String continentVal,
							   Coordinate wgs84c, Coordinate swerefc, Coordinate rt90c,
							   String alternativeVal, String coordsourceVal, String commentsVal, int size,
							   String categoryVal, int zli, boolean isPlace) throws SQLException {

		String sqlstmt = "INSERT INTO locality (locality, district, province, country, continent, lat, `long`, RT90N, RT90E, SWTMN, SWTME, createdby, alternative_names, coordinate_source, lcomments, Coordinateprecision, category, zoomLevel, isPlace) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

		try (PreparedStatement preparedStmt = conn.prepareStatement(sqlstmt)) {
			preparedStmt.setString(1, localityName);
			preparedStmt.setString(2, districtName);
			preparedStmt.setString(3, provinceName);
			preparedStmt.setString(4, countryVal);
			preparedStmt.setString(5, continentVal);
			preparedStmt.setDouble(6, wgs84c.getNorth());
			preparedStmt.setDouble(7, wgs84c.getEast());
			preparedStmt.setInt(8, (int) Math.round(rt90c.getNorth()));
			preparedStmt.setInt(9, (int) Math.round(rt90c.getEast()));
			preparedStmt.setInt(10, (int) Math.round(swerefc.getNorth()));
			preparedStmt.setInt(11, (int) Math.round(swerefc.getEast()));
			preparedStmt.setString(12, Settings.getValue("user"));
			preparedStmt.setString(13, alternativeVal);
			preparedStmt.setString(14, coordsourceVal);
			preparedStmt.setString(15, commentsVal);
			preparedStmt.setInt(16, size);
			preparedStmt.setString(17, categoryVal);
			preparedStmt.setInt(18, zli);
			preparedStmt.setBoolean(19, isPlace);

			preparedStmt.executeUpdate();
		}
	}

	private boolean localityExists(Connection conn, String localityName, String districtName, String provinceName, String countryName) throws SQLException {
		// We only need to know if at least one row exists
		String sql = "SELECT 1 FROM locality WHERE locality = ? AND district = ? AND province = ? AND country = ? LIMIT 1;";

		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			// Use trim() to prevent accidental space mismatches
			ps.setString(1, localityName.trim());
			ps.setString(2, districtName.trim());
			ps.setString(3, provinceName.trim());
			ps.setString(4, countryName.trim());

			try (ResultSet rs = ps.executeQuery()) {
				return rs.next(); // If there is a row, it exists
			}
		}
	}

	private void refreshMapsAndLists() {
		// Update UI elements
		if (bridgeDialog != null && bridgeDialog.isVisible()) {
			bridgeDialog.invalidateLocalityList();
		}

		mapCanvas.layerManager.get(MapLayers.LOKAL_DB).ifPresent(Layer::invalidateCache);
		mapCanvas.repaint();
	}

	@Override
	public void actionPerformed(ActionEvent ev) {
		if ("ok".equals(ev.getActionCommand())) {
			createLocality();
		} else if ("cancel".equals(ev.getActionCommand())) {
			this.dispose();
		}
	}
}