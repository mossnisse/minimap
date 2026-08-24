package app.plugin.collection;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static app.plugin.collection.CollectionTypes.TaxonNameKind;

/**
 * Species name entry with the Dyntaxa checklist behind it: names are suggested while typing, a
 * synonym says what Dyntaxa recommends instead, and a Swedish name resolves to the scientific one.
 *
 * <p>Free text is always allowed. Field notes carry names Dyntaxa does not have - undescribed
 * species, working names, "Chyliza sp." - and refusing them would lose the record. A name that is
 * not in the list is simply saved without an id, and the status line says so without shouting.
 *
 * <p>The rows are handed out through {@link #formRows()} so the caller can splice them into its own
 * label/field form, the same way {@code gis.ui.CoordinateEntry} does.
 */
public final class TaxonEntry {
    private static final int SUGGESTIONS = 12;
    private static final String EMPTY = "Free text is fine - start typing for names from Dyntaxa.";

    private final CollectionRepository repository;
    private final String label;
    private final JTextField field = new JTextField(28);
    private final JLabel status = new JLabel();
    private final DefaultListModel<CollectionRepository.TaxonName> model = new DefaultListModel<>();
    private final JList<CollectionRepository.TaxonName> list = new JList<>(model);
    private final JPopupMenu popup = new JPopupMenu();

    /** Last suggestion the user picked; only trusted while the field still holds exactly that name. */
    private CollectionRepository.TaxonName picked;
    /** Guards the document listener against the programmatic writes in setName and choose. */
    private boolean writing;
    /** Stale-result token, the same idea the manager's refresh worker uses. */
    private long generation;
    private SwingWorker<Answer, Void> worker;

    public TaxonEntry(CollectionRepository repository, String label) {
        this.repository = repository; this.label = label;
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focused) {
                return super.getListCellRendererComponent(l, describe((CollectionRepository.TaxonName)value), index, selected, focused);
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0) { list.setSelectedIndex(index); choose(model.get(index)); hidePopup(); }
            }
        });
        // A focusable popup installs a menu grab that swallows keystrokes; leaving it unfocusable keeps
        // the caret in the field, so typing carries on while the list is open and clicks still land.
        popup.setFocusable(false);
        popup.setBorder(BorderFactory.createLineBorder(UIManager.getColor("controlShadow")));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        popup.add(scroll);

        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
            private void changed() { if (!writing) typed(); }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { navigate(e); }
        });
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { hidePopup(); }
        });
        updateStatus();
    }

    /** Label/field pairs to splice into a form, in display order. */
    public Object[] formRows() { return new Object[]{ label, field, "", status }; }

    /** The bare field, for the one caller with a single-line layout and no room for a status row. */
    public JTextField field() { return field; }

    /** What was typed. Free text is fine here, so this never throws and never rewrites the name. */
    public String name() { return field.getText().trim(); }

    /**
     * The Dyntaxa taxon this name resolves to, or null when Dyntaxa does not have it or is ambiguous
     * about it. A synonym reports the accepted taxon's id, because that is the concept being recorded.
     */
    public Long dyntaxaId() {
        try {
            CollectionRepository.TaxonName match = resolved(name(), picked);
            return match == null ? null : match.reportedId();
        } catch (SQLException e) { return null; }
    }

    public void setName(String value) {
        writing = true;
        try { field.setText(value == null ? "" : value); } finally { writing = false; }
        picked = null;
        updateStatus();
    }

    /** Names starting with what is typed. Package-private so the test can drive this without a window. */
    List<CollectionRepository.TaxonName> lookup(String typed) throws SQLException {
        List<CollectionRepository.TaxonName> found = new ArrayList<>(repository.suggestTaxonNames(typed, SUGGESTIONS));
        // Ordered here rather than in SQL: sorting in the query would sort the whole matched range.
        found.sort(Comparator.comparing((CollectionRepository.TaxonName t) -> t.kind() == TaxonNameKind.SCIENTIFIC ? 0 : 1)
                .thenComparing(t -> t.synonym() ? 1 : 0)
                .thenComparing(CollectionRepository.TaxonName::name));
        return found;
    }

    /**
     * Takes a suggestion. The <em>accepted scientific</em> name goes into the field, not the row's own
     * name - that one line is what turns a synonym into the recommended name and a Swedish name into
     * the scientific one.
     */
    void choose(CollectionRepository.TaxonName suggestion) {
        writing = true;
        try { field.setText(suggestion.scientificName()); } finally { writing = false; }
        picked = suggestion;
        updateStatus();
    }

    String statusText() { return status.getText(); }

    /** The suggestion list and what the status line should say, both worked out in one background pass. */
    private record Answer(List<CollectionRepository.TaxonName> suggestions, String status) {}

    private void typed() {
        picked = null;
        long mine = ++generation;
        String typed = name();
        if (typed.length() < 2) {
            hidePopup();
            status.setForeground(UIManager.getColor("Label.foreground"));
            status.setText(typed.isEmpty() ? EMPTY : "Keep typing for names from Dyntaxa.");
            return;
        }
        if (worker != null) worker.cancel(true);
        // Off the EDT not because the queries are slow - they are indexed lookups - but because every
        // repository call takes the one shared connection, and a dashboard refresh or a checklist import
        // can be holding it for seconds. Nothing in here touches a Swing component.
        worker = new SwingWorker<>() {
            @Override protected Answer doInBackground() throws Exception { return new Answer(lookup(typed), statusFor(typed, null)); }
            @Override protected void done() {
                if (mine != generation || isCancelled()) return;
                try {
                    Answer answer = get();
                    status.setForeground(UIManager.getColor("Label.foreground"));
                    status.setText(answer.status());
                    showSuggestions(answer.suggestions());
                } catch (Exception e) { hidePopup(); error(e); }
                finally { if (worker == this) worker = null; }
            }
        };
        worker.execute();
    }

    private void showSuggestions(List<CollectionRepository.TaxonName> found) {
        model.clear();
        for (CollectionRepository.TaxonName t : found) model.addElement(t);
        if (model.isEmpty() || !field.isShowing()) { hidePopup(); return; }
        list.setSelectedIndex(0);
        list.setVisibleRowCount(Math.min(model.size(), SUGGESTIONS));
        popup.setPopupSize(Math.max(field.getWidth(), 320), list.getPreferredScrollableViewportSize().height + 4);
        popup.show(field, 0, field.getHeight());
        // Showing the popup must not take the caret away from what is still being typed.
        field.requestFocusInWindow();
    }

    /**
     * The editor's default button is Save, so a bare Enter would store the record. While the list is
     * open every key it uses has to be consumed here - a key listener runs before the field's own
     * bindings and before the root pane's default button, and a consumed event reaches neither.
     */
    private void navigate(KeyEvent e) {
        if (!popup.isVisible()) return;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_DOWN -> { move(1); e.consume(); }
            case KeyEvent.VK_UP -> { move(-1); e.consume(); }
            case KeyEvent.VK_ENTER -> {
                CollectionRepository.TaxonName selected = list.getSelectedValue();
                if (selected != null) { choose(selected); hidePopup(); e.consume(); }
            }
            case KeyEvent.VK_ESCAPE -> { hidePopup(); e.consume(); }
            default -> { }
        }
    }

    private void move(int step) {
        if (model.isEmpty()) return;
        int next = Math.floorMod(list.getSelectedIndex() + step, model.size());
        list.setSelectedIndex(next); list.ensureIndexIsVisible(next);
    }

    private void hidePopup() { if (popup.isVisible()) popup.setVisible(false); }

    /**
     * The taxon this text stands for. A pick counts only while the text still equals it, so editing
     * after picking drops the id instead of leaving a stale one attached, and an ambiguous name that
     * nobody picked from the list resolves to nothing rather than to a guess.
     */
    private CollectionRepository.TaxonName resolved(String typed, CollectionRepository.TaxonName pick) throws SQLException {
        if (typed.isEmpty()) return null;
        if (matches(typed, pick)) return pick;
        List<CollectionRepository.TaxonName> exact = repository.taxonNamesExactly(typed, 2);
        return exact.size() == 1 ? exact.getFirst() : null;
    }

    private static boolean matches(String typed, CollectionRepository.TaxonName pick) {
        return pick != null && typed.equalsIgnoreCase(pick.scientificName());
    }

    /** Everything the status line says, worked out without touching a Swing component. */
    private String statusFor(String typed, CollectionRepository.TaxonName pick) throws SQLException {
        if (typed.isEmpty()) return EMPTY;
        if (repository.taxonNameCount() == 0) return "No species list imported yet - use “Update species list…” in the toolbar.";
        List<CollectionRepository.TaxonName> exact = repository.taxonNamesExactly(typed, 4);
        if (exact.isEmpty()) return "Not in Dyntaxa - saved as typed, without an id.";
        if (exact.size() > 1 && !matches(typed, pick)) {
            return exact.size() + " taxa in Dyntaxa share this name - pick one in the list to store an id.";
        }
        return describeStatus(matches(typed, pick) ? pick : exact.getFirst());
    }

    /** Only for the once-per-dialog writes; typing goes through the worker instead. */
    private void updateStatus() {
        status.setForeground(UIManager.getColor("Label.foreground"));
        try { status.setText(statusFor(name(), picked)); }
        catch (SQLException e) { error(e); }
    }

    private static String describeStatus(CollectionRepository.TaxonName match) {
        if (match.kind() == TaxonNameKind.VERNACULAR) {
            return match.name() + " → " + match.scientificName() + " (Dyntaxa " + match.reportedId() + ")";
        }
        if (match.synonym()) {
            String status = match.status() == null || match.status().isBlank() ? "Synonym" : "Synonym (" + match.status() + ")";
            return status + ". Dyntaxa recommends: " + match.scientificName();
        }
        StringBuilder text = new StringBuilder("Dyntaxa ").append(match.dyntaxaId()).append(" · ").append(match.name());
        if (match.authorship() != null && !match.authorship().isBlank()) text.append(' ').append(match.authorship());
        if (match.rank() != null && !match.rank().isBlank()) text.append(" · ").append(match.rank());
        return text.toString();
    }

    /** One suggestion row: what a synonym or Swedish name resolves to is shown next to it. */
    private static String describe(CollectionRepository.TaxonName t) {
        StringBuilder text = new StringBuilder(t.name());
        if (t.authorship() != null && !t.authorship().isBlank()) text.append(' ').append(t.authorship());
        if (t.kind() == TaxonNameKind.VERNACULAR || t.synonym()) text.append("  →  ").append(t.scientificName());
        return text.toString();
    }

    private void error(Exception e) {
        status.setForeground(Color.RED.darker());
        status.setText("Could not read the species list: " + e.getMessage());
    }
}
