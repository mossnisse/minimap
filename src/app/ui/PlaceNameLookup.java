package app.ui;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static java.util.Map.entry;

/**
 * Looking a place name up outside the application: what the user highlighted,
 * the clipboard, and Ortnamnsregistret.
 */
public final class PlaceNameLookup {
    private PlaceNameLookup() {}

    // Isof groups all of Lappmarken under 'Lappland' ID 24.
    private static final Map<String, Integer> ISOF_PROVINCE_IDS = Map.ofEntries(
            entry("Skåne", 1), entry("Blekinge", 2), entry("Öland", 3),
            entry("Halland", 4), entry("Småland", 5), entry("Gotland", 6),
            entry("Västergötland", 7), entry("Östergötland", 8), entry("Bohuslän", 9),
            entry("Dalsland", 10), entry("Närke", 11), entry("Södermanland", 12),
            entry("Värmland", 13), entry("Västmanland", 14), entry("Uppland", 15),
            entry("Gästrikland", 16), entry("Dalarna", 17), entry("Hälsingland", 18),
            entry("Härjedalen", 19), entry("Medelpad", 20), entry("Ångermanland", 21),
            entry("Jämtland", 22), entry("Västerbotten", 23), entry("Norrbotten", 25),
            entry("Lappland", 24), entry("Torne lappmark", 24), entry("Lule lappmark", 24),
            entry("Pite lappmark", 24), entry("Lycksele lappmark", 24), entry("Åsele lappmark", 24));

    /**
     * The text the user highlighted in whatever field has focus, or
     * {@code fallback} when nothing is selected. Never null.
     */
    public static String selectionOr(String fallback) {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner instanceof javax.swing.text.JTextComponent text) {
            String selection = text.getSelectedText();
            if (selection != null && !selection.isBlank()) return selection.trim();
        }
        return fallback == null ? "" : fallback;
    }

    /** Best-effort copy; a locked or unavailable clipboard is not worth failing over. */
    public static void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (Exception e) {
            System.err.println("Could not copy " + text + " to the clipboard: " + e.getMessage());
        }
    }

    /** Opens the place name in Ortnamnsregistret, narrowed to {@code province} when it is one Isof knows. */
    public static void browseOrtnamnsregistret(String placeName, String province) {
        if (placeName == null || placeName.isEmpty()) return;
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            System.err.println("No browser available for Ortnamnsregistret on this system.");
            return;
        }
        StringBuilder url = new StringBuilder("https://ortnamnsregistret.isof.se/place-names?place-name=")
                .append(URLEncoder.encode(placeName, StandardCharsets.UTF_8));
        Integer provinceId = ISOF_PROVINCE_IDS.get(province);
        if (provinceId != null) url.append("&province-id=").append(provinceId);
        try {
            Desktop.getDesktop().browse(new URI(url.toString()));
        } catch (Exception e) {
            System.err.println("Could not open " + url + ": " + e.getMessage());
        }
    }
}
