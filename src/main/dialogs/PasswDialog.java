package main.dialogs;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serial;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

public class PasswDialog extends JDialog implements PropertyChangeListener {

	@Serial
	private static final long serialVersionUID = 385404689327110960L;
	private final JPasswordField passw; // Use JPasswordField for masking
	private final JOptionPane optionPane;
	private String typedText = "codeCancel";

	public PasswDialog() {
		setTitle("Password to the VH db server");
		this.passw = new JPasswordField(20);

		Object[] array = {"Enter Password:", passw};
		Object[] options = {"Cancel", "OK"};

		optionPane = new JOptionPane(array,
				JOptionPane.QUESTION_MESSAGE,
				JOptionPane.YES_NO_OPTION,
				null,
				options,
				options[1]); // Default to OK

		setContentPane(optionPane);
		setModal(true);
		pack();

		// Handle the window 'X' button
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent we) {
				// Set value to simulate a Cancel click
				optionPane.setValue("Cancel");
			}
		});

		optionPane.addPropertyChangeListener(this);
	}

	public String open() {
		// Request focus on password field when shown
		passw.requestFocusInWindow();
		this.setVisible(true);
		return typedText;
	}

	@Override
	public void propertyChange(PropertyChangeEvent e) {
		String prop = e.getPropertyName();

		if (isVisible() && (e.getSource() == optionPane)
				&& (JOptionPane.VALUE_PROPERTY.equals(prop) ||
				JOptionPane.INPUT_VALUE_PROPERTY.equals(prop))) {

			Object value = optionPane.getValue();

			if (value == JOptionPane.UNINITIALIZED_VALUE) return;

			// Reset the value so that if the user clicks again, it triggers another event
			optionPane.setValue(JOptionPane.UNINITIALIZED_VALUE);

			if ("OK".equals(value)) {
				typedText = new String(passw.getPassword());
				setVisible(false);
			} else {
				typedText = "codeCancel";
				setVisible(false);
			}
		}
	}
}