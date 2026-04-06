import coords.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serial;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

public class MarkCoordinateDialog extends JDialog implements PropertyChangeListener{
	@Serial
	private static final long serialVersionUID = 1L;
	private final JTextField north, east, coordinateSys, swerefF, rt90F, wgs84F, provinceF, districtF, rubinF;
	private final JOptionPane optionPane;
	private final TNGPolygonFileLayer provinces, district;
	private final Canvas canvas;
	
	public MarkCoordinateDialog(Frame aFrame, Canvas canvas, TNGPolygonFileLayer provinces, TNGPolygonFileLayer district) {
        super(aFrame, true);
        setTitle("Mark Coordinate");
        this.canvas = canvas;
        this.provinces = provinces;
        this.district = district;

		// Editable input fields
        north = new JTextField(10);
        east = new JTextField(10);

		// Result fields - Selectable but NOT editable
		coordinateSys = createResultField();
		swerefF = createResultField();
		rt90F = createResultField();
		wgs84F = createResultField();
		rubinF = createResultField();
		provinceF = createResultField();
		districtF = createResultField();

		Object[] array = {
				"North / Index / RUBIN:", north,
				"East:", east,
				"--- Results ---", null,
				"Detected System:", coordinateSys,
				"Sweref99TM (N, E):", swerefF,
				"RT90 (N, E):", rt90F,
				"WGS84 (Lat, Lon):", wgs84F,
				"RUBIN Code:", rubinF,
				"Province:", provinceF,
				"District:", districtF
		};

		Object[] options = {"Mark", "Close"};

		optionPane = new JOptionPane(array,
				JOptionPane.QUESTION_MESSAGE,
				JOptionPane.YES_NO_OPTION,
				null,
				options,
				options[0]);

		setContentPane(optionPane);
		pack();

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		addComponentListener(new ComponentAdapter() {
			public void componentShown(ComponentEvent ce) {
				north.requestFocusInWindow();
			}
		});
		optionPane.addPropertyChangeListener(this);
	}

	/**
	 * Helper to create a text field that looks like a result
	 */
	private JTextField createResultField() {
		JTextField field = new JTextField(10);
		field.setEditable(false);
		field.setFocusable(true); // Allows the user to click into it to copy text
		field.setBackground(Color.decode("#EEEEEE")); // Light gray to signify read-only
		return field;
	}

	public void mark() {
		String northS = north.getText().trim();
		String eastS = east.getText().trim();

		Coordinates sweref;
		Coordinates rt90;
		Coordinates wgs84;
		String rubin;

		try {
			double n = Double.parseDouble(northS);
			double e = Double.parseDouble(eastS);

			// SWEREF 99 TM detection
			if (n > 6000000 && n < 8000000 && e > 100000 && e < 1000000) {
				coordinateSys.setText("Sweref99TM");
				sweref = new Coordinates(n, e);
				wgs84 = sweref.toWGS84(CoordSystem.SWEREF99TM);
				rt90 = wgs84.toProjected(CoordSystem.RT90);
			}
			// RT90 detection
			else if (n > 6000000 && n < 8000000 && e > 1000000 && e < 2000000) {
				coordinateSys.setText("RT90");
				rt90 = new Coordinates(n, e);
				wgs84 = rt90.toWGS84(CoordSystem.RT90);
				sweref = wgs84.toProjected(CoordSystem.SWEREF99TM);
			}
			// WGS84 detection
			else if (Math.abs(n) <= 90 && Math.abs(e) <= 180) {
				coordinateSys.setText("WGS84");
				wgs84 = new Coordinates(n, e);
				sweref = wgs84.toProjected(CoordSystem.SWEREF99TM);
				rt90 = wgs84.toProjected(CoordSystem.RT90);
			} else {
				coordinateSys.setText("Unknown Range");
				return; // Stop here
			}
			rubin = rt90.toRUBIN(false);

		} catch (NumberFormatException ex) {
			// Assume RUBIN
			coordinateSys.setText("RUBIN");
			rubin = northS;
			rt90 = new Coordinates(0, 0);
			rt90.setFromRUBIN(rubin, false);
			wgs84 = rt90.toWGS84(CoordSystem.RT90);
			sweref = wgs84.toProjected(CoordSystem.SWEREF99TM);

			RubinLayer r = new RubinLayer(rubin, "Rubin", Color.green);
			canvas.delLayer("Rubin");
			canvas.addLayerTop(r);
		}

		// Update UI
		swerefF.setText(Math.round(sweref.getNorth()) + ", " + Math.round(sweref.getEast()));
		rt90F.setText(Math.round(rt90.getNorth()) + ", " + Math.round(rt90.getEast()));
		wgs84F.setText(String.format(java.util.Locale.US, "%.5f, %.5f", wgs84.getNorth(), wgs84.getEast()));
		rubinF.setText(rubin);

		Point p = new Point((int) sweref.getEast(), (int) sweref.getNorth());
		canvas.focus(p);
		canvas.setCoordinate(p);
		TNGPolygonFileLayer.Province pr = provinces.inPolygon(p);
    	if (pr != null) {
    		provinceF.setText(pr.getName());
    	} else {
    		provinceF.setText("utanför lager");
    	}
    	TNGPolygonFileLayer.Province so = district.inPolygon(p);
    	if (so != null) {
    		districtF.setText(so.getName());
    	} else {
    		districtF.setText("utanför lager");
    	}
	}

	@Override
	public void propertyChange(PropertyChangeEvent e) {
		System.out.println("Property change");
    	//JOptionPane source = (JOptionPane) e.getSource();
    	if(isVisible()){
    		System.out.println(e.getNewValue());
    		if (e.getNewValue().equals("Mark")) {
    			mark();
    		} else {
    			setVisible(false);
            	dispose();
    		}
    	}
	}
}
