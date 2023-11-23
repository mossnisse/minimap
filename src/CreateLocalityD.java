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
	
	private JTextField localityf, districtf, provincef, countryf, continentf, Nf, Ef, coordSysf, commentsf, alternativef, coordsourcef;
	
	private JOptionPane optionPane;
	
	//private Connection conn;
	
	public CreateLocalityD(Frame aFrame, String N, String E, String province, String district, CoordSystem coordSys) {
		super(aFrame, true);
		setTitle("Create new Locality");
		
		//this.conn = conn;
		this.localityf = new JTextField();
		this.districtf = new JTextField();
		this.provincef = new JTextField();
		this.countryf = new JTextField();
		this.continentf = new JTextField();
		this.Nf = new JTextField();
		this.Ef = new JTextField();
		this.coordSysf = new JTextField();
		this.commentsf = new JTextField();
		this.alternativef = new JTextField();
		this.coordsourcef = new JTextField();
		
		H2Table odb = (H2Table) GUI.canvas.getLayer("Ortnamnsdb");
		Point p = new Point(Integer.parseInt(N),Integer.parseInt(E));
		if (odb== null) {
			System.out.println("hittar inte ortnamnslagret");
		}
		String sugestName = odb.findNearest(p,1000);
		
		Object[] array = {"Locality", localityf, "Alternative names", alternativef, "coordinate source", coordsourcef, "comments", commentsf,  "North", Nf, "East", Ef, "Provins", provincef, "District", districtf};

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
	    
	    this.localityf.setText(sugestName);
	    this.districtf.setText(district);
	    this.provincef.setText(province);
	    this.countryf.setText("Sweden");
	    this.continentf.setText("Europe");
	    this.Nf.setText(N);
	    this.Ef.setText(E);
	    this.coordSysf.setText(coordSys.getName());
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
		
		localityf = new JTextField();
		districtf = new JTextField();
		provincef = new JTextField();
		countryf = new JTextField();
		continentf = new JTextField();
		Nf = new JTextField();
		Ef = new JTextField();
		coordSysf = new JTextField();
		
		Object[] array = {"Locality", localityf, "North", Nf, "East", Ef, "Coordinate System", coordSysf, "Provins", provincef, "District", districtf};

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
		String localityName = localityf.getText();
		String districtName = districtf.getText();
		String provinceName = provincef.getText();
		String Nt = Nf.getText();
		String Et = Ef.getText();
	
		Coordinates sweref99TM = new Coordinates(Double.parseDouble(Nt), Double.parseDouble(Et));
		Coordinates wgs84 = sweref99TM.convertToWGS84FromSweref99TM();
		Coordinates rt90 = wgs84.convertToRT90FromSweref99TM();
		Point rt90p = rt90.getPoint();
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
		    preparedStmt.setDouble(6, wgs84.getNorth());
		    preparedStmt.setDouble(7, wgs84.getEast());
		    preparedStmt.setString (8, Integer.toString(rt90p.getY()));
		    preparedStmt.setString (9, Integer.toString(rt90p.getX()));
		    preparedStmt.setString (10, Settings.getValue("user"));
		    preparedStmt.setString (11, alternativef.getText() );
		    preparedStmt.setString (12, coordsourcef.getText());
		    preparedStmt.setString (13, commentsf.getText());
		    preparedStmt.execute();
			SpecimenList.updateLocalityList();
			SpecimenList.updateSpecimenList();		
		} catch (SQLException | IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
		}
		
		
	}

}
