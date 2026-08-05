package app.ui;

import app.BusyCursor;
import app.MapLayers;
import app.repo.PlaceNameRepository;
import gis.coords.CoordSystem;
import gis.coords.Coordinate;
import gis.core.MapCanvas;
import gis.layers.TNGPointFileLayer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/** H2-only place-name search that remains available without Herbarium. */
public final class PlaceNameSearchDialog extends JDialog {
    private final BusyCursor busy;
    private final MapCanvas canvas;
    private final PlaceNameRepository placeNames;
    private final JTextField name = new JTextField(18);
    private final JTextField district = new JTextField("*", 14);
    private final JPanel results = new JPanel();
    private final JButton search = new JButton("Search");
    private SwingWorker<List<Result>, Void> worker;

    private record Result(Coordinate coordinate, String label) {}

    public PlaceNameSearchDialog(Frame owner, BusyCursor busy, MapCanvas canvas,
                                 PlaceNameRepository placeNames) {
        super(owner, "Search place names", false);
        this.busy = busy;
        this.canvas = canvas;
        this.placeNames = placeNames;
        buildUi();
        pack();
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                if (worker != null) worker.cancel(true);
                busy.setCursorDefault();
            }
        });
    }

    private void buildUi() {
        JPanel fields = new JPanel(new GridLayout(2, 2, 6, 6));
        fields.add(new JLabel("Name:"));
        fields.add(name);
        fields.add(new JLabel("District:"));
        fields.add(district);

        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(results);
        scroll.setPreferredSize(new Dimension(420, 300));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        buttons.add(search);
        buttons.add(close);
        search.addActionListener(e -> runSearch());
        close.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(search);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(fields, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
    }

    private void runSearch() {
        String pattern = name.getText().trim().replace("*", "%");
        String districtPattern = district.getText().trim().replace("*", "%");
        if (pattern.isEmpty()) return;
        if (worker != null) worker.cancel(true);
        CoordSystem resultCrs = canvas.getCRS();
        search.setEnabled(false);
        busy.setCursorWait();
        results.removeAll();
        results.add(new JLabel("Searching…"));

        worker = new SwingWorker<>() {
            @Override protected List<Result> doInBackground() throws Exception {
                List<Result> found = new ArrayList<>();
                for (PlaceNameRepository.PlaceHit hit : placeNames.search(pattern, -1, districtPattern)) {
                    Coordinate c = CoordSystem.SWEREF99TM.convertTo(hit.sweref(), resultCrs);
                    found.add(new Result(c, hit.type() + ", " + hit.district() + " (Lantmäteriet)"));
                }
                return found;
            }

            @Override protected void done() {
                if (!isDisplayable() || isCancelled()) return;
                try {
                    List<Result> found = get();
                    results.removeAll();
                    ArrayList<Coordinate> points = new ArrayList<>();
                    ArrayList<String> labels = new ArrayList<>();
                    if (found.isEmpty()) results.add(new JLabel("No place names found."));
                    for (Result result : found) {
                        JButton button = new JButton(result.label());
                        button.setAlignmentX(Component.LEFT_ALIGNMENT);
                        button.addActionListener(e -> canvas.focus(
                                resultCrs.convertTo(result.coordinate(), canvas.getCRS())));
                        results.add(button);
                        points.add(result.coordinate());
                        labels.add(result.label());
                    }
                    if (!points.isEmpty()) {
                        TNGPointFileLayer layer = new TNGPointFileLayer(points, labels,
                                "Search Results", canvas, resultCrs);
                        layer.setColor(Color.BLUE);
                        canvas.getLayerManager().setOverlay(MapLayers.SEARCH_RESULTS, layer);
                    }
                } catch (Exception e) {
                    results.removeAll();
                    results.add(new JLabel("Search failed: " + e.getMessage()));
                } finally {
                    search.setEnabled(true);
                    busy.setCursorDefault();
                    results.revalidate();
                    results.repaint();
                }
            }
        };
        worker.execute();
    }
}
