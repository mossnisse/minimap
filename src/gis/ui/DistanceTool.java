package gis.ui;

import gis.coords.Coordinate;
import gis.core.LayerKey;
import gis.core.MapCanvas;
import gis.core.Keyboard;
import gis.layers.DistanceLayer;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class DistanceTool extends MouseAdapter {
    private final MapCanvas mapCanvas;
    private final LayerKey<DistanceLayer> overlayKey;
    private Coordinate startCoord = null;
    private Coordinate startWGS84 = null;

    public DistanceTool(MapCanvas mapCanvas, LayerKey<DistanceLayer> overlayKey) {
        this.mapCanvas = mapCanvas;
        this.overlayKey = overlayKey;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (!Keyboard.isKeyDown(KeyEvent.VK_G) && startCoord == null) return;
        // Convert screen pixel to map coordinate using your translatePoint method
        Coordinate currentCoord = mapCanvas.translatePoint(e.getPoint());
        Coordinate currentWgs84 = mapCanvas.getCRS().toWGS84(currentCoord);
        if (currentWgs84 == null) return;

        if (startCoord == null) {
            // First click: drop a marker or just save the point
            startCoord = currentCoord;
            startWGS84 = currentWgs84;
            mapCanvas.setCoordinate(startCoord); // Use your existing marker logic to show start
            System.out.println("Start point set. Click destination.");
        } else {
            // Second click: Calculate distance and bearing
            double dist = startWGS84.distanceWGS84(currentWgs84);
            double bearing = startWGS84.getBearingWGS84(currentWgs84);
            String direction = Coordinate.getDirectionFromBearing(bearing);

            // Show the measured vector while the result dialog is open
            DistanceLayer layer = new DistanceLayer(
                    mapCanvas,
                    "Distance",
                    startCoord,
                    (int)dist,
                    direction
            );

            mapCanvas.getLayerManager().setOverlay(overlayKey, layer);

            // Show result to user
            JOptionPane.showMessageDialog(mapCanvas,
                    String.format("Distance: %.0f m\nDirection: %s (%.1f°)", dist, direction, bearing));

            // Reset for next measurement
            startCoord = null;
            mapCanvas.getLayerManager().removeOverlay(overlayKey);
            mapCanvas.repaint();
        }
    }
}
