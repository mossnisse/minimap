import geometry.BoundingBox;
import geometry.Point;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;


public class SearchDialog extends JDialog implements ActionListener, ItemListener {

	private static final long serialVersionUID = 5830869660497471486L;
	JButton searchb, enterb, cancelb;
	private JTextField lokal;  // socken, 
	private JComboBox<String> provins;

	private Container contentPane;
	private String[] prov = {"*", "Torne lappmark", "Norrbotten", "Lule lappmark", "Pite lappmark", "Lycksele lappmark", "Åsele lappmark", 
			"Ångermanland", "Västerbotten", "Härjedalen", "Medelpad", "Jämtland", "Hälsingland", "Dalarna", "Gästrikland", 
			"Uppland", "Värmland", "Västmanland", "Närke", "Södermanland", "Dalsland", "Gotland", "Östergötland", "Bohuslän", 
			"Halland", "Öland", "Blekinge", "Skåne", "Småland", "Västergötland"};
	private int[] provnr = {-1, 27, 25,26,28,24,29,22,23,19,20,21,18,17,16,13,12,14,10,9,11,15,6,8,5,3,2,1,4,7};
	//JPanel aPanel = new JPanel();
	SpringLayout layout;
	boolean search;
	GUI gui;

	public SearchDialog(Frame aFrame, GUI gui, String text) {  //, String provinsStr
		super(aFrame, true);
		this.gui = gui;
		setTitle("Search");
		search = false;

		initGUI(text);
		//setDefaultCloseOperation(EXIT_ON_CLOSE);
		setVisible(true);
		//lokal.requestFocus();
		//lokal.setCaretPosition(0) ;    
		// lokal.requestFocusInWindow();
		//
	}

	public void initGUI(String text) {   //String provinsStr
		//JPanel panel1 = new JPanel();

		//JPanel sfield = new JPanel();
		contentPane = getContentPane();
		layout = new SpringLayout();
		contentPane.setLayout(layout);

		JLabel label2 = new JLabel("Lokal");
		contentPane.add(label2);
		lokal = new JTextField(text);
		lokal.setPreferredSize(new Dimension(150,20));

		contentPane.add(lokal);

		JLabel label = new JLabel("Provins");
		contentPane.add(label);
		layout.putConstraint(SpringLayout.NORTH, label, 5, SpringLayout.NORTH, contentPane);
		layout.putConstraint(SpringLayout.WEST, label, 5, SpringLayout.WEST, contentPane);

		System.out.println();
		
		provins = new JComboBox<String>(prov);
		try {
			provins.setSelectedItem(Settings.getValue("landskap"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		contentPane.add(provins);
		layout.putConstraint(SpringLayout.WEST, provins, 5, SpringLayout.EAST, label);
		layout.putConstraint(SpringLayout.NORTH, provins, 0, SpringLayout.NORTH, label);
		layout.putConstraint(SpringLayout.NORTH, label2, 15, SpringLayout.SOUTH, label);
		layout.putConstraint(SpringLayout.WEST, label2, 5, SpringLayout.WEST, contentPane);
		layout.putConstraint(SpringLayout.WEST, lokal, 5, SpringLayout.EAST, label2);
		layout.putConstraint(SpringLayout.NORTH, lokal, 0, SpringLayout.NORTH, label2);
		searchb = new JButton("Search");
		//searchb.setActionCommand(label);
		searchb.addActionListener(this);
		contentPane.add(searchb);
		layout.putConstraint(SpringLayout.NORTH, searchb, 5, SpringLayout.SOUTH, label2);
		layout.putConstraint(SpringLayout.WEST, searchb, 0, SpringLayout.WEST, label2);

		enterb = new JButton("Enter");
		enterb.addActionListener(this);
		contentPane.add(enterb);
		layout.putConstraint(SpringLayout.WEST, enterb, 5, SpringLayout.EAST, searchb);
		layout.putConstraint(SpringLayout.NORTH, enterb, 0, SpringLayout.NORTH, searchb);
		//bpanel.add(enterb);

		cancelb = new JButton("Cancel");
		cancelb.addActionListener(this);
		//bpanel.add(cancelb);
		contentPane.add(cancelb);
		layout.putConstraint(SpringLayout.WEST, cancelb, 5, SpringLayout.EAST, enterb);
		layout.putConstraint(SpringLayout.NORTH, cancelb, 0, SpringLayout.NORTH, enterb);

		// aPanel = new JPanel();
		//aLayout = new SpringLayout();
		//aPanel.setLayout(aLayout);
		//contentPane.add(aPanel);
		//layout.putConstraint(SpringLayout.WEST, aPanel, 0, SpringLayout.WEST, searchb);
		//layout.putConstraint(SpringLayout.NORTH, aPanel, 5, SpringLayout.SOUTH, searchb);

		layout.putConstraint(SpringLayout.SOUTH, contentPane, 5, SpringLayout.SOUTH, searchb);
		layout.putConstraint(SpringLayout.EAST, contentPane, 5, SpringLayout.EAST, cancelb);

		//aPanel = new JPanel();
		// panel1.add(aPanel, BorderLayout.PAGE_END);
		// getContentPane().add(panel1);
		getRootPane().setDefaultButton(searchb);
		//lokal.requestFocusInWindow();
		// panel1.setPreferredSize(new Dimension(270,100));
		provins.addItemListener(this);
		pack();
	}



	public String getLokal(){
		return lokal.getText();
	}

	public String getProvins() {
		return (String) provins.getSelectedItem();
	}

	public int getProvinsNr() {
		String provstr = (String) provins.getSelectedItem();
		for(int i=0; i<prov.length; i++) 
			if(prov[i].equals(provstr))
				return provnr[i];
		return -1;
	}

	public boolean isEntered() {
		return search;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		String actionCommand = ((JButton) e.getSource()).getActionCommand();
		System.out.println("Action command for pressed button: " + actionCommand);

		if (actionCommand.equals("Search")) {
			//aPanel.removeAll();
			Component[] comps = contentPane.getComponents();
			for(Component comp:  comps) {
				if(comp.getName()!=null) {
					if (comp.getName().equals("ans")) {
						contentPane.remove(comp);
					}

				}
			}
			//LayoutManager layout = aPanel.getLayout();
			//aPanel.revalidate();
			//SELECT RT90N, RT90E, locality FROM Locality where province = "Åsele Lappmark" and (locality = "Tjåkkola") or MATCH (alternative_names) against  ("Tjåkkola");
			
			Component lastC = cancelb;
			try {
				String slocal = getLokal();
				slocal = slocal
					    .replace("!", "!!")
					    .replace("%", "!%")
					    .replace("_", "!_")
					    .replace("[", "![");
				Connection conn = MYSQLConnection.getConn();
				//String query = "";
				PreparedStatement statement;
				String query;
				if (getProvins().equals("*")) {
					//String query = "SELECT lat, `long`, locality, district FROM Locality where locality = ? or MATCH (alternative_names) against (?)";
					query = "SELECT lat, `long`, locality, district FROM Locality where locality Like ? ESCAPE '!' or MATCH (alternative_names) against (?) ";
					statement = conn.prepareStatement(query);
					
					statement.setString(1, slocal);
					statement.setString(2, slocal);
				} else {
					//String query = "SELECT lat, `long`, locality, district FROM Locality where province = ? and (locality = ? or MATCH (alternative_names) against (?))";
					query = "SELECT lat, `long`, locality, district FROM Locality where province = ? and (locality like ? ESCAPE '!' or MATCH (alternative_names) against (?))";
					statement = conn.prepareStatement(query);

					statement.setString(1, getProvins());
					statement.setString(2, slocal);
					statement.setString(3, slocal);
				}
				System.out.println("search q:"+query+" str: "+slocal);
				/*PreparedStatement statement = conn.prepareStatement(query);
				statement.setString(1, getProvins());
				statement.setString(2, getLokal());
				statement.setString(3, getLokal());*/
				
				//Statement select = conn.createStatement();
				ResultSet result = statement.executeQuery();
				
				while (result.next()) { 
					System.out.println("träff i lokalDB: "+result.getString(3));
					Coordinates c = new Coordinates(Double.parseDouble(result.getString(1)),Double.parseDouble(result.getString(2)));
					//public Coordinates(double north, double east)
					System.out.println(c);
					Coordinates swtm = c.convertToSweref99TMFromWGS84();
					System.out.println(swtm);
					Point p = swtm.getPoint();
					//Point p = new Point(Integer.parseInt(result.getString(1)), Integer.parseInt(result.getString(2)));
					NButton r1 = new NButton(result.getString(3)+ " - " + result.getString(4), p);
					r1.setName("ans");
					r1.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e)
						{
							//Execute when button is pressed
							NButton source = (NButton) e.getSource();
							System.out.println("You clicked the button "+source.getPoint());
							// GUI.canvas.focus(new Point(, ));
							BoundingBox box = new BoundingBox(source.getPoint().getX()-5000,source.getPoint().getY()-5000,source.getPoint().getX()+5000,source.getPoint().getY()+5000);
							GUI.canvas.setBounds(box);
							System.out.println(GUI.canvas.getBoundingBox());
						}
					});      
					contentPane.add(r1);

					layout.putConstraint(SpringLayout.NORTH, r1, 5, SpringLayout.SOUTH, lastC);
					lastC = r1;
				}
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			
			H2Table od = (H2Table) GUI.canvas.getLayer("Ortnamnsdb");
			int prNr = getProvinsNr(); 
			TNGPointFile ans = od.find(prNr, lokal.getText());
			if (ans.size() != 0) {
				ans.setColor(Color.blue);
				BoundingBox b = ans.getBounds();

				if (b.getWidth() < 5000) {
					Point middle = b.getMidlePoint();
					b.setX1(middle.getX() - 2500);
					b.setY1(middle.getY() - 2500);
					b.setX2(middle.getX() + 2500);
					b.setY2(middle.getY() + 2500);
				}
				// System.out.println(b);
				GUI.canvas.setBounds(b);
				GUI.canvas.delLayer("ans");
				GUI.canvas.addLayerTop(ans);
				TNGPointFile.Locality[] localities = ans.getLocalities();
				//

				//int i =0;
				//Component lastC = cancelb;
				for(TNGPointFile.Locality locus:localities) {
					NButton r1 = new NButton(locus.getName(), locus.getPoint());
					r1.setName("ans");
					r1.addActionListener(new ActionListener() {

						public void actionPerformed(ActionEvent e)
						{
							//Execute when button is pressed

							NButton source = (NButton) e.getSource();
							System.out.println("You clicked the button "+source.getPoint());
							// GUI.canvas.focus(new Point(, ));
							BoundingBox box = new BoundingBox(source.getPoint().getY()-5000,source.getPoint().getX()-5000,source.getPoint().getY()+5000,source.getPoint().getX()+5000);
							GUI.canvas.setBounds(box);
							System.out.println(GUI.canvas.getBoundingBox());



						}
					});      
					contentPane.add(r1);

					layout.putConstraint(SpringLayout.NORTH, r1, 5, SpringLayout.SOUTH, lastC);
					lastC = r1;
					//i++;
				}
				layout.putConstraint(SpringLayout.SOUTH, contentPane, 5, SpringLayout.SOUTH, lastC);
				pack();
			} else {
				JLabel r1 = new JLabel("no hitts");
				contentPane.add(r1);
				layout.putConstraint(SpringLayout.NORTH, r1, 5, SpringLayout.SOUTH, cancelb);
				layout.putConstraint(SpringLayout.SOUTH, contentPane, 5, SpringLayout.SOUTH, r1);
				pack();
				//System.out.println("no hits");
			}
		}
		if(actionCommand.equals("Cancel")) {
			System.out.println("canc pressed");
			setVisible(false);
			dispose();
			//repaint();
			//setVisible(false);
		}
		if(actionCommand.equals("Enter")) {
			System.out.println("Enter pressed");
			search = true;
			setVisible(false);
			dispose();
			//repaint();
			//setVisible(false);
		}
	}



	@Override
	public void itemStateChanged(ItemEvent ev) {
	/*	if (ev.getStateChange() == ItemEvent.SELECTED) {
			System.out.println("save landskap settings");
			try {
				Settings.setValue("landskap", (String)provins.getSelectedItem());
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
		}*/
	}
}