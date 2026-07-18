package app.project;

import app.AppContext;
import app.plugin.PluginManager;
import app.ui.GUI;
import gis.coords.CoordSystem;
import gis.core.Layer;
import gis.core.MapCanvas;
import gis.core.Settings;
import gis.geometry.Extent;
import gis.layers.CsvPointLayer;
import gis.layers.EditableTableLayer;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProjectManager {
    public static final Path ROOT = Path.of("projects");
    private static final String DEFAULT = "default";
    private static final String PLUGINS_KEY = "project.plugins";

    public record Bootstrap(String activeName) {}

    private final GUI gui;
    private final JFrame frame;
    private final MapCanvas canvas;
    private final AppContext core;
    private final PluginManager plugins;
    private String activeName;

    public ProjectManager(Bootstrap bootstrap, GUI gui, JFrame frame, MapCanvas canvas,
                          AppContext core, PluginManager plugins) {
        this.activeName = bootstrap.activeName();
        this.gui = gui;
        this.frame = frame;
        this.canvas = canvas;
        this.core = core;
        this.plugins = plugins;
    }

    public static Bootstrap bootstrap() throws IOException {
        Files.createDirectories(ROOT);
        Path defaultDir = projectDir(DEFAULT);
        Files.createDirectories(defaultDir);
        Path defaultSettings = defaultDir.resolve("settings.txt");
        Path legacy = Path.of("settings.txt");

        if (!Files.exists(defaultSettings) && Files.isRegularFile(legacy)) {
            Files.copy(legacy, defaultSettings);
            Settings.useFile(defaultSettings.toFile());
            Settings.setValue(PLUGINS_KEY, "herbarium");
            LayerSerializer.writeDefault(defaultDir.resolve("layers.txt"), true);
        } else if (!Files.exists(defaultSettings)) {
            writeAtomic(defaultSettings, List.of(PLUGINS_KEY + ": "));
            LayerSerializer.writeDefault(defaultDir.resolve("layers.txt"), false);
        }

        String requested = readLastProject();
        String start = projectExists(requested) ? requested : DEFAULT;
        ensureProjectFiles(start);
        Settings.useFile(settingsFile(start).toFile());
        return new Bootstrap(start);
    }

    public String activeName() { return activeName; }

    public List<String> listProjects() {
        try (var stream = Files.list(ROOT)) {
            return stream.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public void restoreInitial() {
        plugins.restoreEnabledSet(readEnabledPlugins());
        showWarnings(LayerSerializer.rebuild(layersFile(activeName), canvas, core, plugins));
        restoreViewAndWindow();
        writeLastProjectQuietly();
    }

    public boolean switchTo(String name) {
        if (name == null || name.equals(activeName)) return true;
        try {
            validateName(name);
            if (!projectExists(name)) throw new IOException("Project does not exist: " + name);
            if (!prepareToLeave()) return false;
            saveCurrent();
            return switchPrepared(name);
        } catch (Exception e) {
            showError("Could not switch project", e);
            return false;
        }
    }

    public boolean createBlank(String name) {
        try {
            createDirectory(name);
            writeAtomic(settingsFile(name), List.of(PLUGINS_KEY + ": "));
            LayerSerializer.writeDefault(layersFile(name), false);
            return switchTo(name);
        } catch (Exception e) {
            showError("Could not create project", e);
            return false;
        }
    }

    public boolean saveAs(String name) {
        try {
            createDirectory(name);
            if (!prepareToLeave()) {
                Files.deleteIfExists(projectDir(name));
                return false;
            }
            saveCurrent();
            Files.copy(settingsFile(activeName), settingsFile(name));
            Files.copy(layersFile(activeName), layersFile(name));
            return switchPrepared(name);
        } catch (Exception e) {
            showError("Could not save project as " + name, e);
            return false;
        }
    }

    public void saveCurrent() throws IOException {
        saveEnabledPlugins();
        LayerSerializer.write(layersFile(activeName), canvas.getLayerManager());
        Extent extent = canvas.getBoundingBox();
        Settings.setValue("view.crs", canvas.getCRS().name());
        Settings.setValue("view.n1", Double.toString(extent.c1.getNorth()));
        Settings.setValue("view.e1", Double.toString(extent.c1.getEast()));
        Settings.setValue("view.n2", Double.toString(extent.c2.getNorth()));
        Settings.setValue("view.e2", Double.toString(extent.c2.getEast()));
        Settings.setValue("window.w", Integer.toString(frame.getWidth()));
        Settings.setValue("window.h", Integer.toString(frame.getHeight()));
    }

    public void saveEnabledPluginsQuietly() {
        try {
            saveEnabledPlugins();
        } catch (IOException e) {
            showError("Could not save plugin settings", e);
        }
    }

    public boolean prepareAndSaveForExit() {
        if (!prepareToLeave()) return false;
        try {
            saveCurrent();
            plugins.deactivateAllPrepared();
            core.db.resetMysql();
            return true;
        } catch (Exception e) {
            // Never leave the user with an app that can't be closed: offer to
            // exit anyway when the project state can't be written.
            int choice = JOptionPane.showConfirmDialog(frame,
                    "Could not save the current project:\n" + e.getMessage()
                            + "\n\nExit anyway? The project's layer list and view will not be saved.",
                    "Save failed", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE);
            return choice == JOptionPane.YES_OPTION;
        }
    }

    private boolean switchPrepared(String name) throws Exception {
        closeOwnedWindows();
        plugins.deactivateAllPrepared();
        core.db.resetMysql();
        Settings.useFile(settingsFile(name).toFile());
        activeName = name;
        plugins.restoreEnabledSet(readEnabledPlugins());
        showWarnings(LayerSerializer.rebuild(layersFile(name), canvas, core, plugins));
        restoreViewAndWindow();
        gui.rebuildMenuBar();
        writeLastProject();
        return true;
    }

    private boolean prepareToLeave() {
        List<ProjectCloseParticipant> participants = new ArrayList<>(plugins.closeParticipants());
        // CsvPointLayer keeps its own dirty/save protocol outside EditableTableLayer;
        // adapt it so unsaved CSV edits get the same Save/Discard/Cancel prompt.
        for (Layer layer : canvas.getLayerManager().getLayers()) {
            if (layer instanceof CsvPointLayer csv) participants.add(csvParticipant(csv));
        }
        return CloseCoordinator.resolve(frame, participants, editableLayers());
    }

    private ProjectCloseParticipant csvParticipant(CsvPointLayer csv) {
        return new ProjectCloseParticipant() {
            @Override public String description() { return csv.getName(); }
            @Override public boolean isDirty() { return csv.isDirty(); }
            @Override public boolean save() {
                try {
                    csv.save();
                    return true;
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(frame,
                            "Could not save " + csv.getName() + ":\n" + e.getMessage(),
                            "Save failed", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
            @Override public void discard() { csv.setDirty(false); }
            @Override public void close() {
                Window editor = csv.getOpenEditor();
                if (editor != null) editor.dispose();
            }
        };
    }

    private List<EditableTableLayer> editableLayers() {
        List<EditableTableLayer> result = new ArrayList<>();
        for (Layer layer : canvas.getLayerManager().getLayers()) {
            if (layer instanceof EditableTableLayer editable) result.add(editable);
        }
        return result;
    }

    private void closeOwnedWindows() {
        for (Window window : frame.getOwnedWindows()) {
            if (window.isDisplayable()) window.dispose();
        }
    }

    private void saveEnabledPlugins() throws IOException {
        Settings.setValue(PLUGINS_KEY, String.join(",", plugins.enabledIds()));
    }

    private Set<String> readEnabledPlugins() {
        String value = Settings.getValue(PLUGINS_KEY);
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) return result;
        Arrays.stream(value.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).forEach(result::add);
        return result;
    }

    private void restoreViewAndWindow() {
        try {
            String crsText = Settings.getValue("view.crs");
            String n1Text = Settings.getValue("view.n1");
            String e1Text = Settings.getValue("view.e1");
            String n2Text = Settings.getValue("view.n2");
            String e2Text = Settings.getValue("view.e2");
            if (crsText != null && n1Text != null && e1Text != null && n2Text != null && e2Text != null) {
                CoordSystem crs = CoordSystem.valueOf(crsText);
                double n1 = finite(n1Text), e1 = finite(e1Text), n2 = finite(n2Text), e2 = finite(e2Text);
                if (n1 == n2 || e1 == e2) throw new IllegalArgumentException("Empty map extent");
                canvas.setCRS(crs);
                canvas.setBounds(new Extent(Math.min(n1, n2), Math.min(e1, e2),
                        Math.max(n1, n2), Math.max(e1, e2)));
            }
        } catch (Exception e) {
            System.err.println("Ignoring invalid saved map view: " + e.getMessage());
        }

        int width = positiveInt(Settings.getValue("window.w"), 1000);
        int height = positiveInt(Settings.getValue("window.h"), 1000);
        frame.setSize(Math.max(300, width), Math.max(300, height));
    }

    private static double finite(String value) {
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed)) throw new IllegalArgumentException("Non-finite number");
        return parsed;
    }

    private static int positiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static void ensureProjectFiles(String name) throws IOException {
        Path dir = projectDir(name);
        Files.createDirectories(dir);
        if (!Files.exists(settingsFile(name))) writeAtomic(settingsFile(name), List.of(PLUGINS_KEY + ": "));
        Settings.useFile(settingsFile(name).toFile());
        if (Settings.getValue(PLUGINS_KEY) == null) Settings.setValue(PLUGINS_KEY, "");
        if (!Files.exists(layersFile(name))) {
            LayerSerializer.writeDefault(layersFile(name),
                    Arrays.asList(Settings.getValue(PLUGINS_KEY).split(",")).contains("herbarium"));
        }
    }

    private static void createDirectory(String name) throws IOException {
        validateName(name);
        Path dir = projectDir(name);
        if (Files.exists(dir)) throw new IOException("A project named '" + name + "' already exists");
        Files.createDirectory(dir);
    }

    public static void validateName(String name) {
        if (name == null || name.length() < 1 || name.length() > 64 || !name.equals(name.trim())
                || name.equals(".") || name.equals("..") || name.endsWith(".")
                || name.matches(".*[<>:\"/\\\\|?*\\p{Cntrl}].*")) {
            throw new IllegalArgumentException("Use 1–64 filename-safe characters; trailing spaces or dots are not allowed");
        }
        Path root = ROOT.toAbsolutePath().normalize();
        Path candidate = root.resolve(name).normalize();
        if (!candidate.startsWith(root)) throw new IllegalArgumentException("Project path escapes the projects folder");
    }

    private static boolean projectExists(String name) {
        if (name == null) return false;
        try {
            validateName(name);
            if (!Files.isDirectory(projectDir(name))) return false;
            return projectDir(name).toRealPath().startsWith(ROOT.toRealPath());
        } catch (Exception e) {
            return false;
        }
    }

    private static Path projectDir(String name) { return ROOT.resolve(name); }
    private static Path settingsFile(String name) { return projectDir(name).resolve("settings.txt"); }
    private static Path layersFile(String name) { return projectDir(name).resolve("layers.txt"); }

    private static String readLastProject() {
        try {
            String value = Files.readString(ROOT.resolve("last.txt"), StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? null : value;
        } catch (IOException e) {
            return null;
        }
    }

    private void writeLastProjectQuietly() {
        try { writeLastProject(); } catch (IOException e) { showError("Could not remember the project", e); }
    }

    private void writeLastProject() throws IOException {
        writeAtomic(ROOT.resolve("last.txt"), List.of(activeName));
    }

    private static void writeAtomic(Path target, List<String> lines) throws IOException {
        Path absolute = target.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temp = Files.createTempFile(absolute.getParent(), absolute.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void showWarnings(List<String> warnings) {
        if (!warnings.isEmpty()) JOptionPane.showMessageDialog(frame, String.join("\n", warnings),
                "Project loaded with warnings", JOptionPane.WARNING_MESSAGE);
    }

    private void showError(String title, Exception e) {
        JOptionPane.showMessageDialog(frame, title + ":\n" + e.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }
}
