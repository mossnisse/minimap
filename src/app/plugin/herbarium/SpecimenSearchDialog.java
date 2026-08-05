package app.plugin.herbarium;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class SpecimenSearchDialog extends JDialog {
    private final SpecimenService service;
    // UI Components
    private JComboBox<String> provinceCombo;
    private JTextField districtField, collectorField, accessionField, yearField;
    private JTextField localityField, genusField, herbariumField, coordSourceField, precisionField;
    private JCheckBox lackBridgeOnly;
    private JLabel statusLabel;
    private JButton searchButton, closeButton;
    private SwingWorker<Integer, Void> searchWorker;

    private final String[] prov = {"*", "Torne lappmark", "Norrbotten", "Lule lappmark", "Pite lappmark", "Lycksele lappmark", "Åsele lappmark",
            "Ångermanland", "Västerbotten", "Härjedalen", "Medelpad", "Jämtland", "Hälsingland", "Dalarna", "Gästrikland",
            "Uppland", "Värmland", "Västmanland", "Närke", "Södermanland", "Dalsland", "Gotland", "Östergötland", "Bohuslän",
            "Halland", "Öland", "Blekinge", "Skåne", "Småland", "Västergötland"};

    public SpecimenSearchDialog(Dialog owner, SpecimenService service) {
        super(owner, "Search Specimens", false);
        this.service = service;
        initUI();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeIfIdle();
            }
        });
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
        closeButton = new JButton("Close");
        closeButton.addActionListener(e -> closeIfIdle());
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
        closeButton.setEnabled(false);
        statusLabel.setText("Updating H2 cache...");
        statusLabel.setForeground(UIManager.getColor("Label.foreground"));

        // Parse precision safely, and capture all input on the EDT
        int parsedPrecision = 0;
        try {
            parsedPrecision = Integer.parseInt(precisionField.getText().trim());
        } catch (NumberFormatException ignored) {}
        final int precision = parsedPrecision;
        final String province = (String) provinceCombo.getSelectedItem();
        final String district = districtField.getText().trim();
        final String collector = collectorField.getText().trim();
        final String accession = accessionField.getText().trim();
        final String year = yearField.getText().trim();
        final String locality = localityField.getText().trim();
        final String genus = genusField.getText().trim();
        final String herbarium = herbariumField.getText().trim();
        final String coordSource = coordSourceField.getText().trim();
        final boolean lackBridge = lackBridgeOnly.isSelected();

        // The cache refresh copies every hit from MySQL to H2 - keep it off the EDT
        searchWorker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return service.refreshCache(province, district, collector, accession, year,
                        locality, genus, herbarium, coordSource, precision, lackBridge);
            }

            @Override
            protected void done() {
                try {
                    int hitCount = get();
                    if (hitCount > 0) {
                        statusLabel.setText("Found " + hitCount + " specimens. Cache updated.");
                        statusLabel.setForeground(new Color(0, 100, 0));
                    } else {
                        statusLabel.setText("No results found. Cache updated.");
                        statusLabel.setForeground(UIManager.getColor("Label.foreground"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Throwable cause = (e.getCause() != null) ? e.getCause() : e;
                    String message = cause.getMessage();
                    statusLabel.setText("Cache update failed" +
                            ((message == null || message.isBlank()) ? "." : ": " + message));
                    statusLabel.setForeground(Color.RED);
                } finally {
                    searchButton.setEnabled(true);
                    closeButton.setEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                    searchWorker = null;
                }
            }
        };
        searchWorker.execute();
    }

    private void closeIfIdle() {
        if (searchWorker == null || searchWorker.isDone()) {
            dispose();
        }
    }
}
