package main.dialogs;

import javax.swing.*;
import java.awt.*;

public class SpecimenSearchDialog extends JDialog {
    private final SpecimenService service;
    // UI Components
    private JComboBox<String> provinceCombo;
    private JTextField districtField, collectorField, accessionField, yearField;
    private JTextField localityField, genusField, herbariumField, coordSourceField, precisionField;
    private JCheckBox lackBridgeOnly;
    private JLabel statusLabel;
    private JButton searchButton;

    private final String[] prov = {"*", "Torne lappmark", "Norrbotten", "Lule lappmark", "Pite lappmark", "Lycksele lappmark", "Åsele lappmark",
            "Ångermanland", "Västerbotten", "Härjedalen", "Medelpad", "Jämtland", "Hälsingland", "Dalarna", "Gästrikland",
            "Uppland", "Värmland", "Västmanland", "Närke", "Södermanland", "Dalsland", "Gotland", "Östergötland", "Bohuslän",
            "Halland", "Öland", "Blekinge", "Skåne", "Småland", "Västergötland"};

    public SpecimenSearchDialog(Dialog owner, SpecimenService service) {
        super(owner, "Search Specimens", false);
        this.service = service;
        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        JPanel searchPanel = new JPanel(new GridBagLayout());
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search Criteria"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 8, 2, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // --- Column 1 Labels & Column 2 Fields ---
        addLabelField(searchPanel, gbc, 0, "Province:", provinceCombo = new JComboBox<>(prov));
        addLabelField(searchPanel, gbc, 1, "District:", districtField = new JTextField("*", 15));
        addLabelField(searchPanel, gbc, 2, "Locality Name:", localityField = new JTextField("*", 15));
        addLabelField(searchPanel, gbc, 3, "Genus:", genusField = new JTextField("*", 15));
        addLabelField(searchPanel, gbc, 4, "Collector:", collectorField = new JTextField("*", 15));
        addLabelField(searchPanel, gbc, 5, "Accession No:", accessionField = new JTextField("*", 15));
        addLabelField(searchPanel, gbc, 6, "Year:", yearField = new JTextField("*", 15));
        addLabelField(searchPanel, gbc, 7, "Herbarium (Code):", herbariumField = new JTextField("*", 15));
        addLabelField(searchPanel, gbc, 8, "Coord Source:", coordSourceField = new JTextField("*", 15));
        addLabelField(searchPanel, gbc, 9, "Precision > (m):", precisionField = new JTextField("0", 15));

        // Lack Bridge Checkbox
        gbc.gridx = 1; gbc.gridy = 10;
        lackBridgeOnly = new JCheckBox("Only show specimens lacking locality bridge");
        searchPanel.add(lackBridgeOnly, gbc);

        // Search Button
        searchButton = new JButton("Search & Cache");
        searchButton.addActionListener(e -> performSearch());
        gbc.gridy = 11; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        searchPanel.add(searchButton, gbc);

        add(searchPanel, BorderLayout.NORTH);

        statusLabel = new JLabel("Results will replace current H2 cache.", SwingConstants.CENTER);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC));
        add(statusLabel, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        actionPanel.add(closeButton);
        add(actionPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(searchButton);
    }

    private void addLabelField(JPanel panel, GridBagConstraints gbc, int y, String label, JComponent field) {
        gbc.gridy = y;
        gbc.gridx = 0; gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(field, gbc);
    }

    private void performSearch() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        searchButton.setEnabled(false);
        statusLabel.setText("Updating H2 cache...");

        // Parse precision safely
        int precision = 0;
        try {
            precision = Integer.parseInt(precisionField.getText().trim());
        } catch (NumberFormatException ignored) {}

        // Call refreshed service method
        int hitCount = service.refreshCache(
                (String) provinceCombo.getSelectedItem(),
                districtField.getText().trim(),
                collectorField.getText().trim(),
                accessionField.getText().trim(),
                yearField.getText().trim(),
                localityField.getText().trim(),
                genusField.getText().trim(),
                herbariumField.getText().trim(),
                coordSourceField.getText().trim(),
                precision,
                lackBridgeOnly.isSelected()
        );

        if (hitCount > 0) {
            statusLabel.setText("Found " + hitCount + " specimens. Cache updated.");
            statusLabel.setForeground(new Color(0, 100, 0));
        } else {
            statusLabel.setText("No results found.");
            statusLabel.setForeground(Color.RED);
        }

        searchButton.setEnabled(true);
        setCursor(Cursor.getDefaultCursor());
    }
}