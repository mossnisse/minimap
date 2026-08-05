package app.plugin.herbarium;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JLabel;
import javax.swing.SpringLayout;

/** Shared SpringLayout form-row helper for the locality/search dialogs. */
final class SpringForm {
    private SpringForm() {}

    /**
     * Adds a "label + field" row: label at the left edge, field at a fixed column.
     * Pass {@code topAnchor == container} for the first row; otherwise pass the
     * previous row's component to stack this one beneath it. Returns the label
     * so it can anchor the next row.
     */
    static JLabel addRow(String labelText, Component field, Container container,
                         SpringLayout layout, int margin, Component topAnchor) {
        JLabel label = new JLabel(labelText);
        container.add(label);
        container.add(field);

        layout.putConstraint(SpringLayout.WEST, label, 10, SpringLayout.WEST, container);
        if (topAnchor == container) {
            layout.putConstraint(SpringLayout.NORTH, label, margin, SpringLayout.NORTH, container);
        } else {
            layout.putConstraint(SpringLayout.NORTH, label, margin, SpringLayout.SOUTH, topAnchor);
        }
        layout.putConstraint(SpringLayout.WEST, field, 120, SpringLayout.WEST, container);
        layout.putConstraint(SpringLayout.NORTH, field, 0, SpringLayout.NORTH, label);

        return label;
    }
}
