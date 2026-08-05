package app.plugin.herbarium;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.Serial;
import javax.swing.*;

import gis.coords.*;
import gis.core.*;
import gis.core.MapCanvas;
import app.MapLayers;
import app.repo.PlaceNameRepository;

public class CreateLocalityDialog extends JDialog {
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
		JLabel lLoc = SpringForm.addRow("Locality:", localityT, content, layout, 10, content);
		JLabel lAlt = SpringForm.addRow("Alt Names:", alternativeT, content, layout, 10, lLoc);
		JLabel lSrc = SpringForm.addRow("Coord Source:", coordsourceT, content, layout, 10, lAlt);

		// Comments Manual Layout
		JLabel lComm = new JLabel("Comments:");
		content.add(lComm);
		content.add(commentScroll);
		layout.putConstraint(SpringLayout.WEST, lComm, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, lComm, 10, SpringLayout.SOUTH, lSrc);
		layout.putConstraint(SpringLayout.WEST, commentScroll, 120, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, commentScroll, 0, SpringLayout.NORTH, lComm);

		JLabel lSize = SpringForm.addRow("Size:", locSizeT, content, layout, 10, commentScroll);
		JLabel lCat = SpringForm.addRow("Category:", categoryT, content, layout, 10, lSize);
		JLabel lZoom = SpringForm.addRow("Zoom Level:", zoomLevelT, content, layout, 10, lCat);

		content.add(isPlaceT);
		layout.putConstraint(SpringLayout.WEST, isPlaceT, 120, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, isPlaceT, 10, SpringLayout.SOUTH, lZoom);

		JLabel lCont = SpringForm.addRow("Continent:", continentT, content, layout, 10, isPlaceT);
		JLabel lCoun = SpringForm.addRow("Country:", countryT, content, layout, 10, lCont);
		JLabel lProv = SpringForm.addRow("Province:", provinceT, content, layout, 10, lCoun);
		JLabel lDist = SpringForm.addRow("District:", districtT, content, layout, 10, lProv);

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
		cancel.addActionListener(e -> dispose());
		ok.addActionListener(e -> createLocality());
	}

	/**
	 * Validates the form and builds the record to insert, or returns null after
	 * telling the user what is wrong. Reads every field, so it must run on the EDT.
	 */
	// todo validate continent
	private LocalityRepository.LocalityDetails validatedDetails() {
		String localityName = localityT.getText().trim();
		if (localityName.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Locality name is required.");
			return null;
		}

		final int size;
		try {
			size = Integer.parseInt(locSizeT.getText().trim());
			if (size < 0) throw new NumberFormatException();
		} catch (NumberFormatException nfe) {
			JOptionPane.showMessageDialog(this, "Size must be a positive integer.");
			return null;
		}

		int zoom;
		try { zoom = Integer.parseInt(zoomLevelT.getText().trim()); }
		catch (NumberFormatException e) { zoom = -1; }

		return new LocalityRepository.LocalityDetails(localityName, alternativeT.getText().trim(),
				districtT.getText().trim(), provinceT.getText().trim(), countryT.getText().trim(),
				continentT.getText().trim(), coordsourceT.getText().trim(), commentsT.getText().trim(),
				size, categoryT.getText().trim(), zoom, isPlaceT.isSelected());
	}

	/** The dialog's map position in the three projections the locality table stores. */
	private LocalityRepository.StoredCoordinates coordinates() {
		Coordinate wgs84 = mapCanvas.getCRS().toWGS84(c);
		return new LocalityRepository.StoredCoordinates(wgs84,
				CoordSystem.SWEREF99TM.toProjected(wgs84), CoordSystem.RT90.toProjected(wgs84));
	}

	private void createLocality() {
		final LocalityRepository.LocalityDetails details = validatedDetails();
		if (details == null) return;

		gui.setCursorWait();

		// Start Background Worker
		saveWorker = new SwingWorker<Boolean, Void>() {
			@Override
			protected Boolean doInBackground() throws Exception {
				if (localities.exists(details.locality(), details.district(),
						details.province(), details.country())) {
					return false;
				}
				localities.insert(details, coordinates(), Settings.getValue("user"));
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
		LocalityRepository.LocalityDetails details = validatedDetails();
		if (details == null) return false;
		try {
			if (localities.exists(details.locality(), details.district(),
					details.province(), details.country())) {
				JOptionPane.showMessageDialog(this, "Locality already exists.");
				return false;
			}
			localities.insert(details, coordinates(), Settings.getValue("user"));
			refreshMapsAndLists();
			dispose();
			return true;
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
			return false;
		}
	}
}
