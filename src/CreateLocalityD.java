import geometry.Point;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;


public class CreateLocalityD extends JDialog implements ActionListener, PropertyChangeListener{

	private static final long serialVersionUID = 7103631385545360092L;
	
	private JTextField locality, district, province, country, continent, RT90N, RT90E, comments, alternative, coordsource;
	
	private JOptionPane optionPane;
	
	//private Connection conn;
	
	public CreateLocalityD(Frame aFrame, String RT90N, String RT90E, String province, String district) {
		super(aFrame, true);
		setTitle("Create new Locality");
		
		//this.conn = conn;
		this.locality = new JTextField();
		this.district = new JTextField();
		this.province = new JTextField();
		this.country = new JTextField();
		this.continent = new JTextField();
		this.RT90N = new JTextField();
		this.RT90E = new JTextField();
		this.comments = new JTextField();
		this.alternative = new JTextField();
		this.coordsource = new JTextField();
		
		H2Table odb = (H2Table) GUI.canvas.getLayer("Ortnamnsdb");
		Point p = new Point(Integer.parseInt(RT90N),Integer.parseInt(RT90E));
		if (odb== null) {
			System.out.println("hittar inte ortnamnslagret");
		}
		String sugestName = odb.findNearest(p,1000);
		
		Object[] array = {"Locality", locality, "Alternative names", alternative, "coordinate source", coordsource, "comments", comments,  "RT90: North", RT90N, "East", RT90E, "Provins", province, "District", district};

	        //Create an array specifying the number of dialog buttons
	        //and their text.
		Object[] options = {"Cancel", "OK"};

	        //Create the JOptionPane.
	    optionPane = new JOptionPane(array,
	                                    JOptionPane.QUESTION_MESSAGE,
	                                    JOptionPane.YES_NO_OPTION,
	                                    null,
	                                    options,
	                                    options[0]);

	        //Make this dialog display it.
	    setContentPane(optionPane);
	    pack();
	    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
	    
	    this.locality.setText(sugestName);
	    this.district.setText(district);
	    this.province.setText(province);
	    this.country.setText("Sweden");
	    this.continent.setText("Europe");
	    this.RT90N.setText(RT90N);
	    this.RT90E.setText(RT90E);
	    addWindowListener(new WindowAdapter() {
	    	public void windowClosing(WindowEvent we) {
	    		/*
	    		 * Instead of directly closing the window,
	    		 * we're going to change the JOptionPane's
	    		 * value property.
	    		 */
	    		/*optionPane.setValue(new Integer(
                                     JOptionPane.CLOSED_OPTION));*/
	    	}
	    });
	    addComponentListener(new ComponentAdapter() {
	    	public void componentShown(ComponentEvent ce) {
	    		//north.requestFocusInWindow();
	    	}
	    });
	    optionPane.addPropertyChangeListener(this);
	}
	
	public CreateLocalityD(Frame aFrame ) {
		super(aFrame, true);
		setTitle("Create new Locality");
		
		locality = new JTextField();
		district = new JTextField();
		province = new JTextField();
		country = new JTextField();
		continent = new JTextField();
		RT90N = new JTextField();
		RT90E = new JTextField();
		
		Object[] array = {"Locality", locality, "RT90: North", RT90N, "East", RT90E, "Provins", province, "District", district};

	        //Create an array specifying the number of dialog buttons
	        //and their text.
		Object[] options = {"Cancel", "OK"};

	        //Create the JOptionPane.
	    optionPane = new JOptionPane(array,
	                                    JOptionPane.QUESTION_MESSAGE,
	                                    JOptionPane.YES_NO_OPTION,
	                                    null,
	                                    options,
	                                    options[0]);

	        //Make this dialog display it.
	    setContentPane(optionPane);
	    pack();
	    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
	}
	
	 

	@Override
	public void propertyChange(PropertyChangeEvent e) {
		// TODO Auto-generated method stub
		if(isVisible()){
    		System.out.println(e.getNewValue());
    		if (e.getNewValue()=="OK") {
    			CreateLocality();
    			setVisible(false);
    			GUI.canvas.repaint();
            	dispose();
    		} else if (e.getNewValue()=="Cancel") {
    			System.out.println("Stänger dialog");
    			setVisible(false);
            	dispose();
    		} 
    	}
	}

	@Override
	public void actionPerformed(ActionEvent arg0) {
		// TODO Auto-generated method stub
		
	}
	
	public void CreateLocality() {
		System.out.println("Skapar lokalD");
		String localityName = locality.getText();
		String districtName = district.getText();
		String provinceName = province.getText();
		String RT90Nt = RT90N.getText();
		String RT90Et = RT90E.getText();
	
		Coordinates c = new Coordinates(Double.parseDouble(RT90Nt), Double.parseDouble(RT90Et));
		c = c.convertToWGS84FromSweref();
		/*String sqlstmt = "INSERT INTO locality (locality, district, province, country, continent, RT90N, RT90E, lat, long) "
		 		+ "VALUES locality = \""+localityName+"\", district = \""+districtName+"\", province = \""+provinceName+"\", country = \"Sweden\", continent = \"Europe\", RT90N = \""+ RT90Nt +"\", RT90E = \""+RT90Et+"\", lat = \"\", long = \"\";";*/
		
		String sqlstmt = "INSERT INTO locality (locality, district, province, country, continent, lat, `long`, RT90N, RT90E, createdby, alternative_names, coordinate_source, lcomments) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
		
	    System.out.println(sqlstmt + " - " + localityName);
		try {
			Connection conn = MYSQLConnection.getConn();
			PreparedStatement preparedStmt = conn.prepareStatement(sqlstmt);
			preparedStmt.setString (1, localityName);
		    preparedStmt.setString (2, districtName);
		    preparedStmt.setString (3, provinceName);
		    preparedStmt.setString (4, "Sweden");
		    preparedStmt.setString (5, "Europe");
		    preparedStmt.setDouble(6, c.getNorth());
		    preparedStmt.setDouble(7, c.getEast());
		    preparedStmt.setString (8, RT90Nt);
		    preparedStmt.setString (9, RT90Et);
		    preparedStmt.setString (10, Settings.getValue("user"));
		    preparedStmt.setString (11, alternative.getText() );
		    preparedStmt.setString (12, coordsource.getText());
		    preparedStmt.setString (13, comments.getText());
		    preparedStmt.execute();
			SpecimenList.updateLocalityList();
			SpecimenList.updateSpecimenList();		
		} catch (SQLException | IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
		}
		
		
	}

}
