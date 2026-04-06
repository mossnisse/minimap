import javax.swing.*;
import java.awt.*;

public class SpecimenSearchDialog extends JDialog {
    private final SpecimenService service;

    // UI Components
    private JComboBox<String> provinceCombo;
    private JTextField districtField;
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

        // --- TOP: Search Controls ---
        JPanel searchPanel = new JPanel(new GridBagLayout());
        searchPanel.setBorder(BorderFactory.createTitledBorder("MySQL Query Criteria"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        searchPanel.add(new JLabel("Province:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        provinceCombo = new JComboBox<>(prov);
        searchPanel.add(provinceCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        searchPanel.add(new JLabel("District:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        districtField = new JTextField(15);
        searchPanel.add(districtField, gbc);

        searchButton = new JButton("Search & Cache");
        searchButton.addActionListener(e -> performSearch());
        gbc.gridx = 1; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE;
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

        // Pass the selection to the service
        int hitCount = service.refreshCache(selectedProv, dist);

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