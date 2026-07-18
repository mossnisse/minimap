package app.plugin;

import java.awt.event.MouseEvent;
import gis.coords.Coordinate;

@FunctionalInterface
public interface MapClickHandler {
    /** @return true when the click was handled and core handling must stop. */
    boolean onClick(MouseEvent event, Coordinate coordinate);
}
