package test.app.project;

import app.project.LayerSerializer;
import app.project.ProjectManager;
import gis.coords.CoordSystem;
import gis.core.Layer;
import gis.core.LayerKey;
import gis.core.MapCanvas;
import gis.core.Settings;
import gis.geometry.Extent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProjectFoundationTest {
    @TempDir Path tempDir;
    private final File originalSettings = Settings.activeFile();

    @AfterEach
    void restoreSettings() {
        Settings.useFile(originalSettings);
    }

    @Test
    void settingsSwitchFilesWithoutLeakingValues() throws Exception {
        File first = tempDir.resolve("one/settings.txt").toFile();
        File second = tempDir.resolve("two/settings.txt").toFile();
        Settings.useFile(first);
        Settings.setValue("value", "one");

        Settings.useFile(second);
        assertNull(Settings.getValue("value"));
        Settings.setValue("value", "two");

        Settings.useFile(first);
        assertEquals("one", Settings.getValue("value"));
        assertTrue(first.isFile());
        assertTrue(second.isFile());
    }

    @Test
    void replacingLayersAlsoClearsOldKeyBindings() {
        MapCanvas canvas = new MapCanvas();
        LayerKey<DummyLayer> oldKey = LayerKey.of("old", DummyLayer.class);
        LayerKey<DummyLayer> newKey = LayerKey.of("new", DummyLayer.class);
        DummyLayer oldLayer = new DummyLayer("old");
        DummyLayer newLayer = new DummyLayer("new");
        canvas.getLayerManager().addLayerTop(oldKey, oldLayer);

        canvas.getLayerManager().replaceAll(List.of(newLayer), Map.of(newKey, newLayer));

        assertTrue(canvas.getLayerManager().get(oldKey).isEmpty());
        assertSame(newLayer, canvas.getLayerManager().get(newKey).orElseThrow());
        assertEquals(List.of(newLayer), canvas.getLayerManager().getLayers());
    }

    @Test
    void defaultManifestsExplicitlyDistinguishHerbarium() throws Exception {
        Path core = tempDir.resolve("core.layers");
        Path herbarium = tempDir.resolve("herbarium.layers");
        LayerSerializer.writeDefault(core, false);
        LayerSerializer.writeDefault(herbarium, true);

        assertFalse(Files.readString(core).contains("LOKAL_DB"));
        assertTrue(Files.readString(herbarium).contains("LOKAL_DB"));
    }

    @Test
    void projectNamesRejectTraversalAndInvalidWindowsNames() {
        assertDoesNotThrow(() -> ProjectManager.validateName("North Sweden"));
        assertThrows(IllegalArgumentException.class, () -> ProjectManager.validateName("../outside"));
        assertThrows(IllegalArgumentException.class, () -> ProjectManager.validateName("bad:name"));
        assertThrows(IllegalArgumentException.class, () -> ProjectManager.validateName("trailing."));
    }

    private static final class DummyLayer extends Layer {
        DummyLayer(String name) { super(name, false, CoordSystem.WGS84); }
        @Override public Extent getBoundaries() { return null; }
        @Override public void invalidateCache() {}
        @Override public void draw(Graphics2D g2d, double xShift, double xScale,
                                   double yShift, double yScale, Extent bounds) {}
    }
}
