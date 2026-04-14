import geometry.BoundingBox;
import coords.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.io.Serial;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import javax.swing.*;

/**
 *    A dialog to search localities in the locality db and Lantmäteriets ortnamnsdb
 */

public class SearchLocalityDialog extends JDialog implements ActionListener, ItemListener {
	@Serial
	private static final long serialVersionUID = 5830869660497471486L;
	private final Canvas canvas;
	private JButton searchb, closeb, zoomb;
	private JTextField lokal;
	private JComboBox<String> provins;
	private Container contentPane;
	private final String[] prov = {"*", "Torne lappmark", "Norrbotten", "Lule lappmark", "Pite lappmark", "Lycksele lappmark", "Åsele lappmark",
			"Ångermanland", "Västerbotten", "Härjedalen", "Medelpad", "Jämtland", "Hälsingland", "Dalarna", "Gästrikland",
			"Uppland", "Värmland", "Västmanland", "Närke", "Södermanland", "Dalsland", "Gotland", "Östergötland", "Bohuslän",
			"Halland", "Öland", "Blekinge", "Skåne", "Småland", "Västergötland"};
	private final int[] provnr = {-1, 27, 25,26,28,24,29,22,23,19,20,21,18,17,16,13,12,14,10,9,11,15,6,8,5,3,2,1,4,7};

	private SpringLayout layout;
	private JPanel resultPanel;
	private JScrollPane scrollPane;
	private TNGPointFileLayer lastResults; // Store reference for zooming

	public class NButton extends JButton {
		@Serial
		private static final long serialVersionUID = -1082059330878801135L;
		private final Point c;

		public NButton(String name, Point c) {
			super(name);
			this.c = c;
		}

		public Point getPoint() {
			return c;
		}
	}

	public SearchLocalityDialog(Frame aFrame, String text, Canvas canvas) {
		super(aFrame, false);
		this.canvas = canvas;
		setTitle("Search localities");
		initGUI(text);
		setLocationRelativeTo(aFrame);
		setVisible(true);
	}

	public void initGUI(String text) {
		contentPane = getContentPane();
		layout = new SpringLayout();
		contentPane.setLayout(layout);

		// --- Locality Row ---
		JLabel label2 = new JLabel("Locality");
		contentPane.add(label2);
		lokal = new JTextField(text);
		lokal.setPreferredSize(new Dimension(180, 25));
		contentPane.add(lokal);

		// --- Province Row ---
		JLabel label = new JLabel("Province");
		contentPane.add(label);
		provins = new JComboBox<>(prov);
		provins.setSelectedItem(Settings.getValue("landskap"));
		contentPane.add(provins);

		// --- Buttons ---
		searchb = new JButton("Search");
		searchb.addActionListener(this);
		contentPane.add(searchb);

		zoomb = new JButton("Zoom to Results");
		zoomb.setEnabled(false); // Disabled until we have results
		zoomb.addActionListener(this);
		contentPane.add(zoomb);

		closeb = new JButton("Close");
		closeb.addActionListener(this);
		contentPane.add(closeb);

		// --- Results Area ---
		resultPanel = new JPanel();
		resultPanel.setLayout(new javax.swing.BoxLayout(resultPanel, javax.swing.BoxLayout.Y_AXIS));
		scrollPane = new JScrollPane(resultPanel);
		scrollPane.setPreferredSize(new Dimension(350, 250));
		contentPane.add(scrollPane);

		// ==========================================
		// --- SPRINGLAYOUT CONSTRAINTS ---
		// ==========================================

		// Province Label
		layout.putConstraint(SpringLayout.NORTH, label, 15, SpringLayout.NORTH, contentPane);
		layout.putConstraint(SpringLayout.WEST, label, 10, SpringLayout.WEST, contentPane);

		// Locality Label
		layout.putConstraint(SpringLayout.NORTH, label2, 20, SpringLayout.SOUTH, label);
		layout.putConstraint(SpringLayout.WEST, label2, 10, SpringLayout.WEST, contentPane);

		// Province ComboBox (Aligns to label, stretches to scrollpane's right edge)
		layout.putConstraint(SpringLayout.NORTH, provins, -3, SpringLayout.NORTH, label);
		layout.putConstraint(SpringLayout.WEST, provins, 15, SpringLayout.EAST, label);
		layout.putConstraint(SpringLayout.EAST, provins, 0, SpringLayout.EAST, scrollPane);

		// Locality TextField (Matches Combobox's left and right edges perfectly)
		layout.putConstraint(SpringLayout.NORTH, lokal, -3, SpringLayout.NORTH, label2);
		layout.putConstraint(SpringLayout.WEST, lokal, 0, SpringLayout.WEST, provins);
		layout.putConstraint(SpringLayout.EAST, lokal, 0, SpringLayout.EAST, provins);

		// Buttons (Anchored Left, allowed to take their natural widths)
		layout.putConstraint(SpringLayout.NORTH, searchb, 20, SpringLayout.SOUTH, lokal);
		layout.putConstraint(SpringLayout.WEST, searchb, 10, SpringLayout.WEST, contentPane);

		layout.putConstraint(SpringLayout.NORTH, zoomb, 0, SpringLayout.NORTH, searchb);
		layout.putConstraint(SpringLayout.WEST, zoomb, 10, SpringLayout.EAST, searchb);

		layout.putConstraint(SpringLayout.NORTH, closeb, 0, SpringLayout.NORTH, searchb);
		layout.putConstraint(SpringLayout.WEST, closeb, 10, SpringLayout.EAST, zoomb);

		// ScrollPane
		layout.putConstraint(SpringLayout.NORTH, scrollPane, 20, SpringLayout.SOUTH, searchb);
		layout.putConstraint(SpringLayout.WEST, scrollPane, 10, SpringLayout.WEST, contentPane);

		// Define overall window boundaries based on the ScrollPane
		layout.putConstraint(SpringLayout.EAST, contentPane, 10, SpringLayout.EAST, scrollPane);
		layout.putConstraint(SpringLayout.SOUTH, contentPane, 10, SpringLayout.SOUTH, scrollPane);

		getRootPane().setDefaultButton(searchb);
		provins.addItemListener(this);
		pack();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();

		if (source == searchb) {
			performSearch();
		} else if (source == zoomb) {
			if (lastResults != null && lastResults.size() > 0) {
				canvas.setBounds(lastResults.getBounds().expand(5000));
				canvas.repaint();
			}
		} else if (source == closeb) {
			setVisible(false);
			dispose();
		}
	}

	private void performSearch() {
		resultPanel.removeAll();
		ArrayList<Point> allPoints = new ArrayList<>();
		ArrayList<String> allNames = new ArrayList<>();

		String slocal = lokal.getText().trim();
		// Use a consistent wildcard pattern for both DBs
		String searchPattern = slocal.contains("*") ? slocal.replace("*", "%") : slocal;

		// --- MySQL ---
		String altonly  = searchPattern;
		String altfirst = searchPattern + ",%";
		String altlast  = "%, " + searchPattern;
		String altmid   = "%, " + searchPattern + ",%";
		String altClause = "(alternative_names LIKE ? OR alternative_names LIKE ? OR alternative_names LIKE ? OR alternative_names LIKE ?)";

		String selectedProv = provins.getSelectedItem().toString();
		String query = "*".equals(selectedProv)
				? "SELECT lat, `long`, locality, district FROM Locality WHERE (locality LIKE ?) OR " + altClause
				: "SELECT lat, `long`, locality, district FROM Locality WHERE province = ? AND ((locality LIKE ?) OR " + altClause + ")";

		try (Connection conn = DBConnection.getConn();
             PreparedStatement statement = conn.prepareStatement(query)) {

			int idx = 1;
			if (!(provins.getSelectedItem()).equals("*")) statement.setString(idx++, (String)provins.getSelectedItem());
			statement.setString(idx++, searchPattern);
			statement.setString(idx++, altonly);
			statement.setString(idx++, altfirst);
			statement.setString(idx++, altlast);
			statement.setString(idx++, altmid);

			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					Coordinates c = new Coordinates(result.getDouble("lat"), result.getDouble("long"));
					Coordinates swer = c.toProjected(CoordSystem.SWEREF99TM);
					Point p = new Point((int) swer.getEast(), (int) swer.getNorth());
					allPoints.add(p);
					allNames.add(result.getString("locality") + " (" + result.getString("district") + ")");
				}
			}
		} catch (SQLException ex) { ex.printStackTrace(); }

		// --- H2 ---
		H2TableLayer od = (H2TableLayer) canvas.getLayer("Ortnamnsdb");
		if (od != null) {
			TNGPointFileLayer h2Results = od.find(getProvinsNr(), searchPattern);
			for (TNGPointFileLayer.Locality locus : h2Results.getLocalities()) {
				allPoints.add(locus.getPoint());
				allNames.add(locus.getName() + " (Local)");
			}
		}

		// --- Result Handling ---
		if (!allPoints.isEmpty()) {
			lastResults = new TNGPointFileLayer(allPoints, allNames, "Search Results");
			lastResults.setColor(Color.BLUE);

			canvas.delLayer("Search Results");
			canvas.addLayerTop(lastResults);

			for (TNGPointFileLayer.Locality locus : lastResults.getLocalities()) {
				addResultButton(locus);
			}
			zoomb.setEnabled(true);
		} else {
			resultPanel.add(new JLabel(" No results found."));
			zoomb.setEnabled(false);
		}

		resultPanel.revalidate();
		resultPanel.repaint();
		scrollPane.getVerticalScrollBar().setValue(0);
		canvas.repaint();
	}

	private void addResultButton(TNGPointFileLayer.Locality locus) {
		NButton btn = new NButton(locus.getName(), locus.getPoint());
		btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		btn.addActionListener(e -> {
			Point p = locus.getPoint();
			canvas.setBounds(new BoundingBox(p.x-2500, p.y-2500, p.x+2500, p.y+2500));
		});
		resultPanel.add(btn);
	}

	@Override public void itemStateChanged(ItemEvent ev) {}
	public int getProvinsNr() {
		String provstr = (String) provins.getSelectedItem();
		for(int i=0; i<prov.length; i++) if(prov[i].equals(provstr)) return provnr[i];
		return -1;
	}
}