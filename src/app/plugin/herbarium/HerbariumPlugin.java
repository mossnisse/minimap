package app.plugin.herbarium;

import app.MapLayers;
import app.plugin.MenuContributions;
import app.plugin.Plugin;
import app.plugin.PluginContext;
import app.ProjectCloseParticipant;
import gis.coords.CoordSystem;
import gis.coords.Coordinate;
import gis.core.Keyboard;
import gis.core.Settings;
import gis.layers.PointTableLayer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** All MySQL-backed locality and specimen behavior. */
public final class HerbariumPlugin implements Plugin, HerbariumController {
    public static final String ID = "herbarium";
    private static final String SPECIMEN_DIALOG_SETTING = "specimen.dialog";
    private static final String LEGACY_SPECIMEN_DIALOG_SETTING = "specimen dialog";

    private PluginContext context;
    private LocalityRepository localities;
    private SpecimenService specimens;
    private SpecimenBridgeDialog bridgeDialog;
    private EditLocalityDialog moveTarget;
    private PluginContext.Registration clickRegistration;
    private final List<Window> windows = new ArrayList<>();
    private boolean programmaticBridgeClose;

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Herbarium"; }

    @Override
    public void activate(PluginContext context) {
        this.context = context;
        this.localities = new LocalityRepository(context.core.db);
        this.specimens = new SpecimenService(context.core.db);
        // The H2 cache is a global file; drop leftovers from another project
        // (or an older schema) so this activation starts from an empty cache.
        specimens.clearCache();
        this.clickRegistration = context.registerClickHandler(100, this::handleMapClick);
        this.programmaticBridgeClose = false;
        String dialogState = Settings.getValue(SPECIMEN_DIALOG_SETTING);
        if (dialogState == null) {
            dialogState = Settings.getValue(LEGACY_SPECIMEN_DIALOG_SETTING);
            if (dialogState != null) setSetting(SPECIMEN_DIALOG_SETTING, dialogState);
        }
        if ("open".equals(dialogState)) {
            SwingUtilities.invokeLater(this::searchSpecimens);
        }
    }

    @Override
    public void installDefaultLayers() {
        if (context.mapCanvas.getLayerManager().get(MapLayers.LOKAL_DB).isEmpty()) {
            context.mapCanvas.getLayerManager().addLayerTop(MapLayers.LOKAL_DB, createLocalityLayer());
        }
    }

    /** The editable locality layer: WGS84 points with precision circles and big labels. */
    public PointTableLayer createLocalityLayer() {
        if (context == null || localities == null) throw new IllegalStateException("Herbarium is not active");
        LocalityRepository source = localities;
        PointTableLayer layer = new PointTableLayer("LokalDB", CoordSystem.WGS84, context.mapCanvas,
                wgs84Bounds -> {
                    List<PointTableLayer.LabeledPoint> points = new ArrayList<>();
                    for (LocalityRepository.LocalityPoint p : source.findInBounds(wgs84Bounds)) {
                        points.add(new PointTableLayer.LabeledPoint(p.wgs84(), p.name(), p.precisionMeters()));
                    }
                    return points;
                },
                0.5, true);
        layer.setColor(Color.BLACK);
        layer.setMaxZoomL(40);
        return layer;
    }

    @Override
    public void contributeMenus(MenuContributions menus) {
        JMenuItem user = new JMenuItem("Set user", KeyEvent.VK_I);
        user.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK));
        user.addActionListener(e -> track(new SetUserDialog()).setVisible(true));
        menus.file().add(user);

        JMenuItem specimensItem = new JMenuItem("Search specimens", KeyEvent.VK_E);
        specimensItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK));
        specimensItem.addActionListener(e -> searchSpecimens());
        menus.view().add(specimensItem);

        JMenuItem localitySearch = new JMenuItem("Search herbarium localities", KeyEvent.VK_F);
        localitySearch.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        localitySearch.addActionListener(e -> searchLocalities());
        menus.view().add(localitySearch);

        JMenuItem edit = new JMenuItem("Edit locality at marker", KeyEvent.VK_J);
        edit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.CTRL_DOWN_MASK));
        edit.addActionListener(e -> showLocalityAtMarker());
        menus.view().add(edit);

        JMenuItem create = new JMenuItem("Create locality at marker", KeyEvent.VK_Y);
        create.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        create.addActionListener(e -> createLocalityAtMarker());
        menus.view().add(create);

        JMenuItem layer = new JMenuItem("Add Virtual Herbarium locality Layer");
        layer.addActionListener(e -> installDefaultLayers());
        menus.layers().add(layer);
    }

    private boolean handleMapClick(MouseEvent event, Coordinate coordinate) {
        if (moveTarget != null) {
            leaveMoveMode(coordinate);
            return true;
        }
        if (Keyboard.isKeyDown(KeyEvent.VK_A)) {
            editLocalityNear(coordinate);
            return true;
        }
        if (Keyboard.isKeyDown(KeyEvent.VK_S)) {
            openCreateDialog(coordinate);
            return true;
        }
        return false;
    }

    private void searchLocalities() {
        track(new SearchLocalityDialog(context.frame, this, context.mapCanvas, "", "", localities))
                .setVisible(true);
    }

    private void showLocalityAtMarker() {
        Coordinate coordinate = context.mapCanvas.getCoordinate();
        if (coordinate != null) editLocalityNear(coordinate);
    }

    private void editLocalityNear(Coordinate coordinate) {
        int id = localities.findNearestId(context.mapCanvas.getCRS().toWGS84(coordinate), 1000);
        if (id != -1) {
            EditLocalityDialog dialog = new EditLocalityDialog(this, context.frame, id, bridgeDialog,
                    context.mapCanvas, localities);
            track(dialog).setVisible(true);
        }
    }

    private void createLocalityAtMarker() {
        Coordinate coordinate = context.mapCanvas.getCoordinate();
        if (coordinate == null) {
            JOptionPane.showMessageDialog(context.mapCanvas, "Please select a point on the map first.");
            return;
        }
        openCreateDialog(coordinate);
    }

    private void openCreateDialog(Coordinate coordinate) {
        CreateLocalityDialog dialog = new CreateLocalityDialog(context.frame, this, context.mapCanvas,
                bridgeDialog, coordinate, localities, context.core.placeNames);
        track(dialog).setVisible(true);
    }

    private void searchSpecimens() {
        if (context == null) return;
        if (bridgeDialog != null && bridgeDialog.isVisible()) {
            bridgeDialog.toFront();
            bridgeDialog.requestFocus();
            return;
        }
        bridgeDialog = new SpecimenBridgeDialog(context.frame, this, specimens,
                context.mapCanvas, localities);
        track(bridgeDialog);
        bridgeDialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                if (!programmaticBridgeClose) setSetting(SPECIMEN_DIALOG_SETTING, "closed");
                windows.remove(bridgeDialog);
                bridgeDialog = null;
            }
        });
        setSetting(SPECIMEN_DIALOG_SETTING, "open");
        bridgeDialog.setVisible(true);
    }

    @Override
    public List<ProjectCloseParticipant> closeParticipants() {
        List<ProjectCloseParticipant> participants = new ArrayList<>();
        if (bridgeDialog != null && bridgeDialog.isDisplayable()) {
            SpecimenBridgeDialog dialog = bridgeDialog;
            participants.add(new ProjectCloseParticipant() {
                @Override public String description() { return "Specimen bridge"; }
                @Override public boolean isDirty() { return dialog.isDirty(); }
                @Override public boolean save() { return dialog.savePendingChanges(); }
                @Override public void discard() { dialog.discardPendingChanges(); }
                @Override public void close() {
                    programmaticBridgeClose = true;
                    dialog.closeForProjectTransition();
                }
            });
        }
        for (Window window : List.copyOf(windows)) {
            if (window != bridgeDialog && window.isDisplayable()
                    && (window instanceof CreateLocalityDialog || window instanceof EditLocalityDialog)) {
                participants.add(new ProjectCloseParticipant() {
                    @Override public String description() { return ((Dialog) window).getTitle(); }
                    @Override public boolean isDirty() {
                        if (window instanceof CreateLocalityDialog create) return create.hasUnsavedWork();
                        return ((EditLocalityDialog) window).hasUnsavedWork();
                    }
                    @Override public boolean save() {
                        if (window instanceof CreateLocalityDialog create) {
                            return create.saveForProjectTransition();
                        }
                        return ((EditLocalityDialog) window).saveForProjectTransition();
                    }
                    @Override public void discard() {}
                    @Override public void close() { window.dispose(); }
                });
            }
        }
        return participants;
    }

    @Override
    public void deactivate() {
        cancelMoveMode();
        if (clickRegistration != null) clickRegistration.close();
        programmaticBridgeClose = true;
        for (Window window : List.copyOf(windows)) window.dispose();
        windows.clear();
        bridgeDialog = null;
        context.mapCanvas.getLayerManager().removeLayer(MapLayers.LOKAL_DB);
        try {
            context.core.db.resetMysql();
        } catch (SQLException e) {
            System.err.println("Could not close MySQL connection: " + e.getMessage());
        }
        clickRegistration = null;
        specimens = null;
        localities = null;
        context = null;
    }

    @Override public void setCursorWait() { context.setCursorWait(); }
    @Override public void setCursorDefault() { context.setCursorDefault(); }
    @Override public void trackWindow(Window window) { track(window); }

    @Override
    public void enterMoveMode(EditLocalityDialog dialog) {
        moveTarget = dialog;
        Cursor cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        context.frame.setCursor(cursor);
        context.mapCanvas.setCursor(cursor);
        dialog.setCursor(cursor);
    }

    private void leaveMoveMode(Coordinate coordinate) {
        EditLocalityDialog target = moveTarget;
        moveTarget = null;
        target.updateCoordinates(coordinate);
        resetMoveCursor(target);
    }

    @Override
    public void cancelMoveMode() {
        if (moveTarget == null) return;
        EditLocalityDialog target = moveTarget;
        moveTarget = null;
        target.setTitle("Edit Locality: " + target.getOldName());
        resetMoveCursor(target);
    }

    private void resetMoveCursor(EditLocalityDialog dialog) {
        Cursor cursor = Cursor.getDefaultCursor();
        if (context != null) {
            context.frame.setCursor(cursor);
            context.mapCanvas.setCursor(cursor);
        }
        dialog.setCursor(cursor);
    }

    private <T extends Window> T track(T window) {
        windows.add(window);
        window.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { windows.remove(window); }
        });
        return window;
    }

    private static void setSetting(String key, String value) {
        try {
            Settings.setValue(key, value);
        } catch (IOException e) {
            System.err.println("Could not save setting " + key + ": " + e.getMessage());
        }
    }
}
