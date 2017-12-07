import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;


public class LocalityDialog  extends JPanel implements ActionListener{
	private static final long serialVersionUID = -6495783408904343790L;
	public JButton cancel, delete, ok;
	private int localityID;
	JTextField name, altNames, RT90N, RT90E, province, district, coordinate_source, comments, localitySize;
	JFrame localFrame;
	 
	
	public LocalityDialog(int localityID, JFrame localFrame) {
		//System.out.println("open Locality diag");
		this.localFrame = localFrame;
		localFrame.setTitle("View Locality");
		this.localityID=localityID;
		SpringLayout layout = new SpringLayout();
		setLayout(layout);
		
		JLabel label1, label2, label3, label4, label5, label6, label7, label8, label9, label10, label11;
		
		cancel = new JButton("Cancel");
		add(cancel);
		cancel.addActionListener(this);
		cancel.setActionCommand("cancel");
		
		try {
			Connection conn = MYSQLConnection.getConn();
			String sqlstmt = "SELECT locality, alternative_names, RT90N, RT90E, province, district, coordinate_source, lcomments, created, createdBy, modified, modifiedBy, Coordinateprecision FROM locality WHERE ID =?";
			System.out.println(sqlstmt);
			PreparedStatement statement = conn.prepareStatement(sqlstmt);
			statement.setInt(1, localityID);
			ResultSet result = statement.executeQuery();
			if (result.next()) {
				
				
				
				label1 = new JLabel("Name: ");
				add(label1);
				name = new JTextField(result.getString(1));
				add(name);
				
				label2 = new JLabel("Alternative names: ");
				add(label2);
				altNames = new JTextField(result.getString(2));
				altNames.setPreferredSize(new Dimension(200,20));
				add(altNames);
				
				label3 = new JLabel("RT90 North: ");
				add(label3);
				RT90N = new JTextField(result.getString(3));
				add(RT90N);
				
				label4 = new JLabel("RT90 East: ");
				add(label4);
				RT90E = new JTextField(result.getString(4));
				add(RT90E);
				
				label5 = new JLabel("Province: ");
				add(label5);
				province = new JTextField(result.getString(5));
				add(province);
				
				label6 = new JLabel("district: ");
				add(label6);
				district = new JTextField(result.getString(6));
				add(district);
				
				label7 = new JLabel("Coordinate source: ");
				add(label7);
				coordinate_source = new JTextField(result.getString(7));
				coordinate_source.setPreferredSize(new Dimension(200,20));
				add(coordinate_source);
				
				label8 = new JLabel("Comments: ");
				add(label8);
				comments = new JTextField(result.getString(8));
				comments.setPreferredSize(new Dimension(200,20));
				add(comments);
				
				
				label9 = new JLabel("Created:" +result.getString(9) + " " + result.getString(10));
				add(label9);
				
				label10 = new JLabel("Modified: "+result.getString(11) + " " +result.getString(12));
				add(label10);
				
				label11 = new JLabel("Size");
				add(label11);
				localitySize = new JTextField(result.getString(13));
				localitySize.setPreferredSize(new Dimension(200,20));
				add(localitySize);
				
				delete = new JButton("Delete");
				add(delete);
				delete.addActionListener(this);
				delete.setActionCommand("delete");
				ok = new JButton("OK -Change");
				add(ok);
				ok.addActionListener(this);
				ok.setActionCommand("ok");
				
				localFrame.getRootPane().setDefaultButton(cancel);
				
				
				//cancel.requestFocus();
				
				layout.putConstraint(SpringLayout.WEST, label1, 10, SpringLayout.WEST, this);
				layout.putConstraint(SpringLayout.NORTH, label1, 10, SpringLayout.NORTH, this);
				
				layout.putConstraint(SpringLayout.WEST, name, 10, SpringLayout.EAST, label1);
				layout.putConstraint(SpringLayout.NORTH, name, 0, SpringLayout.NORTH, label1);
				
				layout.putConstraint(SpringLayout.WEST, label2, 10, SpringLayout.WEST, this);
				layout.putConstraint(SpringLayout.NORTH, label2, 10, SpringLayout.SOUTH, label1);
				
				layout.putConstraint(SpringLayout.WEST, altNames, 10, SpringLayout.EAST, label2);
				layout.putConstraint(SpringLayout.NORTH, altNames, 0, SpringLayout.NORTH, label2);
				
				layout.putConstraint(SpringLayout.WEST, label3, 10, SpringLayout.WEST, this);
				layout.putConstraint(SpringLayout.NORTH, label3, 10, SpringLayout.SOUTH, label2);
				
				layout.putConstraint(SpringLayout.WEST, RT90N, 10, SpringLayout.EAST, label3);
				layout.putConstraint(SpringLayout.NORTH, RT90N, 0, SpringLayout.NORTH, label3);
				
				layout.putConstraint(SpringLayout.WEST, label4, 10, SpringLayout.WEST, this);
				layout.putConstraint(SpringLayout.NORTH, label4, 10, SpringLayout.SOUTH, label3);
				
				layout.putConstraint(SpringLayout.WEST, RT90E, 10, SpringLayout.EAST, label4);
				layout.putConstraint(SpringLayout.NORTH, RT90E, 0, SpringLayout.NORTH, label4);
				
				layout.putConstraint(SpringLayout.WEST, label5, 10, SpringLayout.WEST, this);
				layout.putConstraint(SpringLayout.NORTH, label5, 10, SpringLayout.SOUTH, label4);
				
				layout.putConstraint(SpringLayout.WEST, province, 10, SpringLayout.EAST, label5);
				layout.putConstraint(SpringLayout.NORTH, province, 0, SpringLayout.NORTH, label5);
				
				
				layout.putConstraint(SpringLayout.WEST, label11, 10, SpringLayout.WEST, this);
				layout.putConstraint(SpringLayout.NORTH, label11, 10, SpringLayout.SOUTH, label5);
				
				layout.putConstraint(SpringLayout.WEST, localitySize, 10, SpringLayout.EAST, label11);
				layout.putConstraint(SpringLayout.NORTH, localitySize, 0, SpringLayout.NORTH, label11);
				
				
				layout.putConstraint(SpringLayout.WEST, label6, 10, SpringLayout.WEST, this);
				layout.putConstraint(SpringLayout.NORTH, label6, 10, SpringLayout.SOUTH, label11);
				
				layout.putConstraint(SpringLayout.WEST, district, 10, SpringLayout.EAST, label6);
				layout.putConstraint(SpringLayout.NORTH, district, 0, SpringLayout.NORTH, label6);
				
				layout.putConstraint(SpringLayout.WEST, label7, 10, SpringLayout.WEST, this);
				layout.putConstraint(SpringLayout.NORTH, label7, 10, SpringLayout.SOUTH, label6);
				
				layout.putConstraint(SpringLayout.WEST, coordinate_source, 10, SpringLayout.EAST, label7);
				layout.putConstraint(SpringLayout.NORTH, coordinate_source, 0, SpringLayout.NORTH, label7);
				
				layout.putConstraint(SpringLayout.WEST, label8, 10, SpringLayout.WEST, this);
				layout.putConstraint(SpringLayout.NORTH, label8, 10, SpringLayout.SOUTH, label7);
				
				layout.putConstraint(SpringLayout.WEST, comments, 10, SpringLayout.EAST, label8);
				layout.putConstraint(SpringLayout.NORTH, comments, 0, SpringLayout.NORTH, label8);
				
				layout.putConstraint(SpringLayout.WEST, label9, 10, SpringLayout.WEST, this);
				layout.putConstraint(SpringLayout.NORTH, label9, 10, SpringLayout.SOUTH, label8);
				
				layout.putConstraint(SpringLayout.WEST, label10, 10, SpringLayout.WEST, this);
				layout.putConstraint(SpringLayout.NORTH, label10, 10, SpringLayout.SOUTH, label9);
				
				layout.putConstraint(SpringLayout.WEST, cancel, 10, SpringLayout.WEST, this);
				layout.putConstraint(SpringLayout.NORTH, cancel, 10, SpringLayout.SOUTH, label10);
				
				layout.putConstraint(SpringLayout.WEST, delete, 10, SpringLayout.EAST, cancel);
				layout.putConstraint(SpringLayout.NORTH, delete, 0, SpringLayout.NORTH, cancel);
				
				layout.putConstraint(SpringLayout.WEST, ok, 10, SpringLayout.EAST, delete);
				layout.putConstraint(SpringLayout.NORTH, ok, 0, SpringLayout.NORTH, cancel);
			}
			
			
			//setPreferredSize(new Dimension(120,120));
			//setVisible(true);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		localFrame.setSize(400, 400);
		localFrame.setVisible(true);
		
		cancel.requestFocusInWindow();
		
	}
	
	private void deleteLokal() {
		try {
			Connection conn = MYSQLConnection.getConn();
			String sqlstmt = "DELETE FROM locality WHERE ID =?";
			System.out.println(sqlstmt);
			PreparedStatement statement = conn.prepareStatement(sqlstmt);
			statement.setInt(1, localityID);
			statement.execute();
			if (SpecimenList.isOpen()) {
		    	SpecimenList.updateLocalityList();
		    	SpecimenList.updateSpecimenList();
		    }
			/*if (result.next()) {
				
			}*/
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	private void updateLokal() {
		try {
			Connection conn = MYSQLConnection.getConn();
			String sqlstmt = "UPDATE locality SET locality = ?, district = ?, province = ?, RT90N = ?, RT90E = ?, alternative_names = ?, coordinate_source = ?, lcomments = ?, modified = NOW(), modifiedBy = ?, Coordinateprecision = ?  WHERE ID =?";
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
			statement.setInt(11, localityID);
			statement.execute();
			 if (SpecimenList.isOpen()) {
			    	SpecimenList.updateLocalityList();
			    	SpecimenList.updateSpecimenList();
			    }
			/*if (result.next()) {
				
			}*/
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
