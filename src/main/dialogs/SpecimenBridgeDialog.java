package main.dialogs;

import main.coords.CoordSystem;
import main.coords.Coordinate;
import main.core.Canvas;
import main.core.Settings;
import main.layers.DistanceLayer;
import main.layers.RubinLayer;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpecimenBridgeDialog extends JDialog {
    private final SpecimenService service;
    private final main.core.Canvas canvas;
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
    private int pendingIndex = -1;
    private boolean isNavigating = false;
    private String currentLoadedDistrict = null;
    private String currentLoadedProvince = null;

    private static final Map<String, Integer> ISOF_PROVINCE_MAP = new HashMap<>();
    static {
        ISOF_PROVINCE_MAP.put("Skåne", 1);
        ISOF_PROVINCE_MAP.put("Blekinge", 2);
        ISOF_PROVINCE_MAP.put("Öland", 3);
        ISOF_PROVINCE_MAP.put("Halland", 4);
        ISOF_PROVINCE_MAP.put("Småland", 5);
        ISOF_PROVINCE_MAP.put("Gotland", 6);
        ISOF_PROVINCE_MAP.put("Västergötland", 7);
        ISOF_PROVINCE_MAP.put("Östergötland", 8);
        ISOF_PROVINCE_MAP.put("Bohuslän", 9);
        ISOF_PROVINCE_MAP.put("Dalsland", 10);
        ISOF_PROVINCE_MAP.put("Närke", 11);
        ISOF_PROVINCE_MAP.put("Södermanland", 12);
        ISOF_PROVINCE_MAP.put("Värmland", 13);
        ISOF_PROVINCE_MAP.put("Västmanland", 14);
        ISOF_PROVINCE_MAP.put("Uppland", 15);
        ISOF_PROVINCE_MAP.put("Gästrikland", 16);
        ISOF_PROVINCE_MAP.put("Dalarna", 17);
        ISOF_PROVINCE_MAP.put("Hälsingland", 18);
        ISOF_PROVINCE_MAP.put("Härjedalen", 19);
        ISOF_PROVINCE_MAP.put("Medelpad", 20);
        ISOF_PROVINCE_MAP.put("Ångermanland", 21);
        ISOF_PROVINCE_MAP.put("Jämtland", 22);
        ISOF_PROVINCE_MAP.put("Västerbotten", 23);
        ISOF_PROVINCE_MAP.put("Norrbotten", 25);
        ISOF_PROVINCE_MAP.put("Lappland", 24); // Note: Isof often groups Lappmarken under 'Lappland' ID 24
        ISOF_PROVINCE_MAP.put("Torne lappmark", 24);
        ISOF_PROVINCE_MAP.put("Lule lappmark", 24);
        ISOF_PROVINCE_MAP.put("Pite lappmark", 24);
        ISOF_PROVINCE_MAP.put("Lycksele lappmark", 24);
        ISOF_PROVINCE_MAP.put("Åsele lappmark", 24);
    }

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
    JButton openSearchBtn = new JButton("Search & Cache...");
    private boolean isAdjusting = false;
    private JButton btnRubin, btnRT90, btnSweref, btnLatLong;
    JPanel coordBar;

    public SpecimenBridgeDialog(Frame owner, SpecimenService service, Canvas canvas) {
        super(owner, "Link Specimen to Locality", false);
        this.service = service;
        this.canvas = canvas;
        this.totalCount = service.getCacheCount(); // Only get the number, not the data

        String cnr = Settings.getValue("cnr");
        currentIndex = (cnr != null) ? Integer.parseInt(cnr) : 0;

        initUI();
        loadSpecimen(currentIndex);
        this.pack();
        this.setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        JRootPane rootPane = this.getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        // --- RIGHT ARROW: NEXT ---
        inputMap.put(KeyStroke.getKeyStroke("RIGHT"), "nextSpecimen");
        actionMap.put("nextSpecimen", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (currentIndex < totalCount - 1) {
                    handleNavigation(currentIndex + 1);
                }
            }
        });

        // --- LEFT ARROW: PREVIOUS ---
        inputMap.put(KeyStroke.getKeyStroke("LEFT"), "prevSpecimen");
        actionMap.put("prevSpecimen", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (currentIndex > 0) {
                    handleNavigation(currentIndex - 1);
                }
            }
        });

        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (isDirty()) {
                    LocalityRecord selected = (LocalityRecord) localityCombo.getSelectedItem();
                    boolean wasPreviouslyLinked = (targetSpecimen != null && targetSpecimen.getLocalityId() > 0);

                    if (selected != null && selected.getId() > 0) {
                        saveBridge(); // Save and stay (to close)
                    } else if (wasPreviouslyLinked) {
                        deleteBridge();
                    }
                }
                saveCurrentIndex();
                dispose();
            }
        });

        // --- F1 & Ctrl + L: COPY LAST SAVED DATA ---
        inputMap.put(KeyStroke.getKeyStroke("F1"), "copyLast");
        inputMap.put(KeyStroke.getKeyStroke("control L"), "copyLast");

        actionMap.put("copyLast", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (lastSavedBridge != null) {
                    applyBridgeToUI(lastSavedBridge);
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("control F"), "searchLoc");
        actionMap.put("searchLoc", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                searchLocality();
            }
        });

        // --- Ctrl + B: SEARCH ORTNAMNSREGISTRET ---
        inputMap.put(KeyStroke.getKeyStroke("control B"), "searchOrt");
        actionMap.put("searchOrt", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                searchOrtReg();
            }
        });

        // --- TOP: NAVIGATION & SPECIMEN INFO ---
        JPanel topPanel = new JPanel(new BorderLayout());

        // Row 1: Detailed Navigation
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
                    loadSpecimen(target);
                } else {
                    // Reset to current if out of bounds
                    indexField.setText(String.valueOf(currentIndex + 1));
                }
            } catch (NumberFormatException ex) {
                indexField.setText(String.valueOf(currentIndex + 1));
            }
        });

        navPanel.add(prevBtn);
        navPanel.add(new JLabel("Specimen:"));
        navPanel.add(indexField);
        navPanel.add(totalLabel);
        navPanel.add(nextBtn);

        topPanel.add(navPanel, BorderLayout.NORTH); // Combined with infoPanel later

        // dialogs.Specimen Data Display
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
        coordBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        coordBar.setOpaque(false);

        coordBar.add(createFocusRow(rubinField, "Rubin", this::focusRubin, "RUBIN"));
        coordBar.add(createFocusRow(rt90Field, "RT90", this::focusRT90, "RT90"));
        coordBar.add(createFocusRow(swerefField, "SWEREF", this::focusSweref, "SWEREF"));
        coordBar.add(createFocusRow(latLongField, "DMS", this::focusLatLong, "DMS"));

        coordWrapper.add(coordBar);

        c.gridy = 5;
        c.weighty = 0; // Ensure this row doesn't grow
        infoPanel.add(coordWrapper, c);

        topPanel.add(infoPanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // --- CENTER: BRIDGE / EDITABLE FIELDS ---
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

        add(bridgePanel, BorderLayout.CENTER);

        // --- BOTTOM: ACTION BUTTONS ---
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
        add(actionPanel, BorderLayout.SOUTH);

        openSearchBtn.addActionListener(e -> {
            SpecimenSearchDialog searchDlg = new SpecimenSearchDialog(this, service);
            searchDlg.setModal(true); // Make it modal so we wait for it to finish
            searchDlg.setVisible(true);

            // After searchDlg is closed, refresh this dialog
            refreshFromCache();
        });
        navPanel.add(openSearchBtn);

        // Navigation Actions
        prevBtn.addActionListener(e -> {
            pendingIndex = currentIndex - 1;
            handleNavigation(pendingIndex);
        });
        nextBtn.addActionListener(e -> {
            pendingIndex = currentIndex + 1;
            handleNavigation(pendingIndex);
        });

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

        // enter button to create link and advance
        this.getRootPane().setDefaultButton(linkBtn);

        overrideDistField.getDocument().addDocumentListener(overrideListener);
        overrideProvField.getDocument().addDocumentListener(overrideListener);
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

    // Helper to keep UI creation clean
    private JPanel createFocusRow(JTextField field, String btnText, Runnable action, String type) {
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

        // Link buttons to your class variables
        if (type.equals("RUBIN")) btnRubin = btn;
        else if (type.equals("RT90")) btnRT90 = btn;
        else if (type.equals("SWEREF")) btnSweref = btn;
        else if (type.equals("DMS")) btnLatLong = btn;

        p.add(field);
        p.add(btn);
        return p;
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
        if (index < 0 || index >= totalCount) return;

        Specimen s = service.getSpecimenAt(index);
        if (s != null) {
            this.targetSpecimen = s;
            this.currentIndex = index;

            // Save the current index to settings so it persists
            try {
                Settings.setValue("cnr", String.valueOf(index));
            } catch (IOException e) {
                e.printStackTrace();
            }

            updateUIFields(s);
        }
    }

    private void handleNavigation(int nextIndex) {
        if (isNavigating) return;
        isNavigating = true;

        try {
            LocalityRecord selected = (LocalityRecord) localityCombo.getSelectedItem();
            boolean hasSelectedLocality = (selected != null && selected.getId() > 0);
            boolean wasPreviouslyLinked = (targetSpecimen != null && targetSpecimen.getLocalityId() > 0);

            if (isDirty()) {
                if (hasSelectedLocality) {
                    boolean saved = saveBridge();
                    if (!saved) return; // Halt navigation if validation/save failed
                } else if(wasPreviouslyLinked) {
                    deleteBridge();
                }
            }

            // Advance/Close logic moved here where it belongs
            if (nextIndex != -1 && nextIndex < totalCount) {
                loadSpecimen(nextIndex);
            } else if (nextIndex >= totalCount) {
                dispose();
            }

            // Always clear the canvas when moving off the record
            canvas.delLayer("Rubin");
            canvas.delLayer("distance");
            canvas.repaint();

        } finally {
            isNavigating = false;
        }
    }

    private void updateUIFields(Specimen s) {
        isAdjusting = true;

        idField.setText(s.getInstitutionCode() + " " + s.getAccessionNo());
        nameField.setText(s.getGenus() + " " + s.getSpecies());
        origTextField.setText(s.getOriginalText());
        collectorField.setText(s.getCollector() + " (" + s.getCollectionCode() + ")      " + String.format("%d-%02d-%02d", s.getYear(), s.getMonth(), s.getDay()));
        provinceDistrField.setText(s.getProvince() + ", " + s.getDistrict() + ", " + s.getSpecimenLocality());
        rubinField.setText(s.getRubin());
        rt90Field.setText("N: " + s.getRiketsN() + " O: " + s.getRiketsO());
        swerefField.setText(s.getSweref());
        latLongField.setText(Coordinate.formatDMS(s.getLatDeg(), s.getLatMin(), s.getLatSec(), s.getLatDir(), s.getLongDeg(), s.getLongMin(), s.getLongSec(), s.getLongDir()));

        // Clear/Update Bridge fields
        overrideDistField.setText(s.getODistrict() != null ? s.getODistrict() : "");
        overrideProvField.setText(s.getOProvince() != null ? s.getOProvince() : "");
        distanceField.setText(s.getDistance() > 0 ? String.valueOf(s.getDistance()) : "");
        directionCombo.setSelectedItem(s.getDirection() != null ? s.getDirection() : "");

        originalBridge = new BridgeData(
                s.getLocalityId() > 0 ? s.getLocalityId() : -1, // normalize 0 -> -1
                s.getDistance() > 0 ? String.valueOf(s.getDistance()) : "",
                s.getDirection() != null ? s.getDirection() : "",
                s.getODistrict() != null ? s.getODistrict() : "",
                s.getOProvince() != null ? s.getOProvince() : ""
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

        deleteBtn.setEnabled(s.getLocalityId() > 0);
        btnRubin.setEnabled(s.getRubin() != null && !s.getRubin().isEmpty());
        btnRT90.setEnabled(s.getRiketsN() != null && !s.getRiketsN().equals("0") && !s.getRiketsN().isEmpty());
        btnSweref.setEnabled(s.getSwerefN() > 0);
        btnLatLong.setEnabled(s.getLatDeg() != null && !s.getLatDeg().equals("0") && !s.getLatDeg().isEmpty());

        toggleComponentVisibility(btnRubin, s.getRubin());
        toggleComponentVisibility(btnRT90, s.getRiketsN()); // Checks if RT90 N exists
        toggleComponentVisibility(btnSweref, s.getSwerefN() > 0 ? "exists" : "");

        // For DMS, check if LatDeg has a value
        String dmsValue = (s.getLatDeg() != null && !s.getLatDeg().isEmpty()) ? "exists" : "";
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
            targetDistrict = targetSpecimen.getDistrict() != null ? targetSpecimen.getDistrict() : "";
        }

        String targetProvince = overrideProvField.getText().trim();
        if (targetProvince.isEmpty()) {
            targetProvince = targetSpecimen.getProvince() != null ? targetSpecimen.getProvince() : "";
        }

        final String finalDist = targetDistrict;
        final String finalProv = targetProvince;
        final int finalId = idToSelect;

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

        SwingWorker<List<LocalityRecord>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<LocalityRecord> doInBackground() {
                return service.getLocalitiesInDistrict(finalDist, finalProv);
            }

            @Override
            protected void done() {
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
        worker.execute();
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
            targetSpecimen.setLocalityId(localityId);
            targetSpecimen.setDistance(dist);
            targetSpecimen.setDirection(dir);
            targetSpecimen.setODistrict(oDist);
            targetSpecimen.setOProvince(oProv);

            return true;
        } else {
            JOptionPane.showMessageDialog(this, "Error saving link to database.");
            return false;
        }
    }

    private void deleteBridge() {
        if (targetSpecimen == null) return;

        int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete the link for this specimen?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            boolean success = service.deleteSpecimenLink(targetSpecimen);
            if (success) {
                // Update the local object state so the UI reflects the change
                targetSpecimen.setLocalityId(0);
                targetSpecimen.setODistrict("");
                targetSpecimen.setOProvince("");
                targetSpecimen.setDistance(0);
                targetSpecimen.setDirection("");

                updateUIFields(targetSpecimen);
            } else {
                JOptionPane.showMessageDialog(this, "Error: Could not delete link from MySQL.");
            }
        }
    }

    public void focusLocality() {
        LocalityRecord selected = (LocalityRecord) localityCombo.getSelectedItem();
        if (selected == null || selected.getId() == -1) return;
        // core.GUI.setCursorWait();
        Coordinate c = service.getLocalityPoint(selected.getId());
        if (c == null) return;

        // Center the map canvas
        canvas.focus(c);
        canvas.setCoordinate(c);

        // Handle Distance/Direction Visualization
        String distText = distanceField.getText().trim();
        String directionS = (String) directionCombo.getSelectedItem();

        // Clear old distance layer regardless
        canvas.delLayer("distance");

        if (!distText.isEmpty() && directionS != null && !directionS.isEmpty()) {
            try {
                int distanceI = Integer.parseInt(distText);
                if (distanceI > 0) {
                    // Add the visual vector layer
                    canvas.addLayerTop(new DistanceLayer(
                            "distance", c, distanceI, directionS, CoordSystem.SWEREF99TM
                    ));
                }
            } catch (NumberFormatException e) {
                // Silent fail for visualization if number is garbled
            }
        }

        // Repaint to show changes
        canvas.repaint();
    }

    public void focusRubin() {
        String rubin = targetSpecimen.getRubin();
        if (rubin != null && !rubin.isEmpty()) {
            RubinLayer r = new RubinLayer(rubin, "Rubin", Color.GREEN);
            canvas.delLayer("Rubin");
            canvas.addLayerTop(r);
            canvas.focus(r.getMiddle());
        }
    }

    public void focusRT90() {
        String nStr = targetSpecimen.getRiketsN();
        String oStr = targetSpecimen.getRiketsO();
        // Validate that we have strings, and they aren't just "0" or empty
        if (nStr != null && oStr != null && !nStr.equals("0") && !nStr.isEmpty()) {
            try {
                double n = Double.parseDouble(nStr);
                double o = Double.parseDouble(oStr);
                if (n == 0 || o == 0) return;

                while (n < 1000000) n *= 10;
                while (o < 1000000) o *= 10;

                Coordinate wgs84 = CoordSystem.RT90.toWGS84(n, o);
                Coordinate swtm = CoordSystem.SWEREF99TM.toProjected(wgs84);

                canvas.focus(swtm);
                canvas.setCoordinate(swtm);
            } catch (NumberFormatException e) {
                System.err.println("Invalid RT90 format");
            }
        }
    }

    public void focusSweref() {
        int n = targetSpecimen.getSwerefN();
        int e = targetSpecimen.getSwerefE();
        // Basic validation for SWEREF99 TM range (approximate Sweden bounds)
        if (n > 6000000 && e > 200000) {
            Coordinate c = new Coordinate(n, e);
            canvas.focus(c);
            canvas.setCoordinate(c);
        }
    }

    public void focusLatLong() {
        if (targetSpecimen == null) return;

        // Check if we actually have degrees set (not just empty or 0)
        String lat = targetSpecimen.getLatDeg();
        String lon = targetSpecimen.getLongDeg();
        if (lat == null || lat.isEmpty() || lat.equals("0")) return;

        try {
            Coordinate c = new Coordinate(0, 0);
            c.setFromDMS(
                    targetSpecimen.getLatDeg(), targetSpecimen.getLatMin(), targetSpecimen.getLatSec(), targetSpecimen.getLatDir(),
                    targetSpecimen.getLongDeg(), targetSpecimen.getLongMin(), targetSpecimen.getLongSec(), targetSpecimen.getLongDir()
            );

            Coordinate sweref = CoordSystem.SWEREF99TM.toProjected(c);

            canvas.focus(sweref);
            canvas.setCoordinate(sweref);
        } catch (Exception e) {
            System.err.println("Lat/Long conversion failed");
        }
    }

    private boolean isDirty() {
        BridgeData currentUI = getBridgeFromUI();
        return !currentUI.equals(originalBridge);
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
        String selectedText = "";
        String province = "";

        // Capture Province from the target specimen
        if (targetSpecimen != null) {
            // Assuming your dialogs.Specimen object has a getProvince method
            province = targetSpecimen.getProvince();
        }

        // Get text selection using Java 21 Pattern Matching
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner instanceof javax.swing.text.JTextComponent textComp) {
            String selection = textComp.getSelectedText();
            if (selection != null && !selection.isBlank()) {
                selectedText = selection.trim();
            }
        }

        if (selectedText.isEmpty() && targetSpecimen != null) {
            selectedText = targetSpecimen.getSpecimenLocality();
        }

        // Clipboard handling
        if (!selectedText.isEmpty()) {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(selectedText), null);
            } catch (Exception e) {
                System.err.println("Clipboard error: " + e.getMessage());
            }
        }

        // Resolve the Parent Frame
        // We look for the top-level Window (the core.GUI Frame) that contains this dialog
        Frame parentFrame = (Frame) javax.swing.SwingUtilities.getWindowAncestor(this);

        // Open and Position the Dialog
        SearchLocalityDialog d = new SearchLocalityDialog(parentFrame, canvas, selectedText, province);

        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private void searchOrtReg() {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            System.out.println("Browser not supported on this system.");
            return;
        }

        String placeName = "";
        String provinceName = targetSpecimen.getProvince();

        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

        // Get selected text from the focused component
        if (focusOwner instanceof javax.swing.text.JTextComponent) {
            String selection = ((javax.swing.text.JTextComponent) focusOwner).getSelectedText();
            if (selection != null && !selection.trim().isEmpty()) {
                placeName = selection.trim();
            }
        }

        // Fallback to the specimen's locality field if nothing is highlighted
        if (placeName.isEmpty() && targetSpecimen != null) {
            placeName = targetSpecimen.getSpecimenLocality();
        }

        if (placeName == null || placeName.isEmpty()) return;

        try {
            StringSelection stringSelection = new StringSelection(placeName);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
        } catch (Exception e) {
            System.err.println("Clipboard copy failed: " + e.getMessage());
        }

        Integer provinceId = ISOF_PROVINCE_MAP.get(provinceName);

        try {
            // Encode the string for a URL (handles spaces and Swedish characters)
            String encodedName = java.net.URLEncoder.encode(placeName, StandardCharsets.UTF_8);

            // Build URL dynamically
            StringBuilder urlBuilder = new StringBuilder("https://ortnamnsregistret.isof.se/place-names?place-name=");
            urlBuilder.append(encodedName);

            if (provinceId != null) {
                urlBuilder.append("&province-id=").append(provinceId);
            }

            Desktop.getDesktop().browse(new java.net.URI(urlBuilder.toString()));
        } catch (Exception e) {
            e.printStackTrace();
        }
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