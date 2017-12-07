import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;


public class SettUserDialog extends JDialog implements ActionListener, PropertyChangeListener{

	private static final long serialVersionUID = 7541165558448469861L;
	private JTextField user;
	private JOptionPane optionPane;

	public SettUserDialog() {
		this.user = new JTextField();
		Object[] array = {"User Name", user};


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
	}
	@Override
	public void propertyChange(PropertyChangeEvent e) {
		// TODO Auto-generated method stub
		if(isVisible()){
    		System.out.println(e.getNewValue());
    		if (e.getNewValue()=="OK") {
    			setUser();
    			setVisible(false);
            	dispose();
    		} else if (e.getNewValue()=="Cancel") {
    			System.out.println("Stänger dialog");
    			setVisible(false);
            	dispose();
    		} 
    	}
	}
	
	public void setUser() {
		System.out.println("set user name");
		try {
			Settings.setValue("user",user.getText());
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	@Override
	public void actionPerformed(ActionEvent e) {
		// TODO Auto-generated method stub
		
	}


}
