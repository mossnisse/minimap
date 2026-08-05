package app.plugin.herbarium;

import gis.coords.*;
import gis.core.*;
import gis.core.MapCanvas;
import app.MapLayers;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.sql.SQLException;
import javax.swing.*;

/* Dialog for viewing and editing already existing Localities in the db */

public class EditLocalityDialog extends JDialog {
	private final MapCanvas mapCanvas;
	private final HerbariumController gui;
	private final int localityID;
	private final SpecimenBridgeDialog bridgeDialog;
	private final LocalityRepository localities;
	private String oldName;
	private Coordinate pendingCoords = null;

	// Components
	private JTextField name, altNames, district, province, country, continent, coordinateSource, localitySize, zoomLevel, category;
	private JTextArea comments;
	private JCheckBox isPlace;
	private JLabel labelCreated, labelModified;
	private JButton cancel, delete, ok, move;
	private SwingWorker<Boolean, Void> saveWorker;
	// Field contents right after loading; unsaved work = any deviation from it
	private String baseline;

	public EditLocalityDialog(HerbariumController gui, Frame owner, int localityID, SpecimenBridgeDialog bridge, MapCanvas mapCanvas,
			LocalityRepository localities) {
		// 'false' makes it non-modal, 'true' would stop interaction with map
		super(owner, "Edit Locality", false);

		this.mapCanvas = mapCanvas;
		this.gui = gui;
		this.localityID = localityID;
		this.bridgeDialog = bridge;
		this.localities = localities;

		// Set up Layout on the Dialog's content pane
		this.getContentPane().setLayout(new SpringLayout());

		initComponents();
		loadData();
		baseline = fieldsSnapshot();

		this.pack();
		this.setLocationRelativeTo(owner);
		this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentShown(ComponentEvent ce) {
				// Request focus on the cancel button to swallow stray keystrokes
				cancel.requestFocusInWindow();
			}
		});
	}

	private void initComponents() {
		Container content = this.getContentPane();
		SpringLayout layout = (SpringLayout) this.getContentPane().getLayout();

		// Initialize Components
		name = new JTextField(20);
		altNames = new JTextField(20);
		district = new JTextField(15);
		province = new JTextField(15);
		country = new JTextField(15);
		continent = new JTextField(15);
		localitySize = new JTextField(10);
		coordinateSource = new JTextField(20);
		category = new JTextField(15);
		zoomLevel = new JTextField(5);
		comments = new JTextArea(4, 30);
		comments.setLineWrap(true);
		comments.setWrapStyleWord(true);
		javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(comments);
		// Force vertical scrollbar only when needed
		scrollPane.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setPreferredSize(new java.awt.Dimension(350, 80));

		isPlace = new JCheckBox("Is Place");

		labelCreated = new JLabel("Created: ");
		labelModified = new JLabel("Modified: ");

		cancel = new JButton("Cancel");
		delete = new JButton("Delete");
		ok = new JButton("OK - Change");
		move = new JButton("Move on Map");

		// Add to Layout
		JLabel lName = SpringForm.addRow("Name:", name, content, layout, 10, content);
		JLabel lAlt = SpringForm.addRow("Alt Names:", altNames, content, layout, 10, lName);
		JLabel lDist = SpringForm.addRow("District:", district, content, layout, 10, lAlt);
		JLabel lProv = SpringForm.addRow("Province:", province, content, layout, 10, lDist);
		JLabel lCountry = SpringForm.addRow("Country:", country, content, layout, 10, lProv);
		JLabel lContinent = SpringForm.addRow("Continent:", continent, content, layout, 10, lCountry);
		JLabel lSize = SpringForm.addRow("Size:", localitySize, content, layout, 10, lContinent);
		JLabel lSrc = SpringForm.addRow("Source:", coordinateSource, content, layout, 10, lSize);

		JLabel lComm = new JLabel("Comments:");
		content.add(lComm);
		content.add(scrollPane);

		layout.putConstraint(SpringLayout.WEST, lComm, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, lComm, 10, SpringLayout.SOUTH, lSrc);

		layout.putConstraint(SpringLayout.WEST, scrollPane, 120, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, scrollPane, 0, SpringLayout.NORTH, lComm);

		// Comments is a JTextArea, so the next field needs a bigger gap (70-80px)
		JLabel lCat = SpringForm.addRow("Category:", category, content, layout, 10, scrollPane);
		JLabel lZoom = SpringForm.addRow("Zoom:", zoomLevel, content, layout, 10, lCat);

		// Metadata Labels
		add(labelCreated);
		layout.putConstraint(SpringLayout.WEST, labelCreated, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, labelCreated, 15, SpringLayout.SOUTH, lZoom);

		add(labelModified);
		layout.putConstraint(SpringLayout.WEST, labelModified, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, labelModified, 5, SpringLayout.SOUTH, labelCreated);

		// Checkbox
		add(isPlace);
		layout.putConstraint(SpringLayout.WEST, isPlace, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, isPlace, 10, SpringLayout.SOUTH, labelModified);

		// Buttons
		add(move);
		add(cancel);
		add(delete);
		add(ok);

		layout.putConstraint(SpringLayout.WEST, move, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, move, 0, SpringLayout.SOUTH, isPlace);

		// Bottom Row: Cancel, Delete, OK
		layout.putConstraint(SpringLayout.WEST, cancel, 10, SpringLayout.WEST, content);
		layout.putConstraint(SpringLayout.NORTH, cancel, 20, SpringLayout.SOUTH, move);

		layout.putConstraint(SpringLayout.WEST, delete, 10, SpringLayout.EAST, cancel);
		layout.putConstraint(SpringLayout.NORTH, delete, 0, SpringLayout.NORTH, cancel);

		layout.putConstraint(SpringLayout.WEST, ok, 10, SpringLayout.EAST, delete);
		layout.putConstraint(SpringLayout.NORTH, ok, 0, SpringLayout.NORTH, cancel);

		// Update content pane boundaries
		layout.putConstraint(SpringLayout.EAST, content, 20, SpringLayout.EAST, scrollPane);
		layout.putConstraint(SpringLayout.SOUTH, content, 10, SpringLayout.SOUTH, cancel);

		// Listeners
		ok.addActionListener(e -> updateLocality());
		cancel.addActionListener(e -> {
			gui.cancelMoveMode();
			dispose();
		});
		delete.addActionListener(e -> {
			deleteLocality();
			mapCanvas.repaint();
		});
		move.addActionListener(e -> {
			this.setTitle("SELECT NEW LOCATION ON MAP...");
			this.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
			gui.enterMoveMode(this);
		});
	}

	private void loadData() {
		try {
			LocalityRepository.StoredLocality loc = localities.load(localityID);
			if (loc != null) {
				oldName = loc.locality();
				name.setText(loc.locality());
				altNames.setText(loc.alternativeNames());
				district.setText(loc.district());
				province.setText(loc.province());
				country.setText(loc.country());
				continent.setText(loc.continent());
				coordinateSource.setText(loc.coordinateSource());
				comments.setText(loc.comments());

				labelCreated.setText("Created: " + loc.created() + " by " + loc.createdBy());
				labelModified.setText("Modified: " + loc.modified() + " by " + loc.modifiedBy());

				localitySize.setText(loc.precision());
				category.setText(loc.category());
				zoomLevel.setText(loc.zoomLevel());

				isPlace.setSelected(loc.isPlace());

				setTitle("Edit Locality: " + name.getText());
			}
		} catch (SQLException e) {
			JOptionPane.showMessageDialog(this, "Database Error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void deleteLocality() {
		int bridgeCount = localities.countBridgeUses(localityID);
		int usesCount = localityUses();

		StringBuilder warning = new StringBuilder();
		boolean errorState = (bridgeCount == -1 || usesCount == -1);

		if (errorState) {
			warning.append("CRITICAL: Could not verify all specimen links due to a database error.\n");
		}

		if (bridgeCount > 0) {
			warning.append("? Linked to ").append(bridgeCount).append(" specimen bridges.\n");
		}

		if (usesCount > 0) {
			warning.append("? Used by ").append(usesCount).append(" specimen records.\n");
		}

		if (warning.isEmpty()) {
			warning.append("Are you sure you want to delete this locality?");
		} else {
			warning.append("\nDeleting it will break these links. Proceed?");
		}

		// Use WARNING_MESSAGE if there are links or errors, otherwise QUESTION_MESSAGE
		int messageType = (bridgeCount > 0 || usesCount > 0 || errorState)
				? JOptionPane.WARNING_MESSAGE
				: JOptionPane.QUESTION_MESSAGE;

		int dialogResult = JOptionPane.showConfirmDialog(
				this,
				warning.toString(),
				"Confirm Delete",
				JOptionPane.YES_NO_OPTION,
				messageType
		);

		if (dialogResult == JOptionPane.YES_OPTION) {
			try {
				localities.delete(localityID);

				if (bridgeDialog != null && bridgeDialog.isVisible()) {
					bridgeDialog.invalidateLocalityList();
				}

				MapLayers.refreshLocalities(mapCanvas);
				this.dispose();
			} catch (SQLException e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(this, " Error deleting locality: " + e.getMessage());
			}
		}
	}

	private int localityUses() {
		return localities.countSpecimenUses(province.getText(), district.getText(), oldName);
	}

	/**
	 * Validates the form and builds the record to save, or returns null after
	 * telling the user what is wrong (or after they declined a risky rename).
	 * Reads every field and can prompt, so it must run on the EDT.
	 */
	// todo validate continent
	private LocalityRepository.LocalityDetails validatedDetails() {
		final String newName = name.getText().trim();
		if (newName.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Locality name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
			return null;
		}

		// Renaming leaves specimen records pointing at a name that no longer
		// exists, so confirm before it happens - on every save path.
		if (oldName != null && !oldName.equals(newName)) {
			int uses = localityUses(); // Since this is a fast count, it's usually okay on EDT
			if (uses > 0 || uses == -1) {
				String msg = (uses > 0) ? "\"" + oldName + "\" is used in " + uses + " records. Update anyway?"
						: "Could not verify usage. Proceed?";
				if (JOptionPane.showConfirmDialog(this, msg, "Warning", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
					return null;
				}
			}
		}

		final int size;
		try {
			size = Integer.parseInt(localitySize.getText().trim());
			if (size < 0) {
				throw new NumberFormatException();
			}
		} catch (NumberFormatException nfe) {
			JOptionPane.showMessageDialog(this, "Size must be a positive integer.", "Error", JOptionPane.ERROR_MESSAGE);
			return null;
		}

		int zl;
		try { zl = Integer.parseInt(zoomLevel.getText().trim()); } catch (Exception e) { zl = -1; }
		return new LocalityRepository.LocalityDetails(
				newName, altNames.getText().trim(), district.getText().trim(), province.getText().trim(),
				country.getText().trim(), continent.getText().trim(), coordinateSource.getText().trim(),
				comments.getText().trim(), size, category.getText().trim(), zl, isPlace.isSelected());
	}

	/** The staged map position in the three stored projections, or null if unmoved. */
	private LocalityRepository.StoredCoordinates movedCoordinates() {
		if (pendingCoords == null) return null;
		// todo only set sweref and rt90 if in Sweden
		// todo check if moved outside district and province
		Coordinate wgs84 = mapCanvas.getCRS().toWGS84(pendingCoords);
		return new LocalityRepository.StoredCoordinates(wgs84,
				CoordSystem.SWEREF99TM.toProjected(wgs84),
				CoordSystem.RT90.toProjected(wgs84));
	}

	/** Refreshes whatever showed the old name/position, then closes the dialog. */
	private void afterSaved(String newName) {
		if (oldName != null && !oldName.equals(newName) && bridgeDialog != null && bridgeDialog.isVisible()) {
			bridgeDialog.invalidateLocalityList();
		}
		MapLayers.refreshLocalities(mapCanvas);
		dispose();
	}

	private void updateLocality() {
		// Capture all fields on the EDT so doInBackground doesn't touch the UI
		final LocalityRepository.LocalityDetails details = validatedDetails();
		if (details == null) return;
		final LocalityRepository.StoredCoordinates moved = movedCoordinates();

		gui.setCursorWait();

		// Start Background Worker
		saveWorker = new SwingWorker<Boolean, Void>() {
			@Override
			protected Boolean doInBackground() throws Exception {
				localities.update(localityID, details, moved, Settings.getValue("user"));
				return true;
			}

			@Override
			protected void done() {
				gui.setCursorDefault();
				try {
					if (get()) afterSaved(details.locality()); // If update wasn't aborted
				} catch (Exception e) {
					e.printStackTrace();
					JOptionPane.showMessageDialog(EditLocalityDialog.this, "Error updating locality: " + e.getMessage());
				}
			}
		};
		saveWorker.execute();
	}

	private String fieldsSnapshot() {
		return String.join("\u0000", name.getText(), altNames.getText(), district.getText(),
				province.getText(), country.getText(), continent.getText(),
				coordinateSource.getText(), comments.getText(), localitySize.getText(),
				zoomLevel.getText(), category.getText(),
				Boolean.toString(isPlace.isSelected()));
	}

	/** True when the user has edited a field or staged a move since the dialog opened. */
	public boolean hasUnsavedWork() {
		return pendingCoords != null
				|| (baseline != null && !baseline.equals(fieldsSnapshot()));
	}

	/** Synchronous save used only by the guarded project/plugin transition. */
	public boolean saveForProjectTransition() {
		LocalityRepository.LocalityDetails details = validatedDetails();
		if (details == null) return false;
		try {
			localities.update(localityID, details, movedCoordinates(), Settings.getValue("user"));
			afterSaved(details.locality());
			return true;
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Error updating locality: " + e.getMessage());
			return false;
		}
	}

	public void updateCoordinates(Coordinate c) {
		this.pendingCoords = c;

		// Feedback to user that a move is pending
		setTitle("View Locality: " + name.getText() + " (LOCATION CHANGED)");
		ok.setText("OK - Save Changes*");
		ok.setBackground(new Color(200, 255, 200)); // Visual cue

		// Optional: If you have a coordinate display label in the UI, update it here
		setCursor(Cursor.getDefaultCursor());
		JOptionPane.showMessageDialog(this, "New position staged. Click 'OK - Change' to save permanently.");
	}

	public String getOldName() {
		return oldName;
	}
}
