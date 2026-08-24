package app.plugin.collection;

import app.plugin.collection.CollectionRepository.ReportData;
import gis.coords.CoordSystem;
import gis.coords.Coordinate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static app.plugin.collection.CollectionTypes.*;

/** Exact semicolon/BOM Artportalen file contract and local reporting state. */
public final class ArtportalenExporter {
    public static final int MAX_ROWS = 2000;
    public static final List<String> HEADERS = List.of(
            "Artnamn", "Antal", "Enhet", "Antal substrat", "Ålder-Stadium", "Kön", "Aktivitet", "Metod",
            "Lokalnamn", "Ost", "Nord", "Noggrannhet", "Diffusion", "Djup min", "Djup max", "Höjd min", "Höjd max",
            "Startdatum", "Starttid", "Slutdatum", "Sluttid", "Publik kommentar", "Intressant kommentar",
            "Privat kommentar", "Ej återfunnen", "Dölj fyndet t.o.m.", "Andrahand", "Osäker artbestämning",
            "Ospontan", "Biotop", "Biotop-beskrivning", "Art som substrat", "Art som substrat beskrivning",
            "Substrat", "Substrat-beskrivning", "Offentlig samling", "Privat samling", "Samlings-nummer",
            "Bestämningsmetod", "Artbestämd av", "Artbestämd av (fritext)", "Bestämningsår", "Beskrivning artbestämning",
            "Bekräftad av", "Bekräftad av (fritext)", "Bekräftelseår", "Länk till BOLD/GenBank",
            "Med-observatör", "Med-observatör", "Med-observatör", "Med-observatör", "Med-observatör",
            "Med-observatör", "Med-observatör", "Med-observatör", "Med-observatör", "Med-observatör",
            "Externid", "Ej funnen");

    private static final Set<Integer> ACCURACY = Set.of(1,5,10,25,50,75,100,125,150,200,250,300,400,500,750,1000,1500,2000,2500,3000,5000);
    private static final Set<String> INVERTEBRATE_METHODS = Set.of("Observerad","Slaghåvning","Vattenhåvning","Bilhåvning","Lampa","Nattsök med lampa","Ljusfälla","Lockbete","Feromon","Fallfälla","Färgskål","Fönsterfälla","Malaisefälla","Limfälla","Stationär sugfälla","Kläckningsfälla","Fälla","Mobil sugsamlare","Sållning","Bankning","Mosskramning","Dränkning","Trampning","Utdrivning","Substratprov","Ultraljudsdetektor","Bottenhämtare","Bottenskrapa","Bottenhuggare","Bottensläde","Surberprovtagare","Ryssja/mjärde","Fiske med nät","Backlina","Trålfiske","Dykning","Slag-/vattenhåvning","Insamlad för uppfödning/kläckning","eDNA");
    private static final Set<String> SEX = Set.of("Hane", "Hona", "I par", "Arbetare");
    private static final Set<String> INVERTEBRATE_STAGES = Set.of("Ägg", "Larv/Nymf", "Puppa", "Juvenil", "Imago/Adult", "Adult");

    /** Warnings never block the export - Artportalen's own import wizard is the final validator. */
    public record PreparedRow(long specimenId, String csvLine, String payloadHash, List<String> warnings) {
        public boolean complete() { return warnings.isEmpty(); }
    }
    public record ExportResult(Path path, List<Long> specimenIds, Map<Long, String> payloadHashes,
                               List<String> warnings) {}

    private final CollectionRepository repository;

    public ArtportalenExporter(CollectionRepository repository) { this.repository = repository; }

    public PreparedRow prepare(long specimenId) throws Exception {
        ReportData d = repository.reportData(specimenId); List<String> warnings = new ArrayList<>();
        // Same precedence the specimen table shows: the current determination, else the working name.
        String taxonName = d.determination() != null ? d.determination().taxonName() : d.specimen().preliminaryTaxon();
        if (taxonName == null || taxonName.isBlank()) warnings.add(d.specimen().accessionNumber() + ": no taxon name at all");
        else if (d.determination() == null) warnings.add(d.specimen().accessionNumber() + ": reported under the preliminary name " + taxonName);
        if (d.event().startDate() == null) warnings.add(d.specimen().accessionNumber() + ": date is missing");
        if (d.effectiveLatitude() == null || d.effectiveLongitude() == null) warnings.add(d.specimen().accessionNumber() + ": coordinate is missing");
        if (d.effectiveUncertainty() != null && d.effectiveUncertainty() > 5000) {
            warnings.add(d.specimen().accessionNumber() + ": coordinate uncertainty exceeds Artportalen's 5000 m maximum");
        }
        if (!"SE".equalsIgnoreCase(d.locality().countryCode())) warnings.add(d.specimen().accessionNumber() + ": only Swedish records can be exported");
        if (d.event().method() != null && !d.event().method().isBlank()
                && d.event().kind() == CollectionKind.INSECT && !INVERTEBRATE_METHODS.contains(d.event().method())) {
            warnings.add(d.specimen().accessionNumber() + ": unknown Artportalen method " + d.event().method());
        }
        if (d.specimen().sex() != null && !d.specimen().sex().isBlank() && !SEX.contains(d.specimen().sex())) warnings.add(d.specimen().accessionNumber() + ": unknown sex " + d.specimen().sex());
        if (d.event().kind() == CollectionKind.INSECT && d.specimen().lifeStage() != null && !d.specimen().lifeStage().isBlank() && !INVERTEBRATE_STAGES.contains(d.specimen().lifeStage())) warnings.add(d.specimen().accessionNumber() + ": unknown stage " + d.specimen().lifeStage());

        List<String> columns = new ArrayList<>(); for (int i = 0; i < HEADERS.size(); i++) columns.add("");
        columns.set(0, clean(taxonName));
        columns.set(1, Integer.toString(d.specimen().quantity())); columns.set(4, clean(d.specimen().lifeStage()));
        columns.set(5, clean(d.specimen().sex())); columns.set(7, clean(d.event().method()));
        columns.set(8, truncate(clean(d.locality().name()), 75));
        if (d.effectiveLatitude() != null && d.effectiveLongitude() != null) {
            Coordinate sweref = CoordSystem.SWEREF99TM.toProjected(d.effectiveLatitude(), d.effectiveLongitude());
            if (!CoordSystem.SWEREF99TM.isValid(sweref)) warnings.add(d.specimen().accessionNumber() + ": coordinate is outside SWEREF 99 TM");
            columns.set(9, String.format(Locale.US, "%.0f", sweref.getEast())); columns.set(10, String.format(Locale.US, "%.0f", sweref.getNorth()));
        }
        columns.set(11, accuracy(d.effectiveUncertainty()));
        if (d.event().elevationMeters() != null) columns.set(15, Long.toString(Math.round(d.event().elevationMeters())));
        columns.set(17, date(d.event().startDate())); columns.set(18, time(d.event().localTime()));
        columns.set(19, date(d.event().endDate())); columns.set(20, time(d.event().endTime()));
        columns.set(21, truncate(clean(d.specimen().comments()), 1000)); columns.set(23, truncate(clean(d.event().notes()), 1000));
        if (d.determination() != null && d.determination().uncertain()) columns.set(27, "Ja");
        columns.set(29, clean(d.event().habitat())); columns.set(33, clean(d.specimen().substrate()));
        columns.set(36, clean(repository.setting("artportalen.privateCollection", "")));
        columns.set(37, clean(d.specimen().accessionNumber()));
        if (d.determination() != null) {
            int nameColumn = d.determination().kind() == IdentificationKind.CONF ? 44 : 40;
            int yearColumn = d.determination().kind() == IdentificationKind.CONF ? 45 : 41;
            columns.set(nameColumn, clean(d.determination().determinerName()));
            if (d.determination().year() != null) columns.set(yearColumn, Integer.toString(d.determination().year()));
            columns.set(42, clean(d.determination().notes()));
        }
        String owner = repository.setting("owner.fullName", ""); int observerColumn = 47;
        for (String collector : d.collectors()) {
            if (collector.equalsIgnoreCase(owner)) continue;
            if (observerColumn > 56) { warnings.add(d.specimen().accessionNumber() + ": more than ten co-observers"); break; }
            columns.set(observerColumn++, clean(collector));
        }
        String line = columns.stream().map(ArtportalenExporter::csv).collect(java.util.stream.Collectors.joining(";"));
        return new PreparedRow(specimenId, line, LabelGenerator.sha256(line), List.copyOf(warnings));
    }

    public ExportResult exportReady(Path requested) throws Exception {
        List<PreparedRow> rows = new ArrayList<>(); List<String> warnings = new ArrayList<>();
        for (long id : repository.reportCandidateIds()) {
            PreparedRow row = prepare(id); ReportStatus status = repository.reportStatus(id, row.complete(), row.payloadHash());
            if (status != ReportStatus.READY && status != ReportStatus.INCOMPLETE) continue;
            rows.add(row); warnings.addAll(row.warnings());
        }
        if (rows.isEmpty()) throw new IllegalStateException("Nothing to export. Every included specimen is already exported or reported.");
        if (rows.size() > MAX_ROWS) throw new IllegalStateException("Export contains " + rows.size() + " rows; maximum is " + MAX_ROWS);
        Path output = requested == null ? repository.database().exportsDirectory().resolve("artportalen-" + LocalDate.now() + ".csv") : requested;
        output = unique(output); Files.createDirectories(output.toAbsolutePath().getParent());
        StringBuilder text = new StringBuilder("\uFEFF").append(String.join(";", HEADERS)).append("\r\n");
        Map<Long, String> hashes = new LinkedHashMap<>(); List<Long> ids = new ArrayList<>();
        for (PreparedRow row : rows) { text.append(row.csvLine()).append("\r\n"); hashes.put(row.specimenId(), row.payloadHash()); ids.add(row.specimenId()); }
        Files.writeString(output, text, StandardCharsets.UTF_8);
        repository.recordExport(output.toString(), hashes);
        return new ExportResult(output, List.copyOf(ids), Map.copyOf(hashes), List.copyOf(warnings));
    }

    public Map<ReportStatus, Integer> statusCounts() throws Exception {
        Map<ReportStatus, Integer> result = new LinkedHashMap<>(); for (ReportStatus s : ReportStatus.values()) result.put(s, 0);
        for (long id : repository.reportCandidateIds()) { PreparedRow row = prepare(id); ReportStatus s = repository.reportStatus(id, row.complete(), row.payloadHash()); result.put(s, result.get(s) + 1); }
        return result;
    }

    public void confirmLatestExport() throws Exception {
        List<CollectionRepository.ReportExport> pending = repository.pendingExports();
        if (pending.isEmpty()) throw new IllegalStateException("There is no export to confirm");
        confirmExport(pending.getFirst().id());
    }

    public void confirmExport(long exportId) throws Exception {
        Map<Long, String> exported = repository.exportHashes(exportId);
        if (exported.isEmpty()) throw new IllegalStateException("The selected export does not exist or contains no records");
        for (long id : exported.keySet()) {
            PreparedRow row = prepare(id);
            if (!row.payloadHash().equals(exported.get(id))) {
                throw new IllegalStateException("Collection data changed after export for specimen " + id
                        + ". Create a new export or review the change before confirmation.");
            }
        }
        repository.confirmReported(new ArrayList<>(exported.keySet()), exported);
    }

    public void confirmManualCorrection(long specimenId) throws Exception {
        PreparedRow row = prepare(specimenId);
        if (repository.reportStatus(specimenId, row.complete(), row.payloadHash()) != ReportStatus.UPDATE_NEEDED) {
            throw new IllegalStateException("The selected specimen is not waiting for an Artportalen correction");
        }
        repository.confirmCurrentReport(specimenId, row.payloadHash());
    }

    private static String accuracy(Integer meters) {
        int value = meters == null || meters < 1 ? 100 : meters;
        return ACCURACY.stream().sorted().filter(v -> value <= v).findFirst().orElse(5000) + " m";
    }
    private static String clean(String text) { return text == null ? "" : text.replace(';', ',').replace('\r', ' ').replace('\n', ' ').trim(); }
    private static String truncate(String text, int max) { return text.length() <= max ? text : text.substring(0, max); }
    private static String date(LocalDate value) { return value == null ? "" : value.toString(); }
    private static String time(java.time.LocalTime value) { return value == null ? "" : value.withSecond(0).withNano(0).toString(); }
    private static String csv(String value) { String v = value == null ? "" : value; return v.indexOf(';') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 ? "\"" + v.replace("\"", "\"\"") + "\"" : v; }
    private static Path unique(Path requested) {
        if (!Files.exists(requested)) return requested; String n = requested.getFileName().toString(); int dot = n.lastIndexOf('.');
        String base = dot < 0 ? n : n.substring(0, dot), ext = dot < 0 ? "" : n.substring(dot); int i = 1; Path p;
        do { p = requested.resolveSibling(base + "-" + i++ + ext); } while (Files.exists(p)); return p;
    }
}
