package main.dialogs;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.util.ArrayList;
import javax.swing.*;

import main.coords.Extent;
import main.core.Canvas;
import main.core.Layer;

public class LayerDialog extends JDialog {
	@Serial
	private static final long serialVersionUID = -5204215066837865198L;
	private final Canvas canvas;
	private final JList<Layer> layerList;
	private final DefaultListModel<Layer> listModel;

	public LayerDialog(Frame aFrame, Canvas canvas) {
		super(aFrame, "Layer Manager (Drag to Reorder)", false);
		this.canvas = canvas;

		setLayout(new BorderLayout());

		// Use a ListModel to handle the data
		listModel = new DefaultListModel<>();
		ArrayList<Layer> layers = canvas.getLayers();
		for (Layer l : layers) {
			listModel.addElement(l);
		}

		layerList = new JList<>(listModel);
		layerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		layerList.setCellRenderer(new LayerCellRenderer());

		// Enable Drag and Drop
		layerList.setDragEnabled(true);
		layerList.setDropMode(DropMode.INSERT);
		layerList.setTransferHandler(new LayerTransferHandler());

		JScrollPane scrollPane = new JScrollPane(layerList);
		scrollPane.setPreferredSize(new Dimension(450, 350));
		add(scrollPane, BorderLayout.CENTER);

		// Control Buttons at Bottom
		JPanel bottomPanel = new JPanel();
		JButton zoomBtn = new JButton("Zoom to Selected");
		zoomBtn.addActionListener(e -> zoomToSelected());

		JButton removeBtn = new JButton("Remove Selected");
		removeBtn.addActionListener(e -> removeSelected());

		JButton close = new JButton("Close");
		close.addActionListener(e -> dispose());

		bottomPanel.add(zoomBtn);
		bottomPanel.add(removeBtn);
		bottomPanel.add(close);
		add(bottomPanel, BorderLayout.SOUTH);

		layerList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int index = layerList.locationToIndex(e.getPoint());
				if (index != -1) {
					Layer l = listModel.getElementAt(index);
					// If the click was roughly in the checkbox area (left side)
					if (e.getX() < 30) {
						l.setHidden(!l.isHidden());
						canvas.repaint();
						layerList.repaint(); // Redraw the "stamp"
					}
				}
			}
		});

		pack();
		setLocationRelativeTo(aFrame);
		setVisible(true);
	}

	private void zoomToSelected() {
		Layer l = layerList.getSelectedValue();
		if (l != null) {
			Extent extent = l.getBoundaries();
			if (extent != null) {
				canvas.setBounds(extent);
				//canvas.focus(extent.getMidlePoint());
				canvas.repaint();
			}
		}
	}


	private void removeSelected() {
		Layer l = layerList.getSelectedValue();
		if (l != null) {
			int confirm = JOptionPane.showConfirmDialog(this, "Delete layer: " + l.getName() + "?");
			if (confirm == JOptionPane.YES_OPTION) {
				canvas.delLayer(l.getName());
				listModel.removeElement(l);
				canvas.repaint();
			}
		}
	}

	// Custom Renderer to show the Checkbox and Name inside the List
	private static class LayerCellRenderer extends JPanel implements ListCellRenderer<Layer> {
		private final JCheckBox visibleBox = new JCheckBox();
		private final JLabel nameLabel = new JLabel();

		public LayerCellRenderer() {
			setOpaque(true);
			setLayout(new FlowLayout(FlowLayout.LEFT));
			add(visibleBox);
			add(nameLabel);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends Layer> list, Layer value,
		                                              int index, boolean isSelected, boolean cellHasFocus) {
			visibleBox.setSelected(!value.isHidden());
			nameLabel.setText(value.getName());

			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			return this;
		}
	}

	// Handles the actual reordering logic
	private class LayerTransferHandler extends TransferHandler {
		@Override
		public int getSourceActions(JComponent c) { return MOVE; }

		@Override
		protected Transferable createTransferable(JComponent c) {
			return new StringSelection(String.valueOf(layerList.getSelectedIndex()));
		}

		@Override
		public boolean canImport(TransferSupport support) { return support.isDataFlavorSupported(DataFlavor.stringFlavor); }

		@Override
		public boolean importData(TransferSupport support) {
			try {
				int fromIndex = Integer.parseInt((String) support.getTransferable().getTransferData(DataFlavor.stringFlavor));
				JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
				int toIndex = dl.getIndex();

				if (fromIndex == toIndex) return false;

				// Calculate the correct index ONCE
				int insertIndex = toIndex;
				if (insertIndex > fromIndex) insertIndex--;

				// Update UI Data Model
				Layer movedLayer = listModel.remove(fromIndex);
				listModel.add(insertIndex, movedLayer);

				// Sync with Canvas Layers safely
				synchronized (canvas.getLayers()) {
					ArrayList<Layer> canvasLayers = canvas.getLayers();
					canvasLayers.remove(fromIndex);
					canvasLayers.add(insertIndex, movedLayer); // Use the pre-calculated insertIndex
				}

				canvas.repaint();
				return true;
			} catch (Exception e) { e.printStackTrace(); }
			return false;
		}
	}
}