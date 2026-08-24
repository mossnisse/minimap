package app.plugin.collection;

import app.MapLayers;
import app.plugin.PluginContext;
import app.ProjectCloseParticipant;
import app.repo.PlaceNameRepository;
import gis.ui.CoordinateEntry;
import gis.coords.CoordSystem;
import gis.coords.Coordinate;
import gis.geometry.Extent;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import static app.plugin.collection.CollectionTypes.*;

/** Single-window Swing workbench for the private collection. */
public final class PrivateCollectionManager extends JDialog implements ProjectCloseParticipant {
    private record NamedId(long id, String name) { @Override public String toString() { return name; } }

    private final PluginContext context;
    private final CollectionRepository repository;
    private final SlimeRecordsImporter importer;
    private final LabelGenerator labels;
    private final ArtportalenExporter exporter;
    private final Runnable dataChanged;
    private long refreshGeneration;
    private SwingWorker<Snapshot, Void> refreshWorker;
    private boolean closed;

    private final JLabel overview = new JLabel();
    private final DefaultTableModel eventModel = tableModel("ID", "Type", "Number", "Date", "Locality", "Expected", "Species");
    private final DefaultTableModel specimenModel = tableModel("ID", "Collection no.", "Taxon", "Locality", "Date", "Determined", "Report");
    private final DefaultTableModel localityModel = tableModel("ID", "Name", "Country", "Province", "District", "Coordinate");
    private final JTable eventTable = new JTable(eventModel), specimenTable = new JTable(specimenModel), localityTable = new JTable(localityModel);
    private final JTextField eventFilter = new JTextField(24), specimenFilter = new JTextField(24);
    private final DefaultTableModel importModel = new DefaultTableModel(new Object[]{"Row", "Kind", "ID", "Locality", "Date", "Taxon", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return column == 1; }
    };
    private final JTable importTable = new JTable(importModel);
    private final JLabel importSummary = new JLabel("Choose a SlimeRecords CSV or ZIP archive.");
    /** Open record editors by "type:id", so one record is never edited in two windows at once. */
    private final Map<String, Editor> editors = new LinkedHashMap<>();
    private SlimeRecordsImporter.Preview preview;

    public PrivateCollectionManager(PluginContext context, CollectionRepository repository,
                                    SlimeRecordsImporter importer, LabelGenerator labels,
                                    ArtportalenExporter exporter, Runnable dataChanged) {
        super(context.frame, "Private Collection", false);
        this.context = context; this.repository = repository; this.importer = importer;
        this.labels = labels; this.exporter = exporter; this.dataChanged = dataChanged;
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setLayout(new BorderLayout()); add(buildToolbar(), BorderLayout.NORTH); add(buildTabs(), BorderLayout.CENTER);
        setSize(1100, 720); setLocationRelativeTo(context.frame);
        addWindowListener(new WindowAdapter() { @Override public void windowClosed(WindowEvent e) { closePreview(); } });
        refreshAll();
        SwingUtilities.invokeLater(this::offerFirstRunSettings);
    }

    private JComponent buildToolbar() {
        JToolBar bar = new JToolBar(); bar.setFloatable(false);
        JButton refresh = new JButton("Refresh"); refresh.addActionListener(e -> refreshAll()); bar.add(refresh);
        JButton settings = new JButton("Settings"); settings.addActionListener(e -> editSettings()); bar.add(settings);
        JButton taxonomy = new JButton("Update species list…"); taxonomy.addActionListener(e -> updateTaxonomy(taxonomy)); bar.add(taxonomy);
        bar.addSeparator(); bar.add(new JLabel("Project: " + context.activeProjectDirectory().getFileName()));
        return bar;
    }

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Overview", overviewPanel()); tabs.addTab("Events", eventsPanel()); tabs.addTab("Specimens", specimensPanel());
        tabs.addTab("Localities", localitiesPanel()); tabs.addTab("Import", importPanel()); tabs.addTab("Labels & Reporting", reportingPanel());
        return tabs;
    }

    private JComponent overviewPanel() {
        overview.setFont(overview.getFont().deriveFont(Font.PLAIN, 17f)); overview.setVerticalAlignment(SwingConstants.TOP);
        JPanel panel = new JPanel(new BorderLayout()); panel.setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32)); panel.add(overview, BorderLayout.NORTH); return panel;
    }

    private JComponent eventsPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6)); JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(new JLabel("Search:")); actions.add(eventFilter); JButton search = new JButton("Search"); search.addActionListener(e -> refreshAll()); actions.add(search);
        JButton add = new JButton("New event"); add.addActionListener(e -> editEvent(0)); actions.add(add);
        JButton edit = new JButton("Edit"); edit.addActionListener(e -> editEvent(selectedId(eventTable))); actions.add(edit);
        JButton batch = new JButton("Create specimen batch"); batch.addActionListener(e -> createBatch()); actions.add(batch);
        JButton print = new JButton("Print event labels"); print.addActionListener(e -> printEventLabels()); actions.add(print);
        JButton photo = new JButton("Attach photos"); photo.addActionListener(e -> attachPhotos()); actions.add(photo);
        JButton openPhotos = new JButton("Open photos"); openPhotos.addActionListener(e -> openEventPhotos()); actions.add(openPhotos);
        JButton delete = new JButton("Delete"); delete.addActionListener(e -> deleteSelected(eventTable, "event", repository::deleteEvent)); actions.add(delete);
        panel.add(actions, BorderLayout.NORTH); panel.add(new JScrollPane(eventTable), BorderLayout.CENTER); return panel;
    }

    private JComponent specimensPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6)); JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(new JLabel("Search:")); actions.add(specimenFilter); JButton search = new JButton("Search"); search.addActionListener(e -> refreshAll()); actions.add(search);
        JButton add = new JButton("New specimen"); add.addActionListener(e -> editSpecimen(0)); actions.add(add);
        JButton edit = new JButton("Edit"); edit.addActionListener(e -> editSpecimen(selectedId(specimenTable))); actions.add(edit);
        JButton det = new JButton("Add determination"); det.addActionListener(e -> addDetermination()); actions.add(det);
        JButton history = new JButton("Determination history"); history.addActionListener(e -> showDeterminationHistory()); actions.add(history);
        JButton printDet = new JButton("Print determination"); printDet.addActionListener(e -> printSpecimenLabel(LabelType.DETERMINATION)); actions.add(printDet);
        JButton printNo = new JButton("Print number"); printNo.addActionListener(e -> printSpecimenLabel(LabelType.ACCESSION)); actions.add(printNo);
        JButton printBot = new JButton("Print botanical"); printBot.addActionListener(e -> printSpecimenLabel(LabelType.BOTANICAL)); actions.add(printBot);
        JButton delete = new JButton("Delete"); delete.addActionListener(e -> deleteSelected(specimenTable, "specimen", repository::deleteSpecimen)); actions.add(delete);
        panel.add(actions, BorderLayout.NORTH); panel.add(new JScrollPane(specimenTable), BorderLayout.CENTER); return panel;
    }

    private JComponent localitiesPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6)); JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("New from map marker"); add.addActionListener(e -> editLocality(0)); actions.add(add);
        JButton edit = new JButton("Edit"); edit.addActionListener(e -> editLocality(selectedId(localityTable))); actions.add(edit);
        JButton delete = new JButton("Delete"); delete.addActionListener(e -> deleteSelected(localityTable, "locality", repository::deleteLocality)); actions.add(delete);
        localityTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) showLocalityOnMap(selectedId(localityTable)); }
        });
        panel.add(actions, BorderLayout.NORTH); panel.add(new JScrollPane(localityTable), BorderLayout.CENTER); return panel;
    }

    private JComponent importPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6)); JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JComboBox<CollectionKind> kind = new JComboBox<>(CollectionKind.values()); top.add(new JLabel("Default type:")); top.add(kind);
        JButton choose = new JButton("Choose CSV/ZIP…"); choose.addActionListener(e -> chooseImport((CollectionKind)kind.getSelectedItem())); top.add(choose);
        JButton commit = new JButton("Write rows to database…"); commit.addActionListener(e -> commitImport()); top.add(commit); top.add(importSummary);
        importTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<>(CollectionKind.values())));
        panel.add(top, BorderLayout.NORTH); panel.add(new JScrollPane(importTable), BorderLayout.CENTER); return panel;
    }

    private JComponent reportingPanel() {
        JPanel panel = new JPanel(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JPanel numbers = new JPanel(new FlowLayout(FlowLayout.LEFT)); JTextField count = new JTextField("10", 5);
        numbers.add(new JLabel("Reserve collection numbers:")); numbers.add(count);
        JButton reserve = new JButton("Reserve and print"); reserve.addActionListener(e -> reserveAndPrint(count.getText())); numbers.add(reserve); panel.add(numbers);
        JPanel reports = new JPanel(new FlowLayout(FlowLayout.LEFT)); JButton export = new JButton("Export ready records to Artportalen CSV"); export.addActionListener(e -> exportReady()); reports.add(export);
        JButton confirm = new JButton("Confirm an export was reported"); confirm.addActionListener(e -> confirmLatest()); reports.add(confirm); panel.add(reports);
        JButton correction = new JButton("Confirm selected specimen correction"); correction.addActionListener(e -> confirmCorrection()); reports.add(correction);
        JTextArea notes = new JTextArea("Exports contain at most 2,000 new records.\nAfter Artportalen accepts a file, select that export for confirmation.\nChanged confirmed records are shown as Update needed and must be corrected on Artportalen.");
        notes.setEditable(false); notes.setOpaque(false); panel.add(notes); return panel;
    }

    /** Everything the three tables and the overview need, gathered off the EDT. */
    private record Snapshot(List<Object[]> events, List<Object[]> specimens, List<Object[]> localities,
                            CollectionRepository.Dashboard dashboard, String taxonomy) {}

    /**
     * Reloads every panel. The queries run on a worker thread: a dashboard over
     * a few thousand specimens takes hundreds of milliseconds, and this fires
     * after every edit, so doing it inline froze the window on each save.
     */
    private void refreshAll() {
        if (closed) return;
        long generation = ++refreshGeneration;
        String eventSearch = eventFilter.getText();
        String specimenSearch = specimenFilter.getText();
        if (refreshWorker != null) refreshWorker.cancel(true);
        refreshWorker = new SwingWorker<Snapshot, Void>() {
            @Override protected Snapshot doInBackground() throws Exception { return loadSnapshot(eventSearch, specimenSearch); }
            @Override protected void done() {
                if (closed || generation != refreshGeneration || isCancelled()) return;
                try { apply(get()); dataChanged.run(); }
                catch (Exception e) { showError("Could not refresh collection", e); }
                finally { if (refreshWorker == this) refreshWorker = null; }
            }
        };
        refreshWorker.execute();
    }

    private Snapshot loadSnapshot(String eventSearch, String specimenSearch) throws Exception {
        List<Object[]> events = new ArrayList<>();
        for (var r : repository.events(eventSearch)) {
            events.add(new Object[]{r.id(), r.kind(), r.fieldNumber(), r.date(), r.localityName(), r.expectedCount(), r.specimenCount()});
        }
        List<Object[]> specimens = new ArrayList<>();
        for (var r : repository.specimens(specimenSearch)) {
            var p = exporter.prepare(r.id());
            specimens.add(new Object[]{r.id(), r.accessionNumber(), r.taxonName(), r.localityName(), r.date(), r.determined(),
                    repository.reportStatus(r.id(), p.valid(), p.payloadHash())});
        }
        List<Object[]> localities = new ArrayList<>();
        for (var l : repository.localities()) {
            localities.add(new Object[]{l.id(), l.name(), l.countryCode(), l.province(), l.district(),
                    l.hasCoordinate() ? String.format(java.util.Locale.US, "%.5f, %.5f", l.latitude(), l.longitude()) : "Text only"});
        }
        return new Snapshot(events, specimens, localities, repository.dashboard(exporter, labels), taxonomyLine());
    }

    private String taxonomyLine() throws Exception {
        String count = repository.setting("taxonomy.count", "");
        if (count.isBlank() || "0".equals(count)) return "not imported";
        return count + " names, updated " + repository.setting("taxonomy.updatedAt", "?");
    }

    private void apply(Snapshot snapshot) {
        fill(eventModel, snapshot.events());
        fill(specimenModel, snapshot.specimens());
        fill(localityModel, snapshot.localities());
        CollectionRepository.Dashboard d = snapshot.dashboard();
        overview.setText("<html><h2>Private Collection</h2><table cellpadding='8'>"
                + row("Insect tubes awaiting specimens", d.tubesAwaitingSpecimens()) + row("Undetermined specimens", d.undetermined())
                + row("Pending or changed labels", d.unprinted()) + row("Ready for Artportalen", d.ready())
                + row("Exported, awaiting confirmation", d.exported()) + row("Reported", d.reported())
                + row("Reported records needing correction", d.updateNeeded())
                + "<tr><td>Species list</td><td><b>" + snapshot.taxonomy() + "</b></td></tr></table></html>");
    }

    private static void fill(DefaultTableModel model, List<Object[]> rows) {
        model.setRowCount(0);
        for (Object[] row : rows) model.addRow(row);
    }

    private void editSettings() {
        try {
            JTextField full = new JTextField(repository.setting("owner.fullName", "Nils Ericson")); JTextField shortName = new JTextField(repository.setting("owner.shortName", "N. Ericson"));
            JTextField collection = new JTextField(repository.setting("artportalen.privateCollection", "Nils Ericson")); JTextField prefix = new JTextField(repository.setting("accession.prefix", "NE"));
            JTextField next = new JTextField(repository.setting("accession.next", "1")); JTextField timezone = new JTextField(repository.setting("timezone", "Europe/Stockholm"));
            JTextField dyntaxaKey = new JTextField(repository.setting("dyntaxa.subscriptionKey", ""));
            dyntaxaKey.setToolTipText("Your own free key from api-portal.artdatabanken.se; leave blank to load the checklist from a downloaded file instead");
            JPanel form = form("Owner full name", full, "Owner label name", shortName, "Artportalen private collection", collection, "Collection number prefix", prefix, "Next number", next, "Timezone", timezone, "Artdatabanken subscription key", dyntaxaKey);
            if (JOptionPane.showConfirmDialog(this, form, "Private Collection settings", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            Long.parseLong(next.getText().trim()); repository.setSetting("owner.fullName", full.getText().trim()); repository.setSetting("owner.shortName", shortName.getText().trim());
            repository.setSetting("artportalen.privateCollection", collection.getText().trim()); repository.setSetting("accession.prefix", prefix.getText().trim()); repository.setSetting("accession.next", next.getText().trim()); repository.setSetting("timezone", timezone.getText().trim());
            repository.setSetting("dyntaxa.subscriptionKey", dyntaxaKey.getText().trim());
            repository.setSetting("setup.complete", "true"); repository.ensurePerson(full.getText().trim(), shortName.getText().trim()); refreshAll();
        } catch (Exception e) { showError("Could not save settings", e); }
    }

    /**
     * Loads the Dyntaxa checklist from a Darwin Core Archive the user downloaded from artfakta.se.
     * On a worker thread because it writes a few hundred thousand rows.
     */
    // ponytail: the import holds the single shared JDBC connection for its whole run, so anything else in
    // this window stalls until it finishes. Disabled button plus wait cursor is the whole mitigation;
    // open a second H2 connection for the import if that ever becomes annoying.
    private void updateTaxonomy(JButton button) {
        String key;
        try { key = repository.setting("dyntaxa.subscriptionKey", ""); }
        catch (Exception e) { showError("Could not read the settings", e); return; }
        String[] choices = { "Download from Artdatabanken", "Choose a downloaded file…", "Cancel" };
        int choice = JOptionPane.showOptionDialog(this,
                key.isBlank() ? "Downloading needs your own free subscription key from api-portal.artdatabanken.se,\n"
                        + "entered under Settings. Without one, choose an archive you downloaded yourself."
                        : "Fetch the current checklist from Artdatabanken, or read an archive already on disk?",
                "Update species list", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                choices, choices[key.isBlank() ? 1 : 0]);
        if (choice != 0 && choice != 1) return;
        Path archive = null;
        if (choice == 1) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Choose the Dyntaxa Darwin Core Archive");
            chooser.setFileFilter(new FileNameExtensionFilter("Dyntaxa Darwin Core Archive", "zip"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            archive = chooser.getSelectedFile().toPath();
        }
        Path chosen = archive;
        button.setEnabled(false); context.setCursorWait();
        new SwingWorker<DyntaxaImporter.Result, Void>() {
            @Override protected DyntaxaImporter.Result doInBackground() throws Exception {
                DyntaxaImporter importer = new DyntaxaImporter(repository);
                return chosen == null ? importer.importFromArtdatabanken(key) : importer.importFrom(chosen);
            }
            @Override protected void done() {
                context.setCursorDefault(); button.setEnabled(true);
                if (closed) return;
                try {
                    DyntaxaImporter.Result r = get();
                    JOptionPane.showMessageDialog(PrivateCollectionManager.this, "Species list updated from " + r.archiveName() + ":\n"
                            + r.scientificNames() + " scientific names and " + r.vernacularNames() + " vernacular names."
                            + (r.skippedRows() == 0 ? "" : "\n" + r.skippedRows() + " rows carried no usable name and were skipped."));
                    refreshAll();
                } catch (Exception e) { showError("Could not update the species list", e); }
            }
        }.execute();
    }

    private void offerFirstRunSettings() {
        try { if (!"true".equals(repository.setting("setup.complete", "false"))) editSettings(); }
        catch (Exception e) { showError("Could not open first-run settings", e); }
    }

    private void editLocality(long id) {
        try {
            CollectionRepository.Locality old = id == 0 ? null : repository.locality(id); Coordinate marker = context.mapCanvas.getCoordinate(); Coordinate wgs = marker == null ? null : context.mapCanvas.getCRS().toWGS84(marker);
            JTextField name = new JTextField(old == null ? "" : old.name()); JTextField shortName = new JTextField(old == null ? "" : text(old.shortName()));
            JTextField countryCode = new JTextField(old == null ? "SE" : text(old.countryCode())); JTextField country = new JTextField(old == null ? "Sweden" : text(old.countryName()));
            JTextField province = new JTextField(old == null ? "" : text(old.province()));
            JTextField district = new JTextField(old == null ? "" : text(old.district()));
            JTextField description = new JTextField(old == null ? "" : text(old.description()));
            CoordinateEntry coordinates = new CoordinateEntry("Coordinate system", "No coordinate - the locality is saved as text only.");
            if (old != null && old.hasCoordinate()) coordinates.setWGS84(old.latitude(), old.longitude());
            else if (wgs != null) coordinates.pickWGS84(wgs.getNorth(), wgs.getEast());
            JTextField uncertainty = new JTextField(old == null || old.uncertaintyMeters() == null ? "" : old.uncertaintyMeters().toString());
            JTextField nearest = new JTextField(old == null ? "" : text(old.nearestPlace())); JTextField distance = new JTextField(old == null || old.nearestDistanceMeters() == null ? "" : old.nearestDistanceMeters().toString()); JTextField direction = new JTextField(old == null ? "" : text(old.nearestDirection()));
            if (old == null && marker != null) describeMarker(marker, province, district, nearest, distance, direction);
            List<Object> rows = new ArrayList<>(List.of("Name", name, "Short label name", shortName, "Country code", countryCode, "Country", country, "Province", province, "District", district, "Description", description));
            rows.addAll(List.of(coordinates.formRows()));
            rows.addAll(List.of("Uncertainty (m)", uncertainty, "Nearest place", nearest, "Distance (m)", distance, "Direction", direction));
            JPanel f = form(rows.toArray());
            Timestamp opened = id == 0 ? null : repository.localityModifiedAt(id);
            openEditor("locality:" + id, id == 0 ? "New locality" : "Edit locality: " + old.name(), f, () -> {
                try {
                    Coordinate entered = coordinates.valueWGS84();
                    Double la = entered == null ? null : entered.getNorth(), lo = entered == null ? null : entered.getEast();
                    CoordinateSource source = la == null ? CoordinateSource.TEXT_ONLY
                            : enteredCoordinateSource(coordinates.origin(), old == null ? null : old.coordinateSource());
                    CollectionRepository.Locality value = new CollectionRepository.Locality(id, name.getText(), shortName.getText(), countryCode.getText(), country.getText(), province.getText(), district.getText(), description.getText(), la, lo, integer(uncertainty.getText()), source, nearest.getText(), integer(distance.getText()), direction.getText());
                    if (id != 0 && !stillCurrent(opened, repository.localityModifiedAt(id), "locality")) return false;
                    List<CollectionRepository.Locality> duplicates = repository.duplicateLocalities(value);
                    if (!duplicates.isEmpty() && JOptionPane.showConfirmDialog(this, "A locality with the same name and region exists. Save another one?", "Possible duplicate", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return false;
                    repository.saveLocality(value); refreshAll(); return true;
                } catch (IllegalArgumentException e) { JOptionPane.showMessageDialog(this, e.getMessage(), "Check the coordinate", JOptionPane.WARNING_MESSAGE); return false; }
                catch (Exception e) { showError("Could not save locality", e); return false; }
            }, showOnMapButton(() -> showOnMap(coordinates.valueWGS84(), integer(uncertainty.getText()))),
                    markerButton(coordinates, picked -> describeMarker(picked, province, district, nearest, distance, direction)));
        } catch (Exception e) { showError("Could not open locality", e); }
    }

    /** Fills in what the map can tell us about a position: which region it is in and the nearest place. */
    private void describeMarker(Coordinate marker, JTextField province, JTextField district, JTextField nearest, JTextField distance, JTextField direction) {
        String markerProvince = "", markerDistrict = "";
        PlaceNameRepository.NearestPlace place = null;
        try {
            markerProvince = text(MapLayers.provinceAt(context.mapCanvas, marker));
            markerDistrict = text(MapLayers.districtAt(context.mapCanvas, marker));
            Coordinate wgs = context.mapCanvas.getCRS().toWGS84(marker);
            place = context.core.placeNames.findNearest(CoordSystem.SWEREF99TM.toProjected(wgs), 100_000);
        } catch (Exception e) {
            markerProvince = markerDistrict = ""; place = null;
            showError("Could not look up the place names for this position", e);
        }
        // The coordinate has already changed, so every derived field must replace its old value, even with blank text.
        province.setText(markerProvince);
        district.setText(markerDistrict);
        nearest.setText(place == null ? "" : place.name());
        distance.setText(place == null ? "" : Integer.toString((int)Math.round(place.distanceMeters())));
        direction.setText(place == null ? "" : place.direction());
    }

    /** Non-modal record editors: the map keeps its own input, so it can be panned and zoomed while one is open. */
    private final class Editor extends JDialog {
        private final BooleanSupplier save;
        Editor(String title, JComponent content, BooleanSupplier save, JButton... extras) {
            super(context.frame, title, false);
            this.save = save;
            setDefaultCloseOperation(DISPOSE_ON_CLOSE); setLayout(new BorderLayout());
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            for (JButton extra : extras) buttons.add(extra);
            JButton ok = new JButton("Save"); ok.addActionListener(e -> saveNow()); buttons.add(ok);
            JButton cancel = new JButton("Cancel"); cancel.addActionListener(e -> dispose()); buttons.add(cancel);
            add(new JScrollPane(content), BorderLayout.CENTER); add(buttons, BorderLayout.SOUTH);
            getRootPane().setDefaultButton(ok);
            pack();
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            setSize(Math.min(getWidth() + 24, screen.width - 80), Math.min(getHeight(), screen.height - 120));
            setLocationRelativeTo(PrivateCollectionManager.this);
        }
        /** True once the record is stored; the editor stays open on anything the user still has to resolve. */
        boolean saveNow() { if (!save.getAsBoolean()) return false; dispose(); return true; }
    }

    /** Opens the editor for a record, or raises the one already editing it — one window per record. */
    private void openEditor(String key, String title, JComponent content, BooleanSupplier save, JButton... extras) {
        Editor open = editors.get(key);
        if (open != null) { open.setVisible(true); open.toFront(); open.requestFocus(); return; }
        Editor editor = new Editor(title, content, save, extras);
        editors.put(key, editor);
        // Remove by value, not by key: a new record's editor is re-keyed once it gets its id.
        editor.addWindowListener(new WindowAdapter() { @Override public void windowClosed(WindowEvent e) { editors.values().remove(editor); } });
        editor.setVisible(true);
    }

    /** A new record just got its id, so its editor moves off the "…:0" key that a second New would collide with. */
    private void rekeyEditor(String oldKey, String newKey) {
        Editor editor = editors.remove(oldKey); if (editor != null) editors.put(newKey, editor);
    }

    /** Takes the marker's current position - the point of keeping the map usable while an editor is open. */
    private JButton markerButton(CoordinateEntry coordinates, Consumer<Coordinate> alsoDescribe) {
        JButton button = new JButton("From map marker");
        button.setToolTipText("Take the position the map marker is on right now");
        button.addActionListener(e -> {
            Coordinate marker = context.mapCanvas.getCoordinate();
            if (marker == null) { JOptionPane.showMessageDialog(this, "Click a position on the map first."); return; }
            Coordinate wgs = context.mapCanvas.getCRS().toWGS84(marker);
            coordinates.pickWGS84(wgs.getNorth(), wgs.getEast());
            if (alsoDescribe != null) alsoDescribe.accept(marker);
        });
        return button;
    }

    /**
     * Guards against saving over a record that changed while this editor was open.
     * Nothing here runs concurrently - Swing is single threaded - but a second
     * window can still have written the row between opening and saving.
     */
    private boolean stillCurrent(Timestamp opened, Timestamp current, String what) {
        if (current == null) { JOptionPane.showMessageDialog(this, "This " + what + " was deleted in another window. Nothing was saved.", "Gone", JOptionPane.WARNING_MESSAGE); return false; }
        if (opened == null || opened.equals(current)) return true;
        return JOptionPane.showConfirmDialog(this, "This " + what + " was changed in another window after you opened it.\nOverwrite those changes?",
                "Changed elsewhere", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private interface Delete { void run(long id) throws Exception; }

    /**
     * Deletes every selected row, one transaction each, and stops at the first one the
     * repository refuses - a locality still carrying events, an event still holding
     * specimens, a reported specimen. The rows before it are already gone, so the tables
     * are reloaded either way.
     */
    private void deleteSelected(JTable table, String what, Delete delete) {
        List<Long> ids = selectedIds(table);
        if (ids.isEmpty()) { JOptionPane.showMessageDialog(this, "Select one or more " + plural(what) + " first."); return; }
        for (long id : ids) if (editors.containsKey(what + ":" + id)) { JOptionPane.showMessageDialog(this, "The " + what + " " + id + " is open in an editor. Close that window first."); return; }
        if (JOptionPane.showConfirmDialog(this, "Delete " + ids.size() + " " + (ids.size() == 1 ? what : plural(what)) + "? This cannot be undone.",
                "Delete " + plural(what), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        long current = 0;
        try { for (long id : ids) { current = id; delete.run(id); } }
        catch (Exception e) { showError("Could not delete " + what + " " + current, e); }
        finally { refreshAll(); }
    }

    private static String plural(String what) { return what.endsWith("y") ? what.substring(0, what.length() - 1) + "ies" : what + "s"; }

    /** Centres the map on a position and zooms to a window that shows its uncertainty. */
    private void showOnMap(Coordinate wgs, Integer uncertaintyMeters) {
        if (wgs == null) { JOptionPane.showMessageDialog(this, "There is no coordinate to show."); return; }
        // Build the view in metres around the point, then hand it to whatever CRS the canvas uses.
        double radius = Math.max(250.0, uncertaintyMeters == null ? 500.0 : uncertaintyMeters * 3.0);
        Coordinate south = wgs.moveWGS84(radius, 180), north = wgs.moveWGS84(radius, 0);
        Coordinate west = wgs.moveWGS84(radius, 270), east = wgs.moveWGS84(radius, 90);
        CoordSystem crs = context.mapCanvas.getCRS();
        context.mapCanvas.setBounds(new Extent(south.getNorth(), west.getEast(), north.getNorth(), east.getEast()).convertCRS(CoordSystem.WGS84, crs));
        context.mapCanvas.setCoordinate(crs.toProjected(wgs));
    }

    private void showLocalityOnMap(long id) {
        if (id == 0) return;
        try {
            CollectionRepository.Locality l = repository.locality(id);
            if (l == null || !l.hasCoordinate()) { JOptionPane.showMessageDialog(this, "This locality is text only and has no coordinate to show."); return; }
            showOnMap(new Coordinate(l.latitude(), l.longitude()), l.uncertaintyMeters());
        } catch (Exception e) { showError("Could not show the locality on the map", e); }
    }

    /** An event is shown at its own exact coordinate, or at its locality when it was never given one. */
    private void showEventOnMap(long id) {
        if (id == 0) return;
        try {
            CollectionRepository.Event e = repository.event(id);
            if (e.latitude() == null) showLocalityOnMap(e.localityId());
            else showOnMap(new Coordinate(e.latitude(), e.longitude()), e.uncertaintyMeters());
        } catch (Exception e) { showError("Could not show the event on the map", e); }
    }

    /**
     * The other direction from {@link #markerButton}: puts what the editor is holding on the map,
     * unsaved edits included, so a typed coordinate can be checked before it is stored.
     */
    private JButton showOnMapButton(Runnable show) {
        JButton button = new JButton("Show on map");
        button.setToolTipText("Centre the map on this position");
        button.addActionListener(e -> { try { show.run(); } catch (Exception ex) { showError("Could not show this on the map", ex); } });
        return button;
    }

    private void editEvent(long id) {
        try {
            var old = id == 0 ? null : repository.event(id); List<NamedId> options = repository.localities().stream().map(l -> new NamedId(l.id(), l.name())).toList();
            if (options.isEmpty()) { JOptionPane.showMessageDialog(this, "Create a locality first."); return; }
            JComboBox<NamedId> locality = new JComboBox<>(options.toArray(NamedId[]::new)); if (old != null) selectId(locality, old.localityId());
            JComboBox<CollectionKind> kind = new JComboBox<>(CollectionKind.values()); if (old != null) kind.setSelectedItem(old.kind());
            JTextField field = new JTextField(old == null ? "" : text(old.fieldNumber())); JTextField start = new JTextField(old == null || old.startDate() == null ? LocalDate.now().toString() : old.startDate().toString());
            JTextField end = new JTextField(old == null || old.endDate() == null ? "" : old.endDate().toString()); JTextField time = new JTextField(old == null || old.localTime() == null ? "" : old.localTime().toString()); JTextField endTime = new JTextField(old == null || old.endTime() == null ? "" : old.endTime().toString());
            JTextField collectors = new JTextField(old == null ? repository.setting("owner.fullName", "") : String.join(", ", repository.collectors(id))); JTextField method = new JTextField(old == null ? "" : text(old.method()));
            JTextField trap = new JTextField(old == null ? "" : text(old.trapNumber())); JTextField expected = new JTextField(old == null ? "0" : Integer.toString(old.expectedCount())); JTextField habitat = new JTextField(old == null ? "" : text(old.habitat()));
            TaxonEntry preliminary = new TaxonEntry(repository, "Preliminary taxon"); preliminary.setName(old == null ? "" : text(old.preliminaryTaxon()));
            JTextArea notes = new JTextArea(old == null ? "" : text(old.notes()), 3, 30);
            CoordinateEntry coordinates = new CoordinateEntry("Exact coordinate system", "No exact coordinate - the event uses the locality position.");
            if (old != null) coordinates.setWGS84(old.latitude(), old.longitude());
            JTextField uncertainty = new JTextField(old == null || old.uncertaintyMeters() == null ? "" : old.uncertaintyMeters().toString()); JTextField elevation = new JTextField(old == null || old.elevationMeters() == null ? "" : old.elevationMeters().toString());
            DefaultTableModel speciesModel = tableModel("ID", "Collection no.", "Species", "Determined");
            JTable speciesTable = new JTable(speciesModel); speciesTable.setPreferredScrollableViewportSize(new Dimension(360, 90));
            speciesTable.getColumnModel().getColumn(0).setMaxWidth(40); // the id is only there for the double-click
            // One line, so this one shows the suggestion list without a status row under it.
            TaxonEntry newSpecies = new TaxonEntry(repository, null); JButton addSpecies = new JButton("Add");
            JPanel adder = new JPanel(new BorderLayout(6, 0)); adder.add(newSpecies.field(), BorderLayout.CENTER); adder.add(addSpecies, BorderLayout.EAST);

            List<Object> rows = new ArrayList<>(List.of("Locality", locality, "Type", kind, "Field/collection number", field, "Start date", start, "End date", end, "Local time", time, "End time", endTime, "Collectors (comma separated)", collectors, "Method", method, "Trap number", trap, "Expected specimens", expected, "Habitat", habitat));
            rows.addAll(List.of(preliminary.formRows()));
            rows.addAll(List.of(coordinates.formRows()));
            rows.addAll(List.of("Coordinate uncertainty (m)", uncertainty, "Elevation (m)", elevation, "Notes", new JScrollPane(notes),
                    "Species in this collection", new JScrollPane(speciesTable), "Add species", adder));
            JPanel f = form(rows.toArray());

            // Both move on the first save: a new event gets its id, and our own write must not look stale
            // to the next one. Adding a species has to persist the event first - a specimen needs a real
            // event id, and its number is derived from the number written on the event.
            long[] eventId = { id };
            Timestamp[] opened = { id == 0 ? null : repository.eventModifiedAt(id) };
            Runnable reloadSpecies = () -> {
                speciesModel.setRowCount(0);
                if (eventId[0] == 0) return;
                try { for (var s : repository.eventSpecimens(eventId[0])) speciesModel.addRow(new Object[]{s.id(), s.accessionNumber(), s.taxonName(), s.determined() ? "Yes" : ""}); }
                catch (Exception e) { showError("Could not list the species", e); }
            };
            reloadSpecies.run();

            // Returns the saved event id, or 0 when the user still has something to resolve - the editor stays open.
            LongSupplier persist = () -> {
                try {
                    Coordinate exact = coordinates.valueWGS84();
                    NamedId l = (NamedId)locality.getSelectedItem();
                    Double exactLat = exact == null ? null : exact.getNorth(), exactLon = exact == null ? null : exact.getEast();
                    CoordinateSource source = exactLat == null ? CoordinateSource.LOCALITY_FALLBACK
                            : enteredCoordinateSource(coordinates.origin(), old == null ? null : old.coordinateSource());
                    CollectionRepository.Event value = new CollectionRepository.Event(eventId[0], l.id(), (CollectionKind)kind.getSelectedItem(), field.getText(), localDate(start.getText()), localDate(end.getText()), localTime(time.getText()), localTime(endTime.getText()), repository.setting("timezone", "Europe/Stockholm"), exactLat, exactLon, integer(uncertainty.getText()), decimal(elevation.getText()), source, method.getText(), trap.getText(), intValue(expected.getText(), 0), habitat.getText(), notes.getText(), preliminary.name());
                    if (eventId[0] != 0 && !stillCurrent(opened[0], repository.eventModifiedAt(eventId[0]), "event")) return 0L;
                    long saved = repository.saveEvent(value, splitComma(collectors.getText()));
                    if (eventId[0] == 0) rekeyEditor("event:0", "event:" + saved);
                    eventId[0] = saved; opened[0] = repository.eventModifiedAt(saved);
                    field.setText(text(repository.event(saved).fieldNumber()));
                    refreshAll(); return saved;
                } catch (IllegalArgumentException e) { JOptionPane.showMessageDialog(this, e.getMessage(), "Check the coordinate", JOptionPane.WARNING_MESSAGE); return 0L; }
                catch (Exception e) { showError("Could not save event", e); return 0L; }
            };

            addSpecies.addActionListener(e -> {
                String taxon = newSpecies.name(); if (taxon.isEmpty()) return;
                long saved = persist.getAsLong(); if (saved == 0) return;
                try {
                    repository.saveSpecimen(new CollectionRepository.Specimen(0, saved, null, null, taxon, 1,
                            null, null, null, null, ReportIntent.INCLUDE, false));
                    newSpecies.setName(""); reloadSpecies.run(); refreshAll();
                } catch (Exception ex) { showError("Could not add the species", ex); }
            });
            newSpecies.field().addActionListener(e -> addSpecies.doClick());
            speciesTable.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) editSpecimen(selectedId(speciesTable), reloadSpecies); }
            });

            // An event without an exact coordinate is collected at its locality, so that is what the map shows.
            JButton showEvent = showOnMapButton(() -> {
                Coordinate exact = coordinates.valueWGS84();
                if (exact == null) showLocalityOnMap(((NamedId)locality.getSelectedItem()).id());
                else showOnMap(exact, integer(uncertainty.getText()));
            });
            openEditor("event:" + id, id == 0 ? "New event" : "Edit event: " + text(old.fieldNumber()), f,
                    () -> persist.getAsLong() != 0, showEvent, markerButton(coordinates, null));
        } catch (Exception e) { showError("Could not open event", e); }
    }

    private void createBatch() {
        long eventId = selectedId(eventTable); if (eventId == 0) return;
        String input = JOptionPane.showInputDialog(this, "Number of specimens to create:", "1"); if (input == null) return;
        try { repository.createSpecimenBatch(eventId, Integer.parseInt(input.trim())); refreshAll(); }
        catch (Exception e) { showError("Could not create specimen batch", e); }
    }

    private void editSpecimen(long id) { editSpecimen(id, null); }

    /** {@code afterSave} lets the window that opened this one - the event editor's species list - catch up. */
    private void editSpecimen(long id, Runnable afterSave) {
        try {
            CollectionRepository.Specimen old = id == 0 ? null : repository.specimen(id); List<NamedId> options = repository.events("").stream().map(e -> new NamedId(e.id(), text(e.fieldNumber()) + " — " + e.localityName() + " " + text(e.date()))).toList();
            if (options.isEmpty()) { JOptionPane.showMessageDialog(this, "Create a collection event first."); return; }
            JComboBox<NamedId> event = new JComboBox<>(options.toArray(NamedId[]::new)); if (old != null) selectId(event, old.eventId());
            JTextField accession = new JTextField(old == null ? "" : old.accessionNumber()); accession.setToolTipText("Leave blank to allocate the next number");
            if (old != null) accession.setEditable(false);
            // "Taxon group" holds Mossor/Invertebrates, not a taxon name, so it stays a plain field.
            JTextField group = new JTextField(old == null ? "" : text(old.taxonGroup())); JTextField quantity = new JTextField(old == null ? "1" : Integer.toString(old.quantity()));
            TaxonEntry preliminary = new TaxonEntry(repository, "Preliminary taxon"); preliminary.setName(old == null ? "" : text(old.preliminaryTaxon()));
            JTextField sex = new JTextField(old == null ? "" : text(old.sex())); JTextField stage = new JTextField(old == null ? "" : text(old.lifeStage())); JTextField substrate = new JTextField(old == null ? "" : text(old.substrate())); JTextArea comments = new JTextArea(old == null ? "" : text(old.comments()), 3, 30);
            JComboBox<ReportIntent> intent = new JComboBox<>(ReportIntent.values()); if (old != null) intent.setSelectedItem(old.reportIntent());
            // A specimen has no coordinate of its own; it is shown where its collection event was.
            JButton show = showOnMapButton(() -> showEventOnMap(((NamedId)event.getSelectedItem()).id()));
            List<Object> rows = new ArrayList<>(List.of("Event", event, "Collection number", accession, "Taxon group", group));
            rows.addAll(List.of(preliminary.formRows()));
            rows.addAll(List.of("Quantity", quantity, "Sex", sex, "Life stage", stage, "Substrate", substrate, "Comments", new JScrollPane(comments), "Report", intent, "Position", show));
            JPanel f = form(rows.toArray());
            Timestamp opened = id == 0 ? null : repository.specimenModifiedAt(id);
            openEditor("specimen:" + id, id == 0 ? "New specimen" : "Edit specimen: " + old.accessionNumber(), f, () -> {
                try {
                    NamedId chosen = (NamedId)event.getSelectedItem();
                    if (id != 0 && !stillCurrent(opened, repository.specimenModifiedAt(id), "specimen")) return false;
                    repository.saveSpecimen(new CollectionRepository.Specimen(id, chosen.id(), accession.getText(), group.getText(), preliminary.name(), intValue(quantity.getText(), 1), sex.getText(), stage.getText(), substrate.getText(), comments.getText(), (ReportIntent)intent.getSelectedItem(), false));
                    refreshAll(); if (afterSave != null) afterSave.run(); return true;
                } catch (Exception ex) { showError("Could not save specimen", ex); return false; }
            }, show);
        } catch (Exception e) { showError("Could not open specimen", e); }
    }

    private void addDetermination() {
        long id = selectedId(specimenTable); if (id == 0) return;
        try {
            var specimen = repository.specimen(id); var current = repository.currentDetermination(id);
            TaxonEntry taxon = new TaxonEntry(repository, "Taxon"); taxon.setName(current == null ? text(specimen.preliminaryTaxon()) : current.taxonName());
            JTextField determiner = new JTextField(repository.setting("owner.fullName", "")); JTextField year = new JTextField(Integer.toString(Year.now().getValue()));
            JComboBox<IdentificationKind> kind = new JComboBox<>(IdentificationKind.values()); JCheckBox uncertain = new JCheckBox("Uncertain / cf."); JTextArea notes = new JTextArea(3, 30);
            List<Object> rows = new ArrayList<>(List.of(taxon.formRows()));
            rows.addAll(List.of("Determiner/confirmer", determiner, "Year", year, "Kind", kind, "Uncertainty", uncertain, "Notes", new JScrollPane(notes)));
            JPanel f = form(rows.toArray());
            if (JOptionPane.showConfirmDialog(this, f, "Add determination", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            // The only record that keeps the id: a determination is the authoritative identification.
            repository.addDetermination(id, taxon.name(), taxon.dyntaxaId(), determiner.getText(), integer(year.getText()), (IdentificationKind)kind.getSelectedItem(), uncertain.isSelected(), notes.getText()); refreshAll();
        } catch (Exception e) { showError("Could not save determination", e); }
    }

    private void showDeterminationHistory() {
        long id = selectedId(specimenTable); if (id == 0) return;
        try {
            StringBuilder text = new StringBuilder();
            for (var d : repository.determinations(id)) text.append(d.current() ? "CURRENT  " : "         ")
                    .append(d.taxonName()).append(" — ").append(d.kind().name().toLowerCase()).append(". ")
                    .append(d.determinerName() == null ? "" : d.determinerName()).append(' ')
                    .append(d.year() == null ? "" : d.year()).append(d.uncertain() ? " (cf.)" : "").append('\n');
            if (text.isEmpty()) text.append("No determinations recorded.");
            JTextArea area = new JTextArea(text.toString(), 12, 55); area.setEditable(false);
            JOptionPane.showMessageDialog(this, new JScrollPane(area), "Determination history", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) { showError("Could not load determination history", e); }
    }

    private void printEventLabels() {
        long id = selectedId(eventTable); if (id == 0) return;
        try { var e = repository.events("").stream().filter(x -> x.id() == id).findFirst().orElseThrow(); int copies = Math.max(1, Math.max(e.expectedCount(), e.specimenCount())); var sheet = labels.writeEventSheet(id, copies); open(sheet.path()); if (confirmPrinted()) repository.recordLabelPrint(LabelType.EVENT, id, copies, sheet.labels().getFirst().contentHash(), sheet.path().toString()); refreshAll(); }
        catch (Exception e) { showError("Could not create event labels", e); }
    }

    private void attachPhotos() {
        long id = selectedId(eventTable); if (id == 0) return;
        JFileChooser chooser = new JFileChooser(); chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try { for (java.io.File file : chooser.getSelectedFiles()) repository.addPhoto(id, file.toPath()); JOptionPane.showMessageDialog(this, "Photos copied into this collection project."); refreshAll(); }
        catch (Exception e) { showError("Could not attach photos", e); }
    }

    private void openEventPhotos() {
        long id = selectedId(eventTable); if (id == 0) return;
        try { List<Path> paths = repository.eventPhotos(id); if (paths.isEmpty()) { JOptionPane.showMessageDialog(this, "This event has no photos."); return; } for (Path path : paths) open(path); }
        catch (Exception e) { showError("Could not open photos", e); }
    }

    /** One sheet for the whole selection - an envelope of five species needs five herbarium labels. */
    private void printSpecimenLabel(LabelType type) {
        List<Long> ids = selectedIds(specimenTable); if (ids.isEmpty()) return;
        try {
            var sheet = labels.writeSpecimenSheet(ids, type); open(sheet.path());
            if (confirmPrinted()) for (int i = 0; i < ids.size(); i++) repository.recordLabelPrint(type, ids.get(i), 1, sheet.labels().get(i).contentHash(), sheet.path().toString());
            refreshAll();
        }
        catch (Exception e) { showError("Could not create label", e); }
    }

    private void reserveAndPrint(String text) {
        try { int count = Integer.parseInt(text.trim()); List<String> numbers = repository.reserveNumbers(count); var sheet = labels.writeReservedNumberSheet(numbers); open(sheet.path()); if (confirmPrinted()) repository.markReservedNumbersPrinted(numbers); refreshAll(); }
        catch (Exception e) { showError("Could not reserve numbers", e); }
    }

    private void chooseImport(CollectionKind kind) {
        JFileChooser chooser = new JFileChooser(); chooser.setFileFilter(new FileNameExtensionFilter("SlimeRecords CSV or ZIP", "csv", "zip"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        closePreview();
        try { preview = importer.preview(chooser.getSelectedFile().toPath(), kind); importModel.setRowCount(0); int valid = 0; for (var row : preview.rows()) { if (row.valid()) valid++; importModel.addRow(new Object[]{row.sourceRow(), row.kind(), row.sourceId(), row.value("locality"), row.value("eventdate"), row.value("taxonname"), row.valid() ? "Ready" : String.join("; ", row.errors())}); } importSummary.setText(valid + " of " + preview.rows().size() + " rows ready"); }
        catch (Exception e) { showError("Could not preview import", e); }
    }

    private void commitImport() {
        if (preview == null) { JOptionPane.showMessageDialog(this, "Choose an import file first."); return; }
        if (JOptionPane.showConfirmDialog(this, "Write these " + preview.rows().size() + " rows into the collection database?\nThe table above is only a preview until you confirm.",
                "Confirm import", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try { for (int i = 0; i < preview.rows().size(); i++) preview.setKind(i, CollectionKind.valueOf(importModel.getValueAt(i, 1).toString())); var result = importer.commit(preview); preview = null; importModel.setRowCount(0); importSummary.setText("Imported " + result.addedEvents() + " events, " + result.addedSpecimens() + " specimens and " + result.photosCopied() + " photos; skipped " + result.skipped()); showWarnings(result.warnings()); refreshAll(); }
        catch (Exception e) { showError("Import failed", e); }
    }

    private void showWarnings(List<String> warnings) {
        if (warnings.isEmpty()) return;
        String more = warnings.size() > 20 ? "\n… and " + (warnings.size() - 20) + " more" : "";
        JOptionPane.showMessageDialog(this, "The import finished with warnings:\n\n" + String.join("\n", warnings.stream().limit(20).toList()) + more,
                "Import warnings", JOptionPane.WARNING_MESSAGE);
    }

    private void exportReady() {
        try { var result = exporter.exportReady(null); open(result.path()); String extra = result.skippedErrors().isEmpty() ? "" : "\n\nSome incomplete rows were skipped:\n" + String.join("\n", result.skippedErrors().stream().limit(10).toList()); JOptionPane.showMessageDialog(this, "Created " + result.path() + " with " + result.specimenIds().size() + " rows." + extra); refreshAll(); }
        catch (Exception e) { showError("Could not export Artportalen CSV", e); }
    }
    private void confirmLatest() {
        try {
            List<CollectionRepository.ReportExport> pending = repository.pendingExports();
            if (pending.isEmpty()) { JOptionPane.showMessageDialog(this, "There are no exports awaiting confirmation."); return; }
            JComboBox<CollectionRepository.ReportExport> choice = new JComboBox<>(pending.toArray(CollectionRepository.ReportExport[]::new));
            JPanel prompt = form("Export accepted by Artportalen", choice);
            if (JOptionPane.showConfirmDialog(this, prompt, "Confirm reported export", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                exporter.confirmExport(((CollectionRepository.ReportExport)choice.getSelectedItem()).id()); refreshAll();
            }
        } catch (Exception e) { showError("Could not confirm export", e); }
    }
    private void confirmCorrection() { long id = selectedId(specimenTable); if (id == 0) { JOptionPane.showMessageDialog(this, "Select the corrected specimen in the Specimens tab first."); return; } try { if (JOptionPane.showConfirmDialog(this, "Confirm that you corrected this existing finding on Artportalen?", "Confirm correction", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) { exporter.confirmManualCorrection(id); refreshAll(); } } catch (Exception e) { showError("Could not confirm correction", e); } }

    private void open(Path path) throws Exception { if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Desktop file opening is unavailable. File: " + path); Desktop.getDesktop().browse(path.toUri()); }
    private boolean confirmPrinted() { return JOptionPane.showConfirmDialog(this, "Did the labels print successfully?", "Record print", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION; }
    private void closePreview() { if (preview != null) { try { preview.close(); } catch (Exception ignored) {} preview = null; } }

    @Override public String description() { return "Private Collection Manager"; }
    @Override public boolean isDirty() { return !editors.isEmpty(); }
    @Override public boolean save() { for (Editor editor : List.copyOf(editors.values())) if (!editor.saveNow()) return false; return true; }
    @Override public void discard() { closeEditors(); }
    @Override public void close() {
        closed = true;
        ++refreshGeneration;
        if (refreshWorker != null) refreshWorker.cancel(true);
        refreshWorker = null;
        closeEditors(); closePreview(); dispose();
    }

    /** Editors outlive this window, so they must be shut down before the database goes away. */
    private void closeEditors() { for (Editor editor : List.copyOf(editors.values())) editor.dispose(); editors.clear(); }

    private static DefaultTableModel tableModel(String... columns) { return new DefaultTableModel(columns, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } }; }
    private static long selectedId(JTable table) { int row = table.getSelectedRow(); if (row < 0) return 0; Object value = table.getValueAt(table.convertRowIndexToModel(row), 0); return ((Number)value).longValue(); }
    private static List<Long> selectedIds(JTable table) { return java.util.Arrays.stream(table.getSelectedRows()).mapToObj(r -> ((Number)table.getValueAt(table.convertRowIndexToModel(r), 0)).longValue()).toList(); }
    private static void selectId(JComboBox<NamedId> combo, long id) { for (int i=0;i<combo.getItemCount();i++) if (combo.getItemAt(i).id()==id) { combo.setSelectedIndex(i); return; } }
    // A label may be given as a component when the caller needs to change its text later.
    // GridBagLayout, not GridLayout: every row keeps its own height, so one tall Notes area
    // does not stretch the single-line fields above it.
    private static JPanel form(Object... pairs) {
        JPanel p = new JPanel(new GridBagLayout()); p.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        GridBagConstraints label = new GridBagConstraints(), field = new GridBagConstraints();
        label.insets = new Insets(3,0,3,7); field.insets = new Insets(3,0,3,0);
        field.gridwidth = GridBagConstraints.REMAINDER; field.weightx = 1;
        for (int i=0;i<pairs.length;i+=2) {
            Component c = (Component)pairs[i+1];
            boolean tall = c instanceof JScrollPane;                 // only the text areas want the leftover height
            field.fill = tall ? GridBagConstraints.BOTH : GridBagConstraints.HORIZONTAL; field.weighty = tall ? 1 : 0;
            label.anchor = tall ? GridBagConstraints.FIRST_LINE_START : GridBagConstraints.LINE_START;
            p.add(pairs[i] instanceof Component lc ? lc : new JLabel(pairs[i].toString()), label);
            p.add(c, field);
        }
        return p;
    }
    private static String text(Object value) { return value == null ? "" : value.toString(); }
    private static Double decimal(String value) { return value == null || value.isBlank() ? null : Double.parseDouble(value.trim().replace(',','.')); }
    private static Integer integer(String value) { return value == null || value.isBlank() ? null : Integer.parseInt(value.trim()); }
    static CoordinateSource enteredCoordinateSource(CoordinateEntry.Origin origin, CoordinateSource loadedSource) {
        return switch (origin) {
            case PICKED -> CoordinateSource.MAP;
            case TYPED -> CoordinateSource.MANUAL;
            case LOADED -> loadedSource == null ? CoordinateSource.MANUAL : loadedSource;
        };
    }
    private static int intValue(String value, int fallback) { try { return Integer.parseInt(value.trim()); } catch (Exception e) { return fallback; } }
    private static LocalDate localDate(String value) { return value == null || value.isBlank() ? null : LocalDate.parse(value.trim()); }
    private static LocalTime localTime(String value) { return value == null || value.isBlank() ? null : LocalTime.parse(value.trim()); }
    private static List<String> splitComma(String text) { return java.util.Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList(); }
    private static String row(String label, int value) { return "<tr><td>" + label + "</td><td><b>" + value + "</b></td></tr>"; }
    private void showError(String title, Exception e) { e.printStackTrace(); JOptionPane.showMessageDialog(this, title + ":\n" + e.getMessage(), title, JOptionPane.ERROR_MESSAGE); }
}
