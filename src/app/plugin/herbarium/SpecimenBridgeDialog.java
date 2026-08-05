package app.plugin.herbarium;

import gis.coords.CoordSystem;
import gis.coords.Coordinate;
import gis.core.MapCanvas;
import gis.core.Settings;
import gis.layers.DistanceLayer;
import app.MapLayers;
import gis.layers.RubinLayer;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

public class SpecimenBridgeDialog extends JDialog {
    private final SpecimenService service;
    private final MapCanvas mapCanvas;
    private final HerbariumController gui;
    private final LocalityRepository localities;
    private int totalCount;
    private int currentIndex;
    private Specimen targetSpecimen;
    private BridgeData originalBridge; // What we loaded from DB
    private BridgeData lastSavedBridge; // For the F1 "Copy Last" feature
    private final String[] directions = {
            "", "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    };
    private JTextField indexField; // For jumping to specific records
    private boolean isNavigating = false;
    private String currentLoadedDistrict = null;
    private String currentLoadedProvince = null;
    private SwingWorker<List<LocalityRecord>, Void> localityWorker;
    private SwingWorker<Specimen, Void> specimenWorker;
    private long localityRequestGeneration = 0;


    private JLabel totalLabel;

    // dialogs.Specimen Info Fields (Selectable)
    private JTextField idField, nameField, collectorField;
    private JTextArea origTextField; // JTextArea for long descriptions
    private JTextField rubinField, rt90Field, swerefField, latLongField;
    private JTextField provinceDistrField;

    // Editable Bridge Fields
    private JComboBox<LocalityRecord> localityCombo;
    private JTextField overrideDistField, overrideProvField;
    private JTextField distanceField;
    private JComboBox<String> directionCombo;

    private JButton prevBtn, nextBtn, linkBtn, deleteBtn;
    private boolean isAdjusting = false;
    private JButton btnRubin, btnRT90, btnSweref, btnLatLong;

    public SpecimenBridgeDialog(Frame owner, HerbariumController gui, SpecimenService service,
                                MapCanvas mapCanvas, LocalityRepository localities) {
        super(owner, "Link Specimen to Locality", false);
        this.service = service;
        this.gui = gui;
        this.mapCanvas = mapCanvas;
        this.localities = localities;
        this.totalCount = service.getCacheCount(); // Only get the number, not the data

        String cnr = Settings.getValue("cnr");
        try {
            currentIndex = (cnr != null) ? Integer.parseInt(cnr.trim()) : 0;
        } catch (NumberFormatException e) {
            currentIndex = 0;
        }

        initUI();
        loadSpecimen(currentIndex);
        this.pack();
        this.setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        installKeyBindings();

        add(buildTopPanel(), BorderLayout.NORTH);
        add(buildBridgePanel(), BorderLayout.CENTER);
        add(buildActionPanel(), BorderLayout.SOUTH);

        // enter button to create link and advance
        getRootPane().setDefaultButton(linkBtn);

        javax.swing.event.DocumentListener overrideListener = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { checkUpdate(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { checkUpdate(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { checkUpdate(); }

            private void checkUpdate() {
                // Only trigger if the user is typing, not when loadSpecimen() is running
                if (!isAdjusting && targetSpecimen != null) {
                    SwingUtilities.invokeLater(() -> updateLocalityList(-1));
                }
            }
        };
        overrideDistField.getDocument().addDocumentListener(overrideListener);
        overrideProvField.getDocument().addDocumentListener(overrideListener);
    }

    private void installKeyBindings() {
        JRootPane rootPane = this.getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        bindKey(inputMap, actionMap, "nextSpecimen", () -> {
            if (currentIndex < totalCount - 1) handleNavigation(currentIndex + 1);
        }, "RIGHT");

        bindKey(inputMap, actionMap, "prevSpecimen", () -> {
            if (currentIndex > 0) handleNavigation(currentIndex - 1);
        }, "LEFT");

        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (isDirty()) {
                    LocalityRecord selected = (LocalityRecord) localityCombo.getSelectedItem();
                    boolean wasPreviouslyLinked = (targetSpecimen != null && targetSpecimen.localityId() > 0);

                    if (selected != null && selected.getId() > 0) {
                        if (!saveBridge()) {
                            return;
                        }
                    } else if (wasPreviouslyLinked) {
                        if (!deleteBridge()) {
                            return;
                        }
                    }
                }
                saveCurrentIndex();
                dispose();
            }
        });

        // --- F1 & Ctrl + L: COPY LAST SAVED DATA ---
        bindKey(inputMap, actionMap, "copyLast", () -> {
            if (lastSavedBridge != null) applyBridgeToUI(lastSavedBridge);
        }, "F1", "control L");

        bindKey(inputMap, actionMap, "searchLoc", this::searchLocality, "control F");

        // --- Ctrl + B: SEARCH ORTNAMNSREGISTRET ---
        bindKey(inputMap, actionMap, "searchOrt", this::searchOrtReg, "control B");
    }

    /** Navigation row plus the read-only specimen info card. */
    private JPanel buildTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(buildNavPanel(), BorderLayout.NORTH);
        topPanel.add(buildInfoPanel(), BorderLayout.CENTER);
        return topPanel;
    }

    /** Prev/next, the jump-to-index box and the search-and-cache entry point. */
    private JPanel buildNavPanel() {
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));

        prevBtn = new JButton("<< Previous");
        nextBtn = new JButton("Next >>");

        // Index Jump Components
        indexField = new JTextField(4);
        indexField.setHorizontalAlignment(JTextField.CENTER);
        totalLabel = new JLabel("/ 0");

        // Add "Jump" logic to the text field (Enter to go)
        indexField.addActionListener(e -> {
            try {
                int target = Integer.parseInt(indexField.getText().trim()) - 1; // 1-based to 0-based
                if (target >= 0 && target < totalCount) {
                    handleNavigation(target);
                } else {
                    // Reset to current if out of bounds
                    indexField.setText(String.valueOf(currentIndex + 1));
                }
            } catch (NumberFormatException ex) {
                indexField.setText(String.valueOf(currentIndex + 1));
            }
        });

        JButton openSearchBtn = new JButton("Search & Cache...");
        openSearchBtn.addActionListener(e -> {
            SpecimenSearchDialog searchDlg = new SpecimenSearchDialog(this, service);
            searchDlg.setModal(true); // Make it modal so we wait for it to finish
            searchDlg.setVisible(true);

            // After searchDlg is closed, refresh this dialog
            refreshFromCache();
        });

        prevBtn.addActionListener(e -> handleNavigation(currentIndex - 1));
        nextBtn.addActionListener(e -> handleNavigation(currentIndex + 1));

        navPanel.add(prevBtn);
        navPanel.add(new JLabel("Specimen:"));
        navPanel.add(indexField);
        navPanel.add(totalLabel);
        navPanel.add(nextBtn);
        navPanel.add(openSearchBtn);
        return navPanel;
    }

    /** Read-only specimen card: identity, original text, and the coordinate bar. */
    private JPanel buildInfoPanel() {
        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBackground(Color.WHITE);
        infoPanel.setBorder(BorderFactory.createTitledBorder("Specimen Info"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 10, 4, 10); // Spacing between lines
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.gridx = 0;

        // Initialize fields
        idField = createPlainField();
        nameField = createPlainField();
        collectorField = createPlainField();
        rubinField = createPlainField();
        rt90Field = createPlainField();
        swerefField = createPlainField();
        latLongField = createPlainField();
        provinceDistrField = createPlainField();

        // Original Text Area
        origTextField = new JTextArea(4, 20);
        origTextField.setEditable(false);
        origTextField.setLineWrap(true);
        origTextField.setWrapStyleWord(true);
        origTextField.setOpaque(false); // Let the white panel show through
        origTextField.setBorder(null);
        origTextField.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 15));

        // Create a scroll pane that is also transparent/borderless
        JScrollPane origScroll = new JScrollPane(origTextField);
        origScroll.setBorder(null);
        origScroll.setOpaque(false);
        origScroll.getViewport().setOpaque(false);

        // Add components in vertical order
        c.gridy = 0; infoPanel.add(idField, c);
        c.gridy = 1; infoPanel.add(nameField, c);

        // Add the scrollable original text with more weight so it can expand
        c.gridy = 2; c.weighty = 1.0; c.fill = GridBagConstraints.BOTH;
        infoPanel.add(origScroll, c);

        // Reset weights for the rest
        c.weighty = 0; c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = 3; infoPanel.add(collectorField, c);
        c.gridy = 4; infoPanel.add(provinceDistrField, c);

        JPanel coordWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        coordWrapper.setOpaque(false);
        coordWrapper.setPreferredSize(new Dimension(10, 32));
        coordWrapper.setMinimumSize(new Dimension(10, 32));

        // Use a FlowLayout with very tight gaps for the bar itself
        JPanel coordBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        coordBar.setOpaque(false);

        btnRubin = addFocusRow(coordBar, rubinField, "Rubin", this::focusRubin);
        btnRT90 = addFocusRow(coordBar, rt90Field, "RT90", this::focusRT90);
        btnSweref = addFocusRow(coordBar, swerefField, "SWEREF", this::focusSweref);
        btnLatLong = addFocusRow(coordBar, latLongField, "DMS", this::focusLatLong);

        coordWrapper.add(coordBar);

        c.gridy = 5;
        c.weighty = 0; // Ensure this row doesn't grow
        infoPanel.add(coordWrapper, c);

        return infoPanel;
    }

    /** The editable half: locality picker, district/province overrides, offset. */
    private JPanel buildBridgePanel() {
        JPanel bridgePanel = new JPanel(new GridBagLayout());
        bridgePanel.setBorder(BorderFactory.createTitledBorder("Create Bridge to Locality"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Locality Picker
        gbc.gridx = 0; gbc.gridy = 0;
        bridgePanel.add(new JLabel("Target Locality:"), gbc);
        // Create a sub-panel for the combo + focus button
        JPanel locPickerPanel = new JPanel(new BorderLayout(5, 0));
        localityCombo = new JComboBox<>();
        JButton focusBtn = new JButton("Focus");
        focusBtn.setToolTipText("Focus map on this locality");

        locPickerPanel.add(localityCombo, BorderLayout.CENTER);
        locPickerPanel.add(focusBtn, BorderLayout.EAST);

        gbc.gridx = 1; gbc.weightx = 1.0;
        bridgePanel.add(locPickerPanel, gbc);
        focusBtn.addActionListener(e -> focusLocality());

        // Override fields
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        bridgePanel.add(new JLabel("Override District:"), gbc);
        gbc.gridx = 1;
        overrideDistField = new JTextField();
        bridgePanel.add(overrideDistField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        bridgePanel.add(new JLabel("Override Province:"), gbc);
        gbc.gridx = 1;
        overrideProvField = new JTextField();
        bridgePanel.add(overrideProvField, gbc);

        // Distance / Direction
        JPanel distDirPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        distDirPanel.setOpaque(false);

// Distance
        distDirPanel.add(new JLabel("Distance (m):"));
        distanceField = new JTextField();
        distanceField.setColumns(8); // Fixed visual width for the input box
        distDirPanel.add(distanceField);

// Spacer (Vertical pipe or extra gap)
       // distDirPanel.add(Box.createHorizontalStrut(10));

// Direction
        distDirPanel.add(new JLabel("Direction:"));
        directionCombo = new JComboBox<>(directions);
// Combos in FlowLayout often shrink too much; give it a reasonable fixed width
        directionCombo.setPreferredSize(new Dimension(60, 25));
        distDirPanel.add(directionCombo);

// Add to the main bridgePanel using your existing GridBag constraints
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE; // CRITICAL: Don't let the FlowPanel stretch
        gbc.anchor = GridBagConstraints.WEST; // Keep it aligned to the left
        bridgePanel.add(distDirPanel, gbc);

        return bridgePanel;
    }

    private JPanel buildActionPanel() {
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        linkBtn = new JButton("Create Link (Save to MySQL)");
        linkBtn.addActionListener(e -> saveBridge());

        deleteBtn = new JButton("Delete Link");
        deleteBtn.setForeground(Color.RED);
        deleteBtn.addActionListener(e -> deleteBridge());
        actionPanel.add(deleteBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e ->
            processWindowEvent(new java.awt.event.WindowEvent(this, java.awt.event.WindowEvent.WINDOW_CLOSING))
        );

        actionPanel.add(linkBtn);
        actionPanel.add(closeBtn);
        return actionPanel;
    }

    /** Binds one or more keystrokes to a named action running {@code body}. */
    private static void bindKey(InputMap inputMap, ActionMap actionMap, String name,
                                Runnable body, String... keyStrokes) {
        for (String keyStroke : keyStrokes) inputMap.put(KeyStroke.getKeyStroke(keyStroke), name);
        actionMap.put(name, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { body.run(); }
        });
    }

    private JTextField createPlainField() {
        JTextField f = new JTextField();
        f.setEditable(false);
        f.setBorder(null);      // No border/clutter
        f.setOpaque(false);     // Blend into background
        f.setBackground(new Color(0,0,0,0));
        // Set a font that looks clear for data entry
        f.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return f;
    }

    /** Adds a "value + focus button" row to {@code bar} and returns the button. */
    private JButton addFocusRow(JPanel bar, JTextField field, String btnText, Runnable action) {
        // 5px gap between the text and its specific button
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        p.setOpaque(false);

        field.setEditable(false);
        field.setBorder(null);
        field.setOpaque(false);

        // IMPORTANT: Clear any previous fixed sizes
        field.setPreferredSize(null);
        // Setting columns to 0 or a very small number allows it to grow with text
        field.setColumns(0);

        JButton btn = new JButton(btnText);
        btn.setMargin(new Insets(1, 4, 1, 4));
        btn.setFocusable(false);
        btn.addActionListener(e -> action.run());

        p.add(field);
        p.add(btn);
        bar.add(p);
        return btn;
    }

    private void refreshFromCache() {
        isAdjusting = true;
        this.totalCount = service.getCacheCount();
        totalLabel.setText("/ " + totalCount);

        if (totalCount > 0) {
            // If our current index is now out of bounds (e.g., search results are fewer), reset to 0
            if (currentIndex >= totalCount) {
                currentIndex = 0;
            }
            loadSpecimen(currentIndex);
        } else {
            targetSpecimen = null;
            clearFields();
            setTitle("Bridge Tool - Cache Empty");
        }
    }

    private void clearFields() {
        isAdjusting = true;

        // dialogs.Specimen Info Fields
        idField.setText("");
        nameField.setText("");
        origTextField.setText("");
        collectorField.setText("");
        //locField.setText("");
        provinceDistrField.setText("");
        rubinField.setText("");
        rt90Field.setText("");
        swerefField.setText("");
        latLongField.setText("");

        // Bridge Input Fields
        overrideDistField.setText("");
        overrideProvField.setText("");
        distanceField.setText("");
        directionCombo.setSelectedIndex(0); // Reset to empty string ""

        // Locality List
        localityCombo.removeAllItems();
        localityCombo.addItem(new LocalityRecord(-1, "-- No Data --"));

        // Navigation
        indexField.setText("0");
        totalLabel.setText("/ 0");
        prevBtn.setEnabled(false);
        nextBtn.setEnabled(false);
        deleteBtn.setEnabled(false);

        isAdjusting = false;
    }

    private void loadSpecimen(int index) {
        if (index < 0 || index >= totalCount) {
            isNavigating = false;
            return;
        }

        // Optional: Visual feedback that things are happening
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        specimenWorker = new SwingWorker<Specimen, Void>() {
            @Override
            protected Specimen doInBackground() throws Exception {
                return service.getSpecimenAt(index);
            }

            @Override
            protected void done() {
                try {
                    Specimen s = get();
                    if (s != null) {
                        SpecimenBridgeDialog.this.targetSpecimen = s;
                        SpecimenBridgeDialog.this.currentIndex = index;

                        Settings.setValue("cnr", String.valueOf(index));
                        updateUIFields(s);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // UNLOCK everything here
                    isNavigating = false;
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };
        specimenWorker.execute();
    }
    private void handleNavigation(int nextIndex) {
        if (isNavigating) return;
        isNavigating = true; // Lock navigation

        try {
            LocalityRecord selected = (LocalityRecord) localityCombo.getSelectedItem();
            boolean hasSelectedLocality = (selected != null && selected.getId() > 0);
            boolean wasPreviouslyLinked = (targetSpecimen != null && targetSpecimen.localityId() > 0);

            if (isDirty()) {
                if (hasSelectedLocality) {
                    boolean saved = saveBridge();
                    if (!saved) {
                        isNavigating = false; // Release lock if save fails
                        return;
                    }
                } else if(wasPreviouslyLinked) {
                    if (!deleteBridge()) {
                        isNavigating = false;
                        return;
                    }
                }
            }

            if (nextIndex != -1 && nextIndex < totalCount) {
                loadSpecimen(nextIndex); // The lock is released inside this method's worker
            } else if (nextIndex >= totalCount) {
                dispose();
            } else {
                isNavigating = false; // Release if index is invalid
            }

            mapCanvas.getLayerManager().removeOverlay(MapLayers.RUBIN_MARKER);
            mapCanvas.getLayerManager().removeOverlay(MapLayers.DISTANCE_OVERLAY);
            mapCanvas.repaint();

        } catch (Exception e) {
            e.printStackTrace();
            isNavigating = false; // Release on unexpected error
        }
    }
    private void updateUIFields(Specimen s) {
        isAdjusting = true;

        idField.setText(s.institutionCode() + " " + s.accessionNo());
        nameField.setText(s.genus() + " " + s.species());
        origTextField.setText(s.originalText());
        collectorField.setText(s.collector() + " (" + s.collectionCode() + ")      " + String.format("%d-%02d-%02d", s.year(), s.month(), s.day()));
        provinceDistrField.setText(s.province() + ", " + s.district() + ", " + s.specimenLocality());
        rubinField.setText(s.rubin());
        rt90Field.setText("N: " + s.riketsN() + " O: " + s.riketsO());
        swerefField.setText(s.sweref());
        latLongField.setText(Coordinate.formatDMS(s.latDeg(), s.latMin(), s.latSec(), s.latDir(), s.longDeg(), s.longMin(), s.longSec(), s.longDir()));

        // Clear/Update Bridge fields
        overrideDistField.setText(s.oDistrict() != null ? s.oDistrict() : "");
        overrideProvField.setText(s.oProvince() != null ? s.oProvince() : "");
        distanceField.setText(s.distance() > 0 ? String.valueOf(s.distance()) : "");
        directionCombo.setSelectedItem(s.direction() != null ? s.direction() : "");

        originalBridge = new BridgeData(
                s.localityId() > 0 ? s.localityId() : -1, // normalize 0 -> -1
                s.distance() > 0 ? String.valueOf(s.distance()) : "",
                s.direction() != null ? s.direction() : "",
                s.oDistrict() != null ? s.oDistrict() : "",
                s.oProvince() != null ? s.oProvince() : ""
        );

        // Apply to UI
        applyBridgeToUI(originalBridge);

        isAdjusting = false;

        // Update Navigation UI
        indexField.setText(String.valueOf(currentIndex + 1));
        totalLabel.setText("/ " + totalCount);

        // Update Navigation state
        prevBtn.setEnabled(currentIndex > 0);
        nextBtn.setEnabled(currentIndex < totalCount - 1);

        deleteBtn.setEnabled(s.localityId() > 0);
        btnRubin.setEnabled(s.rubin() != null && !s.rubin().isEmpty());
        btnRT90.setEnabled(s.riketsN() != null && !s.riketsN().equals("0") && !s.riketsN().isEmpty());
        btnSweref.setEnabled(s.swerefN() > 0);
        btnLatLong.setEnabled(s.latDeg() != null && !s.latDeg().equals("0") && !s.latDeg().isEmpty());

        toggleComponentVisibility(btnRubin, s.rubin());
        toggleComponentVisibility(btnRT90, s.riketsN()); // Checks if RT90 N exists
        toggleComponentVisibility(btnSweref, s.swerefN() > 0 ? "exists" : "");

        // For DMS, check if LatDeg has a value
        String dmsValue = (s.latDeg() != null && !s.latDeg().isEmpty()) ? "exists" : "";
        toggleComponentVisibility(btnLatLong, dmsValue);

        setTitle("Link Specimen " + (currentIndex + 1) + " of " + totalCount);
    }

    private void toggleComponentVisibility(JButton btn, Object value) {
        boolean hasData = value != null && !value.toString().trim().isEmpty() && !value.toString().equals("0");
        // This hides/shows the sub-panel (p) created in createFocusRow
        btn.getParent().setVisible(hasData);
    }

    public void updateLocalityList(int idToSelect) {
        if (targetSpecimen == null) return;

        // Determine which District/Province to filter by
        String targetDistrict = overrideDistField.getText().trim();
        if (targetDistrict.isEmpty()) {
            targetDistrict = targetSpecimen.district() != null ? targetSpecimen.district() : "";
        }

        String targetProvince = overrideProvField.getText().trim();
        if (targetProvince.isEmpty()) {
            targetProvince = targetSpecimen.province() != null ? targetSpecimen.province() : "";
        }

        final String finalDist = targetDistrict;
        final String finalProv = targetProvince;
        final int finalId = idToSelect;
        final long requestGeneration = ++localityRequestGeneration;

        if (localityWorker != null) {
            localityWorker.cancel(true);
        }

        // --- NEW: Bypass rebuild if the list data hasn't changed ---
        if (finalDist.equals(currentLoadedDistrict) && finalProv.equals(currentLoadedProvince)) {
            setLocalitySelectionById(finalId);
            return;
        }

        // Set to Loading state
        boolean wasAdjusting = isAdjusting;
        isAdjusting = true;
        localityCombo.removeAllItems();
        localityCombo.addItem(new LocalityRecord(-1, "Loading..."));
        isAdjusting = wasAdjusting;

        localityWorker = new SwingWorker<>() {
            @Override
            protected List<LocalityRecord> doInBackground() {
                return service.getLocalitiesInDistrict(finalDist, finalProv);
            }

            @Override
            protected void done() {
                if (isCancelled() || requestGeneration != localityRequestGeneration) {
                    return;
                }
                try {
                    List<LocalityRecord> localities = get();

                    // Block listeners while we rebuild the list
                    isAdjusting = true;

                    localityCombo.removeAllItems();
                    localityCombo.addItem(new LocalityRecord(-1, "-- Select a Locality --"));

                    LocalityRecord toSelect = null;
                    for (LocalityRecord l : localities) {
                        localityCombo.addItem(l);
                        if (l.getId() == finalId) {
                            toSelect = l;
                        }
                    }

                    if (toSelect != null) {
                        localityCombo.setSelectedItem(toSelect);
                    } else {
                        localityCombo.setSelectedIndex(0);
                    }

                    // --- NEW: Update our tracking variables ---
                    currentLoadedDistrict = finalDist;
                    currentLoadedProvince = finalProv;

                    isAdjusting = false;

                } catch (Exception e) {
                    e.printStackTrace();
                    isAdjusting = false;
                }
            }
        };
        localityWorker.execute();
    }

    public void invalidateLocalityList() {
        service.invalidateLocalityCache();
        currentLoadedDistrict = null; // Force a rebuild
        currentLoadedProvince = null;
        updateLocalityList(-1);
    }

    private boolean saveBridge() {
        if (targetSpecimen == null) return false;

        LocalityRecord selectedLoc = (LocalityRecord) localityCombo.getSelectedItem();
        // Check if a valid locality is selected (ignoring the "-- Select --" placeholder)
        if (selectedLoc == null || selectedLoc.getId() <= 0) {
            JOptionPane.showMessageDialog(this, "Please select a target locality.");
            return false;
        }

        // Extract and Validate Distance
        int dist = 0;
        String distText = distanceField.getText().trim();
        boolean hasDistance = !distText.isEmpty();

        if (hasDistance) {
            try {
                dist = Integer.parseInt(distText);
                if (dist < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Distance must be a positive whole number (meters).",
                        "Invalid Distance", JOptionPane.ERROR_MESSAGE);
                distanceField.requestFocus();
                return false;
            }
        }

        // Extract Direction
        String dir = directionCombo.getSelectedItem() != null ? (String) directionCombo.getSelectedItem() : "";
        boolean hasDirection = (dir != null && !dir.isEmpty());

        // Co-dependency Check (Both or Neither)
        if (hasDistance != hasDirection) {
            String msg = hasDistance ?
                    "You provided a distance. Please select a Direction." :
                    "You selected a direction. Please provide a Distance (in meters).";
            JOptionPane.showMessageDialog(this, msg, "Incomplete Offset", JOptionPane.WARNING_MESSAGE);
            if (!hasDistance) distanceField.requestFocus();
            else directionCombo.requestFocus();
            return false;
        }

        // Collect bridge data for MySQL
        int localityId = selectedLoc.getId();
        String oDist = overrideDistField.getText().trim();
        String oProv = overrideProvField.getText().trim();

        // Save to MySQL
        boolean success = service.linkSpecimenToLocality(targetSpecimen, localityId, oDist, oProv, dist, dir);

        if (success) {
            // --- UPDATE STATE FOR WORKFLOW ---
            BridgeData currentUI = getBridgeFromUI();

            // Memory for the F1 "Copy Last" feature
            lastSavedBridge = currentUI;

            // Update originalBridge so the Dirty Check knows we are now in sync with DB
            originalBridge = currentUI;

            // Update the actual specimen object so the UI stays consistent if we don't move
            targetSpecimen = targetSpecimen.withBridge(localityId, dist, dir, oDist, oProv);

            return true;
        } else {
            JOptionPane.showMessageDialog(this, "Error saving link to database.");
            return false;
        }
    }

    private boolean deleteBridge() {
        if (targetSpecimen == null) return true;

        int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete the link for this specimen?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            boolean success = service.deleteSpecimenLink(targetSpecimen);
            if (success) {
                // Update the local object state so the UI reflects the change
                targetSpecimen = targetSpecimen.withBridge(0, 0, "", "", "");

                updateUIFields(targetSpecimen);
                return true;
            } else {
                JOptionPane.showMessageDialog(this, "Error: Could not delete link from MySQL.");
                return false;
            }
        }
        return false;
    }

    public void focusLocality() {
        LocalityRecord selected = (LocalityRecord) localityCombo.getSelectedItem();
        if (selected == null || selected.getId() == -1) return;
        // core.GUI.setCursorWait();
        Coordinate sweref = service.getLocalityPoint(selected.getId());
        if (sweref == null) return;

        // The DB stores SWEREF99TM; the canvas may be in another CRS
        Coordinate c = CoordSystem.SWEREF99TM.convertTo(sweref, mapCanvas.getCRS());

        // Center the map canvas
        mapCanvas.focus(c);
        mapCanvas.setCoordinate(c);

        // Handle Distance/Direction Visualization
        String distText = distanceField.getText().trim();
        String directionS = (String) directionCombo.getSelectedItem();

        // Clear old distance layer regardless
        mapCanvas.getLayerManager().removeOverlay(MapLayers.DISTANCE_OVERLAY);

        if (!distText.isEmpty() && directionS != null && !directionS.isEmpty()) {
            try {
                int distanceI = Integer.parseInt(distText);
                if (distanceI > 0) {
                    // Add the visual vector layer
                    mapCanvas.getLayerManager().setOverlay(MapLayers.DISTANCE_OVERLAY,
                            new DistanceLayer(mapCanvas, "Distance", c, distanceI, directionS));
                }
            } catch (NumberFormatException e) {
                // Silent fail for visualization if number is garbled
            }
        }

        // Repaint to show changes
        mapCanvas.repaint();
    }

    public void focusRubin() {
        if (targetSpecimen == null) return;
        String rubin = targetSpecimen.rubin();
        if (rubin != null && !rubin.isEmpty()) {
            RubinLayer r = new RubinLayer(rubin, mapCanvas, "Rubin", Color.GREEN);
            mapCanvas.getLayerManager().setOverlay(MapLayers.RUBIN_MARKER, r);
            mapCanvas.focus(r.getMiddle());
        }
    }

    public void focusRT90() {
        if (targetSpecimen == null) return;
        String nStr = targetSpecimen.riketsN();
        String oStr = targetSpecimen.riketsO();
        // Validate that we have strings, and they aren't just "0" or empty
        if (nStr != null && oStr != null && !nStr.equals("0") && !nStr.isEmpty()) {
            try {
                double n = Double.parseDouble(nStr);
                double o = Double.parseDouble(oStr);
                if (n == 0 || o == 0) return;

                while (n < 1000000) n *= 10;
                while (o < 1000000) o *= 10;

                Coordinate wgs84 = CoordSystem.RT90.toWGS84(n, o);
                Coordinate c = mapCanvas.getCRS().toProjected(wgs84);

                mapCanvas.focus(c);
                mapCanvas.setCoordinate(c);
            } catch (NumberFormatException e) {
                System.err.println("Invalid RT90 format");
            }
        }
    }

    public void focusSweref() {
        if (targetSpecimen == null) return;
        int n = targetSpecimen.swerefN();
        int e = targetSpecimen.swerefE();
        // Basic validation for SWEREF99 TM range (approximate Sweden bounds)
        if (n > 6000000 && e > 200000) {
            Coordinate c = CoordSystem.SWEREF99TM.convertTo(new Coordinate(n, e), mapCanvas.getCRS());
            mapCanvas.focus(c);
            mapCanvas.setCoordinate(c);
        }
    }

    public void focusLatLong() {
        if (targetSpecimen == null) return;

        // Check if we actually have degrees set (not just empty or 0)
        String lat = targetSpecimen.latDeg();
        String lon = targetSpecimen.longDeg();
        if (lat == null || lat.isEmpty() || lat.equals("0")) return;

        try {
            Coordinate c = new Coordinate(0, 0);
            c.setFromDMS(
                    targetSpecimen.latDeg(), targetSpecimen.latMin(), targetSpecimen.latSec(), targetSpecimen.latDir(),
                    targetSpecimen.longDeg(), targetSpecimen.longMin(), targetSpecimen.longSec(), targetSpecimen.longDir()
            );

            Coordinate canvasCoord = mapCanvas.getCRS().toProjected(c);

            mapCanvas.focus(canvasCoord);
            mapCanvas.setCoordinate(canvasCoord);
        } catch (Exception e) {
            System.err.println("Lat/Long conversion failed");
        }
    }

    public boolean isDirty() {
        BridgeData currentUI = getBridgeFromUI();
        return !currentUI.equals(originalBridge);
    }

    public boolean savePendingChanges() {
        if (!isDirty()) return true;
        LocalityRecord selected = (LocalityRecord) localityCombo.getSelectedItem();
        boolean wasPreviouslyLinked = targetSpecimen != null && targetSpecimen.localityId() > 0;
        if (selected != null && selected.getId() > 0) return saveBridge();
        if (wasPreviouslyLinked) return deleteBridge();
        return true;
    }

    public void discardPendingChanges() {
        originalBridge = getBridgeFromUI();
    }

    public void closeForProjectTransition() {
        if (localityWorker != null) localityWorker.cancel(true);
        if (specimenWorker != null) specimenWorker.cancel(true);
        saveCurrentIndex();
        dispose();
    }

    private BridgeData getBridgeFromUI() {
        LocalityRecord sel = (LocalityRecord) localityCombo.getSelectedItem();
        return new BridgeData(
                (sel != null) ? sel.getId() : -1,
                distanceField.getText().trim(),
                (String) directionCombo.getSelectedItem(),
                overrideDistField.getText().trim(),
                overrideProvField.getText().trim()
        );
    }

    private void applyBridgeToUI(BridgeData data) {
        // Block the DocumentListener from firing updateLocalityList(-1)
        boolean wasAdjusting = isAdjusting;
        isAdjusting = true;

        distanceField.setText(data.distance);
        directionCombo.setSelectedItem(data.direction);
        overrideDistField.setText(data.oDistrict);
        overrideProvField.setText(data.oProvince);

        // Restore state before calling the final update
        isAdjusting = wasAdjusting;

        // Tell the list to select the locality ID from the specimen bridge record
        updateLocalityList(data.localityId);
    }

    public void searchLocality() {
        String province = targetSpecimen != null ? targetSpecimen.province() : "";
        String placeName = PlaceNameLookup.selectionOr(
                targetSpecimen != null ? targetSpecimen.specimenLocality() : "");
        PlaceNameLookup.copyToClipboard(placeName);

        // The top-level Window (the GUI Frame) that contains this dialog
        Frame parentFrame = (Frame) javax.swing.SwingUtilities.getWindowAncestor(this);

        SearchLocalityDialog d = new SearchLocalityDialog(parentFrame, gui, mapCanvas, placeName, province,
                localities);
        gui.trackWindow(d);

        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private void searchOrtReg() {
        if (targetSpecimen == null) return;
        String placeName = PlaceNameLookup.selectionOr(targetSpecimen.specimenLocality());
        if (placeName.isEmpty()) return;

        PlaceNameLookup.copyToClipboard(placeName);
        PlaceNameLookup.browseOrtnamnsregistret(placeName, targetSpecimen.province());
    }

    private void saveCurrentIndex() {
        try {
            Settings.setValue("cnr", String.valueOf(currentIndex));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setLocalitySelectionById(int id) {
        boolean wasAdjusting = isAdjusting;
        isAdjusting = true;
        for (int i = 0; i < localityCombo.getItemCount(); i++) {
            LocalityRecord record = localityCombo.getItemAt(i);
            if (record.getId() == id) {
                localityCombo.setSelectedIndex(i);
                isAdjusting = wasAdjusting;
                return;
            }
        }
        localityCombo.setSelectedIndex(0); // Fallback to placeholder
        isAdjusting = wasAdjusting;
    }
}
