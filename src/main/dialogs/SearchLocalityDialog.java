package main.dialogs;

import main.core.Canvas;
import main.core.DBConnection;
import main.coords.*;
import main.core.GUI;
import main.layers.H2TableLayer;
import main.layers.TNGPointFileLayer;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.Serial;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import javax.swing.*;

public class SearchLocalityDialog extends JDialog implements ActionListener, ItemListener {
	@Serial
	private static final long serialVersionUID = 5830869660497471486L;
	private final Canvas canvas;
	private final GUI gui;
	private final String[] prov = {"*", "Torne lappmark", "Norrbotten", "Lule lappmark", "Pite lappmark", "Lycksele lappmark", "Åsele lappmark",
			"Ångermanland", "Västerbotten", "Härjedalen", "Medelpad", "Jämtland", "Hälsingland", "Dalarna", "Gästrikland",
			"Uppland", "Värmland", "Västmanland", "Närke", "Södermanland", "Dalsland", "Gotland", "Östergötland", "Bohuslän",
			"Halland", "Öland", "Blekinge", "Skåne", "Småland", "Västergötland"};
	private final int[] provnr = {-1, 27, 25,26,28,24,29,22,23,19,20,21,18,17,16,13,12,14,10,9,11,15,6,8,5,3,2,1,4,7};

	private JButton searchb, closeb, zoomb;
	private JTextField lokal, country, district, source, precision, category;
	private JCheckBox isPlace;
	private JComboBox<String> provinceBox;
	private JPanel resultPanel;
	private TNGPointFileLayer lastResults;

	public SearchLocalityDialog(Frame aFrame, GUI gui, Canvas canvas, String text, String province) {
		super(aFrame, "Search Localities", false);
		this.canvas = canvas;
		this.gui = gui;
		initComponents(text, province);
		pack();
		setLocationRelativeTo(aFrame);
		setVisible(true);
	}

	private void initComponents(String text, String province) {
		Container content = getContentPane();
		SpringLayout layout = new SpringLayout();
		content.setLayout(layout);

		// Inputs Section
		lokal = new JTextField(text, 15);
		country = new JTextField("Sweden",10);
		district = new JTextField("*", 10);
		source = new JTextField("*",10);
		precision = new JTextField("*", 5);
		category = new JTextField("*", 10);
		isPlace = new JCheckBox("Is Place Only");
		provinceBox = new JComboBox<>(prov);
		provinceBox.setSelectedItem(province);

		// Helper to add rows quickly
		JLabel l1 = addField("Name:", lokal, content, layout, 10, content);
		JLabel l2 = addField("Province:", provinceBox, content, layout, 5, l1);
		JLabel l3 = addField("District:", district, content, layout, 5, l2);
		JLabel l4 = addField("Country:", country, content, layout, 5, l3);
		JLabel l5 = addField("Source:", source, content, layout, 5, l4);
		JLabel l6 = addField("Precision > :", precision, content, layout, 5, l5);
		JLabel l7 = addField("Category:", category, content, layout, 5, l6);

		content.add(isPlace);
		layout.putConstraint(SpringLayout.WEST, isPlace, 120, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, isPlace, 5, SpringLayout.SOUTH, category);

		// Buttons
		searchb = new JButton("Search");
		zoomb = new JButton("Zoom");
		closeb = new JButton("Close");

		JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		btnPanel.add(searchb);
		btnPanel.add(zoomb);
		btnPanel.add(closeb);
		content.add(btnPanel);
		layout.putConstraint(SpringLayout.WEST, btnPanel, 5, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, btnPanel, 10, SpringLayout.SOUTH, isPlace);

		// Results
		resultPanel = new JPanel();
		resultPanel.setLayout(new BoxLayout(resultPanel, BoxLayout.Y_AXIS));
		JScrollPane scrollPane = new JScrollPane(resultPanel);
		scrollPane.setPreferredSize(new Dimension(400, 300));
		content.add(scrollPane);

		layout.putConstraint(SpringLayout.NORTH, scrollPane, 10, SpringLayout.SOUTH, btnPanel);
		layout.putConstraint(SpringLayout.WEST, scrollPane, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.EAST, content, 10, SpringLayout.EAST, scrollPane);
		layout.putConstraint(SpringLayout.SOUTH, content, 10, SpringLayout.SOUTH, scrollPane);

		searchb.addActionListener(this);
		zoomb.addActionListener(this);
		closeb.addActionListener(this);
		zoomb.setEnabled(false);
		getRootPane().setDefaultButton(searchb);
	}

	private JLabel addField(String labelText, Component field, Container container, SpringLayout layout, int margin, Component topAnchor) {
		JLabel label = new JLabel(labelText);
		container.add(label);
		container.add(field);
		layout.putConstraint(SpringLayout.WEST, label, 10, SpringLayout.WEST, container);
		if (topAnchor == container) layout.putConstraint(SpringLayout.NORTH, label, margin, SpringLayout.NORTH, container);
		else layout.putConstraint(SpringLayout.NORTH, label, margin, SpringLayout.SOUTH, topAnchor);
		layout.putConstraint(SpringLayout.WEST, field, 120, SpringLayout.WEST, container);
		layout.putConstraint(SpringLayout.NORTH, field, 0, SpringLayout.NORTH, label);
		return label;
	}

	private void performSearch() {
		resultPanel.removeAll();
		ArrayList<Coordinate> allPoints = new ArrayList<>();
		ArrayList<String> allNames = new ArrayList<>();

		StringBuilder sql = new StringBuilder("SELECT ID, lat, `long`, locality, district FROM Locality WHERE 1=1 ");
		ArrayList<Object> params = new ArrayList<>();

		// Dynamic filters
		if (!lokal.getText().isEmpty()) {
			String p = lokal.getText().trim().replace("*", "%");
			// Matches exact, starts with, ends with, or is in the middle of a comma-separated list
			sql.append(" AND (locality LIKE ? OR alternative_names LIKE ? OR alternative_names LIKE ? OR alternative_names LIKE ? OR alternative_names LIKE ?)");
			params.add(p); // locality: exact if user don't write *
			params.add(p);             // alt: is first and last in list
			params.add(p + ",%");      // alt: first in list
			params.add("%, " + p);     // alt: last in list
			params.add("%, " + p + ",%");// alt: middle of list
		}
		// Helper for metadata fields (Handles NULL or Empty vs LIKE)
		addNullableLikeFilter(sql, params, "country", country.getText());
		addNullableLikeFilter(sql, params, "district", district.getText());
		addNullableLikeFilter(sql, params, "coordinate_source", source.getText());
		// Specialized Precision Logic
		String precInput = precision.getText().trim();
		if (!"*".equals(precInput)) {
			if (precInput.isEmpty()) {
				// Search for "empty/invalid" records
				sql.append(" AND (Coordinateprecision IS NULL OR Coordinateprecision = 0)");
			} else {
				try {
					// Remove any * if user accidentally typed one, treat as "greater than or equal"
					int val = Integer.parseInt(precInput.replace("*", ""));
					sql.append(" AND (Coordinateprecision >= ? OR Coordinateprecision IS NULL OR Coordinateprecision = 0)");
					params.add(val);
				} catch (NumberFormatException e) {
					// If user types gibberish, we can either ignore it or fall back to LIKE
					// Let's ignore it to prevent SQL errors
				}
			}
		}
		addNullableLikeFilter(sql, params, "category", category.getText());

		if (!"*".equals(provinceBox.getSelectedItem())) {
			sql.append(" AND province = ?");
			params.add(provinceBox.getSelectedItem());
		}

		if (isPlace.isSelected()) {
			sql.append(" AND isPlace = 1");
		}

		sql.append(" LIMIT 50");

		//System.out.println("locality search: "+sql.toString());
		try {
			Connection conn = DBConnection.getConn();
			// Main Search
			try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
				for (int i = 0; i < params.size(); i++) stmt.setObject(i + 1, params.get(i));
				try (ResultSet rs = stmt.executeQuery()) {
					while (rs.next()) {
						int id = rs.getInt("ID");
						String name = rs.getString("locality");
						String distr = rs.getString("district");
						String label = String.format("%s (%s)", name, distr);
						allNames.add(label);

						Coordinate wgs84 = new Coordinate(rs.getDouble("lat"), rs.getDouble("long"));
						Coordinate sweref = CoordSystem.SWEREF99TM.toProjected(wgs84);
						allPoints.add(sweref);

						addResultButton(sweref, label, id);
					}
				}
			}
		} catch (SQLException ex) { ex.printStackTrace(); }

		// H2 Search (Simplified to just name/province) only when name is used.
		// should also search on district
		String sCountry = country.getText().trim();
		if (!lokal.getText().isEmpty() && ("Sweden".equals(sCountry) || "*".equals(sCountry))) {
			H2TableLayer od = (H2TableLayer) canvas.getLayer("Ortnamnsdb");
			if (od != null && !lokal.getText().isEmpty()) {
				TNGPointFileLayer h2Res = od.find(getProvinsNr(), lokal.getText().replace("*", "%"), district.getText());
				for (TNGPointFileLayer.Locality locus : h2Res.getLocalities()) {
					Coordinate sweref = new Coordinate(locus.getPoint());
					allPoints.add(sweref);
					allNames.add(locus.getName() + " (Lantmäteriet)");
					addResultButton(sweref, locus.getName() + " (Lantmäteriet)", -1);
				}
			}
		}

		if (!allPoints.isEmpty()) {
			lastResults = new TNGPointFileLayer(allPoints, allNames, "Search Results");
			lastResults.setColor(Color.blue);
			canvas.delLayer("Search Results");
			canvas.addLayerTop(lastResults);
			//for (TNGPointFileLayer.Locality l : lastResults.getLocalities()) addResultButton(l,0,-1);
			resultPanel.add(Box.createVerticalGlue());
			zoomb.setEnabled(true);
		}
		resultPanel.revalidate();
		resultPanel.repaint();
	}

	private void addNullableLikeFilter(StringBuilder sql, ArrayList<Object> params, String columnName, String input) {
		String trimmed = input.trim();
		if (!"*".equals(trimmed)) {
			if (trimmed.isEmpty()) {
				// Optional: specific character to force search for empty records
				sql.append(" AND (" + columnName + " IS NULL OR " + columnName + " = '')");
			} else {
				String pattern = trimmed.replace("*", "%");
				sql.append(" AND " + columnName + " LIKE ?");
				params.add(pattern);
			}
		}
	}

	private void addResultButton(Coordinate coord, String label, int id) {
		JButton btn = new JButton(label);
		btn.setAlignmentX(Component.LEFT_ALIGNMENT);
		btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

		// Left click: Pan to map
		btn.addActionListener(e -> {
			canvas.focus(new TNGPointFileLayer.Locality(coord, label));
			canvas.repaint();
		});

		// Right click: Open Edit Dialog
		btn.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				if (SwingUtilities.isRightMouseButton(e) && id != -1) {
					doPop(e);
				}
			}

			@Override
			public void mouseReleased(java.awt.event.MouseEvent e) {
				if (SwingUtilities.isRightMouseButton(e) && id != -1) {
					doPop(e);
				}
			}

			private void doPop(java.awt.event.MouseEvent e) {
				JPopupMenu menu = new JPopupMenu();
				JMenuItem editItem = new JMenuItem("Edit Locality Details...");

				editItem.addActionListener(al -> {
					// Get the parent frame to own the new dialog
					Frame owner = (Frame) SwingUtilities.getWindowAncestor(SearchLocalityDialog.this);

					// Open EditLocalityDialog using the ID from the search results
					EditLocalityDialog editDlg = new EditLocalityDialog(
							gui,
							owner,
							id,
							null, // bridgeDialog
							canvas
					);
					editDlg.setVisible(true);
				});

				menu.add(editItem);
				menu.show(e.getComponent(), e.getX(), e.getY());
			}
		});

		resultPanel.add(btn);
	}

	@Override public void actionPerformed(ActionEvent e) {
		if (e.getSource() == searchb) performSearch();
		else if (e.getSource() == zoomb && lastResults != null) canvas.setBounds(lastResults.getBounds().expand(2000));
		else if (e.getSource() == closeb) dispose();
	}

	@Override public void itemStateChanged(ItemEvent e) {}

	public int getProvinsNr() {
		String provstr = (String) provinceBox.getSelectedItem();
		for(int i=0; i<prov.length; i++) if(prov[i].equals(provstr)) return provnr[i];
		return -1;
	}
}