import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.swing.*;

/* Dialog for viewing and editing already existing Localities in the db */

public class EditLocalityDialog extends JDialog implements ActionListener {
	private final Canvas canvas;
	private final int localityID;
	private final SpecimenBridgeDialog bridgeDialog;

	// Components
	private JTextField name, altNames, RT90N, RT90E, province, district, coordinate_source, localitySize, zoomLevel, category;
	private JTextArea comments;
	private JCheckBox isPlace;
	private JLabel labelCreated, labelModified;
	public JButton cancel, delete, ok;

	public EditLocalityDialog(Frame owner, int localityID, SpecimenBridgeDialog bridge, Canvas canvas) {
		// 'false' makes it non-modal, 'true' would stop interaction with map
		super(owner, "Edit Locality", false);

		this.canvas = canvas;
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
		this.setVisible(true);
	}

	private void initComponents() {
		Container content = this.getContentPane();
		SpringLayout layout = (SpringLayout) this.getContentPane().getLayout();

		// Initialize Components
		name = new JTextField(20);
		altNames = new JTextField(20);
		RT90N = new JTextField(10);
		RT90E = new JTextField(10);
		province = new JTextField(15);
		district = new JTextField(15);
		localitySize = new JTextField(10);
		coordinate_source = new JTextField(20);
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

		// Add to Layout
		JLabel lName = addField("Name:", name, content, layout, 10, content);
		JLabel lAlt = addField("Alt Names:", altNames, content, layout, 10, lName);
		JLabel lNorth = addField("RT90 N:", RT90N, content, layout, 10, lAlt);
		JLabel lEast = addField("RT90 E:", RT90E, content, layout, 10, lNorth);
		JLabel lProv = addField("Province:", province, content, layout, 10, lEast);
		JLabel lSize = addField("Size:", localitySize, content, layout, 10, lProv);
		JLabel lDist = addField("District:", district, content, layout, 10, lSize);
		JLabel lSrc = addField("Source:", coordinate_source, content, layout, 10, lDist);

		JLabel lComm = new JLabel("Comments:");
		content.add(lComm);
		content.add(scrollPane);

		layout.putConstraint(SpringLayout.WEST, lComm, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, lComm, 10, SpringLayout.SOUTH, lSrc);

		layout.putConstraint(SpringLayout.WEST, scrollPane, 120, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, scrollPane, 0, SpringLayout.NORTH, lComm);

		// Comments is a JTextArea, so the next field needs a bigger gap (70-80px)
		JLabel lCat = addField("Category:", category, content, layout, 80, scrollPane);
		JLabel lZoom = addField("Zoom:", zoomLevel, content, layout, 10, lCat);

		// Metadata Labels
		add(labelCreated);
		layout.putConstraint(SpringLayout.WEST, labelCreated, 10, SpringLayout.WEST, this.getContentPane());
		layout.putConstraint(SpringLayout.NORTH, labelCreated, 15, SpringLayout.SOUTH, lZoom);

		add(labelModified);
		layout.putConstraint(SpringLayout.WEST, labelModified, 10, SpringLayout.WEST, this.getContentPane());
		layout.putConstraint(SpringLayout.NORTH, labelModified, 5, SpringLayout.SOUTH, labelCreated);

		// Checkbox
		add(isPlace);
		layout.putConstraint(SpringLayout.WEST, isPlace, 10, SpringLayout.WEST, this.getContentPane());
		layout.putConstraint(SpringLayout.NORTH, isPlace, 10, SpringLayout.SOUTH, labelModified);

		// Buttons
		add(cancel);
		add(delete);
		add(ok);

		layout.putConstraint(SpringLayout.WEST, cancel, 10, SpringLayout.WEST, this.getContentPane());
		layout.putConstraint(SpringLayout.NORTH, cancel, 20, SpringLayout.SOUTH, isPlace);

		layout.putConstraint(SpringLayout.WEST, delete, 10, SpringLayout.EAST, cancel);
		layout.putConstraint(SpringLayout.NORTH, delete, 0, SpringLayout.NORTH, cancel);

		layout.putConstraint(SpringLayout.WEST, ok, 10, SpringLayout.EAST, delete);
		layout.putConstraint(SpringLayout.NORTH, ok, 0, SpringLayout.NORTH, cancel);

		// Anchor the right edge of the panel to the right edge of the text fields
		layout.putConstraint(SpringLayout.EAST, this.getContentPane(), 10, SpringLayout.EAST, name);
		// Anchor the bottom edge of the panel to the bottom of the buttons
		layout.putConstraint(SpringLayout.SOUTH, this.getContentPane(), 10, SpringLayout.SOUTH, cancel);
		// This tells the layout "The width is name.width + 10px"
		layout.putConstraint(SpringLayout.EAST, this.getContentPane(), 20, SpringLayout.EAST, scrollPane);
		// This tells the layout "The height is cancel.bottom + 10px"
		layout.putConstraint(SpringLayout.SOUTH, this.getContentPane(), 10, SpringLayout.SOUTH, cancel);

		// Listeners
		cancel.addActionListener(this);
		delete.addActionListener(this);
		ok.addActionListener(this);
		cancel.setActionCommand("cancel");
		delete.setActionCommand("delete");
		ok.setActionCommand("ok");
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
		String sql = "SELECT locality, alternative_names, RT90N, RT90E, province, district, " +
				"coordinate_source, lcomments, created, createdBy, modified, modifiedBy, " +
				"Coordinateprecision, category, zoomLevel, isPlace FROM locality WHERE ID = ?";

		try (Connection conn = DBConnection.getConn();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

			stmt.setInt(1, localityID);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					// Basic Fields
					name.setText(rs.getString(1));
					altNames.setText(rs.getString(2));
					RT90N.setText(rs.getString(3));
					RT90E.setText(rs.getString(4));
					province.setText(rs.getString(5));
					district.setText(rs.getString(6));
					coordinate_source.setText(rs.getString(7));
					comments.setText(rs.getString(8));

					// Metadata Labels (Columns 9, 10, 11, 12)
					labelCreated.setText("Created: " + rs.getString(9) + " by " + rs.getString(10));
					labelModified.setText("Modified: " + rs.getString(11) + " by " + rs.getString(12));

					// Lower Fields
					localitySize.setText(rs.getString(13));
					category.setText(rs.getString(14));
					zoomLevel.setText(rs.getString(15));

					isPlace.setSelected(rs.getInt(16) == 1);

					setTitle("View Locality: " + rs.getString(1));
				}
			}
		} catch (SQLException e) {
			JOptionPane.showMessageDialog(this, "Database Error: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	private void deleteLokal() {
		int dialogResult = JOptionPane.showConfirmDialog (null, "Do you realy want to delete the local?","Warning",JOptionPane.YES_NO_OPTION);
		if(dialogResult == JOptionPane.YES_OPTION){
			try {
				Connection conn = DBConnection.getConn();
				String sqlstmt = "DELETE FROM locality WHERE ID =?";
				System.out.println(sqlstmt);
				PreparedStatement statement = conn.prepareStatement(sqlstmt);
				statement.setInt(1, localityID);
				statement.execute();
				if (bridgeDialog != null && bridgeDialog.isVisible()) {
					bridgeDialog.updateLocalityList();
				}
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				JOptionPane.showMessageDialog(this, "Error deleting locality: " + e.getMessage());
			}
		}
	}
	
	private void updateLokal() {
		try {
			Connection conn = DBConnection.getConn();
			String sqlstmt = "UPDATE locality SET locality = ?, district = ?, province = ?, RT90N = ?, RT90E = ?, alternative_names = ?, coordinate_source = ?, lcomments = ?, modified = NOW(), modifiedBy = ?, Coordinateprecision = ?, category = ?, zoomLevel =?, isPlace =?  WHERE ID =?";
			System.out.println(sqlstmt);
			PreparedStatement statement = conn.prepareStatement(sqlstmt);
			statement.setString(1, name.getText());
			statement.setString(2, district.getText());
			statement.setString(3, province.getText());
			statement.setString(4, RT90N.getText());
			statement.setString(5, RT90E.getText());
			statement.setString(6, altNames.getText());
			statement.setString(7, coordinate_source.getText());
			statement.setString(8, comments.getText());
			statement.setString(9,Settings.getValue("user"));
			statement.setString(10,localitySize.getText());
			statement.setString(11, category.getText());
			
			int zl;
			try {
				zl = Integer.parseInt(zoomLevel.getText());
			} catch (NumberFormatException e) {
				zl = -1;
			}
			statement.setInt(12, zl);
			
			int i=0;
			if (isPlace.isSelected()) {
				i=1;
			}
			statement.setInt(13, i);
			
			statement.setInt(14, localityID);

			statement.execute();
			if (bridgeDialog != null && bridgeDialog.isVisible()) {
				bridgeDialog.updateLocalityList();
			}
		} catch (SQLException | IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error updating locality: " + e.getMessage());
		}
	}

	@Override
	public void actionPerformed(ActionEvent ev) {
		String cmd = ev.getActionCommand();
		if ("ok".equals(cmd)) {
			updateLokal();
			canvas.repaint();
			this.dispose();
		} else if ("cancel".equals(cmd)) {
			this.dispose();
		} else if ("delete".equals(cmd)) {
			deleteLokal();
			canvas.repaint();
			this.dispose();
		}
	}
}