package gis.ui;

import gis.coords.CoordSystem;
import gis.core.MapCanvas;

import javax.swing.*;
import java.awt.*;

public class CRSDialog extends JDialog {
    private final MapCanvas mapCanvas;
    private final JComboBox<CoordSystem> crsBox;
    private final JButton okButton, cancelButton;
    private boolean approved = false;

    public CRSDialog(Frame owner, MapCanvas mapCanvas) {
        super(owner, "Set Canvas Projection (CRS)", true); // True for modal
        this.mapCanvas = mapCanvas;

        // Initialize Components
        crsBox = new JComboBox<>(CoordSystem.values());

        // Set current selection based on canvas state
        crsBox.setSelectedItem(mapCanvas.getCRS());

        okButton = new JButton("Apply Changes");
        cancelButton = new JButton("Cancel");

        // Layout
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel formPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        formPanel.add(new JLabel("Select Coordinate System:"));
        formPanel.add(crsBox);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);

        content.add(formPanel, BorderLayout.CENTER);
        content.add(buttonPanel, BorderLayout.SOUTH);

        this.setContentPane(content);

        // Listeners
        okButton.addActionListener(e -> {
            approved = true;
            setVisible(false);
        });

        cancelButton.addActionListener(e -> setVisible(false));

        this.pack();
        this.setLocationRelativeTo(owner);
    }

    public void showDialog() {
        this.setVisible(true);

        if (approved) {
            CoordSystem selected = (CoordSystem) crsBox.getSelectedItem();
            if (selected != null && selected != mapCanvas.getCRS()) {
                applyCRSChange(selected);
            }
        }
        this.dispose();
    }

    private void applyCRSChange(CoordSystem newCS) {
        // Logically set the CRS
        mapCanvas.setCRS(newCS);
    }
}
