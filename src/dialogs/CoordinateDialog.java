package dialogs;
import java.awt.*;
import java.io.Serial;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import coords.*;
import core.Canvas;
import layers.TNGPolygonFileLayer;

public class CoordinateDialog extends JDialog {
	@Serial
	private static final long serialVersionUID = 1L;

	public CoordinateDialog(Frame owner, Canvas canvas, Point p) {
		super(owner, "Coordinate Details", false);

		Coordinates sweref = new Coordinates(p.y, p.x);
		Coordinates wgs84 = sweref.toWGS84(CoordSystem.SWEREF99TM);
		Coordinates rt90 = wgs84.toProjected(CoordSystem.RT90);
		String rubin = rt90.toRUBIN(false);
		UTMResult utm = wgs84.toUTM();
		String mgrs = wgs84.toMGRS();

		// Get Layers
		String prov = "outside layer";
		String dist = "outside layer";
		TNGPolygonFileLayer provinces = (TNGPolygonFileLayer) canvas.getLayer("provinser");
		TNGPolygonFileLayer districts = (TNGPolygonFileLayer) canvas.getLayer("socknar");

		if (provinces != null) {
			TNGPolygonFileLayer.Province pr = provinces.inPolygon(p);
			if (pr != null) prov = pr.getName();
		}

		if (districts != null) {
			TNGPolygonFileLayer.Province di = districts.inPolygon(p);
			if (di != null) dist = di.getName();
		}

		// Main container with some padding
		JPanel panel = new JPanel(new SpringLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		SpringLayout layout = (SpringLayout) panel.getLayout();

		// Initialize and add fields
		JTextField provF = createReadOnlyField(prov);
		JTextField distF = createReadOnlyField(dist);
		JTextField swerefF = createReadOnlyField(Math.round(sweref.getNorth()) + ", " + Math.round(sweref.getEast()));
		JTextField rt90F = createReadOnlyField(Math.round(rt90.getNorth()) + ", " + Math.round(rt90.getEast()));
		JTextField wgs84F = createReadOnlyField(String.format(java.util.Locale.US, "%.5f, %.5f", wgs84.getNorth(), wgs84.getEast()));
		JTextField rubinF = createReadOnlyField(rubin);
		JTextField utmF = createReadOnlyField(utm.toString());
		JTextField mgrsF = createReadOnlyField(mgrs);

		// Build the UI rows
		JLabel last = null;
		last = addRow("Province:", provF, panel, layout, last);
		last = addRow("District:", distF, panel, layout, last);
		last = addRow("Sweref99TM (N, E):", swerefF, panel, layout, last);
		last = addRow("RT90 (N, E):", rt90F, panel, layout, last);
		last = addRow("WGS84 (lat, lon):", wgs84F, panel, layout, last);
		last = addRow("RUBIN:", rubinF, panel, layout, last);
		last = addRow("UTM (GZD, E, N):", utmF, panel, layout, last);
		last = addRow("MGRS:", mgrsF, panel, layout, last);

		// OK Button to close
		JButton okButton = new JButton("Close");
		okButton.addActionListener(e -> dispose());
		panel.add(okButton);

		layout.putConstraint(SpringLayout.NORTH, okButton, 20, SpringLayout.SOUTH, last);
		layout.putConstraint(SpringLayout.HORIZONTAL_CENTER, okButton, 0, SpringLayout.HORIZONTAL_CENTER, panel);

		// Set panel boundaries for pack()
		layout.putConstraint(SpringLayout.EAST, panel, 10, SpringLayout.EAST, swerefF);
		layout.putConstraint(SpringLayout.SOUTH, panel, 10, SpringLayout.SOUTH, okButton);

		setContentPane(panel);
		this.getRootPane().setDefaultButton(okButton);
		pack();
		setLocationRelativeTo(owner);
	}

	private JTextField createReadOnlyField(String text) {
		JTextField field = new JTextField(text, 20);
		field.setEditable(false);
		field.setFocusable(true);
		field.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
		field.setBackground(new Color(245, 245, 245)); // Very light gray
		return field;
	}

	private JLabel addRow(String labelText, JTextField field, Container parent, SpringLayout layout, JLabel topAnchor) {
		JLabel label = new JLabel(labelText);
		parent.add(label);
		parent.add(field);

		layout.putConstraint(SpringLayout.WEST, label, 5, SpringLayout.WEST, parent);
		if (topAnchor == null) {
			layout.putConstraint(SpringLayout.NORTH, label, 5, SpringLayout.NORTH, parent);
		} else {
			layout.putConstraint(SpringLayout.NORTH, label, 10, SpringLayout.SOUTH, topAnchor);
		}

		layout.putConstraint(SpringLayout.WEST, field, 120, SpringLayout.WEST, parent);
		layout.putConstraint(SpringLayout.NORTH, field, 0, SpringLayout.NORTH, label);

		return label;
	}
}