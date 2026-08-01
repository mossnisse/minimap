package test.gis.layers;

import gis.core.MapCanvas;
import gis.layers.TopowebLayer;
import app.project.LayerSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.*;

class TopowebLayerTest {
    @TempDir Path tempDir;

    @Test
    void defaultProviderRemainsTheSluProxy() {
        InspectableTopowebLayer layer = new InspectableTopowebLayer(new MapCanvas());

        assertEquals(TopowebLayer.Provider.SLU, layer.getProvider());
        assertTrue(layer.url(7, 12, 34).startsWith("http://hades.slu.se/"));
        assertTrue(layer.request(layer.url(7, 12, 34)).headers()
                .firstValue("Authorization").isEmpty());
    }

    @Test
    void officialProviderUsesHttpsAndBasicAuthentication() {
        InspectableTopowebLayer layer = new InspectableTopowebLayer(new MapCanvas(),
                TopowebLayer.Provider.LANTMATERIET, "map-user", "map-password");

        String url = layer.url(7, 12, 34);
        HttpRequest request = layer.request(url);
        String expected = "Basic " + Base64.getEncoder().encodeToString(
                "map-user:map-password".getBytes(StandardCharsets.UTF_8));

        assertTrue(url.startsWith("https://maps.lantmateriet.se/open/topowebb-ccby/v1/wmts?"));
        assertTrue(url.contains("TILEMATRIXSET=3006&TILEMATRIX=7&TILEROW=34&TILECOL=12"));
        assertEquals(expected, request.headers().firstValue("Authorization").orElseThrow());
        assertEquals(9, layer.zoom(1.0));
    }

    @Test
    void officialProviderBuildsWithoutCredentialsSoSavedProjectsStillRestore() {
        InspectableTopowebLayer layer = new InspectableTopowebLayer(new MapCanvas(),
                TopowebLayer.Provider.LANTMATERIET, null, null);

        assertFalse(layer.hasCredentials());
        assertTrue(layer.request(layer.url(7, 12, 34)).headers().firstValue("Authorization").isEmpty());
    }

    @Test
    void setCredentialsStillRejectsAHalfEnteredAccount() {
        InspectableTopowebLayer layer = new InspectableTopowebLayer(new MapCanvas(),
                TopowebLayer.Provider.LANTMATERIET, "map-user", "map-password");

        assertThrows(IllegalArgumentException.class, () -> layer.setCredentials("map-user", ""));
        assertTrue(layer.hasCredentials(), "a rejected update must not clear the working account");
    }

    @Test
    void nonAuthErrorsAreStillReportedWhileTheCredentialPromptIsOpen() throws Exception {
        InspectableTopowebLayer layer = new InspectableTopowebLayer(new MapCanvas(),
                TopowebLayer.Provider.LANTMATERIET, "map-user", "map-password");
        CountDownLatch prompted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        layer.setHttpErrorHandler(status -> {
            prompted.countDown();
            try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        });

        layer.error(401);
        assertTrue(prompted.await(2, TimeUnit.SECONDS));
        // The prompt is still open on the EDT; a server fault is not a duplicate of it.
        assertTrue(layer.reported(500));
        assertFalse(layer.reported(403), "the auth failure being handled stays suppressed");
        release.countDown();
    }

    @Test
    void credentialsCanBeReplacedForRetry() {
        InspectableTopowebLayer layer = new InspectableTopowebLayer(new MapCanvas(),
                TopowebLayer.Provider.LANTMATERIET, "old-user", "old-password");

        layer.setCredentials("new-user", "new-password");

        String expected = "Basic " + Base64.getEncoder().encodeToString(
                "new-user:new-password".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, layer.request(layer.url(7, 12, 34)).headers()
                .firstValue("Authorization").orElseThrow());
    }

    @Test
    void concurrentAuthenticationFailuresProduceOneUiNotification() throws Exception {
        InspectableTopowebLayer layer = new InspectableTopowebLayer(new MapCanvas(),
                TopowebLayer.Provider.LANTMATERIET, "map-user", "map-password");
        CountDownLatch notified = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger receivedStatus = new AtomicInteger();
        layer.setHttpErrorHandler(status -> {
            calls.incrementAndGet();
            receivedStatus.set(status);
            notified.countDown();
        });

        layer.error(401);
        layer.error(401);

        assertTrue(notified.await(2, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals(1, calls.get());
        assertEquals(401, receivedStatus.get());
    }

    @Test
    void projectManifestPreservesTheProvider() throws Exception {
        MapCanvas canvas = new MapCanvas();
        canvas.getLayerManager().addLayerBottom(new TopowebLayer(canvas,
                TopowebLayer.Provider.LANTMATERIET, "map-user", "map-password"));
        Path manifest = tempDir.resolve("layers.txt");

        LayerSerializer.write(manifest, canvas.getLayerManager());

        assertTrue(Files.readString(manifest).contains("TOPOWEB_LANTMATERIET"));
        assertFalse(Files.readString(manifest).contains("map-password"));
    }

    private static final class InspectableTopowebLayer extends TopowebLayer {
        InspectableTopowebLayer(MapCanvas canvas) {
            super(canvas);
        }

        InspectableTopowebLayer(MapCanvas canvas, Provider provider, String username, String password) {
            super(canvas, provider, username, password);
        }

        String url(int zoom, int col, int row) {
            return tileUrl(zoom, col, row);
        }

        HttpRequest request(String url) {
            return httpRequest(url);
        }

        void error(int statusCode) {
            handleHttpError(statusCode);
        }

        /** True when the status reached TiledLayer's stderr reporting instead of being suppressed. */
        boolean reported(int statusCode) {
            PrintStream original = System.err;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            try {
                handleHttpError(statusCode);
            } finally {
                System.setErr(original);
            }
            return captured.size() > 0;
        }

        int zoom(double xScale) {
            return calculateZoom(xScale);
        }
    }
}
