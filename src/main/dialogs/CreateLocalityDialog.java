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
import main.core.Canvas;
import main.layers.H2TableLayer;
import main.layers.MYSQLTableLayer;
import main.layers.TNGPolygonFileLayer;

public class CreateLocalityDialog extends JDialog implements ActionListener {
	@Serial
	private static final long serialVersionUID = 5999128550024317489L;
	private final GUI gui;
	private final Canvas canvas;
	private final SpecimenBridgeDialog bridgeDialog;
	Coordinate SWTM;
	private JTextField localityT, districtT, provinceT, countryT, continentT, alternativeT, coordsourceT, locSizeT, categoryT, zoomLevelT;
	private JTextArea commentsT;
	private JCheckBox isPlaceT;
	private JButton cancel, ok;

	public CreateLocalityDialog(Frame owner, GUI gui, main.core.Canvas canvas, SpecimenBridgeDialog bridge, Coordinate SWTM) {
		super(owner, "Create New Locality", false);
		this.gui = gui;
		this.canvas = canvas;
		this.bridgeDialog = bridge;
		this.SWTM = SWTM;

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

		String province = "";
		String district = "";

		TNGPolygonFileLayer provinces = (TNGPolygonFileLayer) canvas.layerManager.getLayer("provinser");
		TNGPolygonFileLayer districts = (TNGPolygonFileLayer) canvas.layerManager.getLayer("socknar");

		if (provinces != null) {
			TNGPolygonFileLayer.Province pr = provinces.inPolygon(SWTM);
			if (pr != null) province = pr.getName();
		}

		if (districts != null) {
			TNGPolygonFileLayer.Province so = districts.inPolygon(SWTM);
			if (so != null) district = so.getName();
		}

		// Logic for Suggesting Name
		String suggestName = "";
		H2TableLayer odb = (H2TableLayer) canvas.layerManager.getLayer("Ortnamnsdb");
		if (odb != null) {
			suggestName = odb.findNearest(SWTM, 1000);
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
		continentT = new JTextField("Europe", 15);
		countryT = new JTextField("Sweden", 15);
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
		JLabel label = new JLabel(labelText);
		container.add(label);
		container.add(field);

		layout.putConstraint(SpringLayout.WEST, label, 10, SpringLayout.WEST, container);
		if (topAnchor == container) {
			layout.putConstraint(SpringLayout.NORTH, label, margin, SpringLayout.NORTH, container);
		} else {
			layout.putConstraint(SpringLayout.NORTH, label, margin, SpringLayout.SOUTH, topAnchor);
		}

		layout.putConstraint(SpringLayout.WEST, field, 120, SpringLayout.WEST, container);
		layout.putConstraint(SpringLayout.NORTH, field, 0, SpringLayout.NORTH, label);

		return label;
	}

	private boolean createLocality() {
		gui.setCursorWait();

		// Gather all inputs first
		String localityName = localityT.getText();
		String districtName = districtT.getText();
		String provinceName = provinceT.getText();
		String countryVal = countryT.getText();
		String continentVal = continentT.getText();
		String alternativeVal = alternativeT.getText();
		String coordsourceVal = coordsourceT.getText();
		String commentsVal = commentsT.getText();
		String sizeVal = locSizeT.getText();
		String categoryVal = categoryT.getText();
		boolean isPlace = isPlaceT.isSelected();

		String zl = zoomLevelT.getText();
		try {
			Integer.parseInt(zl);
		} catch (NumberFormatException nfe) {
			zl = "-1";
		}

		try {
			// Obtain connection (Scope fixed: declared here)
			Connection conn = DBConnection.getConn();

			// Coordinate calculations
			Coordinate wgs84c = CoordSystem.SWEREF99TM.toWGS84(SWTM);
			Coordinate rt90c = CoordSystem.RT90.toProjected(wgs84c);
			String RT90Nt = Integer.toString((int) Math.round(rt90c.getNorth()));
			String RT90Et = Integer.toString((int) Math.round(rt90c.getEast()));

			// 4. Check if locality already exists
			String sqltestifU = "SELECT COUNT(1) FROM locality WHERE locality = ? AND district = ? AND province = ? AND country = 'Sweden';";
			try (PreparedStatement preparedStmt = conn.prepareStatement(sqltestifU)) {
				preparedStmt.setString(1, localityName);
				preparedStmt.setString(2, districtName);
				preparedStmt.setString(3, provinceName);

				try (ResultSet result = preparedStmt.executeQuery()) {
					if (result.next() && result.getInt(1) > 0) {
						JOptionPane.showMessageDialog(null, "There is already a locality with the same name in the district", "InfoBox: Error", JOptionPane.INFORMATION_MESSAGE);
						return false;
					}
				}
			}

			// Validate size
			try {
				int size = Integer.parseInt(sizeVal);
				if (size < 0) throw new NumberFormatException();
			} catch (NumberFormatException nfe) {
				JOptionPane.showMessageDialog(null, "Size is not a positive integer", "InfoBox: Error", JOptionPane.INFORMATION_MESSAGE);
				return false;
			}

			// 6. Perform Insert
			String sqlstmt = "INSERT INTO locality (locality, district, province, country, continent, lat, `long`, RT90N, RT90E, SWTMN, SWTME, createdby, alternative_names, coordinate_source, lcomments, Coordinateprecision, category, zoomLevel, isPlace) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

			try (PreparedStatement preparedStmt = conn.prepareStatement(sqlstmt)) {
				preparedStmt.setString(1, localityName);
				preparedStmt.setString(2, districtName);
				preparedStmt.setString(3, provinceName);
				preparedStmt.setString(4, countryVal);
				preparedStmt.setString(5, continentVal);
				preparedStmt.setDouble(6, wgs84c.getNorth());
				preparedStmt.setDouble(7, wgs84c.getEast());
				preparedStmt.setString(8, RT90Nt);
				preparedStmt.setString(9, RT90Et);
				preparedStmt.setInt(10, (int) Math.round(SWTM.getNorth()));
				preparedStmt.setInt(11, (int) Math.round(SWTM.getEast()));
				preparedStmt.setString(12, Settings.getValue("user"));
				preparedStmt.setString(13, alternativeVal);
				preparedStmt.setString(14, coordsourceVal);
				preparedStmt.setString(15, commentsVal);
				preparedStmt.setString(16, sizeVal);
				preparedStmt.setString(17, categoryVal);
				preparedStmt.setString(18, zl);
				preparedStmt.setBoolean(19, isPlace);

				preparedStmt.executeUpdate();
			}

			// Update UI elements
			if (bridgeDialog != null && bridgeDialog.isVisible()) {
				bridgeDialog.invalidateLocalityList();
			}

			Layer layer = canvas.layerManager.getLayer("LokalDB");
			if (layer instanceof MYSQLTableLayer mysqlLayer) {
				mysqlLayer.invalidateCache();
			}
			canvas.repaint();

			return true;

		} catch (SQLException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "Database operation failed: " + e.getMessage(), "InfoBox: SQL Error", JOptionPane.INFORMATION_MESSAGE);
			return false;
		} finally {
			// This ensures the cursor always returns to default, regardless of success or failure
			gui.setCursorDefault();
		}
	}

	@Override
	public void actionPerformed(ActionEvent ev) {
		if ("ok".equals(ev.getActionCommand())) {
			if (createLocality()) {
				this.dispose();
			}
		} else if ("cancel".equals(ev.getActionCommand())) {
			this.dispose();
		}
	}
}