import java.awt.Frame;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serial;
import javax.swing.*;

public class DistanceDialog extends JDialog implements PropertyChangeListener {
	@Serial
	private static final long serialVersionUID = 2464657686998213912L;
	private final JTextField distance;
	private final JComboBox<String> direction;
	private final JOptionPane optionPane;
	private String btnValue = null; // Track which button was pressed

	public DistanceDialog(Frame aFrame) {
		super(aFrame, true); // Modal
		setTitle("Distance and Direction");

		String[] dirStrings = { "N", "E", "S", "W", "NE", "SE", "NW", "SW", "NNE", "ENE", "ESE", "SSE", "SSW", "WSW", "WNW", "NNW" };
		direction = new JComboBox<>(dirStrings);
		distance = new JTextField(10);

		// UI Components inside the Pane
		Object[] array = { "Direction:", direction, "Distance (m):", distance };
		Object[] options = { "Enter", "Cancel" };

		optionPane = new JOptionPane(array,
				JOptionPane.QUESTION_MESSAGE,
				JOptionPane.YES_NO_OPTION,
				null,
				options,
				options[0]);

		setContentPane(optionPane);

		// Ensure the distance field gets focus when the dialog opens
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentShown(ComponentEvent ce) {
				distance.requestFocusInWindow();
			}
		});

		// Handle button clicks
		optionPane.addPropertyChangeListener(this);

		pack();
		setLocationRelativeTo(aFrame);
	}

	/**
	 * Call this after setVisible(true) to see if user clicked Enter
	 */
	public boolean wasCancelled() {
		return btnValue == null || btnValue.equals("Cancel");
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

		// Check if the user clicked a button or closed the dialog
		if (isVisible() && (e.getSource() == optionPane)
				&& (prop.equals(JOptionPane.VALUE_PROPERTY))) {

			Object value = optionPane.getValue();

			if (value == JOptionPane.UNINITIALIZED_VALUE) {
				return;
			}

			// Reset value so the next click triggers the listener
			optionPane.setValue(JOptionPane.UNINITIALIZED_VALUE);

			if (value.equals("Enter")) {
				btnValue = "Enter";
				// Add validation here if needed (e.g., check if distance is numeric)
				setVisible(false);
			} else {
				btnValue = "Cancel";
				setVisible(false);
			}
		}
	}
}