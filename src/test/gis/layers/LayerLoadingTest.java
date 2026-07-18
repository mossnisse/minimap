package test.gis.layers;

import gis.coords.CoordSystem;
import gis.coords.Coordinate;
import gis.core.MapCanvas;
import gis.geometry.Extent;
import gis.layers.GPXFileLayer;
import gis.layers.TNGPointFileLayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LayerLoadingTest {
    @TempDir
    Path tempDir;

    @Test
    void malformedGpxIsRejectedBeforeAUsableLayerIsReturned() throws Exception {
        Path gpx = tempDir.resolve("broken.gpx");
        Files.writeString(gpx, """
                <gpx><wpt lat="59.3" lon="18.0"/><wpt lat="bad" lon="18.1"/></gpx>
                """);

        assertThrows(IOException.class, () -> new GPXFileLayer(gpx.toString(), new MapCanvas()));
    }

    @Test
    void validGpxReprojectsItsCachedCoordinatesWhenCanvasCrsChanges() throws Exception {
        Path gpx = tempDir.resolve("valid.gpx");
        Files.writeString(gpx, "<gpx><wpt lat=\"59.3\" lon=\"18.0\"/></gpx>");
        MapCanvas canvas = new MapCanvas();
        GPXFileLayer layer = new GPXFileLayer(gpx.toString(), canvas);
        double swerefEast = layer.getBoundaries().c1.getEast();

        canvas.getLayerManager().addLayerTop(layer);
        canvas.setCRS(CoordSystem.WEB_MERCATOR);

        assertNotEquals(swerefEast, layer.getBoundaries().c1.getEast());
        Coordinate expected = CoordSystem.WGS84.convertTo(new Coordinate(59.3, 18.0), CoordSystem.WEB_MERCATOR);
        assertEquals(expected.getEast(), layer.getBoundaries().c1.getEast(), 0.001);
    }

    @Test
    void inMemoryPointLayerRetainsItsSourceCrsAcrossCanvasChanges() {
        MapCanvas canvas = new MapCanvas();
        Coordinate source = new Coordinate(59.3, 18.0);
        TNGPointFileLayer layer = new TNGPointFileLayer(
                new ArrayList<>(List.of(source)), new ArrayList<>(List.of("A")),
                "results", canvas, CoordSystem.WGS84);
        canvas.getLayerManager().addLayerTop(layer);
        Extent before = layer.getBoundaries();

        canvas.setCRS(CoordSystem.WEB_MERCATOR);

        Coordinate expected = CoordSystem.WGS84.convertTo(source, CoordSystem.WEB_MERCATOR);
        assertNotEquals(before.c1.getEast(), layer.getBoundaries().c1.getEast());
        assertEquals(expected.getEast(), layer.getBoundaries().c1.getEast(), 0.001);
        assertEquals(expected.getNorth(), layer.getBoundaries().c1.getNorth(), 0.001);
    }
}
