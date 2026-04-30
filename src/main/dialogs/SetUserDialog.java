package main.dialogs;

import main.core.Settings;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.Serial;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

public class SetUserDialog extends JDialog implements PropertyChangeListener {
	@Serial
	private static final long serialVersionUID = 7541165558448469861L;
	private final JTextField user;
	private final JOptionPane optionPane;

	public SetUserDialog() {
		super((java.awt.Frame)null, "Set User", true); // Make it modal!

		String current = "";
		try { current = Settings.getValue("user"); }
		catch (Exception e) {
			e.printStackTrace();
		}

		this.user = new JTextField(current, 20);
		Object[] array = {"Enter Registrator Name:", user};
		Object[] options = {"OK", "Cancel"};

		optionPane = new JOptionPane(array,
				JOptionPane.QUESTION_MESSAGE,
				JOptionPane.YES_NO_OPTION,
				null,
				options,
				options[0]);

		setContentPane(optionPane);
		pack();
		setLocationRelativeTo(null);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		optionPane.addPropertyChangeListener(this);
	}

	@Override
	public void propertyChange(PropertyChangeEvent e) {
		String prop = e.getPropertyName();

		if (isVisible() && (e.getSource() == optionPane) &&
				(JOptionPane.VALUE_PROPERTY.equals(prop))) {

			Object value = optionPane.getValue();

			if (value == JOptionPane.UNINITIALIZED_VALUE) return;
			// Reset so next click works
			optionPane.setValue(JOptionPane.UNINITIALIZED_VALUE);

			if ("OK".equals(value)) {
				saveUser();
				dispose();
			} else {
				dispose();
			}
		}
	}

	private void saveUser() {
		try {
			Settings.setValue("user", user.getText().trim());
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error saving settings.");
		}
	}
}