package app.plugin.collection;

import app.MapLayers;
import app.plugin.MenuContributions;
import app.plugin.Plugin;
import app.plugin.PluginContext;
import app.project.ProjectCloseParticipant;
import gis.layers.PointTableLayer;

import javax.swing.JMenuItem;
import java.util.List;

/** Project-scoped private specimen collection plugin. */
public final class PrivateCollectionPlugin implements Plugin {
    public static final String ID = "private-collection";

    private PluginContext context;
    private CollectionDatabase database;
    private CollectionRepository repository;
    private SlimeRecordsImporter importer;
    private LabelGenerator labels;
    private ArtportalenExporter exporter;
    private PrivateCollectionManager manager;

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Private Collection"; }

    @Override
    public void activate(PluginContext context) throws Exception {
        this.context = context;
        this.database = new CollectionDatabase(context.activeProjectDirectory());
        this.repository = new CollectionRepository(database);
        this.importer = new SlimeRecordsImporter(repository);
        this.labels = new LabelGenerator(repository);
        this.exporter = new ArtportalenExporter(repository);
    }

    @Override
    public void contributeMenus(MenuContributions menus) {
        JMenuItem open = new JMenuItem("Private Collection Manager"); open.addActionListener(e -> openManager()); menus.view().add(open);
        JMenuItem layer = new JMenuItem("Add Private Collection events"); layer.addActionListener(e -> installDefaultLayers()); menus.layers().add(layer);
    }

    @Override
    public void installDefaultLayers() {
        if (context.mapCanvas.getLayerManager().get(MapLayers.COLLECTION_EVENTS).isEmpty()) {
            context.mapCanvas.getLayerManager().addLayerTop(MapLayers.COLLECTION_EVENTS, createEventLayer());
        }
    }

    public PointTableLayer createEventLayer() {
        if (repository == null) throw new IllegalStateException("Private Collection is not active");
        return MapLayers.collectionEvents(context.mapCanvas, repository);
    }

    private void openManager() {
        if (manager == null || !manager.isDisplayable()) {
            manager = new PrivateCollectionManager(context, repository, importer, labels, exporter, this::refreshLayer);
        }
        manager.setVisible(true); manager.toFront();
    }

    private void refreshLayer() {
        context.mapCanvas.getLayerManager().get(MapLayers.COLLECTION_EVENTS).ifPresent(PointTableLayer::invalidateCache);
        context.mapCanvas.repaint();
    }

    @Override public List<ProjectCloseParticipant> closeParticipants() { return manager == null || !manager.isDisplayable() ? List.of() : List.of(manager); }

    @Override
    public void deactivate() {
        if (manager != null) manager.close(); manager = null;
        if (context != null) context.mapCanvas.getLayerManager().removeLayer(MapLayers.COLLECTION_EVENTS);
        try { if (database != null) database.close(); } catch (Exception e) { System.err.println("Could not close collection database: " + e.getMessage()); }
        database = null; repository = null; importer = null; labels = null; exporter = null; context = null;
    }
}
