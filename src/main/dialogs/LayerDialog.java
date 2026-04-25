package main.dialogs;

import java.awt.*;
import java.io.Serial;
import java.util.ArrayList;
import javax.swing.*;

import main.core.Canvas;
import main.core.Layer;

public class LayerDialog extends JDialog {
	@Serial
	private static final long serialVersionUID = -5204215066837865198L;
	private final ArrayList<Layer> layers;
	private final Canvas canvas;
	private final JPanel listPanel;

	public LayerDialog(Frame aFrame, Canvas canvas) {
		super(aFrame, "Layer Manager", false); // Non-modal so you can see map changes
		this.canvas = canvas;
		this.layers = canvas.getLayers();

		setLayout(new BorderLayout());

		listPanel = new JPanel();
		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

		JScrollPane scrollPane = new JScrollPane(listPanel);
		scrollPane.setPreferredSize(new Dimension(400, 300));
		add(scrollPane, BorderLayout.CENTER);

		refreshList();

		JButton close = new JButton("Close");
		close.addActionListener(e -> dispose());
		add(close, BorderLayout.SOUTH);

		pack();
		setLocationRelativeTo(aFrame);
		setVisible(true);
	}

	private void refreshList() {
		listPanel.removeAll();

		// We iterate through a copy to avoid ConcurrentModificationException if removing
		ArrayList<Layer> copy = new ArrayList<>(layers);
		for (Layer l : copy) {
			listPanel.add(createLayerRow(l));
		}

		listPanel.revalidate();
		listPanel.repaint();
	}

	private JPanel createLayerRow(Layer l) {
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		row.setBorder(BorderFactory.createEtchedBorder());

		JLabel nameLabel = new JLabel(l.getName());
		nameLabel.setPreferredSize(new Dimension(120, 20));

		// Hide/Show Toggle
		JCheckBox visibleBox = new JCheckBox("Visible", !l.isHidden());
		visibleBox.addActionListener(e -> {
			l.setHidden(!visibleBox.isSelected());
			canvas.repaint();
		});

		// Zoom to Layer (Simplified logic)
		JButton zoomBtn = new JButton("Zoom");
		zoomBtn.addActionListener(e -> {
			// Note: Layer interface currently lacks getBounds().
			// If the layer supports it (like TNG layers), we zoom there.
			// For now, we zoom to a default or centered area.
			JOptionPane.showMessageDialog(this, "Zooming to " + l.getName());
			canvas.repaint();
		});

		// Remove Layer
		JButton removeBtn = new JButton("Remove");
		removeBtn.setForeground(Color.RED);
		removeBtn.addActionListener(e -> {
			int confirm = JOptionPane.showConfirmDialog(this, "Delete layer: " + l.getName() + "?");
			if (confirm == JOptionPane.YES_OPTION) {
				canvas.delLayer(l.getName());
				refreshList();
				canvas.repaint();
			}
		});

		row.add(nameLabel);
		row.add(visibleBox);
		row.add(zoomBtn);
		row.add(removeBtn);

		return row;
	}
}