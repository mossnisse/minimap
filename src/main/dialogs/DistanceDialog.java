package main.dialogs;

import main.coords.CoordSystem;
import main.coords.Coordinate;
import main.layers.DistanceLayer;
import main.core.Canvas;

import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serial;
import javax.swing.*;

public class DistanceDialog extends JDialog implements PropertyChangeListener {
	@Serial
	private static final long serialVersionUID = 2464657686998213912L;
	private final Canvas canvas;
	private final Coordinate origin;
	private final JTextField distance;
	private final JComboBox<String> direction;
	private final JOptionPane optionPane;

	public DistanceDialog(Frame aFrame, Canvas canvas, Coordinate c) {
		super(aFrame, true); // Modal
		setTitle("Distance and Direction");
		this.canvas = canvas;
		this.origin = c;

		direction = new JComboBox<>(Coordinate.directions);
		distance = new JTextField(10);

		// UI Components inside the Pane
		Object[] array = {"Direction:", direction, "Distance (m):", distance};
		Object[] options = {"Enter", "Cancel"};

		optionPane = new JOptionPane(array,
				JOptionPane.QUESTION_MESSAGE,
				JOptionPane.YES_NO_OPTION,
				null,
				options,
				options[1]);

		setContentPane(optionPane);

		// Handle button clicks
		optionPane.addPropertyChangeListener(this);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent e) {
				optionPane.selectInitialValue();
			}
		});

		pack();
		setLocationRelativeTo(aFrame);
	}

	public String getDirection() {
		return (String) direction.getSelectedItem();
	}

	public String getDistance() {
		return distance.getText();
	}

	@Override
	public void propertyChange(PropertyChangeEvent e) {
		String prop = e.getPropertyName();

		// Only react to the JOptionPane's value changing
		if (isVisible() && (e.getSource() == optionPane) && (prop.equals(JOptionPane.VALUE_PROPERTY))) {

			Object value = optionPane.getValue();
			if (value == JOptionPane.UNINITIALIZED_VALUE) return;

			// Reset the value so the same button can be clicked again if the dialog stays open
			optionPane.setValue(JOptionPane.UNINITIALIZED_VALUE);

			if (value.equals("Enter")) {
				if (processInput()) {
					dispose(); // Only close if input is valid
				}
			} else {
				// User clicked "Cancel" or closed the dialog
				dispose();
			}
		}
	}

	private boolean processInput() {
		try {
			int distVal = Integer.parseInt(getDistance());
			String dir = getDirection();

			DistanceLayer distLayer = new DistanceLayer( canvas,"dist", origin, distVal, dir, CoordSystem.SWEREF99TM);
			distLayer.setColor(Color.RED);

			canvas.layerManager.delLayer("dist");
			canvas.layerManager.addLayerTop(distLayer);
			canvas.repaint();
			return true;
		} catch (NumberFormatException ex) {
			JOptionPane.showMessageDialog(this, "Please enter a valid numeric distance (e.g., 500).");
			distance.selectAll();
			distance.requestFocusInWindow();
			return false; // Keep dialog open so user can fix the error
		}
	}
}