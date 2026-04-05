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

	public CreateLocalityD(Frame aFrame, String N, String E, String province, String district, CoordSystem coordSys) {
		super(aFrame, true);
		setTitle("Create new Locality");

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

		//Create an array specifying the number of dialog buttons and their text.
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

	@Override
	public void propertyChange(PropertyChangeEvent e) {
		if (!isVisible() || !"value".equals(e.getPropertyName())) return;

		Object value = e.getNewValue();
		if (value == JOptionPane.UNINITIALIZED_VALUE) return;

		// Modern switch handles the button clicks
		switch (value.toString()) {
			case "OK" -> {
				try {
					CreateLocality();
					setVisible(false);
					GUI.canvas.repaint();
					dispose();
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(this, "Error saving: " + ex.getMessage());
				}
			}
			case "Cancel" -> {
				setVisible(false);
				dispose();
			}
		}
		// Reset optionPane so it can be clicked again if there was a validation error
		optionPane.setValue(JOptionPane.UNINITIALIZED_VALUE);
	}

	@Override
	public void actionPerformed(ActionEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	public void CreateLocality() {
		String localityName = localityf.getText();
		String districtName = districtf.getText();
		String provinceName = provincef.getText();
		String Nt = Nf.getText();
		String Et = Ef.getText();

		Coordinates sweref99TM = new Coordinates(Double.parseDouble(Nt), Double.parseDouble(Et));
		Coordinates wgs84 = sweref99TM.convertToWGS84FromSweref99TM();
		Coordinates rt90 = wgs84.convertToRT90FromSweref99TM();
		Point rt90p = rt90.getPoint();

		String sqlstmt = "INSERT INTO locality (locality, district, province, country, continent, lat, `long`, RT90N, RT90E, createdby, alternative_names, coordinate_source, lcomments) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";

	    //System.out.println(sqlstmt + " - " + localityName);
		try {
			Connection conn = DBConnection.getConn();
			PreparedStatement pstmt = conn.prepareStatement(sqlstmt);
			pstmt.setString (1, localityName);
			pstmt.setString (2, districtName);
			pstmt.setString (3, provinceName);
			pstmt.setString (4, "Sweden");
			pstmt.setString (5, "Europe");
			pstmt.setDouble(6, wgs84.getNorth());
			pstmt.setDouble(7, wgs84.getEast());
			pstmt.setString (8, Integer.toString(rt90p.getY()));
			pstmt.setString (9, Integer.toString(rt90p.getX()));
			pstmt.setString (10, Settings.getValue("user"));
			pstmt.setString (11, alternativef.getText() );
			pstmt.setString (12, coordsourcef.getText());
			pstmt.setString (13, commentsf.getText());
			pstmt.executeUpdate();
			SpecimenList.updateLocalityList();
			SpecimenList.updateSpecimenList();
		} catch (SQLException | IOException ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this, "Database Error: " + ex.getMessage());
		}
	}
}