import coords.Coordinates;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class SpecimenBridgeDialog extends JDialog {
    private final SpecimenService service;
    private int totalCount;
    private int currentIndex = 0;
    private Specimen targetSpecimen;

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

    private final String[] directions = {
            "", "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    };

    // Memory for the "Copy Last" feature
    private LocalityRecord lastLocality = null;
    private String lastDist = "";
    private String lastDir = "";
    private String lastODist = "";
    private String lastOProv = "";

    public SpecimenBridgeDialog(Frame owner, SpecimenService service) {
        super(owner, "Link Specimen to Locality", false);
        this.service = service;
        this.totalCount = service.getCacheCount(); // Only get the number, not the data

        initUI();
        loadSpecimen(0); // Fetch the first one
        this.pack();
        this.setLocationRelativeTo(owner);
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
                    loadSpecimen(currentIndex + 1);
                }
            }
        });

        // --- LEFT ARROW: PREVIOUS ---
        inputMap.put(KeyStroke.getKeyStroke("LEFT"), "prevSpecimen");
        actionMap.put("prevSpecimen", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (currentIndex > 0) {
                    loadSpecimen(currentIndex - 1);
                }
            }
        });

        // --- F1: COPY LAST SAVED DATA ---
        inputMap.put(KeyStroke.getKeyStroke("F1"), "copyLast");
        actionMap.put("copyLast", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                applyLastBridge();
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
        c.gridy = 6; infoPanel.add(rubinField, c);
        c.gridy = 7; infoPanel.add(rt90Field, c);
        c.gridy = 8; infoPanel.add(swerefField, c);
        c.gridy = 9; infoPanel.add(latLongField, c);

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
        gbc.gridx = 1; gbc.weightx = 1.0;
        localityCombo = new JComboBox<>(); // This will need to be populated based on district
        bridgePanel.add(localityCombo, gbc);

        // Overrides
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
        prevBtn.addActionListener(e -> loadSpecimen(currentIndex - 1));
        nextBtn.addActionListener(e -> loadSpecimen(currentIndex + 1));

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

        isAdjusting = false;

        // Update the Locality ComboBox based on current specimen's district
        updateLocalityList();

        // Update Navigation UI
        indexField.setText(String.valueOf(currentIndex + 1));
        totalLabel.setText("/ " + totalCount);

        // Update Navigation state
        prevBtn.setEnabled(currentIndex > 0);
        nextBtn.setEnabled(currentIndex < totalCount - 1);

        deleteBtn.setEnabled(s.getLocalityId() > 0);
        setTitle("Link Specimen " + (currentIndex + 1) + " of " + totalCount);
    }

    private void updateLocalityList() {
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

        System.out.println("update Locality List: " + targetDistrict + ", " + targetProvince + ", ");
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
        if (selectedLoc == null) {
            JOptionPane.showMessageDialog(this, "Please select a target locality.");
            return;
        }

        // --- Distance and direction Validation ---
        // Extract and Validate Distance
        int dist = 0;
        String distText = distanceField.getText().trim();
        boolean hasDistance = !distText.isEmpty();

        if (hasDistance) {
            try {
                dist = Integer.parseInt(distText);
                if (dist < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this,
                        "Distance must be a positive whole number (meters).",
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

        // Collect bridge data
        int specimenId = targetSpecimen.getId();
        int localityId = selectedLoc.getId();
        String oDist = overrideDistField.getText().trim();
        String oProv = overrideProvField.getText().trim();

        // Save to MySQL
        boolean success = service.linkSpecimenToLocality(specimenId, localityId, oDist, oProv, dist, dir);

        if (success) {
            lastLocality = (LocalityRecord) localityCombo.getSelectedItem();
            lastDist = distanceField.getText().trim();
            lastDir = (String) directionCombo.getSelectedItem();
            lastODist = overrideDistField.getText().trim();
            lastOProv = overrideProvField.getText().trim();
            // Auto-advance to next specimen for high-speed workflow
            if (currentIndex < totalCount - 1) {
                loadSpecimen(currentIndex + 1);
            } else {
                JOptionPane.showMessageDialog(this, "All specimens processed!");
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
            boolean success = service.deleteSpecimenLink(targetSpecimen.getId());
            if (success) {
                // Update the local object state so the UI reflects the change
                targetSpecimen.setLocalityId(0);
                targetSpecimen.setODistrict("");
                targetSpecimen.setOProvince("");
                targetSpecimen.setDistance(0);
                targetSpecimen.setDirection("");

                updateUIFields(targetSpecimen);
                JOptionPane.showMessageDialog(this, "Link removed.");
            } else {
                JOptionPane.showMessageDialog(this, "Error: Could not delete link from MySQL.");
            }
        }
    }

    private void applyLastBridge() {
        if (lastLocality == null) return;
        System.out.println("copy from last bridge");

        isAdjusting = true; // Prevent triggering database refreshes mid-paste

        overrideDistField.setText(lastODist);
        overrideProvField.setText(lastOProv);
        distanceField.setText(lastDist);
        directionCombo.setSelectedItem(lastDir);

        // Refresh the list based on the pasted overrides
        updateLocalityList();

        // Select the correct locality in the newly populated list
        localityCombo.setSelectedItem(lastLocality);

        isAdjusting = false;
    }
}