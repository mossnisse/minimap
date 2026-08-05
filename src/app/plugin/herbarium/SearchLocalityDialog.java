package app.plugin.herbarium;

import app.MapLayers;
import gis.coords.CoordSystem;
import gis.coords.Coordinate;
import gis.core.MapCanvas;
import gis.geometry.Extent;
import gis.layers.TNGPointFileLayer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

/** MySQL-only locality search owned by the Herbarium plugin. */
public final class SearchLocalityDialog extends JDialog {
    private static final String[] PROVINCES = {
            "*", "Torne lappmark", "Norrbotten", "Lule lappmark", "Pite lappmark",
            "Lycksele lappmark", "Åsele lappmark", "Ångermanland", "Västerbotten",
            "Härjedalen", "Medelpad", "Jämtland", "Hälsingland", "Dalarna", "Gästrikland",
            "Uppland", "Värmland", "Västmanland", "Närke", "Södermanland", "Dalsland",
            "Gotland", "Östergötland", "Bohuslän", "Halland", "Öland", "Blekinge",
            "Skåne", "Småland", "Västergötland"
    };

    private final HerbariumController controller;
    private final MapCanvas canvas;
    private final LocalityRepository localities;
    private final JTextField name;
    private final JTextField country = new JTextField("Sweden", 10);
    private final JTextField district = new JTextField("*", 10);
    private final JTextField source = new JTextField("*", 10);
    private final JTextField precision = new JTextField("*", 5);
    private final JTextField category = new JTextField("*", 10);
    private final JCheckBox placeOnly = new JCheckBox("Is Place Only");
    private final JComboBox<String> province = new JComboBox<>(PROVINCES);
    private final JPanel results = new JPanel();
    private final JButton search = new JButton("Search");
    private final JButton zoom = new JButton("Zoom");
    private SwingWorker<ArrayList<Result>, Void> worker;
    private TNGPointFileLayer lastResults;

    private record Result(Coordinate coordinate, String label, int id) {}

    public SearchLocalityDialog(Frame owner, HerbariumController controller, MapCanvas canvas,
                                String initialText, String initialProvince,
                                LocalityRepository localities) {
        super(owner, "Search Herbarium Localities", false);
        this.controller = controller;
        this.canvas = canvas;
        this.localities = localities;
        this.name = new JTextField(initialText, 15);
        province.setSelectedItem(initialProvince);
        buildUi();
        pack();
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                if (worker != null) worker.cancel(true);
                controller.setCursorDefault();
            }
        });
    }

    private void buildUi() {
        JPanel fields = new JPanel(new GridLayout(7, 2, 6, 5));
        addField(fields, "Name:", name);
        addField(fields, "Province:", province);
        addField(fields, "District:", district);
        addField(fields, "Country:", country);
        addField(fields, "Source:", source);
        addField(fields, "Precision >:", precision);
        addField(fields, "Category:", category);

        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(results);
        scroll.setPreferredSize(new Dimension(440, 300));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        zoom.setEnabled(false);
        buttons.add(placeOnly);
        buttons.add(search);
        buttons.add(zoom);
        buttons.add(close);
        search.addActionListener(e -> performSearch());
        zoom.addActionListener(e -> zoomResults());
        close.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(search);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(fields, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
    }

    private static void addField(JPanel panel, String label, Component field) {
        panel.add(new JLabel(label));
        panel.add(field);
    }

    private void performSearch() {
        if (worker != null) worker.cancel(true);
        LocalityRepository.SearchCriteria criteria = new LocalityRepository.SearchCriteria(
                name.getText().trim(), country.getText().trim(), district.getText().trim(),
                source.getText().trim(), precision.getText().trim(), category.getText().trim(),
                String.valueOf(province.getSelectedItem()), placeOnly.isSelected());
        CoordSystem resultCrs = canvas.getCRS();
        search.setEnabled(false);
        controller.setCursorWait();
        results.removeAll();
        results.add(new JLabel("Searching…"));

        worker = new SwingWorker<>() {
            @Override protected ArrayList<Result> doInBackground() throws Exception {
                ArrayList<Result> found = new ArrayList<>();
                for (LocalityRepository.SearchHit hit : localities.search(criteria)) {
                    String label = String.format("%s (%s)", hit.locality(),
                            hit.district() == null ? "" : hit.district());
                    found.add(new Result(resultCrs.toProjected(hit.wgs84()), label, hit.id()));
                }
                return found;
            }

            @Override protected void done() {
                if (!isDisplayable() || isCancelled()) return;
                try {
                    ArrayList<Result> found = get();
                    results.removeAll();
                    ArrayList<Coordinate> points = new ArrayList<>();
                    ArrayList<String> labels = new ArrayList<>();
                    if (found.isEmpty()) {
                        results.add(new JLabel("No localities found."));
                        // Drop the previous search's markers so the map and the
                        // Zoom button can't show results that don't match the query
                        lastResults = null;
                        canvas.getLayerManager().removeOverlay(MapLayers.SEARCH_RESULTS);
                    }
                    for (Result result : found) {
                        results.add(resultButton(result, resultCrs));
                        points.add(result.coordinate());
                        labels.add(result.label());
                    }
                    if (!points.isEmpty()) {
                        lastResults = new TNGPointFileLayer(points, labels, "Search Results", canvas, resultCrs);
                        lastResults.setColor(Color.BLUE);
                        canvas.getLayerManager().setOverlay(MapLayers.SEARCH_RESULTS, lastResults);
                    }
                    zoom.setEnabled(lastResults != null);
                } catch (Exception e) {
                    results.removeAll();
                    results.add(new JLabel("Search failed: " + e.getMessage()));
                } finally {
                    search.setEnabled(true);
                    controller.setCursorDefault();
                    results.revalidate();
                    results.repaint();
                }
            }
        };
        worker.execute();
    }

    private JButton resultButton(Result result, CoordSystem resultCrs) {
        JButton button = new JButton(result.label());
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        button.addActionListener(e -> canvas.focus(
                resultCrs.convertTo(result.coordinate(), canvas.getCRS())));
        JPopupMenu popup = new JPopupMenu();
        JMenuItem edit = new JMenuItem("Edit Locality Details…");
        edit.addActionListener(e -> {
            EditLocalityDialog dialog = new EditLocalityDialog(controller,
                    (Frame) getOwner(), result.id(), null, canvas, localities);
            controller.trackWindow(dialog);
            dialog.setVisible(true);
        });
        popup.add(edit);
        button.setComponentPopupMenu(popup);
        return button;
    }

    private void zoomResults() {
        if (lastResults == null || lastResults.getBoundaries() == null) return;
        Extent bounds = lastResults.getBoundaries();
        double width = Math.abs(bounds.c2.getEast() - bounds.c1.getEast());
        double height = Math.abs(bounds.c2.getNorth() - bounds.c1.getNorth());
        if (width == 0 || height == 0) {
            // A single result (or collinear results) has a zero-size extent;
            // grow(0.1) would keep it zero-size and blow up the map scale, so
            // center on it at the current zoom instead
            canvas.focus(new Coordinate(
                    (bounds.c1.getNorth() + bounds.c2.getNorth()) / 2,
                    (bounds.c1.getEast() + bounds.c2.getEast()) / 2));
        } else {
            canvas.setBounds(bounds.grow(0.1));
        }
    }
}
