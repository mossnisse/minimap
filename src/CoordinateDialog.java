import geometry.Point;
import coords.*;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener; //property change stuff
import java.io.Serial;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

/* 1.4 example used by DialogDemo.java. */
class CoordinateDialog extends JDialog
                   implements ActionListener, PropertyChangeListener {
	@Serial
	private static final long serialVersionUID = -4511067776450458493L;
	private final JTextField rt90north, rt90east, wgs84north, wgs84east, sweref99TMnorth, sweref99TMeast, socken, provins, rubin;
    public JOptionPane optionPane;
    private final TNGPolygonFileLayer provinces, district;
    private final Point p;
    public JButton cancel;

    public Point getCoordinateSweref99TM() {
    	int norths = Integer.parseInt(sweref99TMnorth.getText());
    	int easts = Integer.parseInt(sweref99TMeast.getText());
        return new Point(easts, norths);
    }

    /** Creates the reusable dialog. */
    public CoordinateDialog(Frame aFrame, Point p, TNGPolygonFileLayer provinces, TNGPolygonFileLayer district) { //DialogDemo parent
        super(aFrame, true);
        setTitle("View Coordinate");
        this.provinces = provinces;
        this.district = district;
        this.p = p;

        sweref99TMnorth = new JTextField(10);
        sweref99TMeast = new JTextField(10);
        wgs84north = new JTextField(10);
        wgs84east = new JTextField(10);
        rt90north = new JTextField(10);
        rt90east = new JTextField(10);
        provins = new JTextField(10);
        socken = new JTextField(10);
        rubin = new JTextField(10);
        cancel = new JButton("Cancel");
        
        if (p!=null) {
        	update();
        }

        //Create an array of the text and components to be displayed.
        Object[] array = {"Sweref99TM North", sweref99TMnorth, "East", sweref99TMeast, "RT90: North", rt90north, "East", rt90east, "WGS84 North", wgs84north, "East", wgs84east, "Provins", provins, "Socken", socken, "RUBIN", rubin};

        //Create an array specifying the number of dialog buttons
        //and their text.
        Object[] options = {"Enter", "Hide", cancel, "Update"};

        //Create the JOptionPane.
        optionPane = new JOptionPane(array,
                                    JOptionPane.QUESTION_MESSAGE,
                                    JOptionPane.YES_NO_OPTION,
                                    null,
                                    options,
                                    cancel);

        //Make this dialog display it.
        setContentPane(optionPane);
        pack();

        //Handle window closing correctly.
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent we) {
            }
        });

		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentShown(ComponentEvent ce) {
				// Request focus on the cancel button to swallow stray keystrokes
				cancel.requestFocusInWindow();
			}
		});

        //Register an event handler that puts the text into the option pane.
        sweref99TMnorth.addActionListener(this);

        //Register an event handler that reacts to option pane state changes.
        optionPane.addPropertyChangeListener(this);
    }

    private void update() {
    	sweref99TMnorth.setText(String.valueOf(p.getY()));
    	sweref99TMeast.setText(String.valueOf(p.getX()));
    	Coordinates sweref99TM = new Coordinates(p.getY(),p.getX());
    	Coordinates wgs84 = sweref99TM.toWGS84(CoordSystem.SWEREF99TM);
    	Coordinates RT90 = wgs84.toProjected(CoordSystem.RT90);
    	wgs84north.setText(String.valueOf(wgs84.getNorth()));
    	wgs84east.setText(String.valueOf(wgs84.getEast()));
    	rt90north.setText(String.valueOf(Math.round(RT90.getNorth())));
    	rt90east.setText(String.valueOf(Math.round(RT90.getEast())));
    	
    	TNGPolygonFileLayer.Province pr = provinces.inPolygon(p);
    	if (pr != null) {
    		provins.setText(pr.getName());
    	} else {
    		provins.setText("utanför lager");
    	}
    	TNGPolygonFileLayer.Province so = district.inPolygon(p);
    	if (so != null) {
    		socken.setText(so.getName());
    	} else {
    		socken.setText("utanför lager");
    	}
    	rubin.setText(RT90.toRUBIN(false));
    }
    
    private void updateFromRubin() {
    	if (rubin.getText().equals("")) {
    		System.out.println("empty");
    	} else {
    		System.out.println("full gubbe");
    		Coordinates c = new Coordinates(0,0);
			c.setFromRUBIN(rubin.getText(), true);
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
		if (!"value".equals(e.getPropertyName()) || !isVisible()) return;

		Object value = e.getNewValue();
		if (value == null || value == JOptionPane.UNINITIALIZED_VALUE) return;

		optionPane.setValue(JOptionPane.UNINITIALIZED_VALUE);

		String valStr = value.toString();

		// Logic separation: Update vs Commit
		if ("Update".equals(valStr)) {
			updateFromRubin();
		} else if ("Enter".equals(valStr)) {
			if (validateInputs()) {
				dispose();
			}
		} else {
			// Cancel or Hide
			dispose();
		}
	}

	private boolean validateInputs() {
		try {
			Integer.parseInt(sweref99TMnorth.getText());
			Integer.parseInt(sweref99TMeast.getText());
			return true;
		} catch (NumberFormatException e) {
			JOptionPane.showMessageDialog(this, "Please enter valid numeric coordinates.", "Input Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}
}