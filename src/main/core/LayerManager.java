package main.core;

import main.layers.LayerFactory;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.*;

public class LayerManager {
    private volatile CopyOnWriteArrayList<Layer> layers = new CopyOnWriteArrayList<>();
    private final MapCanvas mapCanvas;
    private Runnable onLayersChanged;

    LayerManager(MapCanvas mapCanvas) {
        this.mapCanvas = mapCanvas;
        initialize();
    }

    private void initialize() {
        try {
            addLayerBottom(LayerFactory.lokalDB(mapCanvas));
            addLayerBottom(LayerFactory.ortnamn(mapCanvas));
            addLayerBottom(LayerFactory.provinser(mapCanvas));
            addLayerBottom(LayerFactory.socknar(mapCanvas));
            addLayerBottom(LayerFactory.topoweb(mapCanvas));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setOnLayersChanged(Runnable listener) {
        this.onLayersChanged = listener;
    }

    public void notifyListeners() {
        if (onLayersChanged != null) {
            // Ensure UI updates happen on the Event Dispatch Thread
            SwingUtilities.invokeLater(onLayersChanged);
        }
        mapCanvas.repaint();
    }

    public void addLayerTop(Layer l) {
        layers.add( l);
        notifyListeners();
    }

    public void addLayerBottom(Layer l) {
        layers.addFirst(l);
        notifyListeners();
    }

    public void delLayer(String name) {
        if (layers.removeIf(l -> l != null && name.equals(l.getName()))) {
            notifyListeners();
        }
    }

    public void delLayer(Layer layerToRemove) {
        if (layers.remove(layerToRemove)) {
            notifyListeners();
        }
    }

    public void setLayerOrder(java.util.List<Layer> newOrder) {
        // Atomic swap. No empty state!
        layers = new CopyOnWriteArrayList<>(newOrder);
        notifyListeners();
    }

    public Layer getLayer(String name) {
        for (Layer l : layers) {
            if (l.getName().equals(name)) { return l; }
        }
        return null;
    }

    public java.util.List<Layer> getLayers() {
        return layers;
    }

    public void invalidateCache() {
        for (Layer l : layers) {
            l.invalidateCache();
        }
    }
}