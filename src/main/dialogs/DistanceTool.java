package main.dialogs;

import main.coords.Coordinate;
import main.core.MapCanvas;
import main.core.Keyboard;
import main.layers.DistanceLayer;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class DistanceTool extends MouseAdapter {
    private final MapCanvas mapCanvas;
    private Coordinate startCoord = null;
    private Coordinate startWGS84 = null;

    public DistanceTool(MapCanvas mapCanvas) {
        this.mapCanvas = mapCanvas;
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

            // Create and add the layer (using your existing DistanceLayer)
            DistanceLayer layer = new DistanceLayer(
                    mapCanvas,
                    "Measurement",
                    startCoord,
                    (int)dist,
                    direction
            );

            mapCanvas.layerManager.addLayerTop(layer);

            // Show result to user
            JOptionPane.showMessageDialog(mapCanvas,
                    String.format("Distance: %.0f m\nDirection: %s (%.1f°)", dist, direction, bearing));

            // Reset for next measurement
            startCoord = null;
            mapCanvas.layerManager.delLayer("Measurement");
            mapCanvas.repaint();
        }
    }
}