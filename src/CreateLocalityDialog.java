import geometry.Point;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;

public class CreateLocalityDialog extends JPanel implements ActionListener{
	private static final long serialVersionUID = 5999128550024317489L;
	private JFrame localFrame;
	private JTextField localityT, districtT, provinceT, countryT, continentT, RT90NT, RT90ET, commentsT, alternativeT, coordsourceT, locSizeT;
	JButton cancel, ok;

	public CreateLocalityDialog(JFrame localFrame, String RT90N, String RT90E, String province, String district) {
		this.localFrame = localFrame;
		localFrame.setTitle("Create new Locality");
		SpringLayout layout = new SpringLayout();
		setLayout(layout);

		cancel = new JButton("Cancel");
		add(cancel);
		cancel.addActionListener(this);
		cancel.setActionCommand("cancel");

		ok = new JButton("OK");
		add(ok);
		ok.addActionListener(this);
		ok.setActionCommand("ok");

		H2Table odb = (H2Table) GUI.canvas.getLayer("Ortnamnsdb");
		Point p = new Point(Integer.parseInt(RT90N),Integer.parseInt(RT90E));
		if (odb== null) {
			System.out.println("hittar inte ortnamnslagret");
		}
		String sugestName = odb.findNearest(p,1000);

		//JLabel label1, label2, label3, label4, label5, label6, label7, label8, label9, label10;
		localityT = new JTextField(sugestName);
		localityT.setPreferredSize(new Dimension(200,20));
		add(localityT);
		districtT = new JTextField(district);
		add(districtT);
		districtT.setPreferredSize(new Dimension(200,20));
		provinceT = new JTextField(province);
		add(provinceT);
		provinceT.setPreferredSize(new Dimension(200,20));
		countryT = new JTextField("Sweden");
		add(countryT);
		countryT.setPreferredSize(new Dimension(200,20));
		continentT = new JTextField("Europe");
		add(continentT);
		continentT.setPreferredSize(new Dimension(200,20));
		RT90NT = new JTextField(RT90N);
		add(RT90NT);
		RT90NT.setPreferredSize(new Dimension(200,20));
		RT90ET = new JTextField(RT90E);
		add(RT90ET);
		commentsT = new JTextField();
		commentsT.setPreferredSize(new Dimension(200,20));
		add(commentsT);
		alternativeT = new JTextField();
		alternativeT.setPreferredSize(new Dimension(200,20));
		add(alternativeT);
		coordsourceT = new JTextField();
		coordsourceT.setPreferredSize(new Dimension(200,20));
		add(coordsourceT);
		locSizeT = new JTextField();
		locSizeT.setPreferredSize(new Dimension(200,20));
		add(locSizeT);
		
		JLabel label1 = new JLabel("Locality");
		add(label1);
		layout.putConstraint(SpringLayout.WEST, label1, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, label1, 10, SpringLayout.NORTH, this);
		
		layout.putConstraint(SpringLayout.WEST, localityT, 10, SpringLayout.EAST, label1);
		layout.putConstraint(SpringLayout.NORTH, localityT, 0, SpringLayout.NORTH, label1);
		
		JLabel label2 = new JLabel("Alternative names");
		add(label2);
		layout.putConstraint(SpringLayout.WEST, label2, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, label2, 10, SpringLayout.SOUTH, label1);
		
		layout.putConstraint(SpringLayout.WEST, alternativeT, 10, SpringLayout.EAST, label2);
		layout.putConstraint(SpringLayout.NORTH, alternativeT, 0, SpringLayout.NORTH, label2);
		
		JLabel label3 = new JLabel("Coordinate source");
		add(label3);
		layout.putConstraint(SpringLayout.WEST, label3, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, label3, 10, SpringLayout.SOUTH, label2);
		
		layout.putConstraint(SpringLayout.WEST, coordsourceT, 10, SpringLayout.EAST, label3);
		layout.putConstraint(SpringLayout.NORTH, coordsourceT, 0, SpringLayout.NORTH, label3);
		
		JLabel label4 = new JLabel("Comments");
		add(label4);
		layout.putConstraint(SpringLayout.WEST, label4, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, label4, 10, SpringLayout.SOUTH, label3);
		
		layout.putConstraint(SpringLayout.WEST, commentsT, 10, SpringLayout.EAST, label4);
		layout.putConstraint(SpringLayout.NORTH, commentsT, 0, SpringLayout.NORTH, label4);
		
		JLabel label5 = new JLabel("RT90 N");
		add(label5);
		layout.putConstraint(SpringLayout.WEST, label5, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, label5, 10, SpringLayout.SOUTH, label4);
		
		layout.putConstraint(SpringLayout.WEST, RT90NT, 10, SpringLayout.EAST, label5);
		layout.putConstraint(SpringLayout.NORTH, RT90NT, 0, SpringLayout.NORTH, label5);
		
		JLabel label6 = new JLabel("E");
		add(label6);
		layout.putConstraint(SpringLayout.WEST, label6, 10, SpringLayout.EAST, RT90NT);
		layout.putConstraint(SpringLayout.NORTH, label6, 0, SpringLayout.NORTH, RT90NT);
		
		layout.putConstraint(SpringLayout.WEST, RT90ET, 10, SpringLayout.EAST, label6);
		layout.putConstraint(SpringLayout.NORTH, RT90ET, 0, SpringLayout.NORTH, label6);

		
		JLabel label11 = new JLabel("Size");
		add(label11);
		layout.putConstraint(SpringLayout.WEST, label11, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, label11, 10, SpringLayout.SOUTH, label6);
		
		layout.putConstraint(SpringLayout.WEST, locSizeT, 10, SpringLayout.EAST, label11);
		layout.putConstraint(SpringLayout.NORTH, locSizeT, 0, SpringLayout.NORTH, label11);
		
		JLabel label10 = new JLabel("Continent");
		add(label10);
		layout.putConstraint(SpringLayout.WEST, label10, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, label10, 10, SpringLayout.SOUTH, label11);
		
		layout.putConstraint(SpringLayout.WEST, continentT, 10, SpringLayout.EAST, label10);
		layout.putConstraint(SpringLayout.NORTH, continentT, 0, SpringLayout.NORTH, label10);
		
		JLabel label9 = new JLabel("Country");
		add(label9);
		layout.putConstraint(SpringLayout.WEST, label9, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, label9, 10, SpringLayout.SOUTH, label10);
		
		layout.putConstraint(SpringLayout.WEST, countryT, 10, SpringLayout.EAST, label9);
		layout.putConstraint(SpringLayout.NORTH, countryT, 0, SpringLayout.NORTH, label9);
		
		
		JLabel label7 = new JLabel("Provins");
		add(label7);
		layout.putConstraint(SpringLayout.WEST, label7, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, label7, 10, SpringLayout.SOUTH, label9);
		
		layout.putConstraint(SpringLayout.WEST, provinceT, 10, SpringLayout.EAST, label7);
		layout.putConstraint(SpringLayout.NORTH, provinceT, 0, SpringLayout.NORTH, label7);
		
		JLabel label8 = new JLabel("District");
		add(label8);
		layout.putConstraint(SpringLayout.WEST, label8, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, label8, 10, SpringLayout.SOUTH, label7);
		
		layout.putConstraint(SpringLayout.WEST, districtT, 10, SpringLayout.EAST, label8);
		layout.putConstraint(SpringLayout.NORTH, districtT, 0, SpringLayout.NORTH, label8);
		
		layout.putConstraint(SpringLayout.WEST, cancel, 10, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, cancel, 10, SpringLayout.SOUTH, label8);
		
		layout.putConstraint(SpringLayout.WEST, ok, 10, SpringLayout.EAST, cancel);
		layout.putConstraint(SpringLayout.NORTH, ok, 0, SpringLayout.NORTH, cancel);
		
		localFrame.setSize(400, 400);
		localFrame.setVisible(true);
		
		localFrame.getRootPane().setDefaultButton(cancel);
	}
	
	private boolean createLokal() {
		GUI.setCursorWait();
		System.out.println("Skapar lokal");  // TODO trace print
		String localityName = localityT.getText();
		String districtName = districtT.getText();
		String provinceName = provinceT.getText();
		String RT90Nt = RT90NT.getText();
		String RT90Et = RT90ET.getText();
		Coordinates rt90c = new Coordinates(Double.parseDouble(RT90Nt), Double.parseDouble(RT90Et));
		Coordinates wgs84c = rt90c.convertToWGS84FromRT90();
		
		/*String sqlstmt = "INSERT INTO locality (locality, district, province, country, continent, RT90N, RT90E, lat, long) "
		 		+ "VALUES locality = \""+localityName+"\", district = \""+districtName+"\", province = \""+provinceName+"\", country = \"Sweden\", continent = \"Europe\", RT90N = \""+ RT90Nt +"\", RT90E = \""+RT90Et+"\", lat = \"\", long = \"\";";*/
		
		String sqlstmt = "INSERT INTO locality (locality, district, province, country, continent, lat, `long`, RT90N, RT90E, createdby, alternative_names, coordinate_source, lcomments, Coordinateprecision) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
		
	    System.out.println(sqlstmt + " - " + localityName + "loc size: "+ locSizeT.getText()); // TODO trace print
		try {
			Connection conn = MYSQLConnection.getConn();
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
		    preparedStmt.setString (10, Settings.getValue("user"));
		    preparedStmt.setString (11, alternativeT.getText() );
		    preparedStmt.setString (12, coordsourceT.getText());
		    preparedStmt.setString (13, commentsT.getText());
		    preparedStmt.setString (14, locSizeT.getText());
		    preparedStmt.execute();
		    if (SpecimenList.isOpen()) {
		    	SpecimenList.updateLocalityList();
		    	SpecimenList.updateSpecimenList();
		    }
			GUI.setCursorDefault();
			return true;
				
		} catch (SQLException | IOException e1) {
				// TODO Auto-generated catch block
			e1.printStackTrace();
			JOptionPane.showMessageDialog(null, "Couldn't create locality", "InfoBox: " + "SQL Error", JOptionPane.INFORMATION_MESSAGE);
			GUI.setCursorDefault();
			return false;
		}
		
	}

	@Override
	public void actionPerformed(ActionEvent ev) {
		if ("ok".equals(ev.getActionCommand())) {
			System.out.println("OK");
			if (createLokal()) {
				localFrame.setVisible(false);
				localFrame.dispose();
			}
			
		} else if ("cancel".equals(ev.getActionCommand())) {
			System.out.println("Cancel");
			localFrame.setVisible(false);
			localFrame.dispose();
		}
	}

}
