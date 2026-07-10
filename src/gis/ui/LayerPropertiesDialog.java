package gis.ui;

import gis.coords.CoordSystem;
import gis.core.MapCanvas;
import gis.core.Layer;

import javax.swing.*;
import java.awt.*;

public class LayerPropertiesDialog extends JDialog {
    private final Layer layer;
    private final MapCanvas mapCanvas;

    private JTextField nameField;
    private JButton colorButton;
    private JComboBox<CoordSystem> crsBox;
    private JSpinner minZoomSpin, maxZoomSpin;
    private Color selectedColor;

    public LayerPropertiesDialog(Window owner, Layer layer, MapCanvas mapCanvas) {
        super(owner, "Properties: " + layer.getName(), ModalityType.APPLICATION_MODAL);
        this.layer = layer;
        this.mapCanvas = mapCanvas;
        this.selectedColor = layer.getColor();

        initComponents();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Row 0: Name
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Layer Name:"), gbc);
        gbc.gridx = 1;
        nameField = new JTextField(layer.getName(), 15);
        panel.add(nameField, gbc);

        // Row 1: Color
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Layer Color:"), gbc);
        gbc.gridx = 1;
        colorButton = new JButton(" ");
        colorButton.setBackground(selectedColor);
        colorButton.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Pick Layer Color", selectedColor);
            if (c != null) {
                selectedColor = c;
                colorButton.setBackground(c);
            }
        });
        panel.add(colorButton, gbc);

        // Row 2: CRS
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Coordinate System:"), gbc);
        gbc.gridx = 1;
        crsBox = new JComboBox<>(CoordSystem.values());
        crsBox.setSelectedItem(layer.getCRS());
        panel.add(crsBox, gbc);

        // Row 3 & 4: Zoom Range
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Min Zoom Level:"), gbc);
        gbc.gridx = 1;
        minZoomSpin = new JSpinner(new SpinnerNumberModel(layer.getMinZoom(), 0, 100, 1));
        panel.add(minZoomSpin, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        panel.add(new JLabel("Max Zoom Level:"), gbc);
        gbc.gridx = 1;
        maxZoomSpin = new JSpinner(new SpinnerNumberModel(layer.getMaxZoom(), 0, 100, 1));
        panel.add(maxZoomSpin, gbc);

        // Row 5: Buttons
        JPanel btnPanel = new JPanel();
        JButton okBtn = new JButton("Apply");
        okBtn.addActionListener(e -> applyChanges());
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);

        add(panel, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
        this.getRootPane().setDefaultButton(okBtn);
    }

    private void applyChanges() {

        if (nameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Layer name cannot be empty.");
            return;
        }
        layer.setName(nameField.getText());
        layer.setColor(selectedColor);
        layer.setCRS((CoordSystem) crsBox.getSelectedItem());

        int min = (Integer) minZoomSpin.getValue();
        int max = (Integer) maxZoomSpin.getValue();
        if (max != 0 && min > max) {
            JOptionPane.showMessageDialog(this, "Min Zoom cannot be greater than Max Zoom.");
            return;
        }
        layer.setMinZoomL(min);
        layer.setMaxZoomL(max);

        mapCanvas.getLayerManager().notifyListeners();
        dispose();
    }
}
