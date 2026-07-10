package main.dialogs;

import main.core.MapCanvas;
import main.core.DBConnection;
import main.coords.*;
import main.core.GUI;
import main.layers.MapLayers;
import main.layers.TNGPointFileLayer;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serial;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import javax.swing.*;

public class SearchLocalityDialog extends JDialog implements ActionListener {
	@Serial
	private static final long serialVersionUID = 5830869660497471486L;
	private final MapCanvas mapCanvas;
	private final GUI gui;
	private final String[] prov = {"*", "Torne lappmark", "Norrbotten", "Lule lappmark", "Pite lappmark", "Lycksele lappmark", "Åsele lappmark",
			"Ångermanland", "Västerbotten", "Härjedalen", "Medelpad", "Jämtland", "Hälsingland", "Dalarna", "Gästrikland",
			"Uppland", "Värmland", "Västmanland", "Närke", "Södermanland", "Dalsland", "Gotland", "Östergötland", "Bohuslän",
			"Halland", "Öland", "Blekinge", "Skåne", "Småland", "Västergötland"};
	private final int[] provnr = {-1, 27, 25,26,28,24,29,22,23,19,20,21,18,17,16,13,12,14,10,9,11,15,6,8,5,3,2,1,4,7};

	private record SearchResult(Coordinate coord, String label, int id) {}

	private JButton searchb, closeb, zoomb;
	private JTextField lokal, country, district, source, precision, category;
	private JCheckBox isPlace;
	private JComboBox<String> provinceBox;
	private JPanel resultPanel;
	private TNGPointFileLayer lastResults;

	public SearchLocalityDialog(Frame aFrame, GUI gui, MapCanvas mapCanvas, String text, String province) {
		super(aFrame, "Search Localities", false);
		this.mapCanvas = mapCanvas;
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
		addField("Category:", category, content, layout, 5, l6);

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
		return SpringForm.addRow(labelText, field, container, layout, margin, topAnchor);
	}

	private void performSearch() {
		// Clear UI and show a loading state
		resultPanel.removeAll();
		resultPanel.add(new JLabel("Searching... Please wait."));
		resultPanel.revalidate();
		resultPanel.repaint();
		searchb.setEnabled(false); // Prevent multiple concurrent searches

		// Capture UI input on the EDT
		final String lokalText = lokal.getText().trim();
		final String countryText = country.getText().trim();
		final String districtText = district.getText().trim();
		final String sourceText = source.getText().trim();
		final String precInput = precision.getText().trim();
		final String catText = category.getText().trim();
		final Object provSelected = provinceBox.getSelectedItem();
		final boolean isPlaceSelected = isPlace.isSelected();
		final int provNr = getProvinsNr();
		final CoordSystem currentCRS = mapCanvas.getCRS();

		// Run Database logic in background
		new SwingWorker<ArrayList<SearchResult>, Void>() {
			@Override
			protected ArrayList<SearchResult> doInBackground() {
				gui.setCursorWait(); // Set hourglass cursor

				ArrayList<SearchResult> results = fetchFromMysql();

				if (!lokalText.isEmpty() && ("Sweden".equals(countryText) || "*".equals(countryText))) {
					ArrayList<SearchResult> h2Results = fetchFromH2(provNr, lokalText, districtText, currentCRS);
					results.addAll(h2Results);
				}

				return results;
			}

			public ArrayList<SearchResult> fetchFromH2(int provNr, String value, String district, CoordSystem targetCRS) {
				value = value.trim().replace("*", "%");
				district = district.trim().replace("*", "%");
				ArrayList<SearchResult> results = new ArrayList<>();

				try {
					Connection conn = DBConnection.getH2Conn();
					StringBuilder sql = new StringBuilder("SELECT NORTH, EAST, DETALJTYP, SOCKEN FROM ortnamnSWTM WHERE Ortnamn ILIKE ?");

					if (provNr != -1) {
						sql.append(" AND FPNUMMER = ?");
					}

					boolean useDistrict = !district.equals("%") && !district.isEmpty();
					if (useDistrict) {
						sql.append(" AND SOCKEN ILIKE ?");
					}

					sql.append(" ORDER BY SOCKEN LIMIT 500");

					try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
						int idx = 1;
						pstmt.setString(idx++, value);
						if (provNr != -1) pstmt.setInt(idx++, provNr);
						if (useDistrict) pstmt.setString(idx, district);

						try (ResultSet result = pstmt.executeQuery()) {
							while (result.next()) {
								int north = result.getInt(1);
								int east = result.getInt(2);

								// Convert from H2's SWEREF99TM to whatever the canvas currently uses
								Coordinate c = CoordSystem.SWEREF99TM.convertTo(new Coordinate(north, east), targetCRS);
								String label = result.getString(3) + ", " + result.getString(4) + " (Lantmäteriet)";

								// ID is -1 because these are from the H2 file, not the editable MySQL DB
								results.add(new SearchResult(c, label, -1));
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					// Return the empty list rather than null to avoid NullPointerExceptions later
				}
				return results;
			}

			private ArrayList<SearchResult> fetchFromMysql() {
				StringBuilder sql = new StringBuilder("SELECT ID, lat, `long`, locality, district FROM Locality WHERE 1=1 ");
				ArrayList<Object> params = new ArrayList<>();
				ArrayList<SearchResult> results = new ArrayList<>();

				if (!lokalText.isEmpty()) {
					String p = lokalText.replace("*", "%");
					sql.append(" AND (locality LIKE ? OR alternative_names LIKE ? OR alternative_names LIKE ? OR alternative_names LIKE ? OR alternative_names LIKE ?)");
					params.add(p); params.add(p); params.add(p + ",%"); params.add("%, " + p); params.add("%, " + p + ",%");
				}

				addNullableLikeFilter(sql, params, "country", countryText);
				addNullableLikeFilter(sql, params, "district", districtText);
				addNullableLikeFilter(sql, params, "coordinate_source", sourceText);

				if (!"*".equals(precInput)) {
					if (precInput.isEmpty()) {
						sql.append(" AND (Coordinateprecision IS NULL OR Coordinateprecision = 0)");
					} else {
						try {
							int val = Integer.parseInt(precInput.replace("*", ""));
							sql.append(" AND (Coordinateprecision >= ? OR Coordinateprecision IS NULL OR Coordinateprecision = 0)");
							params.add(val);
						} catch (NumberFormatException ignored) {}
					}
				}

				addNullableLikeFilter(sql, params, "category", catText);

				if (!"*".equals(provSelected)) {
					sql.append(" AND province = ?");
					params.add(provSelected);
				}

				if (isPlaceSelected) sql.append(" AND isPlace = 1");
				sql.append(" LIMIT 500");

				// --- MySQL Query ---
				try {
					Connection conn = DBConnection.getConn();
					try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
						for (int i = 0; i < params.size(); i++) stmt.setObject(i + 1, params.get(i));
						try (ResultSet rs = stmt.executeQuery()) {
							while (rs.next()) {
								int id = rs.getInt("ID");
								String label = String.format("%s (%s)", rs.getString("locality"), rs.getString("district"));
								Coordinate wgs84 = new Coordinate(rs.getDouble("lat"), rs.getDouble("long"));

								Coordinate c = mapCanvas.getCRS().toProjected(wgs84);
								results.add(new SearchResult(c, label, id));
							}
						}
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
				return results;
			}

			@Override
			protected void done() {
				if (!isDisplayable()) return;
				try {
					ArrayList<SearchResult> results = get();

					// Clear the "Searching..." label
					resultPanel.removeAll();

					if (results.isEmpty()) {
						resultPanel.add(new JLabel("No localities found."));
						zoomb.setEnabled(false);
					} else {
						ArrayList<Coordinate> allPoints = new ArrayList<>();
						ArrayList<String> allNames = new ArrayList<>();

						// Build UI Buttons and Layer Lists
						for (SearchResult res : results) {
							addResultButton(res.coord(), res.label(), res.id());
							allPoints.add(res.coord());
							allNames.add(res.label());
						}

						// Update Layer
						lastResults = new TNGPointFileLayer(allPoints, allNames, "Search Results");
						lastResults.setColor(Color.blue);
						mapCanvas.layerManager.setOverlay(MapLayers.SEARCH_RESULTS, lastResults);

						resultPanel.add(Box.createVerticalGlue());
						zoomb.setEnabled(true);
						mapCanvas.repaint();
					}
				} catch (Exception e) {
					e.printStackTrace();
					resultPanel.removeAll();
					resultPanel.add(new JLabel("An error occurred during search."));
				} finally {
					searchb.setEnabled(true); // Re-enable button
					gui.setCursorDefault();
					resultPanel.revalidate();
					resultPanel.repaint();
				}
			}
		}.execute();
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
			mapCanvas.focus(new TNGPointFileLayer.Locality(coord, label));
			mapCanvas.repaint();
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
					EditLocalityDialog editDlg = new EditLocalityDialog(gui, owner, id, null , mapCanvas); // null should be the bridge dialog
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
		else if (e.getSource() == zoomb && lastResults != null) mapCanvas.setBounds(lastResults.getBoundaries().expand(2000));
		else if (e.getSource() == closeb) dispose();
	}

	public int getProvinsNr() {
		String provstr = (String) provinceBox.getSelectedItem();
		for(int i=0; i<prov.length; i++) if(prov[i].equals(provstr)) return provnr[i];
		return -1;
	}
}