import geometry.Point;
import coords.*;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.io.Serial;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.swing.*;

public class CreateLocalityDialog extends JDialog implements ActionListener {
	@Serial
	private static final long serialVersionUID = 5999128550024317489L;
	private final GUI gui;
	private final Canvas canvas;
	private SpecimenBridgeDialog bridgeDialog;
	private JTextField localityT, districtT, provinceT, countryT, continentT, SWTMNT, SWTMET, alternativeT, coordsourceT, locSizeT, categoryT, zoomLevelT;
	private JTextArea commentsT;
	private JCheckBox isPlaceT;
	private JScrollPane commentScroll;
	private JButton cancel, ok;

	public CreateLocalityDialog(Frame owner, GUI gui, Canvas canvas, SpecimenBridgeDialog bridge, String SWTMN, String SWTME, String province, String district) {
		super(owner, "Create New Locality", true); // Set to true for Modal behavior
		this.gui = gui;
		this.canvas = canvas;
		this.bridgeDialog = bridge;

		// Use Content Pane for Layout
		Container content = this.getContentPane();
		content.setLayout(new SpringLayout());

		initComponents(content, SWTMN, SWTME, province, district);

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
		this.setVisible(true);
	}

	private void initComponents(Container content, String SWTMN, String SWTME, String province, String district) {
		SpringLayout layout = (SpringLayout) content.getLayout();

		// 1. Logic for Suggesting Name
		String suggestName = "";
		H2TableLayer odb = (H2TableLayer) canvas.getLayer("Ortnamnsdb");
		if (odb != null) {
			Point p = new Point(Integer.parseInt(SWTMN), Integer.parseInt(SWTME));
			suggestName = odb.findNearest(p, 1000);
		}

		// 2. Initialize Components
		localityT = new JTextField(suggestName, 20);
		alternativeT = new JTextField(20);
		coordsourceT = new JTextField(20);
		commentsT = new JTextArea(4, 35);
		commentsT.setLineWrap(true);
		commentsT.setWrapStyleWord(true);
		commentScroll = new JScrollPane(commentsT);
		SWTMNT = new JTextField(SWTMN, 10);
		SWTMET = new JTextField(SWTME, 10);
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

		// 3. Layout Flow using the addField helper
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

		JLabel lN = addField("Sweref99TM N:", SWTMNT, content, layout, 10, commentScroll);
		JLabel lE = addField("E:", SWTMET, content, layout, 10, lN);
		JLabel lSize = addField("Size:", locSizeT, content, layout, 10, lE);
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
		System.out.println("Skapar lokal");  // TODO trace print
		String localityName = localityT.getText();
		String districtName = districtT.getText();
		String provinceName = provinceT.getText();
		String SWTMNt = SWTMNT.getText();
		String SWTMEt = SWTMET.getText();
		String zl = zoomLevelT.getText();
		try {
		        Integer.parseInt(zl);
		} catch (NumberFormatException nfe) {
			zl = "0";
		}
		
		int isPlaceV = 0;
		if (isPlaceT.isSelected()) {
			isPlaceV =1;
		}
		Coordinates SWTMc = new Coordinates(Double.parseDouble(SWTMNt), Double.parseDouble(SWTMEt));
		Coordinates wgs84c = SWTMc.toWGS84(CoordSystem.SWEREF99TM);
		Coordinates rt90c = wgs84c.toProjected(CoordSystem.RT90);
		String RT90Nt = Long.toString(Math.round(rt90c.getNorth()));
		String RT90Et = Long.toString(Math.round(rt90c.getEast()));
		
		//check if locality already exists and show message if do
		String sqltestifU = "SELECT COUNT(1) FROM locality WHERE locality = ? AND district = ? AND province = ? AND country = 'Sweden';";
		try {
			Connection conn = DBConnection.getConn();
			PreparedStatement preparedStmt = conn.prepareStatement(sqltestifU);
			preparedStmt.setString (1, localityName);
		    preparedStmt.setString (2, districtName);
		    preparedStmt.setString (3, provinceName);
		    ResultSet result = preparedStmt.executeQuery();
		    result.next();
		    int i = result.getInt(1);
		    System.out.println("result"+i);
		    if (i ==1) {
		    	JOptionPane.showMessageDialog(null, "There is already a locality with the same name in the district", "InfoBox: "+"Error", JOptionPane.INFORMATION_MESSAGE);
		    	gui.setCursorDefault();
				return false;
		    }
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			JOptionPane.showMessageDialog(null, "couldnt check if locality already exists", "InfoBox: " + "SQL Error", JOptionPane.INFORMATION_MESSAGE);
			gui.setCursorDefault();
			return false;
		}

		// check if size is possitive integer
		try {
	        Integer.parseInt(locSizeT.getText());
		} catch (NumberFormatException nfe) {
			JOptionPane.showMessageDialog(null, "Size is not an possitive integer", "InfoBox: " + "Error", JOptionPane.INFORMATION_MESSAGE);
			gui.setCursorDefault();
			return false;
		}
		
		String sqlstmt = "INSERT INTO locality (locality, district, province, country, continent, lat, `long`, RT90N, RT90E, SWTMN, SWTME, createdby, alternative_names, coordinate_source, lcomments, Coordinateprecision, category, zoomLevel, isPlace) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
		
	    System.out.println(sqlstmt + " - " + localityName + "loc size: "+ locSizeT.getText()); // TODO trace print
		try {
			Connection conn = DBConnection.getConn();
			PreparedStatement preparedStmt = conn.prepareStatement(sqlstmt);
			preparedStmt.setString (1, localityName);
		    preparedStmt.setString (2, districtName);
		    preparedStmt.setString (3, provinceName);
		    preparedStmt.setString (4, "Sweden");
		    preparedStmt.setString (5, "Europe");
		    preparedStmt.setDouble(6, wgs84c.getNorth());
		    preparedStmt.setDouble(7, wgs84c.getEast());
		    preparedStmt.setString (8, RT90Nt);
		    preparedStmt.setString (9, RT90Et);
		    preparedStmt.setString (10, SWTMNt);
		    preparedStmt.setString (11, SWTMEt);
		    preparedStmt.setString (12, Settings.getValue("user"));
		    preparedStmt.setString (13, alternativeT.getText() );
		    preparedStmt.setString (14, coordsourceT.getText());
		    preparedStmt.setString (15, commentsT.getText());
		    preparedStmt.setString (16, locSizeT.getText());
		    preparedStmt.setString (17, categoryT.getText());
		    preparedStmt.setString (18, zl);
		    preparedStmt.setInt(19, isPlaceV);
		    
		    preparedStmt.execute();

			if (bridgeDialog != null && bridgeDialog.isVisible()) {
				bridgeDialog.updateLocalityList();
			}

			gui.setCursorDefault();
			return true;
				
		} catch (SQLException | IOException e1) {
				// TODO Auto-generated catch block
			e1.printStackTrace();
			JOptionPane.showMessageDialog(null, "Couldn't create locality", "InfoBox: " + "SQL Error", JOptionPane.INFORMATION_MESSAGE);
			gui.setCursorDefault();
			return false;
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