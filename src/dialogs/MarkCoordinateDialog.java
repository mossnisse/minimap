package dialogs;

import coords.*;
import core.Canvas;
import layers.RubinLayer;
import layers.TNGPolygonFileLayer;

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
	
	public MarkCoordinateDialog(Frame aFrame, core.Canvas canvas, TNGPolygonFileLayer provinces, TNGPolygonFileLayer district) {
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

		Coordinate wgs84;

		try {
			Coordinate c = new Coordinate(Double.parseDouble(northS), Double.parseDouble(eastS));
			CoordSystem cs;
			if (CoordSystem.SWEREF99TM.isValid(c)) {
				coordinateSys.setText("Sweref99TM");
				cs = CoordSystem.SWEREF99TM;
			}
			else if (CoordSystem.RT90.isValid(c)) {
				coordinateSys.setText("RT90");
				cs = CoordSystem.RT90;
			}
			else if (CoordSystem.WGS84.isValid(c)) {
				coordinateSys.setText("WGS84");
				cs = CoordSystem.SWEREF99TM;
			} else {
				coordinateSys.setText("Unknown Range");
				return; // Stop here
			}
			wgs84 = cs.toWGS84(c);


		} catch (NumberFormatException ex) {
			// Assume RUBIN
			coordinateSys.setText("RUBIN");
			String rubin = northS;
			Coordinate rt90r = RUBIN.toRT90(rubin);
			wgs84 = CoordSystem.RT90.toWGS84(rt90r);

			RubinLayer r = new RubinLayer(rubin, "Rubin", Color.green);
			canvas.delLayer("Rubin");
			canvas.addLayerTop(r);
		}

		Point sweref = CoordSystem.SWEREF99TM.toProjected(wgs84);
		Point rt90 = CoordSystem.RT90.toProjected(wgs84);
		String rubin = RUBIN.fromRT90(rt90);

		// Update UI
		swerefF.setText(sweref.y + ", " + sweref.x);
		rt90F.setText(rt90.y + ", " + rt90.x);
		wgs84F.setText(wgs84.toString());
		rubinF.setText(rubin);

		canvas.focus(sweref);
		canvas.setCoordinate(sweref);
		TNGPolygonFileLayer.Province pr = provinces.inPolygon(sweref);
    	if (pr != null) {
    		provinceF.setText(pr.getName());
    	} else {
    		provinceF.setText("utanför lager");
    	}
    	TNGPolygonFileLayer.Province so = district.inPolygon(sweref);
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
