import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class SpecimenSearchDialog extends JDialog {
    private final SpecimenService service;

    // UI Components
    private JComboBox<String> provinceCombo;
    private JTextField districtField;
    private JTextField collectorField;
    private JTextField accessionField;
    private JTextField yearField;
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
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Province
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        searchPanel.add(new JLabel("Province:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        provinceCombo = new JComboBox<>(prov);
        searchPanel.add(provinceCombo, gbc);

        // Row 1: District
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        searchPanel.add(new JLabel("District:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        districtField = new JTextField("*", 15);
        searchPanel.add(districtField, gbc);

        // Row 2: Collector
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        searchPanel.add(new JLabel("Collector:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        collectorField = new JTextField("*", 15);
        searchPanel.add(collectorField, gbc);

        // Row 3: Accession
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        searchPanel.add(new JLabel("Accession No:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        accessionField = new JTextField("*", 15);
        searchPanel.add(accessionField, gbc);

        // Row 4: Year
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        searchPanel.add(new JLabel("Year:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        yearField = new JTextField("*", 15);
        searchPanel.add(yearField, gbc);

        // Row 5: Search Button
        searchButton = new JButton("Search & Cache");
        searchButton.addActionListener(e -> performSearch());
        gbc.gridx = 1; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        searchPanel.add(searchButton, gbc);

        add(searchPanel, BorderLayout.NORTH);

        // --- CENTER: Status Information ---
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setPreferredSize(new Dimension(400, 80));
        statusLabel = new JLabel("Results will replace current H2 cache.");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC));
        centerPanel.add(statusLabel);
        add(centerPanel, BorderLayout.CENTER);

        // --- BOTTOM: Actions ---
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        actionPanel.add(closeButton);
        add(actionPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(searchButton);
    }

    private void performSearch() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        searchButton.setEnabled(false);
        statusLabel.setText("Updating H2 cache...");

        String selectedProv = (String) provinceCombo.getSelectedItem();
        String dist = districtField.getText().trim();
        String coll = collectorField.getText().trim();
        String acc = accessionField.getText().trim();
        String year = yearField.getText().trim();

        // Update your Service method signature to accept these new parameters
        int hitCount = service.refreshCache(selectedProv, dist, coll, acc, year);

        if (hitCount > 0) {
            statusLabel.setText("Found " + hitCount + " specimens. Cache updated.");
            statusLabel.setForeground(new Color(0, 100, 0));
        } else {
            statusLabel.setText("No results found.");
            statusLabel.setForeground(Color.RED);
        }

        /*
        try {
            Settings.setValue("cnr", String.valueOf(0));
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        searchButton.setEnabled(true);
        setCursor(Cursor.getDefaultCursor());
    }
}