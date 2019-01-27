	import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;


	public class DistanceDialog extends JDialog  implements ActionListener,
	PropertyChangeListener {
		private static final long serialVersionUID = 2464657686998213912L;
		private JTextField distance;
		private JComboBox<String> direction;
		private JOptionPane optionPane;
		GUI gui;
	
		
		public DistanceDialog(Frame aFrame) {
			//System.out.println("Open search dialog");
			super(aFrame, true);
			setTitle("Distance and Direction");
			String[] dirStrings = { "N", "E", "S", "W", "NE" , "SE", "NW" , "SW", "NNE", "ENE", "ESE", "SSE", "SSW", "WSW", "WNW", "NNW"  };
		    direction = new JComboBox<String>(dirStrings);
		    distance = new JTextField(10);
		    
		  //Create an array of the text and components to be displayed.
	        Object[] array = {"Direction", direction, "Distance", distance};

	        //Create an array specifying the number of dialog buttons
	        //and their text.
	        Object[] options = {"Enter", "Cancel"};
	        
	        optionPane = new JOptionPane(array,
	                JOptionPane.QUESTION_MESSAGE,
	                JOptionPane.YES_NO_OPTION,
	                null,
	                options,
	                options[0]);
	        setContentPane(optionPane);
	        
	        optionPane.addPropertyChangeListener(this);
	        pack();
	        
	        addWindowListener(new WindowAdapter() {
	            public void windowClosing(WindowEvent we) {
	            /*
	             * Instead of directly closing the window,
	             * we're going to change the JOptionPane's
	             * value property.
	             */
	                /*optionPane.setValue(new Integer(
	                                    JOptionPane.CLOSED_OPTION));*/
	        }
	    	});
	        
	      //Ensure the text field always gets the first focus.
	        addComponentListener(new ComponentAdapter() {
	            public void componentShown(ComponentEvent ce) {
	                //provins.requestFocusInWindow();
	            }
	        });

	        //Register an event handler that puts the text into the option pane.
	        //provins.addActionListener(this);

	        //Register an event handler that reacts to option pane state changes.
	        optionPane.addPropertyChangeListener(this);
		}
		
		

		public String getDirection() {
			return (String) direction.getSelectedItem();
		}
		
		
		public String getDistance() {
			return distance.getText();
		}
		
		@Override
		public void propertyChange(PropertyChangeEvent e) {
	    				setVisible(false);
		}

		
		@Override
		public void actionPerformed(ActionEvent arg0) {
			// TODO Auto-generated method stub
			System.out.println("acti"+arg0);
			
		}
		
	}
