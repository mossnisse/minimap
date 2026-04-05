import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.Serial;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpringLayout;

/* Dialog for viewing and editing already existing Localities in the db */

public class EditLocalityDialog extends JPanel implements ActionListener{
	@Serial
	private static final long serialVersionUID = -6495783408904343790L;
	public JButton cancel, delete, ok;
	private final int localityID;
	JTextField name, altNames, RT90N, RT90E, province, district, coordinate_source, localitySize, zoomLevel, category;
	JTextArea comments;
	JCheckBox isPlace;
	JLabel labelCreated, labelModified;
	JFrame localFrame;

	public EditLocalityDialog(int localityID, JFrame localFrame) {
		this.localFrame = localFrame;
		this.localityID = localityID;
		this.localFrame.setTitle("Loading Locality...");

		// Set up Layout
		SpringLayout layout = new SpringLayout();
		setLayout(layout);

		// Initialize components (but leave them empty)
		initComponents();

		// Load data from DB
		loadData();

		//localFrame.setSize(600, 700);
		localFrame.pack();
		localFrame.setLocationRelativeTo(null); // Center it!
		localFrame.setVisible(true);
	}

	private void initComponents() {
		SpringLayout layout = (SpringLayout) getLayout();

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
		JLabel lName = addField("Name:", name, this, layout, 10);
		JLabel lAlt = addField("Alt Names:", altNames, lName, layout, 10);
		JLabel lNorth = addField("RT90 N:", RT90N, lAlt, layout, 10);
		JLabel lEast = addField("RT90 E:", RT90E, lNorth, layout, 10);
		JLabel lProv = addField("Province:", province, lEast, layout, 10);
		JLabel lSize = addField("Size:", localitySize, lProv, layout, 10);
		JLabel lDist = addField("District:", district, lSize, layout, 10);
		JLabel lSrc = addField("Source:", coordinate_source, lDist, layout, 10);
		//JLabel lComm = addField("Comments:", comments, lSrc, layout, 10);
		JLabel lComm = new JLabel("Comments:");
		add(lComm);
		add(scrollPane); // Add the scrollPane, NOT comments

		layout.putConstraint(SpringLayout.WEST, lComm, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, lComm, 10, SpringLayout.SOUTH, lSrc);

		layout.putConstraint(SpringLayout.WEST, scrollPane, 120, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, scrollPane, 0, SpringLayout.NORTH, lComm);

		// Comments is a JTextArea, so the next field needs a bigger gap (70-80px)
		JLabel lCat = addField("Category:", category, scrollPane, layout, 80);
		JLabel lZoom = addField("Zoom:", zoomLevel, lCat, layout, 10);

		// Metadata Labels
		add(labelCreated);
		layout.putConstraint(SpringLayout.WEST, labelCreated, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, labelCreated, 15, SpringLayout.SOUTH, lZoom);

		add(labelModified);
		layout.putConstraint(SpringLayout.WEST, labelModified, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, labelModified, 5, SpringLayout.SOUTH, labelCreated);

		// Checkbox
		add(isPlace);
		layout.putConstraint(SpringLayout.WEST, isPlace, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, isPlace, 10, SpringLayout.SOUTH, labelModified);

		// Buttons
		add(cancel);
		add(delete);
		add(ok);

		layout.putConstraint(SpringLayout.WEST, cancel, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, cancel, 20, SpringLayout.SOUTH, isPlace);

		layout.putConstraint(SpringLayout.WEST, delete, 10, SpringLayout.EAST, cancel);
		layout.putConstraint(SpringLayout.NORTH, delete, 0, SpringLayout.NORTH, cancel);

		layout.putConstraint(SpringLayout.WEST, ok, 10, SpringLayout.EAST, delete);
		layout.putConstraint(SpringLayout.NORTH, ok, 0, SpringLayout.NORTH, cancel);

		// Anchor the right edge of the panel to the right edge of the text fields
		layout.putConstraint(SpringLayout.EAST, this, 10, SpringLayout.EAST, name);
		// Anchor the bottom edge of the panel to the bottom of the buttons
		layout.putConstraint(SpringLayout.SOUTH, this, 10, SpringLayout.SOUTH, cancel);
		// This tells the layout "The width is name.width + 10px"
		layout.putConstraint(SpringLayout.EAST, this, 20, SpringLayout.EAST, scrollPane);
		// This tells the layout "The height is cancel.bottom + 10px"
		layout.putConstraint(SpringLayout.SOUTH, this, 10, SpringLayout.SOUTH, cancel);

		// Listeners
		cancel.addActionListener(this);
		delete.addActionListener(this);
		ok.addActionListener(this);
		cancel.setActionCommand("cancel");
		delete.setActionCommand("delete");
		ok.setActionCommand("ok");
	}

	private JLabel addField(String labelText, java.awt.Component field, java.awt.Component topAnchor, SpringLayout layout, int margin) {
		JLabel label = new JLabel(labelText);
		add(label);
		add(field);

		// Label Constraints
		layout.putConstraint(SpringLayout.WEST, label, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, label, margin, (topAnchor == this) ? SpringLayout.NORTH : SpringLayout.SOUTH, topAnchor);

		// Field Constraints (Align to a fixed column at x=120)
		layout.putConstraint(SpringLayout.WEST, field, 120, SpringLayout.WEST, this);
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

					localFrame.setTitle("View Locality: " + rs.getString(1));
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
				if (SpecimenList.isOpen()) {
					SpecimenList.updateLocalityList();
					SpecimenList.updateSpecimenList();
				}
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
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
			 if (SpecimenList.isOpen()) {
			    	SpecimenList.updateLocalityList();
			    	SpecimenList.updateSpecimenList();
			    }
		} catch (SQLException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void actionPerformed(ActionEvent ev) {
		if ("ok".equals(ev.getActionCommand())) {
			System.out.println("OK");
			updateLokal();
			GUI.canvas.repaint();
			localFrame.setVisible(false);
			localFrame.dispose();
		} else if ("cancel".equals(ev.getActionCommand())) {
			System.out.println("Cancel");
			localFrame.setVisible(false);
			localFrame.dispose();
		} else if ("delete".equals(ev.getActionCommand())) {
			deleteLokal();
			GUI.canvas.repaint();
			localFrame.setVisible(false);
			localFrame.dispose();
		}
	}
}