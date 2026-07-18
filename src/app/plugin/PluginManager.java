package app.plugin;

import app.project.CloseCoordinator;
import app.project.ProjectCloseParticipant;

import javax.swing.JOptionPane;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PluginManager {
    private final PluginContext context;
    private final Map<String, Plugin> plugins = new LinkedHashMap<>();
    private final Set<String> active = new LinkedHashSet<>();
    private Runnable enabledChanged = () -> {};

    public PluginManager(PluginContext context) {
        this.context = context;
    }

    public void register(Plugin plugin) {
        if (plugins.putIfAbsent(plugin.id(), plugin) != null) {
            throw new IllegalArgumentException("Duplicate plugin id: " + plugin.id());
        }
    }

    public Collection<Plugin> all() { return Collections.unmodifiableCollection(plugins.values()); }
    public boolean isActive(String id) { return active.contains(id); }
    public Set<String> enabledIds() { return Collections.unmodifiableSet(new LinkedHashSet<>(active)); }
    public void setEnabledChangedListener(Runnable listener) {
        enabledChanged = listener == null ? () -> {} : listener;
    }

    public boolean setEnabled(String id, boolean enabled) {
        Plugin plugin = requirePlugin(id);
        if (enabled == isActive(id)) return true;
        if (enabled) {
            if (!activate(plugin)) return false;
            plugin.installDefaultLayers();
        } else {
            if (!CloseCoordinator.resolve(context.frame, plugin.closeParticipants(), List.of())) return false;
            deactivate(plugin);
        }
        enabledChanged.run();
        context.rebuildMenuBar();
        return true;
    }

    /** Activates an already-persisted set without adding plugin default layers. */
    public void restoreEnabledSet(Set<String> wanted) {
        for (Plugin plugin : plugins.values()) {
            if (wanted.contains(plugin.id())) activate(plugin);
        }
        context.rebuildMenuBar();
    }

    /** Called after the project-wide close coordinator has already resolved dirty work. */
    public void deactivateAllPrepared() {
        List<Plugin> reverse = new ArrayList<>(plugins.values());
        Collections.reverse(reverse);
        for (Plugin plugin : reverse) {
            if (isActive(plugin.id())) deactivate(plugin);
        }
    }

    public List<ProjectCloseParticipant> closeParticipants() {
        List<ProjectCloseParticipant> result = new ArrayList<>();
        for (Plugin plugin : plugins.values()) {
            if (isActive(plugin.id())) result.addAll(plugin.closeParticipants());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public <T extends Plugin> T get(String id, Class<T> type) {
        Plugin plugin = requirePlugin(id);
        if (!type.isInstance(plugin)) throw new IllegalArgumentException("Wrong plugin type for " + id);
        return (T) plugin;
    }

    private boolean activate(Plugin plugin) {
        if (isActive(plugin.id())) return true;
        try {
            plugin.activate(context);
            active.add(plugin.id());
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(context.frame,
                    "Could not enable " + plugin.displayName() + ":\n" + e.getMessage(),
                    "Plugin error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void deactivate(Plugin plugin) {
        try {
            plugin.deactivate();
        } finally {
            active.remove(plugin.id());
        }
    }

    private Plugin requirePlugin(String id) {
        Plugin plugin = plugins.get(id);
        if (plugin == null) throw new IllegalArgumentException("Unknown plugin: " + id);
        return plugin;
    }
}
