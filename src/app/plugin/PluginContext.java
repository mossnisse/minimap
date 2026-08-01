package app.plugin;

import app.AppContext;
import app.ui.GUI;
import gis.coords.Coordinate;
import gis.core.MapCanvas;

import javax.swing.JFrame;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class PluginContext {
    public interface Registration extends AutoCloseable {
        @Override void close();
    }

    private record HandlerRegistration(long sequence, int priority, MapClickHandler handler) {}

    public final JFrame frame;
    public final MapCanvas mapCanvas;
    public final GUI gui;
    public final AppContext core;
    private final Supplier<Path> activeProjectDirectory;
    private final List<HandlerRegistration> clickHandlers = new ArrayList<>();
    private long nextSequence;

    public PluginContext(JFrame frame, MapCanvas mapCanvas, GUI gui, AppContext core,
                         Supplier<Path> activeProjectDirectory) {
        this.frame = frame;
        this.mapCanvas = mapCanvas;
        this.gui = gui;
        this.core = core;
        this.activeProjectDirectory = Objects.requireNonNull(activeProjectDirectory);
    }

    /** The directory owned by the project whose plugins are currently active. */
    public Path activeProjectDirectory() {
        Path path = activeProjectDirectory.get();
        if (path == null) throw new IllegalStateException("No active project");
        return path.toAbsolutePath().normalize();
    }

    public Registration registerClickHandler(int priority, MapClickHandler handler) {
        HandlerRegistration registration = new HandlerRegistration(nextSequence++, priority, handler);
        clickHandlers.add(registration);
        clickHandlers.sort(Comparator.comparingInt(HandlerRegistration::priority).reversed()
                .thenComparingLong(HandlerRegistration::sequence));
        return () -> clickHandlers.remove(registration);
    }

    public boolean dispatchClick(MouseEvent event, Coordinate coordinate) {
        for (HandlerRegistration registration : List.copyOf(clickHandlers)) {
            if (registration.handler().onClick(event, coordinate)) return true;
        }
        return false;
    }

    public void rebuildMenuBar() { gui.rebuildMenuBar(); }
    public void setCursorWait() { gui.setCursorWait(); }
    public void setCursorDefault() { gui.setCursorDefault(); }
}
