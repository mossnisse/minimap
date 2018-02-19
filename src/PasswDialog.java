import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.sql.SQLException;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

public class PasswDialog extends JDialog implements ActionListener, PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 385404689327110960L;
	/**
	 * 
	 */

	private JTextField passw;
	private JOptionPane optionPane;
	
	
	
	public PasswDialog() {
		setTitle("Password to the VH db server");
		this.passw = new JTextField();
		//try {
			//String user = Settings.getValue("user");
			Object[] array = {"Password", passw};
			
			Object[] options = {"Cancel", "OK"};

			optionPane = new JOptionPane(array,
					JOptionPane.QUESTION_MESSAGE,
					JOptionPane.YES_NO_OPTION,
					null,
					options,
					options[0]);

			//Make this dialog display it.
			setContentPane(optionPane);
			pack();
			setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
			optionPane.addPropertyChangeListener(this);
		/*} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("Kan inte läsa in user från settings.txt");
		}*/
		
	}

	
	String open() {
		setModal(true);
		this.setVisible(true);
		return passw.getText();
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent e) {
		// TODO Auto-generated method stub
		if(isVisible()){
    		System.out.println(e.getNewValue());
    		if (e.getNewValue()=="OK") {
    			//setUser();
    			setVisible(false);
            	dispose();
    		} else if (e.getNewValue()=="Cancel") {
    			System.out.println("Stänger passw dialog");
    			passw.setText("codeCancel");
    			setVisible(false);
            	dispose();
    		} 
    	}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		// TODO Auto-generated method stub
		
	}
}
