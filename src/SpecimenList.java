import geometry.Point;
import javafx.scene.input.KeyCode;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SpringLayout;

public class SpecimenList extends JPanel implements ActionListener, ItemListener, FocusListener, NActionListener  {
	private static final long serialVersionUID = 7300006405490558336L;
	private static JTextField landskap, socken, cnr, yearS, collectorS, originalS, distance, oProvince, oDistrict, coordinateprecision;
	private static JButton next, prev, rubinD, RT90D, latlongD, focusL, searchspecimens, copyLastB, localityD;
	private static JTextPane collect_info;
	private static JComboBox<String> lokaler, direction;
	private static int nr;
	private static String specimenID, oldLocality, oldOverrideDistrict, oldOverrideProvince, lastLocality, lastDistance, lastDirection, lastODistrict, lastOProvince, dbLocality;
	private static String latdir, latdeg, latmin, latsec, longdir, longdeg, longmin, longsec;
	private static boolean comboenabled = false;
	private static String accnr, instCode, collCode; 
	private String oldLokalNamn;

	public SpecimenList() {
		specimenID = "-1";
		comboenabled = false;
		oldLocality = "";
		SpringLayout layout = new SpringLayout();
		setLayout(layout);
		JLabel label1 = new JLabel("Landskap: ");
		this.add(label1);
		try {
			landskap = new JTextField(Settings.getValue("landskap"));
			this.add(landskap);
			landskap.setPreferredSize(new Dimension(320,20));
		} catch (IOException e) {
			e.printStackTrace(); // TODO Auto-generated catch block
		}

		JLabel label2 = new JLabel("Socken: ");
		this.add(label2);
		try {
			socken = new JTextField(Settings.getValue("socken"));
			this.add(socken);
			socken.setPreferredSize(new Dimension(320,20));
		} catch (IOException e) {
			e.printStackTrace(); // TODO Auto-generated catch block
		}

		searchspecimens = new JButton("Search specimens");
		searchspecimens.setActionCommand("searchspecimens");
		this.add(searchspecimens);
		///createSpecimenList();
		
		JLabel label3 = new JLabel("Year: ");
		this.add(label3);
		yearS = new JTextField();
		yearS.setPreferredSize(new Dimension(320,20));
		this.add(yearS);

		JLabel label4 = new JLabel("Collector: ");
		this.add(label4);
		collectorS = new JTextField();
		collectorS.setPreferredSize(new Dimension(320,20));
		this.add(collectorS);

		JLabel label5 = new JLabel("Original Text: ");
		this.add(label5);
		originalS = new JTextField();
		originalS.setPreferredSize(new Dimension(320,20));
		this.add(originalS);
		
		try {
			nr = Integer.parseInt(Settings.getValue("cnr"));
			cnr = new JTextField(new Integer(nr).toString());
			this.add(cnr);
		} catch (NumberFormatException | IOException e) {
			e.printStackTrace(); // TODO Auto-generated catch block
		}

		prev = new JButton("prev");
		prev.setMnemonic(KeyEvent.VK_A);
		prev.setActionCommand("prev");
		this.add(prev);

		next = new JButton("next");
		next.setMnemonic(KeyEvent.VK_D);
		next.setActionCommand("next");
		this.add(next);

		collect_info = new JTextPane();
		collect_info.setContentType("text/html");
		this.add(collect_info);

		lokaler = new JComboBox<String>();
		this.add(lokaler);
		
		focusL = new JButton("Focus on map");
		focusL.setActionCommand("focusL");
		this.add(focusL);
		
		JLabel distDir = new JLabel("Distance and direction: ");
		this.add(distDir);
		distance = new JTextField("test direction");
		distance.setPreferredSize(new Dimension(200,20));
		this.add(distance);
		String[] dirString = { "", "N", "NNW", "NW", "WNW", "W", "WSW", "SW", "SSW", "S", "SSE", "SE", "ESE", "E", "ENE", "NE", "NNE" };
		direction = new JComboBox<String>(dirString);
		this.add(direction);
		
		JLabel coordinatePrecisionL = new JLabel("CoordinatePrecision: ");
		this.add(coordinatePrecisionL);
		coordinateprecision = new JTextField();
		coordinateprecision.setPreferredSize(new Dimension(200,20));
		this.add(coordinateprecision);
		
		JLabel overrideD = new JLabel("Override District: ");
		this.add(overrideD);
		oDistrict = new JTextField();
		oDistrict.setPreferredSize(new Dimension(200,20));
		this.add(oDistrict);
		
		JLabel overrideP = new JLabel("Province: ");
		this.add(overrideP);
		oProvince = new JTextField();
		oProvince.setPreferredSize(new Dimension(200,20));
		this.add(oProvince);

		JLabel localityL = new JLabel("Locality:");
		this.add(localityL);
		localityD = new JButton("test Locality");
		localityD.setActionCommand("localityD");
		this.add(localityD);

		
		JLabel rubinL = new JLabel("RUBIN:");
		this.add(rubinL);
		rubinD = new JButton("test RUBIN");
		rubinD.setActionCommand("rubinD");
		this.add(rubinD);

		JLabel RT90L = new JLabel("RT90:");
		this.add(RT90L);
		RT90D = new JButton("test RT90");
		RT90D.setActionCommand("RT90D");
		this.add(RT90D);

		JLabel latlongL = new JLabel("lat/long:");
		this.add(latlongL);
		latlongD = new JButton("test lat/long");
		latlongD.setActionCommand("latlongD");
		this.add(latlongD);
		
		copyLastB = new JButton("Copy from last post");
		copyLastB.setMnemonic(KeyEvent.VK_P);
		copyLastB.setActionCommand("copyLastB");
		this.add(copyLastB);


		layout.putConstraint(SpringLayout.WEST, label1,
				10,
				SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.NORTH, label1,
				10,
				SpringLayout.NORTH, this);

		layout.putConstraint(SpringLayout.SOUTH, landskap,
				0,
				SpringLayout.SOUTH, label1);

		layout.putConstraint(SpringLayout.WEST, landskap,
				5,
				SpringLayout.EAST, label1);

		layout.putConstraint(SpringLayout.NORTH, label2,
				10,
				SpringLayout.SOUTH, label1);

		layout.putConstraint(SpringLayout.WEST, label2,
				10,
				SpringLayout.WEST, this);

		layout.putConstraint(SpringLayout.WEST, socken,
				5,
				SpringLayout.EAST, label2);

		layout.putConstraint(SpringLayout.SOUTH, socken,
				0,
				SpringLayout.SOUTH, label2);

		layout.putConstraint(SpringLayout.NORTH, label3,
				10,
				SpringLayout.SOUTH, label2);

		layout.putConstraint(SpringLayout.WEST, label3,
				10,
				SpringLayout.WEST, this);

		layout.putConstraint(SpringLayout.WEST, yearS,
				5,
				SpringLayout.EAST, label3);

		layout.putConstraint(SpringLayout.SOUTH, yearS,
				0,
				SpringLayout.SOUTH, label3);

		layout.putConstraint(SpringLayout.NORTH, label4,
				5,
				SpringLayout.SOUTH, label3);

		layout.putConstraint(SpringLayout.WEST, label4,
				10,
				SpringLayout.WEST, this);

		layout.putConstraint(SpringLayout.WEST, collectorS,
				5,
				SpringLayout.EAST, label4);

		layout.putConstraint(SpringLayout.SOUTH, collectorS,
				5,
				SpringLayout.SOUTH, label4);

		layout.putConstraint(SpringLayout.NORTH, label5,
				10,
				SpringLayout.SOUTH, label4);

		layout.putConstraint(SpringLayout.WEST, label5,
				10,
				SpringLayout.WEST, this);

		layout.putConstraint(SpringLayout.WEST, originalS,
				5,
				SpringLayout.EAST, label5);

		layout.putConstraint(SpringLayout.SOUTH, originalS,
				5,
				SpringLayout.SOUTH, label5);
		
		layout.putConstraint(SpringLayout.NORTH, searchspecimens,
				5,
				SpringLayout.SOUTH, label5);
		
		layout.putConstraint(SpringLayout.WEST, searchspecimens,
				10,
				SpringLayout.WEST, this);

		layout.putConstraint(SpringLayout.NORTH, prev,
				20,
				SpringLayout.SOUTH, searchspecimens);

		layout.putConstraint(SpringLayout.WEST, prev,
				10,
				SpringLayout.WEST, this);

		layout.putConstraint(SpringLayout.NORTH, cnr,
				0,
				SpringLayout.NORTH, prev);

		layout.putConstraint(SpringLayout.WEST, cnr,
				10,
				SpringLayout.EAST, prev);


		layout.putConstraint(SpringLayout.NORTH, next,
				0,
				SpringLayout.NORTH, prev);

		layout.putConstraint(SpringLayout.WEST, next,
				10,
				SpringLayout.EAST, cnr);

		
		layout.putConstraint(SpringLayout.NORTH, collect_info,
				20,
				SpringLayout.SOUTH, prev);

		layout.putConstraint(SpringLayout.NORTH, lokaler,
				50,
				SpringLayout.SOUTH, collect_info);

		layout.putConstraint(SpringLayout.NORTH, focusL,
				0,
				SpringLayout.NORTH, lokaler);

		layout.putConstraint(SpringLayout.WEST, focusL,
				5,
				SpringLayout.EAST, lokaler);
		
		
		layout.putConstraint(SpringLayout.NORTH, coordinatePrecisionL,
				10,
				SpringLayout.SOUTH, lokaler);
		
		layout.putConstraint(SpringLayout.WEST, coordinateprecision,
				5,
				SpringLayout.EAST, coordinatePrecisionL);
		
		layout.putConstraint(SpringLayout.NORTH, coordinateprecision,
				0,
				SpringLayout.NORTH, coordinatePrecisionL);
		
		
		
		layout.putConstraint(SpringLayout.NORTH, overrideD,
				5,
				SpringLayout.SOUTH, coordinatePrecisionL);
		
		layout.putConstraint(SpringLayout.NORTH, oDistrict,
				0,
				SpringLayout.NORTH, overrideD);
		
		layout.putConstraint(SpringLayout.WEST, oDistrict,
				5,
				SpringLayout.EAST, overrideD);
		
		layout.putConstraint(SpringLayout.NORTH, overrideP,
				0,
				SpringLayout.NORTH, overrideD);
		
		layout.putConstraint(SpringLayout.WEST, overrideP,
				0,
				SpringLayout.EAST, oDistrict);
		
		layout.putConstraint(SpringLayout.NORTH, oProvince,
				0,
				SpringLayout.NORTH, overrideD);
		
		layout.putConstraint(SpringLayout.WEST, oProvince,
				5,
				SpringLayout.EAST, overrideP);
		
		
		layout.putConstraint(SpringLayout.NORTH, distDir,
				5,
				SpringLayout.SOUTH, overrideD);
		
		layout.putConstraint(SpringLayout.WEST, distance,
				5,
				SpringLayout.EAST, distDir);
		
		layout.putConstraint(SpringLayout.NORTH, distance,
				0,
				SpringLayout.NORTH, distDir);
		
		layout.putConstraint(SpringLayout.WEST, direction,
				5,
				SpringLayout.EAST, distance);
		
		layout.putConstraint(SpringLayout.NORTH, direction,
				0,
				SpringLayout.NORTH, distDir);

		layout.putConstraint(SpringLayout.NORTH, localityL,
				10,
				SpringLayout.SOUTH, distDir);
		
		layout.putConstraint(SpringLayout.NORTH, localityD,
				0,
				SpringLayout.NORTH, localityL);

		layout.putConstraint(SpringLayout.WEST, localityD,
				5,
				SpringLayout.EAST, localityL);
		
		layout.putConstraint(SpringLayout.NORTH, rubinL,
				10,
				SpringLayout.SOUTH, localityL);

		/*layout.putConstraint(SpringLayout.WEST, rubinL,
		10,
		SpringLayout.WEST, this);*/

		layout.putConstraint(SpringLayout.NORTH, rubinD,
				0,
				SpringLayout.NORTH, rubinL);

		layout.putConstraint(SpringLayout.WEST, rubinD,
				5,
				SpringLayout.EAST, rubinL);

		layout.putConstraint(SpringLayout.NORTH, RT90L,
				10,
				SpringLayout.SOUTH, rubinL);

		layout.putConstraint(SpringLayout.NORTH, RT90D,
				0,
				SpringLayout.NORTH, RT90L);

		layout.putConstraint(SpringLayout.WEST, RT90D,
				5,
				SpringLayout.EAST, RT90L);

		layout.putConstraint(SpringLayout.NORTH, latlongL,
				10,
				SpringLayout.SOUTH, RT90L);

		layout.putConstraint(SpringLayout.NORTH, latlongD,
				0,
				SpringLayout.NORTH, latlongL);

		layout.putConstraint(SpringLayout.WEST, latlongD,
				5,
				SpringLayout.EAST, latlongL);
		
		layout.putConstraint(SpringLayout.NORTH, copyLastB,
				10,
				SpringLayout.SOUTH, latlongL);
		
		
		

		updateLocalityList();
		updateSpecimenList();
		setOldLocality();

		prev.addActionListener(this);
		next.addActionListener(this);
		localityD.addActionListener(this);
		rubinD.addActionListener(this);
		latlongD.addActionListener(this);
		RT90D.addActionListener(this);
		focusL.addActionListener(this);
		searchspecimens.addActionListener(this);
		copyLastB.addActionListener(this);
		//lokaler.addItemListener(this);

		cnr.addFocusListener(this);
		socken.addFocusListener(this);
		landskap.addFocusListener(this);
		lokaler.addFocusListener(this);
		oDistrict.addFocusListener(this);
		oProvince.addFocusListener(this);
		
		
		Keyboard.addActionListener(this);
		
		
		//pack();
		//setVisible(true);
		//addKeyListener(Keyboard.extatic);
		comboenabled = true;
	}


	public static void createSpecimenList() {
		GUI.setCursorWait();
		
		String province = landskap.getText();
		String district = socken.getText();
		//String province = "Lycksele Lappmark";
		//String district = "Malå";
		
		System.out.println("\ncreate SpecimenList: "+province+": "+district);  // TODO: print trace
		try {
			
			Connection conn = MYSQLConnection.getConn();
			// get data from MYSQL
			String sqlstmt = "SELECT specimens.AccessionNo, Year, Month, Day, original_text, Genus, Species, LEFT(Collector,64), specimens.InstitutionCode, locality.ID, specimens.locality, specimens.ID, RUBIN, RiketsN, RiketsO, Lat_dir, Lat_deg, Lat_min, Lat_sec, Long_dir, Long_deg, Long_min, Long_sec, specimens.CollectionCode, distance, direction, oDistrict, oProvince"
					//+ " FROM specimens left join specimen_locality on specimens.InstitutionCode = specimen_locality.InstitutionCode and specimens.AccessionNo = specimen_locality.AccessionNo left join locality on specimen_locality.locality_ID = locality.ID WHERE "
					+ " FROM specimens left join specimen_locality on specimens.ID = specimen_locality.specimen_ID  left join locality on specimen_locality.locality_ID = locality.ID WHERE "
					+ "Specimens.Province = ? and specimens.district = ?";
			System.out.println(sqlstmt);  // TODO: print trace
			
			PreparedStatement statement = conn.prepareStatement(sqlstmt);
			statement.setString(1, province);
			statement.setString(2, district);
			ResultSet result = statement.executeQuery();
			
			Connection h2Conn = MYSQLConnection.getH2Conn();
			
			
			//Create table tempspecimens if not exists  /// 
			String sqlstmt2 = "CREATE TABLE IF NOT EXISTS tempspecimens (AccessionNo VARCHAR(16), Year CHAR(4), Month CHAR(2), Day CHAR(2), original_text TEXT, Genus VARCHAR(32), Species VARCHAR(32), Collector VARCHAR(64), InstitutionCode VARCHAR(3), locality_ID INT(11), locality TEXT, specimens_ID INT(10), RUBIN VARCHAR(16), RiketsN VARCHAR(16), RiketsO VARCHAR(16), Lat_dir VARCHAR(1), Lat_deg VARCHAR(32), Lat_min VARCHAR(16), Lat_sec VARCHAR(16), Long_dir VARCHAR(1), Long_deg VARCHAR(32), Long_min VARCHAR(16), Long_sec VARCHAR(16), CollectionCode VARCHAR(10), distance INT(11), direction VARCHAR(4), oDistrict VARCHAR(32), oProvince VARCHAR(40));";
			PreparedStatement statement2 = h2Conn.prepareStatement(sqlstmt2);
			statement2.executeUpdate();
			
			//Empty H2 table
			String sqlstmt3 = "DELETE FROM tempspecimens;";
			PreparedStatement statement3 = h2Conn.prepareStatement(sqlstmt3);
			statement3.executeUpdate();
			// Insert  data into H2 from MYSQL
			while (result.next()) {
				String sqlstmt4 = "INSERT INTO tempspecimens (AccessionNo, Year, Month, Day, original_text, Genus, Species, Collector, InstitutionCode, locality_ID, locality, specimens_ID, RUBIN, RiketsN, RiketsO, Lat_dir, Lat_deg, Lat_min, Lat_sec, Long_dir, Long_deg, Long_min, Long_sec, CollectionCode, distance, direction, oDistrict, oProvince) "
						+ "		values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";
				System.out.println(result.getString(1)+", "+result.getString(2)+", "+result.getString(3)+", "+result.getString(4)+", "+result.getString(5)+", "+result.getString(6)); // TODO: print trace
				PreparedStatement statement4 = h2Conn.prepareStatement(sqlstmt4);
				statement4.setString(1, result.getString(1));
				statement4.setString(2, result.getString(2));
				statement4.setString(3, result.getString(3));
				statement4.setString(4, result.getString(4));
				statement4.setString(5, result.getString(5));
				statement4.setString(6, result.getString(6));
				statement4.setString(7, result.getString(7));
				statement4.setString(8, result.getString(8));
				statement4.setString(9, result.getString(9));
				statement4.setString(10, result.getString(10));
				statement4.setString(11, result.getString(11));
				statement4.setString(12, result.getString(12));
				statement4.setString(13, result.getString(13));
				statement4.setString(14, result.getString(14));
				statement4.setString(15, result.getString(15));
				statement4.setString(16, result.getString(16));
				statement4.setString(17, result.getString(17));
				statement4.setString(18, result.getString(18));
				statement4.setString(19, result.getString(19));
				statement4.setString(20, result.getString(20));
				statement4.setString(21, result.getString(21));
				statement4.setString(22, result.getString(22));
				statement4.setString(23, result.getString(23));
				statement4.setString(24, result.getString(24));
				statement4.setString(25, result.getString(25));
				statement4.setString(26, result.getString(26));
				statement4.setString(27, result.getString(27));
				statement4.setString(28, result.getString(28));
				statement4.executeUpdate();
			}
		} catch (SQLException e) {
			e.printStackTrace(); // TODO Auto-generated catch block
		}
		GUI.setCursorDefault();
	}
	
	/*
	public static void updateSpecimenList() {
		GUI.setCursorWait();
		String province = landskap.getText();
		String district = socken.getText();
		System.out.println("\nUppdate SpecimenList UI");  // TODO: print trace
		

		try {
			Connection conn = MYSQLConnection.getConn();
			String sqlstmt = "SELECT specimens.AccessionNo, Year, Month, Day, original_text, Genus, Species, Collector, specimens.InstitutionCode, locality.ID, locality.locality, specimens.ID, RUBIN, RiketsN, RiketsO, Lat_dir, Lat_deg, Lat_min, Lat_sec, Long_dir, Long_deg, Long_min, Long_sec, specimens.CollectionCode, distance, direction, oDistrict, oProvince"
					//+ " FROM specimens left join specimen_locality on specimens.InstitutionCode = specimen_locality.InstitutionCode and specimens.AccessionNo = specimen_locality.AccessionNo left join locality on specimen_locality.locality_ID = locality.ID WHERE "
					+ " FROM specimens left join specimen_locality on specimens.ID = specimen_locality.specimen_ID  left join locality on specimen_locality.locality_ID = locality.ID WHERE "
					+ "Specimens.Province = ? and specimens.district = ? limit ?, 1;";
			System.out.println(sqlstmt);  // TODO: print trace
			
			PreparedStatement statement = conn.prepareStatement(sqlstmt);
			statement.setString(1, province);
			statement.setString(2, district);
			statement.setInt(3, nr);
			ResultSet result = statement.executeQuery();

			if (result.next()) { // process results one row at a time
				accnr = result.getString(1);
				collCode = result.getString(24);
				instCode = result.getString(9);
				String text = "<html>" + result.getString(9) + " " + result.getString(1) + "<br>"  
						//+ result.getString(10) + " - "+ result.getString(11) + "<br>"
						+ "<i>" + result.getString(6) + " " + result.getString(7) 
						+ "<br> <br> </i> " + SQL.HTMLF(result.getString(5))
						+ "<br> <br>" + result.getString(8) + "  -  " +  result.getString(2)+ "-" + result.getString(3) + "-" + result.getString(4) + "</html>";

				System.out.println(text);  // TODO: print trace
				collect_info.setText(text);
				//collect_info.repaint();
				//collect_info.validate();
				rubinD.setText(result.getString(13));
				RT90D.setText(result.getString(14)+", "+result.getString(15));
				//41°25'01"N and 120°58'57"W
				latlongD.setText(result.getString(17)+"°"+result.getString(18)+"'"+result.getString(19)+"''"+ result.getString(16) +" and "+result.getString(21)+"°"+result.getString(22)+"'"+result.getString(23)+"''"+result.getString(20));
				specimenID = result.getString(12);
				distance.setText(result.getString(25));
				String directionS = "";
				if (result.getString(26) != null) {
					directionS = result.getString(26);
				}
				direction.setSelectedItem(directionS);
				
				oProvince.setText(result.getString(28));
				oDistrict.setText(result.getString(27));
				
				comboenabled = false;
				String lokalNamn = result.getString(11);
				if (lokalNamn == null) lokalNamn = "";
				System.out.println("Set Selected Locality: \""+ lokalNamn+"\" distance: "+result.getString(25) + " direction: "+result.getString(26)); // TODO: print trace
				
				if (!(oProvince.getText().equals(oldOverrideProvince) && oDistrict.getText().equals(oldOverrideDistrict))) {
					updateLocalityList();
				}

				lokaler.setSelectedItem(lokalNamn);
				comboenabled = true;
				//sp.revalidate();
				//Dimensio
				//setSize(this.getSize());
				//revalidate();
				//exstatic.repaint();
				//exstatic.pack();
			}
			
		} catch (SQLException e) {
			e.printStackTrace(); // TODO Auto-generated catch block
		}
		GUI.setCursorDefault();
	}*/
	
	public static void updateSpecimenList() {
		GUI.setCursorWait();
		//String province = landskap.getText();
		//String district = socken.getText();
		System.out.println("\nUppdate SpecimenList UI");  // TODO: print trace
		

		try {
			Connection conn = MYSQLConnection.getH2Conn();
			String sqlstmt = "SELECT AccessionNo, Year, Month, Day, original_text, Genus, Species, Collector, InstitutionCode, locality_ID, locality, specimens_ID, RUBIN, RiketsN, RiketsO, Lat_dir, Lat_deg, Lat_min, Lat_sec, Long_dir, Long_deg, Long_min, Long_sec, CollectionCode, distance, direction, oDistrict, oProvince"
					//+ " FROM specimens left join specimen_locality on specimens.InstitutionCode = specimen_locality.InstitutionCode and specimens.AccessionNo = specimen_locality.AccessionNo left join locality on specimen_locality.locality_ID = locality.ID WHERE "
					+ " FROM tempspecimens ORDER BY Year, Month, Day, AccessionNo limit ?, 1;";
			System.out.println(sqlstmt);  // TODO: print trace
			
			PreparedStatement statement = conn.prepareStatement(sqlstmt);
			statement.setInt(1, nr);
			ResultSet result = statement.executeQuery();
			
			

			if (result.next()) { // process results one row at a time
				accnr = result.getString(1);
				collCode = result.getString(24);
				instCode = result.getString(9);
				String text = "<html>" + result.getString(9) + " " + result.getString(1) + "<br>"  
						//+ result.getString(10) + " - "+ result.getString(11) + "<br>"
						+ "<i>" + result.getString(6) + " " + result.getString(7) 
						+ "<br> <br> </i> " + SQL.HTMLF(result.getString(5))
						+ "<br> <br>" + result.getString(8) + "  -  " +  result.getString(2)+ "-" + result.getString(3) + "-" + result.getString(4) + "</html>";

				System.out.println(text);  // TODO: print trace
				collect_info.setText(text);
				//collect_info.repaint();
				//collect_info.validate();
				dbLocality = result.getString(11);
				localityD.setText(dbLocality);
				rubinD.setText(result.getString(13));
				RT90D.setText(result.getString(14)+", "+result.getString(15));
				//41°25'01"N and 120°58'57"W
				latlongD.setText(result.getString(17)+"°"+result.getString(18)+"'"+result.getString(19)+"''"+ result.getString(16) +" and "+result.getString(21)+"°"+result.getString(22)+"'"+result.getString(23)+"''"+result.getString(20));
				//Lat_dir, Lat_deg, Lat_min, Lat_sec, Long_dir, Long_deg, Long_min, Long_sec
				latdir = result.getString(16);
				latdeg = result.getString(17);
				latmin = result.getString(18);
				latsec = result.getString(19);
				longdir = result.getString(20);
				longdeg = result.getString(21);
				longmin = result.getString(22); 
				longsec = result.getString(23);
				specimenID = result.getString(12);
				System.out.println("Specimen ID: "+specimenID);
				
				
				comboenabled = false;
				Connection MySQLconn = MYSQLConnection.getConn();
				String sqlstmt2 = "SELECT locality, distance, direction, oDistrict, oProvince FROM specimen_locality left join locality on specimen_locality.locality_ID = locality.ID WHERE specimen_ID = ?;";
				System.out.println("Test1" + sqlstmt2);
				PreparedStatement statement2 = MySQLconn.prepareStatement(sqlstmt2);
				statement2.setString(1, specimenID);
				System.out.println(sqlstmt2+" specimenID: "+specimenID);
				ResultSet result2 = statement2.executeQuery();
				String lokalNamn = "";
				String distanceStr = "";
				String directionStr = "";
				String oDistrictStr = "";
				String oProvinceStr = "";
				if (result2.next()) { 
					System.out.println("upsalget lokalnamn: "+result2.getString(1));
					lokalNamn = result2.getString(1);
					distanceStr = result2.getString(2);
					directionStr = result2.getString(3);
					oDistrictStr = result2.getString(4);
					oProvinceStr = result2.getString(5);
					
					if (distanceStr == null || distanceStr == "") {
						distanceStr = result.getString(25);
					}
					
					if (directionStr == null || directionStr == "") {
						directionStr = result.getString(26);
					}
					
					if (oDistrictStr == null || oDistrictStr == "") {
						oDistrictStr = result.getString(27);
					}
					
					if (oProvinceStr == null || oProvinceStr == "") {
						oProvinceStr = result.getString(28);
					}
					
					if (lokalNamn == null || lokalNamn == "") {
						lokalNamn = result.getString(11);
						if (lokalNamn == null) lokalNamn = "";
					}
				} else {
					System.out.println("inget upsalget lokalnamn");
					lokalNamn = result.getString(11);
					distanceStr = result.getString(25);
					directionStr = result.getString(26);
					oDistrictStr = result.getString(27);
					oProvinceStr = result.getString(28);
				}
				
				if (directionStr==null) directionStr = "";
				
				distance.setText(distanceStr);
				direction.setSelectedItem(directionStr);
				oProvince.setText(oProvinceStr);
				oDistrict.setText(oDistrictStr);
				
				System.out.println("Set Selected Locality: \""+ lokalNamn+"\" distance: "+result.getString(25) + " direction: "+result.getString(26)); // TODO: print trace
				
				if (!(oProvince.getText().equals(oldOverrideProvince) && oDistrict.getText().equals(oldOverrideDistrict))) {
					updateLocalityList();
				}

				lokaler.setSelectedItem("");
				lokaler.setSelectedItem(lokalNamn);
				comboenabled = true;
				//sp.revalidate();
				//Dimensio
				//setSize(this.getSize());
				//revalidate();
				//exstatic.repaint();
				//exstatic.pack();
			}
			
		} catch (SQLException e) {
			e.printStackTrace(); // TODO Auto-generated catch block
			System.out.println("Nu blev det fel");
		}
		GUI.setCursorDefault();
	}

	
	public static String getProvinceFromUI() {
		if (!oProvince.getText().equals("")) {
			return oProvince.getText();
		} else {
			return landskap.getText();
		}
	}
	
	public static String getDistrictFromUI() {
		if (!oDistrict.getText().equals("")) {
			return oDistrict.getText();
		} else {
			return socken.getText();
		}
	}
	
	public static void updateLocalityList() {
		GUI.setCursorWait();
		System.out.println("\nUppdate LocalityList UI"); // TODO print trace
		
		String province = getProvinceFromUI();
		String district = getDistrictFromUI();

		try {
			Connection conn = MYSQLConnection.getConn();
			String sqlstmt2 = "SELECT locality FROM locality WHERE Province = ? Collate utf8_swedish_ci and district = ? Collate utf8_swedish_ci order by locality Collate utf8_swedish_ci;";
			System.out.println(sqlstmt2 + " Province: " +province + " district: "+district);
			PreparedStatement statement2 = conn.prepareStatement(sqlstmt2);
			statement2.setString(1, province);
			statement2.setString(2, district);
			ResultSet result2 = statement2.executeQuery();
			comboenabled = false;
			lokaler.removeAllItems();
			lokaler.addItem("");
			while (result2.next()) {
				//System.out.println(result2.getString(1));
				lokaler.addItem(result2.getString(1));
			}
			//lokaler.setSelectedItem(result.getString(11));
			comboenabled = true;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		GUI.setCursorDefault();
	}
	
	public String getSelectedText() {
		return collect_info.getSelectedText();
	}

	/*
	public void setOldOverrides() {
		
		  dff
	}*/
	
	public void next() {
		System.out.println("\nstega upp nummer");  //TODO: print trace
		lastLocality = (String)lokaler.getSelectedItem();
		lastDirection = (String) direction.getSelectedItem();
		lastDistance = distance.getText();
		lastODistrict = oDistrict.getText();
		lastOProvince = oProvince.getText();
		updateSpecimenLocality();
		nr++;
		cnr.setText(new Integer(nr).toString());
		try {
			Settings.setValue("cnr",cnr.getText());
		} catch (IOException e) {
			e.printStackTrace(); // TODO Auto-generated catch block
		}
		updateSpecimenList();
		setOldLocality();
		//setOldOverrides();
	}
	
	public void prev() {
		if (nr>0) {
			System.out.println("\nstega ner nummer"); //TODO: print trace
			updateSpecimenLocality();
			nr--;
			cnr.setText(new Integer(nr).toString());
			try {
				Settings.setValue("cnr",cnr.getText());
			} catch (IOException e) {
				e.printStackTrace(); // TODO Auto-generated catch block
			}
			updateSpecimenList();
			setOldLocality();
		}
	}
	
	public void copyLast() {
		String lokalNamn = (String)lokaler.getSelectedItem();
		if (lastLocality == null) lastLocality ="";
		if (lokalNamn == null) lokalNamn ="";
		if (lastOProvince == null) lastODistrict ="";
		if (lastODistrict == null) lastODistrict ="";
		System.out.println("Copy Last: "+ lastLocality + "Last dist: "+lastDistance + "oProvince: " + lastOProvince  + "oDistrict: "+ lastODistrict );
		boolean fl = false;
		if (!lastOProvince.equals(oProvince) || !lastODistrict.equals(oDistrict)) {
			oProvince.setText(lastOProvince);
			oDistrict.setText(lastODistrict);
			updateLocalityList();
			fl = true;
		}
		
		if (!lastLocality.equals(lokalNamn)) {
			lokaler.setSelectedItem(lastLocality);
			fl = true;
		}
		if (!lastDistance.equals(distance)) {
			distance.setText(lastDistance);
			fl = true;
		}
		if (!lastDirection.equals(direction)) {
			direction.setSelectedItem(lastDirection);
			fl = true;
		}
		
		if (fl) updateSpecimenLocality();
	}
	
	public void focusRubin() {
		String rubin = rubinD.getText();
		if (!rubin.equals("")) {
			System.out.println("click RUBIN: "+rubin);
			Rubin r = new Rubin(rubin, "Rubin", Color.green);
			GUI.canvas.delLayer("Rubin");
			GUI.canvas.addLayerTop(r);
			Point p = r.getMiddle();
			//Coordinates rt90 = new Coordinates(p);
			//Coordinates swtm = rt90.convertToSweref99TMFromRT90();
			//p = swtm.getPoint();
			GUI.canvas.focus(p);
		}
	}
	
	public void focusRT90() {
		String coord = RT90D.getText();
		System.out.println("click RT90: "+RT90D.getText());
		if (!coord.equals(", ")) {
			Point p = new Point(RT90D.getText());
			System.out.println("point: "+p);
			Coordinates rt90 = new Coordinates(p);
			Coordinates swtm = rt90.convertToSweref99TMFromRT90();
			p = swtm.getPoint();
			GUI.canvas.focus(p);
			GUI.canvas.setCoordinate(p);
		}
	}
	
	public void focuslatlong() {
		System.out.println("Focus on lat/long");
		Coordinates c = new Coordinates(0,0);
		System.out.println("lat: "+latdeg+"long: "+longdeg);
		c.latlong(latdeg, longdeg, latmin, longmin, latsec, longsec, latdir, longdir);
		System.out.println(c);
		c = c.convertToSweref99TMFromWGS84();
		System.out.println(c);
		Point p = new Point(c.toPoint());
		System.out.println(p);
		GUI.canvas.focus(p);
		GUI.canvas.setCoordinate(p);
	}
	
	public void focusLocality() {
		GUI.setCursorWait();
		String lokal = (String) lokaler.getSelectedItem();
		String landskapN = getProvinceFromUI(); // landskap.getText();
		String sockenN = getDistrictFromUI(); //socken.getText();
		int distanceI;
		String directionS;
		try {
			distanceI = Integer.parseInt(distance.getText());
			directionS = (String) direction.getSelectedItem();
		} catch(Exception e) {
			distanceI =-1;
			directionS = "NAN";
		}
		System.out.println("focus Locality: "+lokal);
		if (!lokal.equals("")) {
			try {
				String query = "SELECT SWTMN, SWTME from locality where locality = ? and province = ? and district = ?";
				System.out.println(query);
				Connection conn = MYSQLConnection.getConn();
				PreparedStatement statement = conn.prepareStatement(query);
				statement.setString(1, lokal);
				statement.setString(2, landskapN);	
				statement.setString(3, sockenN);	
				ResultSet result = statement.executeQuery();
				if (result.next()) {
					
					String SWTMN = result.getString(1);
					String SWTME = result.getString(2);
					System.out.println("locality coord: "+SWTMN + ", " +SWTME);
					Point p = new Point(Integer.parseInt(SWTME), Integer.parseInt(SWTMN));
					GUI.canvas.focus(p);
					GUI.canvas.setCoordinate(p);
					if (distanceI>0) {
						System.out.println("lägger till sträck för distanc, direction. dir: ");
						//Distance(String name, Point c, int dist, String direction)
						//GUI.canvas.getLayer("distance");
						
						System.out.println(distanceI);
						System.out.println(directionS);
						GUI.canvas.delLayer("distance");
						GUI.canvas.addLayerTop(new Distance("distance",p,distanceI,directionS,CoordSystem.Sweref99TM));
					}
				}
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		}
		GUI.setCursorDefault();
	}
	
	public void focusLocalityD() {
		if (Keyboard.isKeyDown(KeyEvent.VK_SHIFT )) {
			System.out.println("add Locality");
		} else {
			System.out.println("focus LocalityD: ");
			String locality = dbLocality;
			String province = landskap.getText();
			String district = socken.getText();
			if (!locality.equals("")) {
				try {
				String query = "SELECT SWTMN, SWTME from locality where locality = ? and province = ? and district = ?";
				System.out.println(query);
				Connection conn = MYSQLConnection.getConn();
				PreparedStatement statement = conn.prepareStatement(query);
				statement.setString(1, locality);
				statement.setString(2, province);	
				statement.setString(3, district);	
				ResultSet result = statement.executeQuery();
				if (result.next()) {
					
					String SWTMN = result.getString(1);
					String SWTME = result.getString(2);
					System.out.println("locality coord: "+SWTMN + ", " +SWTME);
					Point p = new Point(Integer.parseInt(SWTME), Integer.parseInt(SWTMN));
					GUI.canvas.focus(p);
					GUI.canvas.setCoordinate(p);
				}
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
		}
		}
	}
	
	private void createSLDB(String localityID, String specimen_ID) {
		GUI.setCursorWait();
		String sqlstmt = "INSERT INTO Specimen_Locality (locality_ID, specimen_ID, InstitutionCode, CollectionCode, AccessionNo, createdby, distance, direction, oDistrict, oProvince) VALUES (?,?,?,?,?,?,?,?,?,?)";
		System.out.println(sqlstmt + " - " + localityID + " - "+specimen_ID); //TODO print trace
		
		String distN = null;
		try {
			Integer.parseInt(distance.getText()); //TODO print trace
			distN = distance.getText();
			//System.out.println("distance is a number: "+distN); //TODO print trace
		} catch(Exception e) {
			//System.out.println("distance is not a number: "+distance.getText()); //TODO print trace
		}
		
		try {
			Connection conn = MYSQLConnection.getConn();
			PreparedStatement preparedStmt = conn.prepareStatement(sqlstmt);
			preparedStmt.setString (1, localityID);
			preparedStmt.setString (2, specimen_ID);
			preparedStmt.setString (3, instCode);
			preparedStmt.setString (4, collCode);
			preparedStmt.setString (5, accnr);
			preparedStmt.setString (6, Settings.getValue("user"));
			preparedStmt.setString (7, distN);
			preparedStmt.setString (8, direction.getSelectedItem().toString());
			preparedStmt.setString (9, oDistrict.getText().toString());
			preparedStmt.setString (10, oProvince.getText().toString());
			preparedStmt.execute();
			SpecimenList.updateLocalityList();
			SpecimenList.updateSpecimenList();
		} catch (SQLException | IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			JOptionPane.showMessageDialog(null, "Couldn't create locality - collect link", "InfoBox: " + "SQL Error", JOptionPane.INFORMATION_MESSAGE);
		}
		GUI.setCursorDefault();
	}

	private void deleteSLDB(String localityID, String specimen_ID) {
		GUI.setCursorWait();
		String sqlstmt = "DELETE FROM Specimen_Locality WHERE locality_ID = ? and specimen_ID = ?"; 
		System.out.println(sqlstmt + " - " + localityID + " - "+specimen_ID); // TODO: print trace
		try {
			Connection conn = MYSQLConnection.getConn();
			PreparedStatement preparedStmt = conn.prepareStatement(sqlstmt);
			preparedStmt.setString (1, localityID);
			preparedStmt.setString (2, specimen_ID);
			preparedStmt.execute();
			SpecimenList.updateLocalityList();
			SpecimenList.updateSpecimenList();
		} catch (SQLException e1) {
			e1.printStackTrace();  // TODO Auto-generated catch block
			JOptionPane.showMessageDialog(null, "Couldn't delete locality - collect link", "InfoBox: " + "SQL Error", JOptionPane.INFORMATION_MESSAGE);
		}
		GUI.setCursorDefault();
	}

	private String getLocalityID(String lokalNamn, String landskapN, String sockenN) throws SQLException {
		String sqlstmt = "SELECT ID from locality where locality = ? and province = ? and district = ?;";
		System.out.println(sqlstmt + "\nlocality: "+  lokalNamn + " landskap: " + landskapN + " socken: " + sockenN);  // TODO: print trace
		Connection conn = MYSQLConnection.getConn();
		PreparedStatement statement = conn.prepareStatement(sqlstmt);
		statement.setString(1, lokalNamn);
		statement.setString(2, landskapN);	
		statement.setString(3, sockenN);	
		ResultSet result = statement.executeQuery();
		if (result.next()) {
			return result.getString(1);
		}
		return null;
	}

	@Override
	public void itemStateChanged(ItemEvent e) {

	}

	
	public void updateSpecimenLocality() {
		System.out.println("Update specimen locality specimenID: " + specimenID + " Lokalnamn: " + (String)lokaler.getSelectedItem() );
		GUI.setCursorWait();
		if (comboenabled) {
			String lokalNamn = (String)lokaler.getSelectedItem();
			//String landskapN = landskap.getText();
			//String sockenN = socken.getText();
			
			String landskapN = getProvinceFromUI();
			String sockenN = getDistrictFromUI();
			

			System.out.println("lokalNamn: " + lokalNamn + " old name: "+ oldLokalNamn); // TODO: print trace
			if (oldLocality == null) oldLocality ="";
			if (lokalNamn == null) lokalNamn ="";

			if(!oldLocality.equals("") && lokalNamn.equals("") ) {
				System.out.println("eraze locality Specimen bridge: "+ oldLocality); // TODO: print trace
				try {
					String locID = getLocalityID(oldLocality, landskapN, sockenN);
					deleteSLDB(locID, specimenID);
				} catch (SQLException e1) {
					e1.printStackTrace(); // TODO Auto-generated catch block
				}

			} else if (!lokalNamn.equals("")) {

				System.out.println("\nÄndra Lokal i db: \""+ lokalNamn +"\""); // TODO: print trace

				try {
					String localityID = getLocalityID(lokalNamn, landskapN, sockenN);
					if (localityID == null) System.out.println("error");
					System.out.println("localityID: "+localityID); // TODO: print trace
					try {
						String distN = null;
						try {
							Integer.parseInt(distance.getText());
							distN = distance.getText();
							System.out.println("distance is a number: "+distN);
						} catch(Exception e) {
							System.out.println("distance is not a number: "+distance.getText());
						}
						
						String directN;
						if (direction.getSelectedItem()==null) {
							directN="";
						} else {
							directN = direction.getSelectedItem().toString();
						}
						
						String sqlstmt2 = "UPDATE specimen_locality set locality_ID = ?, modifiedby = ? , modified = now(), direction = ?, distance = ?, oDistrict = ?, oProvince = ? where specimen_ID = ?;";
						//System.out.println(sqlstmt2 + " ID: "+localityID+" user: "+ Settings.getValue("user")+ " direction: " + direction.getSelectedItem().toString() + " distance: " + distN + " oDistrict: " + oDistrict.getText() + "oProvince: " + oProvince.getText()+ " specimenID: "+specimenID); // TODO: print trace
						//System.out.println("localityID: "+localityID);
						
						Connection conn = MYSQLConnection.getConn();
						PreparedStatement statement2 = conn.prepareStatement(sqlstmt2);
						statement2.setString(1, localityID);
						statement2.setString(2, Settings.getValue("user"));
						statement2.setString(3, directN);
						statement2.setString(4, distN);
						statement2.setString(5, oDistrict.getText());
						statement2.setString(6, oProvince.getText());
						statement2.setString(7, specimenID);
						int r = statement2.executeUpdate();
						if(r !=0) {
							System.out.println("success: "+r);  // TODO: print trace
						} else {
							System.out.println("fail");  // TODO: print trace
							System.out.println(sqlstmt2);
							createSLDB(localityID, specimenID);
						}
					} catch (SQLException | IOException e1) {
						e1.printStackTrace(); // TODO Auto-generated catch block
						JOptionPane.showMessageDialog(null, "Couldn't update locality - collect link", "InfoBox: " + "SQL Error", JOptionPane.INFORMATION_MESSAGE);
					}
				} catch (SQLException e1) {
					e1.printStackTrace(); // TODO Auto-generated catch block
					JOptionPane.showMessageDialog(null, "Couldn't update locality - collect link", "InfoBox: " + "SQL Error", JOptionPane.INFORMATION_MESSAGE);
				}
			}
		}
		GUI.setCursorDefault();
	}
	
	public void setOldLocality() {
		oldLocality = (String) lokaler.getSelectedItem();
		oldOverrideDistrict = oProvince.getText();
		oldOverrideProvince = oDistrict.getText();
	}
	
	@Override
	public void focusGained(FocusEvent fe) {
		if (fe.getSource() == lokaler) {
			setOldLocality();
			System.out.println("lokaler gained focus: "+oldLocality); //TODO: print trace
			
		}
	}

	@Override
	public void focusLost(FocusEvent fe) {
		System.out.println("\nlost focus\n"); //TODO: print trace

		if (fe.getSource() == lokaler) {
			System.out.println("lokaler lost focus"); //TODO: print trace
			String newLocality = (String) lokaler.getSelectedItem();
			if (newLocality!= null) {
				if (!oldLocality.equals(newLocality)) {
					updateSpecimenLocality();
				}
			}
		}
		if (fe.getSource() == cnr) {
			System.out.println("save nr settings");  //TODO: print trace
			nr = Integer.parseInt(cnr.getText());
			try {
				Settings.setValue("cnr",cnr.getText());
			} catch (IOException e) {
				e.printStackTrace(); // TODO Auto-generated catch block
			}
			updateSpecimenList();
		}
		if (fe.getSource() == socken) {
			System.out.println("save socken settings"); //TODO: print trace
				try {
					Settings.setValue("socken", socken.getText());
				} catch (IOException e) {
					e.printStackTrace(); // TODO Auto-generated catch block
				}
			updateLocalityList();
		}
		if (fe.getSource() == landskap) {
			System.out.println("save landskap settings"); //TODO: print trace
			try {
				Settings.setValue("landskap", landskap.getText());
			} catch (IOException e) {
				e.printStackTrace();  // TODO Auto-generated catch block
			}
			updateLocalityList();
		}
		if (fe.getSource() == oProvince) {
			System.out.println("oProvnce lost focus");
			updateLocalityList();
		}
		if (fe.getSource() == oDistrict) {
			System.out.println("oDistrict lost focus");
			updateLocalityList();
		}
		//uppdate();
	}


	@Override
	public void nActionPerformed(String a) {
		Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		if(!(c instanceof JTextField)) {
			switch (a) {
			case "Next":
				next();
				break;
			case  "Prev":
				prev();
				break;
			case  "copyLastB":
				System.out.println("Copy last action");
				copyLast();
				break;
		}
		}
	}
	
	public void actionPerformed(ActionEvent ev) {
		if ("next".equals(ev.getActionCommand())) {
			next();
		} else if ("prev".equals(ev.getActionCommand())) {
			prev();
		} else if ("localityD".equals(ev.getActionCommand())) {
			focusLocalityD();
		} else if ("rubinD".equals(ev.getActionCommand())) {
			focusRubin();
		} else if ("latlongD".equals(ev.getActionCommand())) {
			System.out.println("click lat/long: "+latlongD.getText());
			focuslatlong();
		} else if ("RT90D".equals(ev.getActionCommand())) {
			focusRT90();
		} else if ("focusL".equals(ev.getActionCommand())) {
			focusLocality();
		} else if ("searchspecimens".equals(ev.getActionCommand())) {
			System.out.println("Search specimens action");
			createSpecimenList();
		} else if ("copyLastB".equals(ev.getActionCommand())) {
			System.out.println("Copy last action");
			copyLast();
		}
	}
	
	public static boolean isOpen() {
		return comboenabled;
	}

	public static void main(String[] args) {
		//createSpecimenList();
	/*	Connection h2Conn;
		try {
			h2Conn = MYSQLConnection.getH2Conn();
			String delstr = "Drop table tempspecimens";
			PreparedStatement statement9 = h2Conn.prepareStatement(delstr);
			statement9.executeUpdate();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
	}

}
