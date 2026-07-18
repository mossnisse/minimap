package gis.ui;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.util.List;
import javax.swing.*;

import gis.core.MapCanvas;
import gis.geometry.Extent;
import gis.core.Layer;
import gis.layers.CsvPointLayer;
import gis.layers.GeoPackageLayer;
import gis.layers.ShapeFileLayer;

public class LayerDialog extends JDialog {
	@Serial
	private static final long serialVersionUID = -5204215066837865198L;
	private final Frame ownerFrame;
	private final MapCanvas mapCanvas;
	private final JList<Layer> layerList;
	private final DefaultListModel<Layer> listModel;

	public LayerDialog(Frame aFrame, MapCanvas mapCanvas) {
		super(aFrame, "Layer Manager (Drag to Reorder)", false);
		this.ownerFrame = aFrame;
		this.mapCanvas = mapCanvas;

		setLayout(new BorderLayout());

		// Use a ListModel to handle the data
		listModel = new DefaultListModel<>();

		List<Layer> layers = mapCanvas.getLayerManager().getLayers();
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

		// Only enabled for layers with an editable table (CSV, shapefile, GeoPackage)
		JButton editBtn = new JButton("Edit Data");
		editBtn.setEnabled(false);
		editBtn.addActionListener(e -> editSelected());
		layerList.addListSelectionListener(e -> {
			Layer selected = layerList.getSelectedValue();
			editBtn.setEnabled(selected instanceof CsvPointLayer
					|| selected instanceof gis.layers.EditableTableLayer);
		});

		JButton removeBtn = new JButton("Remove Selected");
		removeBtn.addActionListener(e -> removeSelected());

		JButton close = new JButton("Close");
		close.addActionListener(e -> dispose());

		bottomPanel.add(zoomBtn);
		bottomPanel.add(editBtn);
		bottomPanel.add(removeBtn);
		bottomPanel.add(close);
		add(bottomPanel, BorderLayout.SOUTH);

		// Register the listener to refresh the UI
		final Runnable layersChangedListener = this::refreshListModel;
		mapCanvas.getLayerManager().addLayersChangedListener(layersChangedListener);

		// Crucial: Remove the listener when dialog is closed to avoid memory leaks
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				mapCanvas.getLayerManager().removeLayersChangedListener(layersChangedListener);
			}
		});

		refreshListModel(); // Initial load

		layerList.addMouseListener(new MouseAdapter() {
			// Checkbox logic. Toggle on press: with drag-and-drop enabled,
			// mouseClicked is swallowed whenever the mouse moves a pixel
			// between press and release, making clicks feel unresponsive.
			@Override
			public void mousePressed(MouseEvent e) {
				int index = rowAt(e);
				if (index == -1) return;

				if (e.getX() <= checkboxWidth() + 10) {
					Layer l = listModel.getElementAt(index);
					l.setHidden(!l.isHidden());
					mapCanvas.repaint();
					layerList.repaint();
				}
			}

			// Double click logic
			@Override
			public void mouseClicked(MouseEvent e) {
				int index = rowAt(e);
				if (index == -1) return;

				if (e.getX() > checkboxWidth() + 10 && e.getClickCount() == 2) {
					Layer l = listModel.getElementAt(index);
					new LayerPropertiesDialog(LayerDialog.this, l, mapCanvas).setVisible(true);
					layerList.repaint(); // In case name changed
				}
			}

			/** The row under the cursor, or -1 when the click is outside every cell. */
			private int rowAt(MouseEvent e) {
				int index = layerList.locationToIndex(e.getPoint());
				if (index == -1 || !layerList.getCellBounds(index, index).contains(e.getPoint())) {
					return -1;
				}
				return index;
			}

			private int checkboxWidth() {
				LayerCellRenderer renderer = (LayerCellRenderer) layerList.getCellRenderer();
				return renderer.visibleBox.getPreferredSize().width;
			}
		});

		pack();
		setLocationRelativeTo(aFrame);
		setVisible(true);
	}

	private void refreshListModel() {
		Layer selected = layerList.getSelectedValue(); // Save current selection
		listModel.clear();

		java.util.List<Layer> currentLayers = new java.util.ArrayList<>(mapCanvas.getLayerManager().getLayers());

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
				mapCanvas.setBounds(extent);
				//canvas.focus(extent.getMidlePoint());
				mapCanvas.repaint();
			}
		}
	}

	private void editSelected() {
		Layer selected = layerList.getSelectedValue();
		if (selected instanceof CsvPointLayer csvLayer) {
			CsvEditorDialog.open(ownerFrame, mapCanvas, csvLayer);
		} else if (selected instanceof ShapeFileLayer shapeLayer) {
			TableEditorDialog.open(ownerFrame, mapCanvas, shapeLayer);
		} else if (selected instanceof GeoPackageLayer gpkgLayer) {
			TableEditorDialog.open(ownerFrame, mapCanvas, gpkgLayer);
		}
	}

	private void removeSelected() {
		Layer l = layerList.getSelectedValue();
		if (l != null) {
			int confirm = JOptionPane.showConfirmDialog(this, "Delete layer: " + l.getName() + "?");
			if (confirm == JOptionPane.YES_OPTION) {
				mapCanvas.getLayerManager().delLayer(l);
				listModel.removeElement(l);
				mapCanvas.repaint();
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
				mapCanvas.getLayerManager().setLayerOrder(newDataOrder);

				return true;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return false;
		}
	}
}