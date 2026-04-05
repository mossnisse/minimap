import geometry.Point;
import coords.*;
import java.awt.Color;
import java.awt.Frame;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serial;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

public class MarkDialog extends JDialog implements PropertyChangeListener{
	@Serial
	private static final long serialVersionUID = 1L;
	private final JTextField north, east, coordinateSys, swerefF, rt90F, wgs84F, provinceF, districtF, rubinF;
	private final JOptionPane optionPane;
	private TNGPolygonFile provinces, district;
	private Frame aFrame;
	private Canvas canvas;
	
	public MarkDialog(Frame aFrame, Canvas canvas, Point p, TNGPolygonFile provinces, TNGPolygonFile district) {
        super(aFrame, true);
        setTitle("Mark Coordinate");
        this.aFrame = aFrame;
        this.canvas = canvas;
        this.provinces = provinces;
        this.district = district;
        north = new JTextField(10);
        east = new JTextField(10);
        coordinateSys = new JTextField(10);
        swerefF = new JTextField(10);
        rt90F = new JTextField(10);
        wgs84F = new JTextField(10);
        rubinF = new JTextField(10);
        provinceF = new JTextField(10);
        districtF = new JTextField(10);

        //Create an array of the text and components to be displayed.
        Object[] array = {"North/index", north, "East", east, "Coordinate system", coordinateSys, "Sweref99TM:", swerefF, "RT90", rt90F, "WGS84", wgs84F, "Province", provinceF, "District", districtF, "Socken", "RUBIN", rubinF};

        //Create an array specifying the number of dialog buttons
        //and their text.
        Object[] options = {"Mark", "Close"};

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
        optionPane.addPropertyChangeListener(this);
    }
	
	public void mark() {
		System.out.println("Mark Cooridnate");
		String northS = north.getText();
		String eastS = east.getText();
		Coordinates rt90 = new Coordinates(0,0);
		Coordinates wgs84 = new Coordinates(0,0);
		Coordinates sweref = new Coordinates(0,0);
		String rubin = new String("");
		
		try {
			double northI = Double.parseDouble(northS);
			double eastI = Double.parseDouble(eastS);
			
			if (northI<7689437 && northI>6070791 && eastI>171506 && eastI<959961) {
				System.out.println("Sweref99TM");
				coordinateSys.setText("Sweref99TM");
				sweref = new Coordinates(northI,eastI);
				wgs84 = sweref.toWGS84(CoordSystem.SWEREF99TM);
				rt90 = wgs84.toProjected(CoordSystem.RT90);
				rubin = rt90.toRUBIN(false);
				
			} else if (northI<7693900 && northI>6100000 && eastI> 1200000 && eastI<1900000) {
				System.out.println("RT90");
				coordinateSys.setText("RT90");
				rt90 = new Coordinates(northI,eastI);
				wgs84 = rt90.toWGS84(CoordSystem.RT90);
				sweref = wgs84.toProjected(CoordSystem.SWEREF99TM);
				rubin = rt90.toRUBIN(false);
			} else if (northI >= -90 && northI <= 90 && eastI >=-180 && eastI <= 180) {
				System.out.println("wgs84");
				coordinateSys.setText("WGS84");
				wgs84 = new Coordinates(northI,eastI);
				rt90 = wgs84.toProjected(CoordSystem.RT90);
				sweref = wgs84.toProjected(CoordSystem.RT90);
				rubin = rt90.toRUBIN(false);
			} else {
				System.out.println("Unkown");
				coordinateSys.setText("Unkonwn");
				swerefF.setText("");
				rt90F.setText("");
				wgs84F.setText("");
				rubinF.setText("");
				provinceF.setText("");
				districtF.setText("");	
			}
		} catch (NumberFormatException e) {
			System.out.println("RUBIN?"+northS);
			coordinateSys.setText("RUBIN");
			rubin = northS;
			rt90.setFromRUBIN(rubin, false);
			wgs84 = rt90.toWGS84(CoordSystem.RT90);
			sweref =wgs84.toProjected(CoordSystem.SWEREF99TM);
			Rubin r = new Rubin(rubin, "Rubin", Color.green);
			canvas.delLayer("Rubin");
			canvas.addLayerTop(r);
		}
		swerefF.setText(Double.toString(Math.round(sweref.getEast()))+", "+Double.toString(Math.round(sweref.getNorth())));
		rt90F.setText(Double.toString(Math.round(rt90.getEast()))+", "+Double.toString(Math.round(rt90.getNorth())));
		wgs84F.setText(Double.toString(wgs84.getEast())+", "+Double.toString(wgs84.getNorth()));
		rubinF.setText(rubin);
		Point p = new Point((int) sweref.getEast(), (int) sweref.getNorth());
		canvas.focus(p);
		canvas.setCoordinate(p);
		TNGPolygonFile.Province pr = provinces.inPolygon(p);
    	if (pr != null) {
    		provinceF.setText(pr.getName());
    	} else {
    		provinceF.setText("utanför lager");
    	}
    	TNGPolygonFile.Province so = district.inPolygon(p);
    	if (so != null) {
    		districtF.setText(so.getName());
    	} else {
    		districtF.setText("utanför lager");
    	}
	}

	@Override
	public void propertyChange(PropertyChangeEvent e) {
		// TODO Auto-generated method stub
		System.out.println("Property change");
    	//JOptionPane source = (JOptionPane) e.getSource();
    	if(isVisible()){
    		System.out.println(e.getNewValue());
    		if (e.getNewValue()=="Mark") {
    			mark();
    		} else {
    			setVisible(false);
            	dispose();
    		}
    	}
	}
}
