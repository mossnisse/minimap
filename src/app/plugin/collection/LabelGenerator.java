package app.plugin.collection;

import app.plugin.collection.CollectionRepository.Event;
import app.plugin.collection.CollectionRepository.Locality;
import app.plugin.collection.CollectionRepository.ReportData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static app.plugin.collection.CollectionTypes.*;

/** Builds deterministic, printable HTML labels with physical millimetre dimensions. */
public final class LabelGenerator {
    public record LabelDocument(LabelType type, long targetId, String contentHash, String bodyHtml) {}
    public record WrittenSheet(Path path, List<LabelDocument> labels) {}

    private static final Map<String, String> PROVINCE_CODES = Map.ofEntries(
            Map.entry("västerbotten", "Vb"), Map.entry("norrbotten", "Nb"),
            Map.entry("lappland", "Lu"), Map.entry("jämtland", "Jä"),
            Map.entry("ångermanland", "Ån"), Map.entry("medelpad", "Me"),
            Map.entry("hälsingland", "Hs"), Map.entry("dalarna", "Dr"),
            Map.entry("uppland", "Up"), Map.entry("södermanland", "Sö"),
            Map.entry("värmland", "Vr"), Map.entry("västmanland", "Vs"),
            Map.entry("närke", "Nä"), Map.entry("östergötland", "Ög"),
            Map.entry("västergötland", "Vg"), Map.entry("bohuslän", "Bo"),
            Map.entry("dalsland", "Ds"), Map.entry("småland", "Sm"),
            Map.entry("öland", "Öl"), Map.entry("gotland", "Go"),
            Map.entry("halland", "Ha"), Map.entry("skåne", "Sk"),
            Map.entry("blekinge", "Bl"), Map.entry("gästrikland", "Gä"),
            Map.entry("härjedalen", "Hr")
    );

    private final CollectionRepository repository;

    public LabelGenerator(CollectionRepository repository) { this.repository = repository; }

    public LabelDocument eventLabel(long eventId) throws Exception {
        Event e = repository.event(eventId); Locality l = repository.locality(e.localityId());
        String geography = join(", ", l.countryCode(), provinceCode(l.province()),
                suffixDistrict(l.district()), first(l.shortName(), l.name()));
        String coordinate = coordinateLine(e.latitude() != null ? e.latitude() : l.latitude(),
                e.longitude() != null ? e.longitude() : l.longitude());
        String collector = repository.collectors(eventId).isEmpty()
                ? repository.setting("owner.fullName", "") : repository.collectors(eventId).getFirst();
        String footer = "Leg. " + collector + dateSuffix(e.startDate(), e.endDate());
        String body = "<div class='line'>" + esc(geography) + "</div>"
                + (coordinate.isBlank() ? "" : "<div class='line coordinate'>" + esc(coordinate) + "</div>")
                + "<div class='line'>" + esc(footer) + "</div>";
        return doc(LabelType.EVENT, eventId, body);
    }

    public LabelDocument determinationLabel(long specimenId) throws Exception {
        ReportData d = repository.reportData(specimenId);
        var det = d.determination();
        String taxon = det == null ? first(d.specimen().preliminaryTaxon(), "Undetermined") : det.taxonName();
        String qualifier = det != null && det.uncertain() ? " cf." : "";
        String verb = det != null && det.kind() == IdentificationKind.CONF ? "conf." : "det.";
        String by = det == null ? "" : first(det.determinerName(), "");
        String year = det == null || det.year() == null ? "" : Integer.toString(det.year());
        String body = "<div class='taxon'><i>" + esc(taxon) + "</i>" + esc(qualifier) + "</div>"
                + "<div class='line'>" + esc(join(" ", verb, by, year)) + "</div>";
        return doc(LabelType.DETERMINATION, specimenId, body);
    }

    public LabelDocument accessionLabel(long specimenId) throws Exception {
        var s = repository.specimen(specimenId);
        String owner = repository.setting("owner.shortName", "N. Ericson");
        String body = "<div class='number'>" + esc(s.accessionNumber()) + "</div>"
                + "<div class='line'>collection " + esc(owner) + "</div>";
        return doc(LabelType.ACCESSION, specimenId, body);
    }

    public LabelDocument botanicalLabel(long specimenId) throws Exception {
        ReportData d = repository.reportData(specimenId);
        Locality l = d.locality(); Event e = d.event();
        String taxon = d.determination() != null ? d.determination().taxonName()
                : first(d.specimen().preliminaryTaxon(), "Undetermined");
        String place = first(l.description(), l.name());
        if (l.nearestPlace() != null && l.nearestDistanceMeters() != null) {
            place = place + ", " + l.nearestDistanceMeters() + " m "
                    + first(l.nearestDirection(), "") + " " + l.nearestPlace();
        }
        String coordinates = coordinateLine(e.latitude() != null ? e.latitude() : l.latitude(),
                e.longitude() != null ? e.longitude() : l.longitude());
        String collectors = join(", ", repository.collectors(e.id()).toArray(String[]::new));
        if (collectors.isBlank()) collectors = repository.setting("owner.fullName", "");
        String body = "<div class='header'>Flora Suecica</div>"
                + "<div class='species'><i>" + esc(taxon) + "</i></div>"
                + "<div><b>" + esc(join(", ", l.countryName(), l.province(), suffixDistrict(l.district()))) + "</b></div>"
                + "<div class='locality'>" + esc(place) + "</div>"
                + optional("Substrat: ", d.specimen().substrate())
                + optional("Biotop: ", e.habitat())
                + (coordinates.isBlank() ? "" : "<div class='coordinates'>" + esc(coordinates) + "</div>")
                + "<div class='spacer'></div><div class='footer'><span>Leg. " + esc(collectors)
                + " &nbsp; " + esc(d.specimen().accessionNumber()) + "</span><span>"
                + esc(formatDate(e.startDate())) + "</span></div>";
        return doc(LabelType.BOTANICAL, specimenId, body);
    }

    public LabelDocument specimenLabel(long specimenId) throws Exception {
        return repository.event(repository.specimen(specimenId).eventId()).kind() == CollectionKind.BOTANICAL
                ? botanicalLabel(specimenId) : determinationLabel(specimenId);
    }

    public WrittenSheet writeEventSheet(long eventId, int copies) throws Exception {
        LabelDocument label = eventLabel(eventId);
        return writeSheet("event-" + eventId, repeat(label, Math.max(1, copies)), false);
    }

    public WrittenSheet writeSpecimenSheet(List<Long> specimenIds, LabelType type) throws Exception {
        List<LabelDocument> labels = new ArrayList<>();
        for (long id : specimenIds) labels.add(switch (type) {
            case DETERMINATION -> determinationLabel(id);
            case ACCESSION -> accessionLabel(id);
            case BOTANICAL -> botanicalLabel(id);
            default -> throw new IllegalArgumentException("Unsupported specimen label type: " + type);
        });
        return writeSheet(type.name().toLowerCase(Locale.ROOT), labels, type == LabelType.BOTANICAL);
    }

    public WrittenSheet writeReservedNumberSheet(List<String> numbers) throws Exception {
        List<LabelDocument> labels = new ArrayList<>();
        String owner = repository.setting("owner.shortName", "N. Ericson");
        for (String number : numbers) {
            String body = "<div class='number'>" + esc(number) + "</div><div class='line'>collection " + esc(owner) + "</div>";
            labels.add(doc(LabelType.ACCESSION, 0, body));
        }
        return writeSheet("reserved-numbers", labels, false);
    }

    private WrittenSheet writeSheet(String stem, List<LabelDocument> labels, boolean botanical) throws IOException {
        String css = botanical ? botanicalCss() : insectCss();
        StringBuilder html = new StringBuilder("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
                .append(css).append("</style></head><body><div class='controls'><button onclick='window.print()'>Print labels</button></div><main>");
        for (LabelDocument label : labels) html.append("<section class='label'>").append(label.bodyHtml()).append("</section>");
        html.append("</main></body></html>");
        String fileName = stem + "-" + LocalDate.now() + ".html";
        Path output = unique(repository.database().labelsDirectory().resolve(fileName));
        Files.writeString(output, html, StandardCharsets.UTF_8);
        return new WrittenSheet(output, List.copyOf(labels));
    }

    private static String insectCss() {
        return "@page{size:A4;margin:10mm}*{box-sizing:border-box}body{margin:0;font-family:Arial,sans-serif}.controls{margin:8px}@media print{.controls{display:none}}main{display:flex;flex-wrap:wrap;align-content:flex-start}.label{width:20mm;height:10mm;border:.15mm solid #bbb;padding:.45mm;overflow:hidden;font-size:5.4pt;line-height:1.08;color:#000}.taxon{font-size:5.8pt}.number{font-size:7pt;font-weight:bold;text-align:center}.coordinate{font-size:4.8pt}.line{white-space:normal}";
    }

    private static String botanicalCss() {
        return "@page{size:A4;margin:12.7mm}*{box-sizing:border-box}body{margin:0;font-family:'Times New Roman',serif}.controls{margin:8px}@media print{.controls{display:none}}main{display:grid;grid-template-columns:repeat(2,92mm);justify-content:center}.label{width:92mm;height:64mm;border:.2mm solid #000;padding:3mm;overflow:hidden;display:flex;flex-direction:column;font-size:9pt;line-height:1.15}.header{text-align:center;font-weight:bold;font-size:11pt;border-bottom:.2mm solid #000}.species{font-size:10pt;margin-top:1mm}.locality{font-weight:bold;margin-top:1mm}.coordinates{font-family:monospace;font-size:8pt}.spacer{flex:1}.footer{display:flex;justify-content:space-between}";
    }

    private static LabelDocument doc(LabelType type, long target, String body) {
        return new LabelDocument(type, target, sha256(type + "|" + target + "|" + body), body);
    }
    private static List<LabelDocument> repeat(LabelDocument label, int copies) {
        List<LabelDocument> result = new ArrayList<>(copies); for (int i = 0; i < copies; i++) result.add(label); return result;
    }
    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static Path unique(Path requested) {
        if (!Files.exists(requested)) return requested;
        String name = requested.getFileName().toString(); int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot), ext = dot < 0 ? "" : name.substring(dot);
        int i = 1; Path candidate; do { candidate = requested.resolveSibling(base + "-" + i++ + ext); } while (Files.exists(candidate)); return candidate;
    }
    private static String optional(String label, String value) { return value == null || value.isBlank() ? "" : "<div>" + esc(label + value) + "</div>"; }
    private static String provinceCode(String value) { if (value == null) return null; return PROVINCE_CODES.getOrDefault(value.toLowerCase(Locale.ROOT), value); }
    private static String suffixDistrict(String value) { return value == null || value.isBlank() ? null : value.endsWith(" sn") ? value : value + " sn"; }
    private static String coordinateLine(Double lat, Double lon) { return lat == null || lon == null ? "" : String.format(Locale.US, "WGS84: %.5f, %.5f", lat, lon); }
    private static String dateSuffix(LocalDate start, LocalDate end) { if (start == null) return ""; return " " + formatDate(start) + (end != null && !end.equals(start) ? "–" + formatDate(end) : ""); }
    private static String formatDate(LocalDate date) { return date == null ? "" : date.format(DateTimeFormatter.ISO_LOCAL_DATE); }
    private static String first(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String join(String delimiter, String... values) { return java.util.Arrays.stream(values).filter(v -> v != null && !v.isBlank()).collect(java.util.stream.Collectors.joining(delimiter)); }
    private static String esc(String value) { if (value == null) return ""; return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
}
