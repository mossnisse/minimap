package main.dialogs;

import main.coords.*;
import main.core.Canvas;
import main.layers.RubinLayer;
import main.layers.TNGPolygonFileLayer;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serial;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class MarkCoordinateDialog extends JDialog implements PropertyChangeListener {
	@Serial
	private static final long serialVersionUID = 1L;
	private final JTextField north, east, coordinateSys, swerefF, rt90F, wgs84F, provinceF, districtF, rubinF;
	private final JOptionPane optionPane;
	private final TNGPolygonFileLayer provinces, districts;
	private final Canvas canvas;

	public MarkCoordinateDialog(Frame aFrame, main.core.Canvas canvas) {
		super(aFrame, false);
		setTitle("Mark Coordinate");
		this.canvas = canvas;

		// Safely fetch layers
		provinces = (canvas.layerManager.getLayer("provinser") instanceof TNGPolygonFileLayer l) ? l : null;
		districts = (canvas.layerManager.getLayer("socknar") instanceof TNGPolygonFileLayer l) ? l : null;

		// Input fields
		north = new JTextField(15);
		east = new JTextField(15);

		// Result fields
		coordinateSys = createResultField();
		swerefF = createResultField();
		rt90F = createResultField();
		wgs84F = createResultField();
		rubinF = createResultField();
		provinceF = createResultField();
		districtF = createResultField();

		// Create the form panel
		JPanel formPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.fill = GridBagConstraints.HORIZONTAL;

		int row = 0;
		addFormRow(formPanel, "North / Index / RUBIN:", north, gbc, row++);
		addFormRow(formPanel, "East:", east, gbc, row++);

		// Separator
		gbc.gridx = 0; gbc.gridy = row++;
		gbc.gridwidth = 2;
		JSeparator sep = new JSeparator();
		sep.setBorder(new EmptyBorder(10, 0, 10, 0));
		formPanel.add(sep, gbc);

		gbc.gridwidth = 1; // Reset width
		addFormRow(formPanel, "Detected System:", coordinateSys, gbc, row++);
		addFormRow(formPanel, "Sweref99TM (N, E):", swerefF, gbc, row++);
		addFormRow(formPanel, "RT90 (N, E):", rt90F, gbc, row++);
		addFormRow(formPanel, "WGS84 (Lat, Lon):", wgs84F, gbc, row++);
		addFormRow(formPanel, "RUBIN Code:", rubinF, gbc, row++);
		addFormRow(formPanel, "Province:", provinceF, gbc, row++);
		addFormRow(formPanel, "District:", districtF, gbc, row++);

		Object[] options = {"Mark", "Close"};
		optionPane = new JOptionPane(formPanel,
				JOptionPane.PLAIN_MESSAGE,
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
	 * Helper to add a label and field to the same row
	 */
	private void addFormRow(JPanel panel, String labelText, JTextField field, GridBagConstraints gbc, int row) {
		gbc.gridy = row;

		// Label
		gbc.gridx = 0;
		gbc.weightx = 0;
		JLabel label = new JLabel(labelText, SwingConstants.RIGHT);
		panel.add(label, gbc);

		// Field
		gbc.gridx = 1;
		gbc.weightx = 1.0;
		panel.add(field, gbc);
	}

	private JTextField createResultField() {
		JTextField field = new JTextField(15);
		field.setEditable(false);
		field.setBackground(new Color(240, 240, 240));
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
				cs = CoordSystem.WGS84;
			} else {
				coordinateSys.setText("Unknown Range");
				return; // Stop here
			}
			wgs84 = cs.toWGS84(c);
		} catch (NumberFormatException ex) {
			// Assume RUBIN
			String rubin = northS;
			if (RUBIN.isValidRUBIN(rubin, false)) {
				coordinateSys.setText("RUBIN");

				Coordinate rt90r = RUBIN.toRT90(rubin);
				wgs84 = CoordSystem.RT90.toWGS84(rt90r);

				RubinLayer r = new RubinLayer(rubin, "Rubin", Color.green);
				canvas.layerManager.delLayer("Rubin");
				canvas.layerManager.addLayerTop(r);
			} else {
				coordinateSys.setText("Invalid Input");
				return;
			}
		}

		Coordinate sweref = CoordSystem.SWEREF99TM.toProjected(wgs84);
		Coordinate rt90 = CoordSystem.RT90.toProjected(wgs84);
		String rubin = RUBIN.fromRT90(rt90);

		// Update UI
		swerefF.setText(sweref.toPString());
		rt90F.setText(rt90.toPString());
		wgs84F.setText(wgs84.toString());
		rubinF.setText(rubin);

		canvas.focus(sweref);
		canvas.setCoordinate(sweref);
		if (provinces!= null) {
			TNGPolygonFileLayer.Province pr = provinces.inPolygon(sweref);
			if (pr != null) {
				provinceF.setText(pr.getName());
			} else {
				provinceF.setText("outside the layer");
			}
		}
		if (districts != null) {
			TNGPolygonFileLayer.Province so = districts.inPolygon(sweref);
			if (so != null) {
				districtF.setText(so.getName());
			} else {
				districtF.setText("outside the layer");
			}
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent e) {
		String prop = e.getPropertyName();

		// Check if the property change is actually a button click
		if (isVisible() && e.getSource() == optionPane &&
				(JOptionPane.VALUE_PROPERTY.equals(prop) || JOptionPane.INPUT_VALUE_PROPERTY.equals(prop))) {

			Object value = optionPane.getValue();

			if (value == JOptionPane.UNINITIALIZED_VALUE) {
				return; // Ignore the reset event
			}

			if ("Mark".equals(value)) {
				mark();
				// Reset so the button can be clicked again without closing
				optionPane.setValue(JOptionPane.UNINITIALIZED_VALUE);
			} else if ("Close".equals(value)) {
				setVisible(false);
				dispose();
			}
		}
	}
}
