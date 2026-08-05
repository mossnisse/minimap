package app.plugin;

import app.ProjectCloseParticipant;
import java.util.List;

public interface Plugin {
    String id();
    String displayName();
    void activate(PluginContext context) throws Exception;
    void deactivate();

    default void contributeMenus(MenuContributions menus) {}
    default void installDefaultLayers() {}
    default List<ProjectCloseParticipant> closeParticipants() { return List.of(); }
}
