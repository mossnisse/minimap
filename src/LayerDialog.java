import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.SpringLayout;


public class LayerDialog extends JDialog {
	private static final long serialVersionUID = -5204215066837865198L;
	private ArrayList<Layer> layers;
	
	public LayerDialog(Frame aFrame, ArrayList<Layer> layers) {
		super(aFrame, true);
		this.layers = layers;
		setTitle("Layers");
        initGUI();
        //setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }
	
	public void initGUI() {
		Container contentPane = getContentPane();
		SpringLayout layout = new SpringLayout();
		contentPane.setLayout(layout);
		//JPanel layerpanel = new JPanel();
		
		Component lastC = contentPane;
		int i =0;
		for(Layer l: layers) {
			System.out.println("layer: "+l.getName());
			JButton button = new JButton(l.getName());
			contentPane.add(button);
			if (i==0) {
				layout.putConstraint(SpringLayout.NORTH, button, 5, SpringLayout.NORTH, lastC);
			} else {
				layout.putConstraint(SpringLayout.NORTH, button, 5, SpringLayout.SOUTH, lastC);
			}
			lastC = button;
		//contentPane.add(layerpanel);
			i++;
		}
		layout.putConstraint(SpringLayout.SOUTH, contentPane, 5, SpringLayout.SOUTH, lastC);
		layout.putConstraint(SpringLayout.EAST, contentPane, 5, SpringLayout.EAST, lastC);
		pack();
    }

}
