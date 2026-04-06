import coords.CoordSystem;
import coords.Coordinates;
import geometry.Point;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class SpecimenBridgeDialog extends JDialog {
    private final SpecimenService service;
    private final Canvas canvas;
    private int totalCount;
    private int currentIndex = 0;
    private Specimen targetSpecimen;
    private BridgeData originalBridge; // What we loaded from DB
    private BridgeData lastSavedBridge; // For the F1 "Copy Last" feature
    private final String[] directions = {
            "", "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    };

    private JTextField indexField; // For jumping to specific records
    private JLabel totalLabel;

    // Specimen Info Fields (Selectable)
    private JTextField idField, nameField, collectorField;
    private JTextArea origTextField; // JTextArea for long descriptions
    private JTextField locField, rubinField, rt90Field, swerefField, latLongField;
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

    public SpecimenBridgeDialog(Frame owner, SpecimenService service, Canvas canvas) {
        super(owner, "Link Specimen to Locality", false);
        this.service = service;
        this.canvas = canvas;
        this.totalCount = service.getCacheCount(); // Only get the number, not the data

        initUI();
        loadSpecimen(0); // Fetch the first one
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

        // --- F1: COPY LAST SAVED DATA ---
        inputMap.put(KeyStroke.getKeyStroke("F1"), "copyLast");
        actionMap.put("copyLast", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                applyLastSaved();
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

        // Row 1: Search Button (Full Width)
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        openSearchBtn.setPreferredSize(new Dimension(200, 30));
        searchPanel.add(openSearchBtn);
        topPanel.add(searchPanel, BorderLayout.NORTH);

        // Row 2: Detailed Navigation
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

        topPanel.add(navPanel, BorderLayout.SOUTH); // Combined with infoPanel later

        // Specimen Data Display
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
        locField = createPlainField();
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
        c.gridy = 4; infoPanel.add(locField, c);
        c.gridy = 5; infoPanel.add(provinceDistrField, c);

        c.gridy = 6; infoPanel.add(createFocusRow(rubinField, "Focus Rubin", this::focusRubin, "RUBIN"), c);
        c.gridy = 7; infoPanel.add(createFocusRow(rt90Field, "Focus RT90", this::focusRT90, "RT90"), c);
        c.gridy = 8; infoPanel.add(createFocusRow(swerefField, "Focus SWEREF", this::focusSweref, "SWEREF"), c);
        c.gridy = 9; infoPanel.add(createFocusRow(latLongField, "Focus DMS", this::focusLatLong, "DMS"), c);

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
        JPanel distDirPanel = new JPanel(new GridLayout(1, 4, 5, 5));
        distDirPanel.add(new JLabel("Distance (m):"));
        distanceField = new JTextField();
        distDirPanel.add(distanceField);
        distDirPanel.add(new JLabel("Direction:"));
        directionCombo = new JComboBox<>(directions);
        distDirPanel.add(directionCombo);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
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
        closeBtn.addActionListener(e -> dispose());

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
        prevBtn.addActionListener(e -> handleNavigation(currentIndex - 1));
        nextBtn.addActionListener(e -> handleNavigation(currentIndex + 1));

        javax.swing.event.DocumentListener overrideListener = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { checkUpdate(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { checkUpdate(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { checkUpdate(); }

            private void checkUpdate() {
                // Only trigger if the user is typing, not when loadSpecimen() is running
                if (!isAdjusting && targetSpecimen != null) {
                    updateLocalityList();
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
        JPanel p = new JPanel(new BorderLayout(5, 0));
        p.setOpaque(false);
        p.add(field, BorderLayout.CENTER);
        JButton btn = new JButton(btnText);
        btn.setMargin(new Insets(1, 4, 1, 4));
        btn.setFocusable(false);
        btn.addActionListener(e -> action.run());

        // Assign to class variables so we can toggle them later
        if (type.equals("RUBIN")) btnRubin = btn;
        else if (type.equals("RT90")) btnRT90 = btn;
        else if (type.equals("SWEREF")) btnSweref = btn;
        else if (type.equals("DMS")) btnLatLong = btn;

        p.add(btn, BorderLayout.EAST);
        return p;
    }

    private void refreshFromCache() {
        isAdjusting = true;
        this.totalCount = service.getCacheCount();
        totalLabel.setText("/ " + totalCount);
        if (totalCount > 0) {
            loadSpecimen(0);
        } else {
            // Reset fields if cache was cleared but no new hits found
            targetSpecimen = null;
            currentIndex = 0;
            clearFields();
            setTitle("Bridge Tool - Cache Empty");
        }
    }

    private void clearFields() {
        isAdjusting = true;

        // Specimen Info Fields
        idField.setText("");
        nameField.setText("");
        origTextField.setText("");
        collectorField.setText("");
        locField.setText("");
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

        // Show a small loading indicator if you want, but H2 is local and very fast
        Specimen s = service.getSpecimenAt(index);
        if (s != null) {
            this.targetSpecimen = s;
            this.currentIndex = index;
            updateUIFields(s);
        }
    }

    private void handleNavigation(int nextIndex) {
        if (isDirty()) {
            LocalityRecord selected = (LocalityRecord) localityCombo.getSelectedItem();

            // Scenario A: User set a locality and changed something -> Auto Save
            if (selected != null && selected.getId() > 0) {
                saveBridge(); // This validates and saves
                return; // saveBridge will call handleNavigation again once clean, so stop here!
            }
            // Scenario B: User cleared the locality -> Ask if they want to delete the link
            else if (targetSpecimen.getLocalityId() > 0 && (selected == null || selected.getId() <= 0)) {
                deleteBridge();
            }
        }

        // If clean, or if we ignored changes, proceed to load
        loadSpecimen(nextIndex);
        canvas.delLayer("Rubin");
        canvas.delLayer("distance");
        canvas.repaint();
    }

    private void updateUIFields(Specimen s) {
        isAdjusting = true;

        idField.setText(s.getInstitutionCode() + " " + s.getAccessionNo());
        nameField.setText(s.getGenus() + " " + s.getSpecies());
        origTextField.setText(s.getOriginalText());
        collectorField.setText(s.getCollector() + " (" + s.getCollectionCode() + ")      " + String.format("%d-%02d-%02d", s.getYear(), s.getMonth(), s.getDay()));
        locField.setText(s.getSpecimenLocality());
        provinceDistrField.setText(s.getProvince() +", "+ s.getDistrict());
        rubinField.setText(s.getRubin());
        rt90Field.setText("N: " + s.getRiketsN() + " O: " + s.getRiketsO());
        swerefField.setText(s.getSweref());
        latLongField.setText(Coordinates.formatDMS(s.getLatDeg(), s.getLatMin(), s.getLatSec(), s.getLatDir(), s.getLongDeg(), s.getLongMin(), s.getLongSec(), s.getLongDir()));

        // Clear/Update Bridge fields
        overrideDistField.setText(s.getODistrict() != null ? s.getODistrict() : "");
        overrideProvField.setText(s.getOProvince() != null ? s.getOProvince() : "");
        distanceField.setText(s.getDistance() > 0 ? String.valueOf(s.getDistance()) : "");
        directionCombo.setSelectedItem(s.getDirection() != null ? s.getDirection() : "");

        originalBridge = new BridgeData(
                s.getLocalityId(),
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

        setTitle("Link Specimen " + (currentIndex + 1) + " of " + totalCount);
    }

    public void updateLocalityList() {
        String targetDistrict = targetSpecimen.getDistrict();
        String overrideDist = overrideDistField.getText().trim();
        if (!overrideDist.isEmpty()) {
            targetDistrict = overrideDist;
        }

        String targetProvince = targetSpecimen.getProvince();
        String overrideProv = overrideProvField.getText().trim();
        if (!overrideProv.isEmpty()) {
            targetProvince = overrideProv;
        }

        // Clear old items
        localityCombo.removeAllItems();

        localityCombo.addItem(new LocalityRecord(-1, "-- Select a Locality --"));
        // Fetch localities from MySQL for this district
        List<LocalityRecord> localities = service.getLocalitiesInDistrict(targetDistrict, targetProvince);

        // Populate
        for (LocalityRecord l : localities) {
            localityCombo.addItem(l);
            if (l.getId() == targetSpecimen.getLocalityId()) {
                localityCombo.setSelectedItem(l);
            }
        }
    }

    private void saveBridge() {
        if (targetSpecimen == null) return;

        LocalityRecord selectedLoc = (LocalityRecord) localityCombo.getSelectedItem();
        // Check if a valid locality is selected (ignoring the "-- Select --" placeholder)
        if (selectedLoc == null || selectedLoc.getId() <= 0) {
            JOptionPane.showMessageDialog(this, "Please select a target locality.");
            return;
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
                return;
            }
        }

        // Extract Direction
        String dir = (String) directionCombo.getSelectedItem();
        boolean hasDirection = (dir != null && !dir.isEmpty());

        // Co-dependency Check (Both or Neither)
        if (hasDistance != hasDirection) {
            String msg = hasDistance ?
                    "You provided a distance. Please select a Direction." :
                    "You selected a direction. Please provide a Distance (in meters).";
            JOptionPane.showMessageDialog(this, msg, "Incomplete Offset", JOptionPane.WARNING_MESSAGE);
            if (!hasDistance) distanceField.requestFocus();
            else directionCombo.requestFocus();
            return;
        }

        // Collect bridge data for MySQL
        int localityId = selectedLoc.getId();
        String oDist = overrideDistField.getText().trim();
        String oProv = overrideProvField.getText().trim();

        // Save to MySQL
        boolean success = service.linkSpecimenToLocality(targetSpecimen, localityId, oDist, oProv, dist, dir);

        if (success) {
            // --- UPDATE STATE FOR WORKFLOW ---

            // Capture the current UI state into our BridgeData objects
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

            // Auto-advance logic
            if (currentIndex < totalCount - 1) {
                handleNavigation(currentIndex + 1); // Use handleNavigation to ensure clean transitions
            } else {
                dispose();
            }
        } else {
            JOptionPane.showMessageDialog(this, "Error saving link to database.");
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

    private void applyLastSaved() {
        if (lastSavedBridge != null) {
            applyBridgeToUI(lastSavedBridge);
        }
    }

    public void focusLocality() {
        LocalityRecord selected = (LocalityRecord) localityCombo.getSelectedItem();
        if (selected == null || selected.getId() == -1) return;
        // Use your existing wait cursor utility
        // GUI.setCursorWait();
        try {

            // Note: Using Sweref99TMN/E to match your DistanceLayer requirement
            String query = "SELECT SWTMN, SWTME FROM locality WHERE ID = ?";

            try (Connection conn = DBConnection.getConn();
                 PreparedStatement ps = conn.prepareStatement(query)) {

                ps.setInt(1, selected.getId());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int swN = rs.getInt("SWTMN");
                    int swE = rs.getInt("SWTME");
                    Point p = new Point(swE, swN);

                    // Center the map canvas
                    canvas.focus(p);
                    canvas.setCoordinate(p);

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
                                        "distance", p, distanceI, directionS, CoordSystem.SWEREF99TM
                                ));
                            }
                        } catch (NumberFormatException e) {
                            // Silent fail for visualization if number is garbled
                        }
                    }

                    // Repaint to show changes
                    canvas.repaint();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            // GUI.setCursorDefault();
        }
    }

    public void focusRubin() {
        String rubin = targetSpecimen.getRubin();
        if (rubin != null && !rubin.isEmpty()) {
            RubinLayer r = new RubinLayer(rubin, "Rubin", Color.GREEN);
            canvas.delLayer("Rubin");
            canvas.addLayerTop(r);
            Point p = r.getMiddle();
            canvas.focus(p);
        }
    }

    public void focusRT90() {
        String nStr = targetSpecimen.getRiketsN();
        String oStr = targetSpecimen.getRiketsO();
        // Validate that we have strings and they aren't just "0" or empty
        if (nStr != null && oStr != null && !nStr.equals("0") && !nStr.isEmpty()) {
            try {
                double n = Double.parseDouble(nStr);
                double o = Double.parseDouble(oStr);
                if (n == 0 || o == 0) return;

                while (n < 1000000) n *= 10;
                while (o < 1000000) o *= 10;

                Coordinates rt90 = new Coordinates(n, o);
                Coordinates swtm = rt90.convertToSweref99TMFromRT90();
                Point p = new Point((int)swtm.getEast(), (int)swtm.getNorth());

                canvas.focus(p);
                canvas.setCoordinate(p);
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
            Point p = new Point(e, n);
            canvas.focus(p);
            canvas.setCoordinate(p);
        }
    }

    public void focusLatLong() {
        if (targetSpecimen == null) return;

        // Check if we actually have degrees set (not just empty or 0)
        String lat = targetSpecimen.getLatDeg();
        String lon = targetSpecimen.getLongDeg();
        if (lat == null || lat.isEmpty() || lat.equals("0")) return;

        try {
            Coordinates c = new Coordinates(0, 0);
            c.setFromDMS(
                    targetSpecimen.getLatDeg(), targetSpecimen.getLatMin(), targetSpecimen.getLatSec(), targetSpecimen.getLatDir(),
                    targetSpecimen.getLongDeg(), targetSpecimen.getLongMin(), targetSpecimen.getLongSec(), targetSpecimen.getLongDir()
            );

            Coordinates projected = c.toProjected(CoordSystem.SWEREF99TM);
            Point p = new Point((int)projected.getEast(), (int)projected.getNorth());

            canvas.focus(p);
            canvas.setCoordinate(p);
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
        isAdjusting = true;
        distanceField.setText(data.distance);
        directionCombo.setSelectedItem(data.direction);
        overrideDistField.setText(data.oDistrict);
        overrideProvField.setText(data.oProvince);

        // This triggers the locality list reload based on overrides
        updateLocalityList();

        // Find the right ID in the newly loaded list
        for (int i = 0; i < localityCombo.getItemCount(); i++) {
            if (localityCombo.getItemAt(i).getId() == data.localityId) {
                localityCombo.setSelectedIndex(i);
                break;
            }
        }
        isAdjusting = false;
    }

    public void searchLocality() {
        // should also catch province and pass to SearchLocalityDialog
        String selectedText = "";

        // Find which component currently has focus in this dialog
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

        if (focusOwner instanceof javax.swing.text.JTextComponent) {
            javax.swing.text.JTextComponent textComp = (javax.swing.text.JTextComponent) focusOwner;
            String selection = textComp.getSelectedText();
            if (selection != null && !selection.trim().isEmpty()) {
                selectedText = selection.trim();
            }
        }

        // If no text was selected, we can fall back to a default (e.g., the specimen's recorded locality)
        if (selectedText.isEmpty() && targetSpecimen != null) {
            selectedText = targetSpecimen.getSpecimenLocality();
        }

        try {
            StringSelection stringSelection = new StringSelection(selectedText);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
        } catch (Exception e) {
            System.err.println("Clipboard copy failed: " + e.getMessage());
        }

        // Open the existing dialog (assuming 'frame' is accessible or use 'this')
        SearchLocalityDialog d = new SearchLocalityDialog(null, selectedText, canvas);
        d.setVisible(true);
    }

    private void searchOrtReg() {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            System.out.println("Browser not supported on this system.");
            return;
        }

        String placeName = "";
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

        try {
            // Encode the string for a URL (handles spaces and Swedish characters)
            String encodedName = java.net.URLEncoder.encode(placeName, "UTF-8");
            String url = "https://ortnamnsregistret.isof.se/place-names?place-name=" + encodedName;

            Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}