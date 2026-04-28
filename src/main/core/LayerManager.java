package main.core;

import main.layers.H2TableLayer;
import main.layers.MYSQLTableLayer;
import main.layers.TNGPolygonFileLayer;
import main.layers.TopowebLayer;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class LayerManager {
    private final CopyOnWriteArrayList<Layer> layers = new CopyOnWriteArrayList<>();
    private final Canvas canvas;

    LayerManager(Canvas canvas) {
        //layers = new CopyOnWriteArrayList<Layer>();
        this.canvas = canvas;
        initialize();
    }

    private void initialize() {
        try {
            MYSQLTableLayer md = new MYSQLTableLayer();
            md.setColor(Color.BLACK);
            md.setName("LokalDB");
            md.setHidden(false);
            md.setMaxZoomL(40);
            md.setRepaintCallback(() ->
                    // Force the map to redraw on the Swing thread when data arrives
                    SwingUtilities.invokeLater(() -> canvas.repaint())
            );
            addLayerBottom(md);

            H2TableLayer od = new H2TableLayer("ortnamnSWTM");
            od.setColor(Color.BLACK);
            od.setName("Ortnamnsdb");
            od.setHidden(false);
            od.setMaxZoomL(5);
            addLayerBottom(od);

            TNGPolygonFileLayer prFile = new TNGPolygonFileLayer("provinserSWEREF99TM.tng");
            prFile.setColor(Color.BLACK);
            prFile.setName("provinser");
            addLayerBottom(prFile);

            TNGPolygonFileLayer socFile = new TNGPolygonFileLayer("socknarSWEREF99TM.tng");
            socFile.setColor(Color.RED);
            socFile.setName("socknar");
            socFile.setHidden(false);
            addLayerBottom(socFile);

            TopowebLayer tb = new TopowebLayer(canvas);
            tb.setName("TopoWeb");
            tb.setHidden(false);
            addLayerBottom(tb);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addLayerTop(Layer l) {
        layers.add(layers.size(), l);
        canvas.repaint();
    }

    public void addLayerBottom(Layer l) {
        layers.add(0, l);
        canvas.repaint();
    }

    public void delLayer(String name) {
        layers.removeIf(l -> l != null && name.equals(l.getName()));
    }

    public Layer getLayer(String name) {
        for (Layer l : layers) {
            if (l.getName().equals(name)) { return l; }
        }
        return null;
    }

    public Iterable<Layer> getLayers() {
        return layers;
    }

    public void setLayerOrder(java.util.List<Layer> newOrder) {
        // Clear current and add in the order provided
        layers.clear();
        // Assuming newOrder is provided from Bottom-to-Top (Data Order)
        layers.addAll(newOrder);
        canvas.repaint();
    }
}