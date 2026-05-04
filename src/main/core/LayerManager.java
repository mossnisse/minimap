package main.core;

import main.layers.H2TableLayer;
import main.layers.MYSQLTableLayer;
import main.layers.TNGPolygonFileLayer;
import main.layers.TopowebLayer;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.*;
import java.awt.*;

public class LayerManager {
    private volatile CopyOnWriteArrayList<Layer> layers = new CopyOnWriteArrayList<>();
    private final Canvas canvas;
    private Runnable onLayersChanged;

    LayerManager(Canvas canvas) {
        this.canvas = canvas;
        initialize();
    }

    private void initialize() {
        try {
            MYSQLTableLayer md = new MYSQLTableLayer(canvas);
            md.setColor(Color.BLACK);
            md.setName("LokalDB");
            md.setHidden(false);
            md.setMaxZoomL(40);
            md.setRepaintCallback(() ->
                    // Force the map to redraw on the Swing thread when data arrives
                    SwingUtilities.invokeLater(canvas::repaint)
            );
            addLayerBottom(md);

            H2TableLayer od = new H2TableLayer("ortnamnSWTM", canvas);
            od.setColor(Color.BLACK);
            od.setName("Ortnamnsdb");
            od.setMaxZoomL(5);
            addLayerBottom(od);

            TNGPolygonFileLayer prFile = new TNGPolygonFileLayer("provinserSWEREF99TM.tng", canvas);
            prFile.setColor(Color.BLACK);
            prFile.setName("provinser");
            addLayerBottom(prFile);

            TNGPolygonFileLayer socFile = new TNGPolygonFileLayer("socknarSWEREF99TM.tng", canvas);
            socFile.setColor(Color.RED);
            socFile.setName("socknar");
            addLayerBottom(socFile);

            TopowebLayer tb = new TopowebLayer(canvas);
            tb.setName("TopoWeb");
            addLayerBottom(tb);

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
        canvas.repaint();
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