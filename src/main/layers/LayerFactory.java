package main.layers;

import java.awt.Color;
import javax.swing.SwingUtilities;

import main.core.MapCanvas;

/**
 * Single source of truth for how the built-in layers are configured.
 * Both the startup set ({@code LayerManager}) and the "Layers" menu ({@code GUI})
 * build their layers here so the two paths can't drift apart.
 */
public final class LayerFactory {
    private LayerFactory() {}

    public static MYSQLTableLayer lokalDB(MapCanvas canvas) {
        MYSQLTableLayer md = new MYSQLTableLayer(canvas);
        md.setColor(Color.BLACK);
        md.setName("LokalDB");
        md.setHidden(false);
        md.setMaxZoomL(40);
        // Force the map to redraw on the Swing thread when data arrives
        md.setRepaintCallback(() -> SwingUtilities.invokeLater(canvas::repaint));
        return md;
    }

    public static H2TableLayer ortnamn(MapCanvas canvas) {
        H2TableLayer od = new H2TableLayer("ortnamnSWTM", canvas);
        od.setColor(Color.BLACK);
        od.setName("Ortnamnsdb");
        od.setMaxZoomL(5);
        return od;
    }

    public static TNGPolygonFileLayer provinser(MapCanvas canvas) {
        TNGPolygonFileLayer l = new TNGPolygonFileLayer("provinserSWEREF99TM.tng", canvas);
        l.setColor(Color.BLACK);
        l.setName("provinser");
        return l;
    }

    public static TNGPolygonFileLayer socknar(MapCanvas canvas) {
        TNGPolygonFileLayer l = new TNGPolygonFileLayer("socknarSWEREF99TM.tng", canvas);
        l.setColor(Color.RED);
        l.setName("socknar");
        return l;
    }

    public static TopowebLayer topoweb(MapCanvas canvas) {
        TopowebLayer tb = new TopowebLayer(canvas);
        tb.setName("TopoWeb");
        return tb;
    }

    public static OSMLayer osm(MapCanvas canvas) {
        OSMLayer osm = new OSMLayer(canvas);
        osm.setName("Open Street Map");
        return osm;
    }
}
