package main.dialogs;

import main.coords.*;
import main.core.*;
import main.core.Canvas;
import main.layers.MYSQLTableLayer;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.swing.*;

/* Dialog for viewing and editing already existing Localities in the db */

public class EditLocalityDialog extends JDialog implements ActionListener {
	private final main.core.Canvas canvas;
	private final GUI gui;
	private final int localityID;
	private final SpecimenBridgeDialog bridgeDialog;
	private String oldName;

	// Components
	private JTextField name, altNames, province, district, coordinateSource, localitySize, zoomLevel, category;
	private JTextArea comments;
	private JCheckBox isPlace;
	private JLabel labelCreated, labelModified;
	private JButton cancel, delete, ok, move;

	public EditLocalityDialog(GUI gui, Frame owner, int localityID, SpecimenBridgeDialog bridge, Canvas canvas) {
		// 'false' makes it non-modal, 'true' would stop interaction with map
		super(owner, "Edit Locality", false);

		this.canvas = canvas;
		this.gui = gui;
		this.localityID = localityID;
		this.bridgeDialog = bridge;

		// Set up Layout on the Dialog's content pane
		this.getContentPane().setLayout(new SpringLayout());

		initComponents();
		loadData();

		this.pack();
		this.setLocationRelativeTo(owner);
		this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentShown(ComponentEvent ce) {
				// Request focus on the cancel button to swallow stray keystrokes
				cancel.requestFocusInWindow();
			}
		});
		//this.setVisible(true);
	}

	private void initComponents() {
		Container content = this.getContentPane();
		SpringLayout layout = (SpringLayout) this.getContentPane().getLayout();

		// Initialize Components
		name = new JTextField(20);
		altNames = new JTextField(20);
		province = new JTextField(15);
		district = new JTextField(15);
		localitySize = new JTextField(10);
		coordinateSource = new JTextField(20);
		category = new JTextField(15);
		zoomLevel = new JTextField(5);
		comments = new JTextArea(4, 30);
		comments.setLineWrap(true);
		comments.setWrapStyleWord(true);
		javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(comments);
		// Force vertical scrollbar only when needed
		scrollPane.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setPreferredSize(new java.awt.Dimension(350, 80));

		isPlace = new JCheckBox("Is Place");

		labelCreated = new JLabel("Created: ");
		labelModified = new JLabel("Modified: ");

		cancel = new JButton("Cancel");
		delete = new JButton("Delete");
		ok = new JButton("OK - Change");
		move = new JButton("Move on Map");

		// Add to Layout
		JLabel lName = addField("Name:", name, content, layout, 10, content);
		JLabel lAlt = addField("Alt Names:", altNames, content, layout, 10, lName);
		JLabel lProv = addField("Province:", province, content, layout, 10, lAlt);
		JLabel lDist = addField("District:", district, content, layout, 10, lProv);
		JLabel lSize = addField("Size:", localitySize, content, layout, 10, lDist);
		JLabel lSrc = addField("Source:", coordinateSource, content, layout, 10, lSize);

		JLabel lComm = new JLabel("Comments:");
		content.add(lComm);
		content.add(scrollPane);

		layout.putConstraint(SpringLayout.WEST, lComm, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, lComm, 10, SpringLayout.SOUTH, lSrc);

		layout.putConstraint(SpringLayout.WEST, scrollPane, 120, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, scrollPane, 0, SpringLayout.NORTH, lComm);

		// Comments is a JTextArea, so the next field needs a bigger gap (70-80px)
		JLabel lCat = addField("Category:", category, content, layout, 10, scrollPane);
		JLabel lZoom = addField("Zoom:", zoomLevel, content, layout, 10, lCat);

		// Metadata Labels
		add(labelCreated);
		layout.putConstraint(SpringLayout.WEST, labelCreated, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, labelCreated, 15, SpringLayout.SOUTH, lZoom);

		add(labelModified);
		layout.putConstraint(SpringLayout.WEST, labelModified, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, labelModified, 5, SpringLayout.SOUTH, labelCreated);

		// Checkbox
		add(isPlace);
		layout.putConstraint(SpringLayout.WEST, isPlace, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, isPlace, 10, SpringLayout.SOUTH, labelModified);

		// Buttons
		add(move);
		add(cancel);
		add(delete);
		add(ok);

		layout.putConstraint(SpringLayout.WEST, move, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, move, 0, SpringLayout.SOUTH, isPlace);

		// Bottom Row: Cancel, Delete, OK
		layout.putConstraint(SpringLayout.WEST, cancel, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, cancel, 20, SpringLayout.SOUTH, move);

		layout.putConstraint(SpringLayout.WEST, delete, 10, SpringLayout.EAST, cancel);
		layout.putConstraint(SpringLayout.NORTH, delete, 0, SpringLayout.NORTH, cancel);

		layout.putConstraint(SpringLayout.WEST, ok, 10, SpringLayout.EAST, delete);
		layout.putConstraint(SpringLayout.NORTH, ok, 0, SpringLayout.NORTH, cancel);

		// Update content pane boundaries
		layout.putConstraint(SpringLayout.EAST, content, 20, SpringLayout.EAST, scrollPane);
		layout.putConstraint(SpringLayout.SOUTH, content, 10, SpringLayout.SOUTH, cancel);

		// Listeners
		cancel.addActionListener(this);
		delete.addActionListener(this);
		ok.addActionListener(this);
		cancel.setActionCommand("cancel");
		delete.setActionCommand("delete");
		ok.setActionCommand("ok");
		move.addActionListener(this);
		move.setActionCommand("move");
	}

	private JLabel addField(String labelText, Component field, Container container, SpringLayout layout, int margin, Component topAnchor) {
		JLabel label = new JLabel(labelText);
		container.add(label);
		container.add(field);

		// Label Constraints: Use 'container' as the anchor, not 'this'
		layout.putConstraint(SpringLayout.WEST, label, 10, SpringLayout.WEST, container);

		// Logic to handle the very first field vs subsequent fields
		if (topAnchor == container) {
			layout.putConstraint(SpringLayout.NORTH, label, margin, SpringLayout.NORTH, container);
		} else {
			layout.putConstraint(SpringLayout.NORTH, label, margin, SpringLayout.SOUTH, topAnchor);
		}

		// Field Constraints (Align to a fixed column at x=120)
		layout.putConstraint(SpringLayout.WEST, field, 120, SpringLayout.WEST, container);
		layout.putConstraint(SpringLayout.NORTH, field, 0, SpringLayout.NORTH, label);

		return label;
	}

	private void loadData() {
		String sql = "SELECT locality, alternative_names, province, district, " +
				"coordinate_source, lcomments, created, createdBy, modified, modifiedBy, " +
				"Coordinateprecision, category, zoomLevel, isPlace FROM locality WHERE ID = ?";
		try {
			Connection conn = DBConnection.getConn();
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {

				stmt.setInt(1, localityID);
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						oldName = rs.getString(1);
						name.setText(rs.getString(1));
						altNames.setText(rs.getString(2));
						province.setText(rs.getString(3));
						district.setText(rs.getString(4));
						coordinateSource.setText(rs.getString(5));
						comments.setText(rs.getString(6));

						// Metadata Labels (Columns 9, 10, 11, 12)
						labelCreated.setText("Created: " + rs.getString(7) + " by " + rs.getString(8));
						labelModified.setText("Modified: " + rs.getString(9) + " by " + rs.getString(10));

						localitySize.setText(rs.getString(11));
						category.setText(rs.getString(12));
						zoomLevel.setText(rs.getString(13));

						isPlace.setSelected(rs.getInt(14) == 1);

						setTitle("View Locality: " + name.getText());
					}
				}
			}
		} catch (SQLException e) {
			JOptionPane.showMessageDialog(this, "Database Error: " + e.getMessage());
			e.printStackTrace();
		}
	}



	private void deleteLocality() {
		int bridgeCount = localityBridgeUses(localityID);
		int usesCount = localityUses();

		StringBuilder warning = new StringBuilder();
		boolean errorState = (bridgeCount == -1 || usesCount == -1);

		if (errorState) {
			warning.append("CRITICAL: Could not verify all specimen links due to a database error.\n");
		}

		if (bridgeCount > 0) {
			warning.append("? Linked to ").append(bridgeCount).append(" specimen bridges.\n");
		}

		if (usesCount > 0) {
			warning.append("? Used by ").append(usesCount).append(" specimen records.\n");
		}

		if (warning.length() == 0) {
			warning.append("Are you sure you want to delete this locality?");
		} else {
			warning.append("\nDeleting it will break these links. Proceed?");
		}

		// Use WARNING_MESSAGE if there are links or errors, otherwise QUESTION_MESSAGE
		int messageType = (bridgeCount > 0 || usesCount > 0 || errorState)
				? JOptionPane.WARNING_MESSAGE
				: JOptionPane.QUESTION_MESSAGE;

		int dialogResult = JOptionPane.showConfirmDialog(
				this,
				warning.toString(),
				"Confirm Delete",
				JOptionPane.YES_NO_OPTION,
				messageType
		);

		if (dialogResult == JOptionPane.YES_OPTION) {
			try {
				Connection conn = DBConnection.getConn();
				String sqlstmt = "DELETE FROM locality WHERE ID = ?";
				try (PreparedStatement statement = conn.prepareStatement(sqlstmt)) {
					statement.setInt(1, localityID);
					statement.execute();

					if (bridgeDialog != null && bridgeDialog.isVisible()) {
						bridgeDialog.invalidateLocalityList();
					}

					Layer layer = canvas.getLayer("LokalDB");
					if (layer instanceof MYSQLTableLayer mysqlLayer) {
						mysqlLayer.invalidateCache();
					}
					this.dispose();
				}
			} catch (SQLException e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(this, " Error deleting locality: " + e.getMessage());
			}
		}
	}

	private int localityUses() {
		try {
			Connection conn = DBConnection.getConn();
			String checkSql = "SELECT count(*) FROM specimens where province = ? AND district = ? AND locality = ? ";
			try (PreparedStatement statem = conn.prepareStatement(checkSql)) {
				statem.setString(1, province.getText());
				statem.setString(2, district.getText());
				statem.setString(3, oldName);
				try (ResultSet rs = statem.executeQuery()) {
					if (rs.next()) {
						return rs.getInt(1);
					}
				}
			}
		} catch (SQLException e) {
			System.err.println("Error checking specimen locality usage: " + e.getMessage());
		}
		return -1;
	}

	private int localityBridgeUses(int localityID) {
		try {
			Connection conn = DBConnection.getConn();
			String checkSql = "SELECT count(*) from specimen_locality where specimen_locality.locality_ID = ?";
			try (PreparedStatement statem = conn.prepareStatement(checkSql)) {
				statem.setInt(1, localityID);
				try (ResultSet rs = statem.executeQuery()) {
					if (rs.next()) {
						return rs.getInt(1);
					}
				}
			}
		} catch (SQLException e) {
			System.err.println("Error checking bridge usage: " + e.getMessage());
		}
		return -1;
	}

	private void updateLocality() {
		// check if size is positive integer
		try {
			int size = Integer.parseInt(localitySize.getText());
			if (size < 0) throw new NumberFormatException();
		} catch (NumberFormatException nfe) {
			JOptionPane.showMessageDialog(this, "Size is not a positive integer", "InfoBox: Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		try {
			Connection conn = DBConnection.getConn();

			// Check if the user is attempting to change the name
			if (oldName != null && !oldName.equals(name.getText())) {
				int uses = localityUses();

				// Only show the warning if the locality is actually used, or if the check failed (-1)
				if (uses > 0 || uses == -1) {
					String warningMsg;
					if (uses > 0) {
						warningMsg = "\"" + oldName + "\" is currently used in " + uses + " specimen records.\n" +
								"Note: Changing this name here will NOT automatically update those specimens.\n\n" +
								"Do you want to proceed with the name change?";
					} else {
						warningMsg = "Could not verify if \"" + oldName + "\" is used by any specimens due to a database error.\n\n" +
								"Do you want to proceed with the name change anyway?";
					}

					// Give the user the option to cancel
					int confirm = JOptionPane.showConfirmDialog(this, warningMsg, "Confirm Name Change", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

					if (confirm != JOptionPane.YES_OPTION) {
						return; // Abort the update process
					}
				}
			}

			String sqlstmt = "UPDATE locality SET locality = ?, district = ?, province = ?, alternative_names = ?, coordinate_source = ?, lcomments = ?, modified = NOW(), modifiedBy = ?, Coordinateprecision = ?, category = ?, zoomLevel =?, isPlace =?  WHERE ID =?";
			try (PreparedStatement statement = conn.prepareStatement(sqlstmt)) {
				statement.setString(1, name.getText());
				statement.setString(2, district.getText());
				statement.setString(3, province.getText());
				statement.setString(4, altNames.getText());
				statement.setString(5, coordinateSource.getText());
				statement.setString(6, comments.getText());
				statement.setString(7, Settings.getValue("user"));
				statement.setString(8, localitySize.getText());
				statement.setString(9, category.getText());

				int zl;
				try {
					zl = Integer.parseInt(zoomLevel.getText());
				} catch (NumberFormatException e) {
					zl = -1;
				}
				statement.setInt(10, zl);
				statement.setBoolean(11, isPlace.isSelected());
				statement.setInt(12, localityID);

				statement.execute();

				if (oldName != null && !oldName.equals(name.getText())) {
					if (bridgeDialog != null && bridgeDialog.isVisible()) {
						bridgeDialog.invalidateLocalityList();
					}
				}
				Layer layer = canvas.getLayer("LokalDB");
				if (layer instanceof MYSQLTableLayer mysqlLayer) {
					mysqlLayer.invalidateCache();
				}
			} catch (SQLException e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(this, "Error updating locality: " + e.getMessage());
			}
		} catch (SQLException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error updating locality: " + e.getMessage());
		}
	}

	public void updateCoordinates(Point p) {
		// p.y is North, p.x is East in SWEREF99TM
		int newN = p.y;
		int newE = p.x;

		// Optional: Auto-lookup the new District/Province for the new spot
		// Update the DB directly
		try {
			Connection conn = DBConnection.getConn();
			String sql = "UPDATE locality SET SWTMN = ?, SWTME = ?, lat = ?, `long` = ?, RT90N = ?, RT90E = ? WHERE ID = ?";
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				Coordinate wgs84c = CoordSystem.SWEREF99TM.toWGS84(newN, newE);
				Point rt90c = CoordSystem.RT90.toProjected(wgs84c);
				stmt.setInt(1, newN);
				stmt.setInt(2, newE);
				stmt.setDouble(3, wgs84c.getNorth());
				stmt.setDouble(4, wgs84c.getEast());
				stmt.setInt(5, rt90c.y);
				stmt.setInt(6, rt90c.x);
				stmt.setInt(7, localityID);
				stmt.executeUpdate();

				// Refresh layers
				Layer layer = canvas.getLayer("LokalDB");
				if (layer instanceof MYSQLTableLayer mysqlLayer) {
					mysqlLayer.invalidateCache();
				}
				canvas.repaint();
				setTitle("View Locality: " + name.getText());
				setCursor(Cursor.getDefaultCursor());

				JOptionPane.showMessageDialog(this, "Locality moved successfully!");
			}
		} catch (SQLException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error moving the locality: " + e.getMessage());
		}
	}

	@Override
	public void actionPerformed(ActionEvent ev) {
		String cmd = ev.getActionCommand();
		if ("ok".equals(cmd)) {
			updateLocality();
			canvas.repaint();
			this.dispose();
		} else if ("cancel".equals(cmd)) {
			this.dispose();
		} else if ("delete".equals(cmd)) {
			deleteLocality();
			canvas.repaint();
		} else if ("move".equals(cmd)) {
			// Minimize dialog or just tell the user to click
			//this.setState(Frame.ICONIFIED); // Optional: hide dialog so they can see the map
			this.setTitle("SELECT NEW LOCATION ON MAP...");
			this.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
			gui.enterMoveMode(this);
		}
	}
}