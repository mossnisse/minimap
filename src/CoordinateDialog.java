import geometry.Point;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener; //property change stuff

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

/* 1.4 example used by DialogDemo.java. */
class CoordinateDialog extends JDialog
                   implements ActionListener,
                              PropertyChangeListener {
	private static final long serialVersionUID = -4511067776450458493L;
	private JTextField north, east, wnorth, weast, socken, provins, rubin, rt90North, rt90East;
    public JOptionPane optionPane;
    private TNGPolygonFile provinces, district;
    private Point p;
    private Frame aFrame;
    public JButton cancel;
    //private boolean hidden;

    public Point getCoordinate() {
    	int norths = Integer.parseInt(north.getText());
    	int easts = Integer.parseInt(east.getText());
        return new Point(easts, norths);
    }

    /** Creates the reusable dialog. */
    public CoordinateDialog(Frame aFrame, Point p, TNGPolygonFile provinces, TNGPolygonFile district) { //DialogDemo parent
        super(aFrame, true);
        setTitle("Coordinate");
        this.aFrame = aFrame;
        this.provinces = provinces;
        this.district = district;
        this.p = p;
        //hidden = false;
        north = new JTextField(10);
        east = new JTextField(10);
        wnorth = new JTextField(10);
        weast = new JTextField(10);
        provins = new JTextField(10);
        socken = new JTextField(10);
        rubin = new JTextField(10);
        rt90North = new JTextField(10);
        rt90East = new JTextField(10);
        
        cancel = new JButton("Cancel");
        
        if (p!=null) {
        	update();
        }

        //Create an array of the text and components to be displayed.
        Object[] array = {"Sweref99TM North", north, "East", east, "RT90: North", rt90North, "East", rt90East, "WGS84 North", wnorth, "East", weast, "Provins", provins, "Socken", socken, "RUBIN", rubin};

        //Create an array specifying the number of dialog buttons
        //and their text.
        Object[] options = {"Enter", "Hide", cancel, "Update"};

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

        //Handle window closing correctly.
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
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

        //Ensure the text field always gets the first focus.
        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent ce) {
                north.requestFocusInWindow();
            }
        });

        //Register an event handler that puts the text into the option pane.
        north.addActionListener(this);

        //Register an event handler that reacts to option pane state changes.
        optionPane.addPropertyChangeListener(this);
    }
    
    private void createLocalityDialog() {
    	System.out.println("skapa lokal");
    	setVisible(false);
    	//CreateLocalityD d = new CreateLocalityD(aFrame);
    	
    	String RT90N = north.getText();
    	String RT90E = east.getText();
    	String province = provins.getText();
    	String district = socken.getText();
    			
    	CreateLocalityD d = new CreateLocalityD(aFrame, RT90N, RT90E, province, district);
		d.setVisible(true);
    	dispose();
    }

    
    private void update() {
    	north.setText(String.valueOf(p.getY()));
    	east.setText(String.valueOf(p.getX()));
    	Coordinates sweref99TM = new Coordinates(p.getY(),p.getX());
    	Coordinates wgs84 = sweref99TM.convertToWGS84FromSweref99TM();
    	Coordinates RT90 = wgs84.convertToRT90FromWGS84();
    	wnorth.setText(String.valueOf(wgs84.getNorth()));
    	weast.setText(String.valueOf(wgs84.getEast()));
    	rt90North.setText(String.valueOf(RT90.getNorth()));
    	rt90East.setText(String.valueOf(RT90.getEast()));
    	
    	TNGPolygonFile.Province pr = provinces.inPolygon(p);
    	if (pr != null) {
    		provins.setText(pr.getName());
    	} else {
    		provins.setText("utanför lager");
    	}
    	TNGPolygonFile.Province so = district.inPolygon(p);
    	if (so != null) {
    		socken.setText(so.getName());
    	} else {
    		socken.setText("utanför lager");
    	}
    	rubin.setText(RT90.getRUBINfromRT90());
    }
    
    private void updateFromRubin() {
    	if (rubin.getText().equals("")) {
    		System.out.println("empty");
    	} else {
    		System.out.println("full gubbe");
    		Coordinates c = new Coordinates(0,0);
    		c.setRUBINSweref99TM(rubin.getText());
    		p.setX((int) c.getEast());
    		p.setY((int) c.getNorth());
    		update();
    	}
    }
    
    /** This method handles events for the text field. */
    public void actionPerformed(ActionEvent e) {
        //optionPane.setValue(btnString1);
    	//System.out.println("action perf");
    }

    /** This method reacts to state changes in the option pane. */
    public void propertyChange(PropertyChangeEvent e) {
    	System.out.println("Property chang");
    	//JOptionPane source = (JOptionPane) e.getSource();
    	if(isVisible()){
    		System.out.println(e.getNewValue());
    		if (e.getNewValue()=="Update") {
    			System.out.println("Updated");
    			//p = getCoordinate();
    			updateFromRubin();
    		} else if (e.getNewValue()=="Hide") {
    			//hidden = true;
    		/*} else if (e.getNewValue()=="Create Locality") {
    			createLocalityDialog();*/
    			
    		} else {
    			setVisible(false);
            	dispose();
    		}
    	}
    }

    /** This method clears the dialog and hides it. */
    /*
    public void clearAndHide() {
        textField.setText(null);
        setVisible(false);
    }*/
}