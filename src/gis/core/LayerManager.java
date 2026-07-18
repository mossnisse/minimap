package gis.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.*;

public class LayerManager {
    private volatile CopyOnWriteArrayList<Layer> layers = new CopyOnWriteArrayList<>();
    private final Map<LayerKey<?>, Layer> keyed = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final MapCanvas mapCanvas;

    LayerManager(MapCanvas mapCanvas) {
        this.mapCanvas = mapCanvas;
    }

    public void addLayersChangedListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeLayersChangedListener(Runnable listener) {
        listeners.remove(listener);
    }

    public void notifyListeners() {
        for (Runnable listener : listeners) {
            // Ensure UI updates happen on the Event Dispatch Thread
            SwingUtilities.invokeLater(listener);
        }
        mapCanvas.repaint();
    }

    public void addLayerTop(Layer l) {
        layers.add(l);
        notifyListeners();
    }

    public void addLayerBottom(Layer l) {
        layers.addFirst(l);
        notifyListeners();
    }

    public <T extends Layer> void addLayerTop(LayerKey<T> key, T layer) {
        keyed.put(key, layer);
        addLayerTop(layer);
    }

    public <T extends Layer> void addLayerBottom(LayerKey<T> key, T layer) {
        keyed.put(key, layer);
        addLayerBottom(layer);
    }

    /** The layer currently bound to {@code key}, if it is on the map. */
    public <T extends Layer> Optional<T> get(LayerKey<T> key) {
        return Optional.ofNullable(key.type().cast(keyed.get(key)));
    }

    /** Puts {@code layer} on top, replacing whatever was previously bound to {@code key}. */
    public <T extends Layer> void setOverlay(LayerKey<T> key, T layer) {
        Layer old = keyed.put(key, layer);
        if (old != null) {
            layers.remove(old);
        }
        layers.add(layer);
        notifyListeners();
    }

    /** Removes the layer bound to {@code key}, if any. */
    public void removeOverlay(LayerKey<?> key) {
        Layer old = keyed.remove(key);
        if (old != null && layers.remove(old)) {
            notifyListeners();
        }
    }

    /** Removes a keyed layer regardless of whether it is an overlay or a normal layer. */
    public void removeLayer(LayerKey<?> key) {
        removeOverlay(key);
    }

    public void delLayer(Layer layerToRemove) {
        if (layers.remove(layerToRemove)) {
            // Unbind so a later get() can't return a layer that is no longer on the map
            keyed.values().remove(layerToRemove);
            notifyListeners();
        }
    }

    public void setLayerOrder(java.util.List<Layer> newOrder) {
        // Atomic swap. No empty state!
        layers = new CopyOnWriteArrayList<>(newOrder);
        notifyListeners();
    }

    /**
     * Atomically replaces both the drawing order and all keyed bindings.
     * The supplied list is ordered bottom-to-top.
     */
    public synchronized void replaceAll(java.util.List<Layer> newLayers,
                                        Map<LayerKey<?>, Layer> newKeyed) {
        for (Map.Entry<LayerKey<?>, Layer> entry : newKeyed.entrySet()) {
            if (!entry.getKey().type().isInstance(entry.getValue())) {
                throw new IllegalArgumentException("Layer for key " + entry.getKey()
                        + " is not a " + entry.getKey().type().getSimpleName());
            }
            if (!newLayers.contains(entry.getValue())) {
                throw new IllegalArgumentException("Keyed layer " + entry.getKey() + " is not in layer order");
            }
        }
        layers = new CopyOnWriteArrayList<>(newLayers);
        keyed.clear();
        keyed.putAll(newKeyed);
        notifyListeners();
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
