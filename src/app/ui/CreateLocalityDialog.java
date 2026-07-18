package app.ui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.Serial;
import javax.swing.*;

import gis.coords.*;
import gis.core.*;
import gis.core.MapCanvas;
import app.MapLayers;
import app.repo.LocalityRepository;
import app.repo.PlaceNameRepository;
import app.plugin.herbarium.HerbariumController;

public class CreateLocalityDialog extends JDialog implements ActionListener {
	@Serial
	private static final long serialVersionUID = 5999128550024317489L;
	private final HerbariumController gui;
	private final MapCanvas mapCanvas;
	private final SpecimenBridgeDialog bridgeDialog;
	private final LocalityRepository localities;
	private final PlaceNameRepository placeNames;
	Coordinate c;
	private JTextField localityT, districtT, provinceT, countryT, continentT, alternativeT, coordsourceT, locSizeT, categoryT, zoomLevelT;
	private JTextArea commentsT;
	private JCheckBox isPlaceT;
	private JButton cancel, ok;
	private SwingWorker<Boolean, Void> saveWorker;
	// Field contents right after opening; unsaved work = any deviation from it
	private String baseline;

	public CreateLocalityDialog(Frame owner, HerbariumController gui, MapCanvas mapCanvas, SpecimenBridgeDialog bridge, Coordinate c,
			LocalityRepository localities, PlaceNameRepository placeNames) {
		super(owner, "Create New Locality", false);
		this.gui = gui;
		this.mapCanvas = mapCanvas;
		this.bridgeDialog = bridge;
		this.localities = localities;
		this.placeNames = placeNames;
		this.c = c;

		// Use Content Pane for Layout
		Container content = this.getContentPane();
		content.setLayout(new SpringLayout());

		initComponents(content);
		baseline = fieldsSnapshot();

		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentShown(ComponentEvent ce) {
				// Request focus on the cancel button to swallow stray keystrokes
				cancel.requestFocusInWindow();
			}
		});

		this.pack();
		this.setLocationRelativeTo(owner);
		this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
	}

	private void initComponents(Container content) {
		SpringLayout layout = (SpringLayout) content.getLayout();

		String continent = "";
		String country = "";
		String province = "";
		String district = "";

		String provName = MapLayers.provinceAt(mapCanvas, c);
		if (provName != null) province = provName;
		String distName = MapLayers.districtAt(mapCanvas, c);
		if (distName != null) district = distName;

		// Logic for Suggesting Name
		String suggestName = placeNames.findNearestName(mapCanvas.getCRS().convertTo(c, CoordSystem.SWEREF99TM), 1000);

		if (!"".equals(province)) {
			continent = "Europe";
			country = "Sweden";
		}

		// Initialize Components
		localityT = new JTextField(suggestName, 20);
		alternativeT = new JTextField(20);
		coordsourceT = new JTextField(20);
		commentsT = new JTextArea(4, 35);
		commentsT.setLineWrap(true);
		commentsT.setWrapStyleWord(true);
		JScrollPane commentScroll = new JScrollPane(commentsT);
		locSizeT = new JTextField(10);
		categoryT = new JTextField(15);
		zoomLevelT = new JTextField(5);
		isPlaceT = new JCheckBox("Is Place");
		continentT = new JTextField(continent, 15);
		countryT = new JTextField(country, 15);
		provinceT = new JTextField(province, 15);
		districtT = new JTextField(district, 15);
		cancel = new JButton("Cancel");
		ok = new JButton("OK");

		// Layout Flow using the addField helper
		JLabel lLoc = addField("Locality:", localityT, content, layout, 10, content);
		JLabel lAlt = addField("Alt Names:", alternativeT, content, layout, 10, lLoc);
		JLabel lSrc = addField("Coord Source:", coordsourceT, content, layout, 10, lAlt);

		// Comments Manual Layout
		JLabel lComm = new JLabel("Comments:");
		content.add(lComm);
		content.add(commentScroll);
		layout.putConstraint(SpringLayout.WEST, lComm, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, lComm, 10, SpringLayout.SOUTH, lSrc);
		layout.putConstraint(SpringLayout.WEST, commentScroll, 120, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, commentScroll, 0, SpringLayout.NORTH, lComm);

		JLabel lSize = addField("Size:", locSizeT, content, layout, 10, commentScroll);
		JLabel lCat = addField("Category:", categoryT, content, layout, 10, lSize);
		JLabel lZoom = addField("Zoom Level:", zoomLevelT, content, layout, 10, lCat);

		content.add(isPlaceT);
		layout.putConstraint(SpringLayout.WEST, isPlaceT, 120, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, isPlaceT, 10, SpringLayout.SOUTH, lZoom);

		JLabel lCont = addField("Continent:", continentT, content, layout, 10, isPlaceT);
		JLabel lCoun = addField("Country:", countryT, content, layout, 10, lCont);
		JLabel lProv = addField("Province:", provinceT, content, layout, 10, lCoun);
		JLabel lDist = addField("District:", districtT, content, layout, 10, lProv);

		// Buttons
		content.add(cancel);
		content.add(ok);
		layout.putConstraint(SpringLayout.WEST, cancel, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, cancel, 20, SpringLayout.SOUTH, lDist);
		layout.putConstraint(SpringLayout.WEST, ok, 10, SpringLayout.EAST, cancel);
		layout.putConstraint(SpringLayout.NORTH, ok, 0, SpringLayout.NORTH, cancel);

		// Final Anchors
		layout.putConstraint(SpringLayout.EAST, content, 20, SpringLayout.EAST, commentScroll);
		layout.putConstraint(SpringLayout.SOUTH, content, 10, SpringLayout.SOUTH, cancel);

		// Listeners
		cancel.addActionListener(this);
		ok.addActionListener(this);
		cancel.setActionCommand("cancel");
		ok.setActionCommand("ok");
	}

	private JLabel addField(String labelText, JComponent field, Container container, SpringLayout layout, int margin, Component topAnchor) {
		return SpringForm.addRow(labelText, field, container, layout, margin, topAnchor);
	}

	private void createLocality() {
		// Immediate UI Validation (No DB needed)
		// todo validate continent
		String localityName = localityT.getText().trim();
		if (localityName.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Locality name is required.");
			return;
		}

		final String sizeText = locSizeT.getText().trim();
		final int size;
		try {
			size = Integer.parseInt(sizeText);
			if (size < 0) throw new NumberFormatException();
		} catch (NumberFormatException nfe) {
			JOptionPane.showMessageDialog(this, "Size must be a positive integer.");
			return;
		}

		// Capture all other fields so doInBackground doesn't touch the UI
		final String distr = districtT.getText().trim();
		final String prov = provinceT.getText().trim();
		final String coun = countryT.getText().trim();
		final String cont = continentT.getText().trim();
		final String alt = alternativeT.getText().trim();
		final String src = coordsourceT.getText().trim();
		final String comm = commentsT.getText().trim();
		final String cat = categoryT.getText().trim();
		final String zlStr = zoomLevelT.getText().trim();
		final boolean isPlace = isPlaceT.isSelected();

		gui.setCursorWait();

		// Start Background Worker
		saveWorker = new SwingWorker<Boolean, Void>() {
			@Override
			protected Boolean doInBackground() throws Exception {
				if (localities.exists(localityName, distr, prov, coun)) {
					return false;
				}

				// Perform transformations in background
				Coordinate wgs84c = mapCanvas.getCRS().toWGS84(c);
				Coordinate swerefc = CoordSystem.SWEREF99TM.toProjected(wgs84c);
				Coordinate rt90c = CoordSystem.RT90.toProjected(wgs84c);

				int zli;
				try { zli = Integer.parseInt(zlStr); } catch (NumberFormatException e) { zli = -1; }

				localities.insert(
						new LocalityRepository.LocalityDetails(localityName, alt, distr, prov, coun, cont,
								src, comm, size, cat, zli, isPlace),
						new LocalityRepository.StoredCoordinates(wgs84c, swerefc, rt90c),
						Settings.getValue("user"));
				return true;
			}

			@Override
			protected void done() {
				try {
					if (get()) {
						refreshMapsAndLists();
						dispose();
					} else {
						JOptionPane.showMessageDialog(CreateLocalityDialog.this, "Locality already exists.");
					}
				} catch (Exception e) {
					JOptionPane.showMessageDialog(CreateLocalityDialog.this, "Error: " + e.getMessage());
				} finally {
					gui.setCursorDefault();
				}
			}
		};
		saveWorker.execute();
	}

	private String fieldsSnapshot() {
		return String.join("\u0000", localityT.getText(), alternativeT.getText(),
				coordsourceT.getText(), commentsT.getText(), locSizeT.getText(),
				categoryT.getText(), zoomLevelT.getText(), continentT.getText(),
				countryT.getText(), provinceT.getText(), districtT.getText(),
				Boolean.toString(isPlaceT.isSelected()));
	}

	/** True when the user has edited any field since the dialog opened. */
	public boolean hasUnsavedWork() {
		return baseline != null && !baseline.equals(fieldsSnapshot());
	}

	private void refreshMapsAndLists() {
		// Update UI elements
		if (bridgeDialog != null && bridgeDialog.isVisible()) {
			bridgeDialog.invalidateLocalityList();
		}

		MapLayers.refreshLocalities(mapCanvas);
	}

	/** Synchronous save used only by the guarded project/plugin transition. */
	public boolean saveForProjectTransition() {
		String localityName = localityT.getText().trim();
		if (localityName.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Locality name is required.");
			return false;
		}
		int size;
		try {
			size = Integer.parseInt(locSizeT.getText().trim());
			if (size < 0) throw new NumberFormatException();
		} catch (NumberFormatException e) {
			JOptionPane.showMessageDialog(this, "Size must be a positive integer.");
			return false;
		}
		try {
			String distr = districtT.getText().trim(), prov = provinceT.getText().trim();
			String coun = countryT.getText().trim();
			if (localities.exists(localityName, distr, prov, coun)) {
				JOptionPane.showMessageDialog(this, "Locality already exists.");
				return false;
			}
			Coordinate wgs84 = mapCanvas.getCRS().toWGS84(c);
			int zoom;
			try { zoom = Integer.parseInt(zoomLevelT.getText().trim()); }
			catch (NumberFormatException e) { zoom = -1; }
			localities.insert(new LocalityRepository.LocalityDetails(localityName,
					alternativeT.getText().trim(), distr, prov, coun, continentT.getText().trim(),
					coordsourceT.getText().trim(), commentsT.getText().trim(), size,
					categoryT.getText().trim(), zoom, isPlaceT.isSelected()),
					new LocalityRepository.StoredCoordinates(wgs84,
							CoordSystem.SWEREF99TM.toProjected(wgs84), CoordSystem.RT90.toProjected(wgs84)),
					Settings.getValue("user"));
			refreshMapsAndLists();
			dispose();
			return true;
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
			return false;
		}
	}

	@Override
	public void actionPerformed(ActionEvent ev) {
		if ("ok".equals(ev.getActionCommand())) {
			createLocality();
		} else if ("cancel".equals(ev.getActionCommand())) {
			this.dispose();
		}
	}
}
