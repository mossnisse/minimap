package main.dialogs;

import main.coords.*;
import main.core.*;
import main.core.MapCanvas;
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
	private final MapCanvas mapCanvas;
	private final GUI gui;
	private final int localityID;
	private final SpecimenBridgeDialog bridgeDialog;
	private String oldName;
	private Coordinate pendingCoords = null;

	// Components
	private JTextField name, altNames, district, province, country, continent, coordinateSource, localitySize, zoomLevel, category;
	private JTextArea comments;
	private JCheckBox isPlace;
	private JLabel labelCreated, labelModified;
	private JButton cancel, delete, ok, move;

	public EditLocalityDialog(GUI gui, Frame owner, int localityID, SpecimenBridgeDialog bridge, MapCanvas mapCanvas) {
		// 'false' makes it non-modal, 'true' would stop interaction with map
		super(owner, "Edit Locality", false);

		this.mapCanvas = mapCanvas;
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
	}

	private void initComponents() {
		Container content = this.getContentPane();
		SpringLayout layout = (SpringLayout) this.getContentPane().getLayout();

		// Initialize Components
		name = new JTextField(20);
		altNames = new JTextField(20);
		district = new JTextField(15);
		province = new JTextField(15);
		country = new JTextField(15);
		continent = new JTextField(15);
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
		JLabel lDist = addField("District:", district, content, layout, 10, lAlt);
		JLabel lProv = addField("Province:", province, content, layout, 10, lDist);
		JLabel lCountry = addField("Country:", country, content, layout, 10, lProv);
		JLabel lContinent = addField("Continent:", continent, content, layout, 10, lCountry);
		JLabel lSize = addField("Size:", localitySize, content, layout, 10, lContinent);
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
		String sql = "SELECT locality, alternative_names, district, province, country, continent, " +
				"coordinate_source, lcomments, created, createdBy, modified, modifiedBy, " +
				"Coordinateprecision, category, zoomLevel, isPlace FROM locality WHERE ID = ?";
		try {
			Connection conn = DBConnection.getConn();
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {

				stmt.setInt(1, localityID);
				try (ResultSet rs = stmt.executeQuery()) {
					if (rs.next()) {
						oldName = rs.getString("locality");
						name.setText(rs.getString("locality"));
						altNames.setText(rs.getString("alternative_names"));
						district.setText(rs.getString("district"));
						province.setText(rs.getString("province"));
						country.setText(rs.getString("country"));
						continent.setText(rs.getString("continent"));
						coordinateSource.setText(rs.getString("coordinate_source"));
						comments.setText(rs.getString("lcomments"));

						// Metadata Labels (Columns 9, 10, 11, 12)
						labelCreated.setText("Created: " + rs.getString("created") + " by " + rs.getString("createdBy"));
						labelModified.setText("Modified: " + rs.getString("modified") + " by " + rs.getString("modifiedBy"));

						localitySize.setText(rs.getString("Coordinateprecision"));
						category.setText(rs.getString("category"));
						zoomLevel.setText(rs.getString("zoomLevel"));

						isPlace.setSelected(rs.getInt("isPlace") == 1);

						setTitle("Edit Locality: " + name.getText());
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

		if (warning.isEmpty()) {
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

					Layer layer = mapCanvas.layerManager.getLayer("LokalDB");
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
		// Validate Input First
		// todo validate continent
		final String newName = name.getText().trim();
		if (newName.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Locality name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (oldName != null && !oldName.equals(newName)) {
			int uses = localityUses(); // Since this is a fast count, it's usually okay on EDT
			if (uses > 0 || uses == -1) {
				String msg = (uses > 0) ? "\"" + oldName + "\" is used in " + uses + " records. Update anyway?"
						: "Could not verify usage. Proceed?";
				if (JOptionPane.showConfirmDialog(this, msg, "Warning", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
					return;
				}
			}
		}

		final String sizeText = localitySize.getText().trim();
		final int size;
		try {
			size = Integer.parseInt(sizeText);
			if (size < 0) {
				throw new NumberFormatException();
			}
		} catch (NumberFormatException nfe) {
			JOptionPane.showMessageDialog(this, "Size must be a positive integer.", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}


		// Start Background Worker
		new SwingWorker<Boolean, Void>() {
			@Override
			protected Boolean doInBackground() throws Exception {
				gui.setCursorWait();
				Connection conn = DBConnection.getConn();

				// Perform Update
				StringBuilder sql = new StringBuilder("UPDATE locality SET locality=?, district=?, province=?, country=?, continent=?, " +
						"alternative_names=?, coordinate_source=?, lcomments=?, modified=NOW(), modifiedBy=?, " +
						"Coordinateprecision=?, category=?, zoomLevel=?, isPlace=?");

				// Append coordinate columns if a move happened
				if (pendingCoords != null) {
					sql.append(", lat=?, `long`=?, SWTMN=?, SWTME=?, RT90N=?, RT90E=?");
				}
				sql.append(" WHERE ID=?");

				try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
					int paramIdx = 1;
					stmt.setString(paramIdx++, name.getText().trim());
					stmt.setString(paramIdx++, district.getText().trim());
					stmt.setString(paramIdx++, province.getText().trim());
					stmt.setString(paramIdx++, country.getText().trim());
					stmt.setString(paramIdx++, continent.getText().trim());
					stmt.setString(paramIdx++, altNames.getText().trim());
					stmt.setString(paramIdx++, coordinateSource.getText().trim());
					stmt.setString(paramIdx++, comments.getText().trim());
					stmt.setString(paramIdx++, Settings.getValue("user")); // modifiedBy
					stmt.setInt(paramIdx++, size);

					stmt.setString(paramIdx++, category.getText().trim());
					int zl;
					try { zl = Integer.parseInt(zoomLevel.getText().trim()); } catch (Exception e) { zl = -1; }
					stmt.setInt(paramIdx++, zl);

					stmt.setBoolean(paramIdx++, isPlace.isSelected());

					if (pendingCoords != null) {
						// todo only set sweref and rt90 if in Sweden
						// todo check if moved outside district and province
						Coordinate wgs84 = mapCanvas.getCRS().toWGS84(pendingCoords);
						Coordinate sweref = CoordSystem.SWEREF99TM.toProjected(wgs84);
						Coordinate rt90 = CoordSystem.RT90.toProjected(wgs84);

						stmt.setDouble(paramIdx++, wgs84.getNorth());
						stmt.setDouble(paramIdx++, wgs84.getEast());
						stmt.setInt(paramIdx++, (int) Math.round(sweref.getNorth()));
						stmt.setInt(paramIdx++, (int) Math.round(sweref.getEast()));
						stmt.setInt(paramIdx++, (int) Math.round(rt90.getNorth()));
						stmt.setInt(paramIdx++, (int) Math.round(rt90.getEast()));
					}

					stmt.setInt(paramIdx, localityID);
					stmt.executeUpdate();
				}
				return true;
			}

			@Override
			protected void done() {
				gui.setCursorDefault();
				try {
					if (get()) { // If update wasn't aborted
						if (oldName != null && !oldName.equals(newName) && bridgeDialog != null && bridgeDialog.isVisible()) {
							bridgeDialog.invalidateLocalityList();
						}

						Layer layer = mapCanvas.layerManager.getLayer("LokalDB");
						if (layer instanceof MYSQLTableLayer mysqlLayer) {
							mysqlLayer.invalidateCache();
						}
						mapCanvas.repaint();
						dispose();
					}
				} catch (Exception e) {
					e.printStackTrace();
					JOptionPane.showMessageDialog(EditLocalityDialog.this, "Error updating locality: " + e.getMessage());
				}
			}
		}.execute();
	}

	public void updateCoordinates(Coordinate c) {
		this.pendingCoords = c;

		// Feedback to user that a move is pending
		setTitle("View Locality: " + name.getText() + " (LOCATION CHANGED)");
		ok.setText("OK - Save Changes*");
		ok.setBackground(new Color(200, 255, 200)); // Visual cue

		// Optional: If you have a coordinate display label in the UI, update it here
		setCursor(Cursor.getDefaultCursor());
		JOptionPane.showMessageDialog(this, "New position staged. Click 'OK - Change' to save permanently.");
	}

	public String getOldName() {
		return oldName;
	}

	@Override
	public void actionPerformed(ActionEvent ev) {
		String cmd = ev.getActionCommand();
		if ("ok".equals(cmd)) {
			updateLocality();
		} else if ("cancel".equals(cmd)) {
			gui.cancelMoveMode();
			this.dispose();
		} else if ("delete".equals(cmd)) {
			deleteLocality();
			mapCanvas.repaint();
		} else if ("move".equals(cmd)) {
			// Minimize dialog or just tell the user to click
			//this.setState(Frame.ICONIFIED); // Optional: hide dialog so they can see the map
			this.setTitle("SELECT NEW LOCATION ON MAP...");
			this.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
			gui.enterMoveMode(this);
		}
	}
}