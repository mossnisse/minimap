package main.dialogs;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.util.List;
import javax.swing.*;

import main.geometry.Extent;
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

		List<Layer> layers = canvas.layerManager.getLayers();
		for (int i = layers.size() - 1; i >= 0; i--) {
			listModel.addElement(layers.get(i));
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

		// Register the listener to refresh the UI
		canvas.layerManager.setOnLayersChanged(this::refreshListModel);

		// Crucial: Clear the listener when dialog is closed to avoid memory leaks
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				canvas.layerManager.setOnLayersChanged(null);
			}
		});

		refreshListModel(); // Initial load

		layerList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int index = layerList.locationToIndex(e.getPoint());
				if (index != -1) {
					// Get the renderer component to ask it how wide the checkbox is
					LayerCellRenderer renderer = (LayerCellRenderer) layerList.getCellRenderer();
					Component checkbox = renderer.visibleBox;

					// Add a little padding to the preferred size
					if (e.getX() <= checkbox.getPreferredSize().width + 5) {
						Layer l = listModel.getElementAt(index);
						l.setHidden(!l.isHidden());
						canvas.repaint();
						layerList.repaint();
					}
				}
			}
		});

		pack();
		setLocationRelativeTo(aFrame);
		setVisible(true);
	}

	private void refreshListModel() {
		Layer selected = layerList.getSelectedValue(); // Save current selection
		listModel.clear();

		java.util.List<Layer> currentLayers = new java.util.ArrayList<>(canvas.layerManager.getLayers());

		for (int i = currentLayers.size() - 1; i >= 0; i--) {
			listModel.addElement(currentLayers.get(i));
		}

		if (selected != null) {
			layerList.setSelectedValue(selected, true); // Restore selection
		}
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
				canvas.layerManager.delLayer(l);
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
		public int getSourceActions(JComponent c) {
			return MOVE;
		}

		@Override
		protected Transferable createTransferable(JComponent c) {
			return new StringSelection(String.valueOf(layerList.getSelectedIndex()));
		}

		@Override
		public boolean canImport(TransferSupport support) {
			return support.isDataFlavorSupported(DataFlavor.stringFlavor);
		}

		@Override
		public boolean importData(TransferSupport support) {
			try {
				// Get indices and perform the visual UI move
				int uiFromIndex = Integer.parseInt((String) support.getTransferable().getTransferData(DataFlavor.stringFlavor));
				JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
				int uiToIndex = dl.getIndex();

				if (uiFromIndex == uiToIndex) return false;

				int insertIndex = (uiToIndex > uiFromIndex) ? uiToIndex - 1 : uiToIndex;
				Layer movedLayer = listModel.remove(uiFromIndex);
				listModel.add(insertIndex, movedLayer);

				// Sync the LayerManager
				// listModel is [Top, ... Bottom]
				// LayerManager needs [Bottom, ... Top]
				java.util.List<Layer> newDataOrder = new java.util.ArrayList<>();

				// Loop backwards through the UI list to build the Bottom-to-Top list
				for (int i = listModel.size() - 1; i >= 0; i--) {
					newDataOrder.add(listModel.getElementAt(i));
				}

				// Push to Manager
				canvas.layerManager.setLayerOrder(newDataOrder);

				return true;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return false;
		}
	}
}