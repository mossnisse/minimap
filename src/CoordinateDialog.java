import java.awt.*;
import java.io.Serial;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class CoordinateDialog extends JDialog {
	@Serial
	private static final long serialVersionUID = 1L;

	public CoordinateDialog(Frame owner, String sweref, String rt90, String wgs84, String rubin, String province, String district) {
		super(owner, "Coordinate Details", false);

		// Main container with some padding
		JPanel panel = new JPanel(new SpringLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		SpringLayout layout = (SpringLayout) panel.getLayout();

		// Initialize and add fields
		JTextField swerefF = createReadOnlyField(sweref);
		JTextField rt90F = createReadOnlyField(rt90);
		JTextField wgs84F = createReadOnlyField(wgs84);
		JTextField rubinF = createReadOnlyField(rubin);
		JTextField provF = createReadOnlyField(province);
		JTextField distF = createReadOnlyField(district);

		// Build the UI rows
		JLabel last = null;
		last = addRow("Sweref99TM (N, E):", swerefF, panel, layout, last);
		last = addRow("RT90 (N, E):", rt90F, panel, layout, last);
		last = addRow("WGS84 (lat, lon):", wgs84F, panel, layout, last);
		last = addRow("RUBIN:", rubinF, panel, layout, last);
		last = addRow("Province:", provF, panel, layout, last);
		last = addRow("District:", distF, panel, layout, last);

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