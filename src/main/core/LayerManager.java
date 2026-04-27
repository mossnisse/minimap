package main.core;

import main.layers.H2TableLayer;
import main.layers.MYSQLTableLayer;
import main.layers.TNGPolygonFileLayer;
import main.layers.TopowebLayer;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;

public class LayerManager {
    private final ArrayList<Layer> layers;
    private final Canvas canvas;

    LayerManager(Canvas canvas) {
        layers = new ArrayList<Layer>();
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
        synchronized (layers) {
            layers.addFirst(l);
        }
        canvas.repaint();
    }

    public void addLayerBottom(Layer l) {
        synchronized (layers) {
            layers.add(l);
        }
        canvas.repaint();
    }

    public void delLayer(String name) {
        synchronized (layers) {
            layers.removeIf(l -> l != null && name.equals(l.getName()));
        }
    }

    public Layer getLayer(String name) {
        for(Layer l: layers) {
            if (l.getName().equals(name)) {
                return l;
            }
        }
        return null;
    }

    public ArrayList<Layer> getLayers() {
        return layers;
    }
}