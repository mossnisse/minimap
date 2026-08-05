package app.plugin.collection;

import app.plugin.collection.CollectionRepository.Event;
import app.plugin.collection.CollectionRepository.Locality;
import app.plugin.collection.CollectionRepository.Specimen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static app.plugin.collection.CollectionTypes.*;

/** Safe, idempotent reader for SlimeRecords CSV and portable ZIP archives. */
public final class SlimeRecordsImporter {
    private static final long MAX_CSV = 10L * 1024 * 1024;
    private static final long MAX_PHOTO = 25L * 1024 * 1024;
    /** Zip-bomb guard: expansion ratio, not absolute size — photo archives are already-compressed JPEGs (~1:1). */
    private static final int MAX_EXPANSION = 10;
    private static final int MAX_ENTRIES = 5_000;

    public record ImportRow(int sourceRow, CollectionKind kind, String sourceId, String fingerprint,
                            Map<String, String> values, List<String> errors) {
        public ImportRow withKind(CollectionKind selected) {
            return new ImportRow(sourceRow, selected, sourceId, fingerprint, values, errors);
        }
        public String value(String key) { return values.getOrDefault(key.toLowerCase(Locale.ROOT), ""); }
        public boolean valid() { return errors.isEmpty(); }
    }

    public static final class Preview implements AutoCloseable {
        private final Path source, staging;
        private final List<ImportRow> rows;
        private final Map<String, Path> photos;
        private final String sourceHash;
        private Preview(Path source, Path staging, List<ImportRow> rows, Map<String, Path> photos, String sourceHash) {
            this.source = source; this.staging = staging; this.rows = rows; this.photos = photos; this.sourceHash = sourceHash;
        }
        public Path source() { return source; }
        public List<ImportRow> rows() { return rows; }
        public void setKind(int index, CollectionKind kind) { rows.set(index, rows.get(index).withKind(kind)); }
        public String sourceHash() { return sourceHash; }
        Map<String, Path> photos() { return photos; }
        @Override public void close() throws IOException { if (staging != null) deleteTree(staging); }
    }

    public record ImportResult(int addedEvents, int addedSpecimens, int skipped,
                               int photosCopied, List<String> warnings) {}

    private final CollectionRepository repository;

    public SlimeRecordsImporter(CollectionRepository repository) { this.repository = repository; }

    public Preview preview(Path source, CollectionKind defaultKind) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("Import file does not exist: " + source);
        String hash = sha256(source); String csv; Path staging = null; Map<String, Path> photos = new HashMap<>();
        try (InputStream input = Files.newInputStream(source)) {
            byte[] signature = input.readNBytes(2);
            if (signature.length == 2 && signature[0] == 'P' && signature[1] == 'K') {
                staging = repository.database().root().resolve("import-staging-" + UUID.randomUUID());
                Files.createDirectories(staging); Archive archive = extractArchive(source, staging); csv = archive.csv(); photos.putAll(archive.photos());
            } else {
                if (Files.size(source) > MAX_CSV) throw new IOException("CSV is larger than 10 MB");
                csv = Files.readString(source, StandardCharsets.UTF_8);
            }
            List<ImportRow> rows = parseRows(csv, defaultKind == null ? CollectionKind.INSECT : defaultKind);
            return new Preview(source.toAbsolutePath(), staging, rows, photos, hash);
        } catch (Exception e) {
            if (staging != null) deleteTree(staging);
            if (e instanceof IOException io) throw io; throw new IOException(e);
        }
    }

    public ImportResult commit(Preview preview) throws Exception {
        for (ImportRow row : preview.rows()) if (!row.valid()) throw new IllegalArgumentException(String.join("\n", row.errors()));
        int skipped = 0, projectedAdded = 0;
        Set<String> sourceIds = new HashSet<>(), fingerprints = new HashSet<>();
        for (ImportRow row : preview.rows()) {
            boolean duplicateInPreview = !sourceIds.add(row.sourceId()) || !fingerprints.add(row.fingerprint());
            if (duplicateInPreview || repository.sourceExists(row.sourceId(), row.fingerprint())) skipped++;
            else projectedAdded++;
        }
        Connection c = repository.database().connection(); boolean auto = c.getAutoCommit(); c.setAutoCommit(false);
        Path photoBatch = null;
        try {
            long batchId = repository.createImportBatch(preview.source().toString(), preview.sourceHash(), projectedAdded, skipped, 0);
            photoBatch = repository.database().photosDirectory().resolve("import-" + batchId); Files.createDirectories(photoBatch);
            int addedEvents = 0, addedSpecimens = 0, copied = 0; List<String> warnings = new ArrayList<>();
            Map<String, Long> botanicalEvents = new HashMap<>();
            for (ImportRow row : preview.rows()) {
                if (repository.sourceExists(row.sourceId(), row.fingerprint())) continue;
                DateParts when = parseDate(row.value("eventdate"));
                double lat = parseDouble(row.value("decimallatitude"), "decimalLatitude");
                double lon = parseDouble(row.value("decimallongitude"), "decimalLongitude");
                long localityId = findOrCreateLocality(row, lat, lon);
                List<String> collectors = splitCollectors(row.value("recordedby"));
                long eventId;
                String groupKey = row.kind() == CollectionKind.BOTANICAL ? botanicalGroupKey(row, lat, lon) : null;
                if (groupKey != null && botanicalEvents.containsKey(groupKey)) {
                    eventId = botanicalEvents.get(groupKey);
                } else {
                    Integer quantity = optionalInt(row.value("organismquantity"));
                    Event event = new Event(0, localityId, row.kind(),
                            row.kind() == CollectionKind.INSECT ? emptyToNull(row.value("specimennr")) : null,
                            when.date(), null, when.time(), null,
                            repository.setting("timezone", "Europe/Stockholm"), lat, lon,
                            optionalInt(row.value("coordinateuncertaintyinmeters")), optionalDouble(row.value("verbatimelevation")),
                            CoordinateSource.PHONE, emptyToNull(row.value("samplingprotocol")), null,
                            row.kind() == CollectionKind.INSECT && quantity != null ? quantity : 0,
                            emptyToNull(row.value("habitat")), emptyToNull(row.value("occurrenceremarks")),
                            emptyToNull(row.value("taxonname")));
                    eventId = repository.saveEvent(event, collectors); addedEvents++;
                    if (groupKey != null) botanicalEvents.put(groupKey, eventId);
                }
                Long specimenId = null;
                if (row.kind() == CollectionKind.BOTANICAL && isSpecimen(row)) {
                    String accession = normalizeBotanicalNumber(row.value("specimennr"), row.sourceRow(), warnings);
                    Specimen specimen = new Specimen(0, eventId, accession, null,
                            emptyToNull(row.value("taxonname")), Math.max(1, optionalInt(row.value("organismquantity")) == null ? 1 : optionalInt(row.value("organismquantity"))),
                            emptyToNull(row.value("sex")), emptyToNull(row.value("lifestage")),
                            emptyToNull(row.value("substrate")), emptyToNull(row.value("occurrenceremarks")),
                            ReportIntent.INCLUDE, false);
                    specimenId = repository.saveSpecimen(specimen); addedSpecimens++;
                }
                copied += copyPhotos(row, preview.photos(), photoBatch, eventId, warnings);
                repository.recordSource(row.sourceId(), row.fingerprint(), eventId, specimenId, batchId);
            }
            c.commit(); return new ImportResult(addedEvents, addedSpecimens, skipped, copied, List.copyOf(warnings));
        } catch (Exception e) {
            c.rollback(); if (photoBatch != null) deleteTree(photoBatch); throw e;
        } finally { if (auto) c.setAutoCommit(true); preview.close(); }
    }

    private long findOrCreateLocality(ImportRow row, double lat, double lon) throws SQLException {
        String name = emptyToNull(row.value("locality")); if (name == null) name = "Imported locality " + row.sourceRow();
        for (Locality l : repository.localities()) {
            if (l.name().equalsIgnoreCase(name) && l.latitude() != null
                    && Math.abs(l.latitude() - lat) < 0.000001 && Math.abs(l.longitude() - lon) < 0.000001) return l.id();
        }
        Locality locality = new Locality(0, name, null, emptyToNull(row.value("countrycode")),
                emptyToNull(row.value("country")), emptyToNull(row.value("province")),
                emptyToNull(row.value("district")), null, lat, lon,
                optionalInt(row.value("coordinateuncertaintyinmeters")), CoordinateSource.PHONE,
                null, null, null);
        return repository.saveLocality(locality);
    }

    private int copyPhotos(ImportRow row, Map<String, Path> staged, Path destination,
                           long eventId, List<String> warnings) throws Exception {
        String value = row.value("photos"); if (value.isBlank()) return 0; int copied = 0;
        for (String requested : value.split("\\|")) {
            String safe = Path.of(requested.trim()).getFileName().toString(); Path source = staged.get(safe);
            if (source == null || !Files.isRegularFile(source)) { warnings.add("Row " + row.sourceRow() + ": photo not found: " + safe); continue; }
            Path target = unique(destination.resolve(safe)); Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            String relative = repository.database().root().relativize(target).toString().replace('\\', '/');
            try (PreparedStatement ps = repository.database().connection().prepareStatement("INSERT INTO photo(event_id,file_name,relative_path,sha256) VALUES (?,?,?,?)")) {
                ps.setLong(1, eventId); ps.setString(2, target.getFileName().toString()); ps.setString(3, relative); ps.setString(4, sha256(target)); ps.executeUpdate();
            }
            copied++;
        }
        return copied;
    }

    /** Only an explicit "false" marks a field observation; exports without the column stay all-specimen as before. */
    private static boolean isSpecimen(ImportRow row) { return !row.value("isspecimen").equalsIgnoreCase("false"); }

    /**
     * The source app does not guarantee unique collection numbers, so a taken number
     * yields a freshly reserved one instead of failing the whole import.
     */
    private String normalizeBotanicalNumber(String raw, int sourceRow, List<String> warnings) throws SQLException {
        String value = emptyToNull(raw); if (value == null) return null;
        String prefix = repository.setting("accession.prefix", "NE");
        String number = value.chars().allMatch(Character::isDigit) ? prefix + value : value;
        if (number.startsWith(prefix)) {
            String suffix = number.substring(prefix.length());
            if (!suffix.isBlank() && suffix.chars().allMatch(Character::isDigit)) advanceCounter(Long.parseLong(suffix));
        }
        if (repository.accessionAvailable(number)) return number;
        String fresh = repository.reserveNumbers(1).getFirst();
        warnings.add("Row " + sourceRow + ": collection number " + number + " was already used, assigned " + fresh + " instead");
        return fresh;
    }

    private void advanceCounter(long importedNumber) throws SQLException {
        long next = Long.parseLong(repository.setting("accession.next", "1"));
        if (next <= importedNumber) repository.setSetting("accession.next", Long.toString(importedNumber + 1));
    }

    private static List<ImportRow> parseRows(String csv, CollectionKind defaultKind) throws IOException {
        if (csv.startsWith("\uFEFF")) csv = csv.substring(1);
        List<List<String>> records = parseCsv(csv, detectDelimiter(csv));
        if (records.size() < 2) throw new IOException("CSV contains no data rows");
        Map<String, Integer> header = new HashMap<>();
        for (int i = 0; i < records.getFirst().size(); i++) header.put(records.getFirst().get(i).trim().toLowerCase(Locale.ROOT), i);
        for (String required : List.of("decimallatitude", "decimallongitude", "eventdate")) if (!header.containsKey(required)) throw new IOException("Unrecognized SlimeRecords CSV: missing " + required);
        List<ImportRow> result = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> cells = records.get(i); if (cells.size() == 1 && cells.getFirst().isBlank()) continue;
            Map<String, String> values = new LinkedHashMap<>();
            for (var h : header.entrySet()) values.put(h.getKey(), h.getValue() < cells.size() ? cells.get(h.getValue()).trim() : "");
            List<String> errors = new ArrayList<>();
            double lat = validateDouble(values.get("decimallatitude"), -90, 90, "decimalLatitude", i + 1, errors);
            double lon = validateDouble(values.get("decimallongitude"), -180, 180, "decimalLongitude", i + 1, errors);
            String date = values.getOrDefault("eventdate", ""); try { parseDate(date); } catch (Exception e) { errors.add("Row " + (i + 1) + ": invalid eventDate"); }
            String fingerprint = rowFingerprint(values);
            String exportedId = values.getOrDefault("id", "");
            String sourceId = exportedId.isBlank() ? "fp:" + fingerprint : "id:" + exportedId;
            result.add(new ImportRow(i + 1, defaultKind, sourceId, fingerprint, Map.copyOf(values), List.copyOf(errors)));
        }
        return result;
    }

    /** Stable identity for source rows that have no exported ID, and a safe unique-index value for all rows. */
    private static String rowFingerprint(Map<String, String> values) {
        StringBuilder canonical = new StringBuilder();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String key = entry.getKey(), value = entry.getValue();
            canonical.append(key.length()).append(':').append(key)
                    .append('=').append(value.length()).append(':').append(value).append(';');
        });
        return LabelGenerator.sha256(canonical.toString());
    }

    private record Archive(String csv, Map<String, Path> photos) {}
    private static Archive extractArchive(Path source, Path staging) throws IOException {
        String csv = null; Map<String, Path> photos = new HashMap<>(); long total = 0; int entries = 0;
        long maxExtracted = Math.max(MAX_CSV, Files.size(source) * MAX_EXPANSION);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(source), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IOException("ZIP contains more than 5000 entries");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.contains(":/")) throw new IOException("Unsafe ZIP entry: " + name);
                if (entry.isDirectory()) continue;
                if (name.toLowerCase(Locale.ROOT).endsWith(".csv") && !name.startsWith("photos/")) {
                    byte[] bytes = readLimited(zip, MAX_CSV); total += bytes.length; csv = new String(bytes, StandardCharsets.UTF_8);
                } else if (name.startsWith("photos/")) {
                    String safe = Path.of(name).getFileName().toString(); if (safe.isBlank()) continue;
                    byte[] bytes = readLimited(zip, MAX_PHOTO); total += bytes.length; if (total > maxExtracted) throw new IOException("ZIP expands beyond " + (maxExtracted >> 20) + " MB - looks like a zip bomb");
                    Path target = unique(staging.resolve(safe)); Files.write(target, bytes); photos.put(safe, target);
                }
            }
        }
        if (csv == null) throw new IOException("ZIP does not contain data.csv"); return new Archive(csv, photos);
    }

    private static byte[] readLimited(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; long total = 0; int read;
        while ((read = input.read(buffer)) != -1) { total += read; if (total > limit) throw new IOException("Archive entry is too large"); output.write(buffer, 0, read); }
        return output.toByteArray();
    }

    static List<List<String>> parseCsv(String text, char delimiter) throws IOException {
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') { if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; } else quoted = false; }
                else cell.append(ch);
            } else if (ch == '"') quoted = true;
            else if (ch == delimiter) { row.add(cell.toString()); cell.setLength(0); }
            else if (ch == '\n') { row.add(cell.toString()); cell.setLength(0); rows.add(row); row = new ArrayList<>(); }
            else if (ch != '\r') cell.append(ch);
        }
        if (quoted) throw new IOException("Unclosed quoted CSV field");
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
        return rows;
    }

    private static char detectDelimiter(String csv) { int end = csv.indexOf('\n'); String first = end < 0 ? csv : csv.substring(0, end); return count(first, ';') > count(first, ',') ? ';' : ','; }
    private static int count(String text, char ch) { int n = 0; for (int i = 0; i < text.length(); i++) if (text.charAt(i) == ch) n++; return n; }
    private record DateParts(LocalDate date, LocalTime time) {}
    private static DateParts parseDate(String value) {
        String text = value == null ? "" : value.trim(); if (text.isBlank()) throw new IllegalArgumentException();
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"), DateTimeFormatter.ISO_LOCAL_DATE_TIME)) {
            try { LocalDateTime dt = LocalDateTime.parse(text, formatter); return new DateParts(dt.toLocalDate(), dt.toLocalTime().withNano(0)); } catch (Exception ignored) {}
        }
        return new DateParts(LocalDate.parse(text), null);
    }
    private static String botanicalGroupKey(ImportRow row, double lat, double lon) { return String.format(Locale.US, "%.6f|%.6f|%s|%s|%s|%s", lat, lon, row.value("eventdate"), row.value("locality"), row.value("samplingprotocol"), row.value("recordedby")); }
    private static List<String> splitCollectors(String value) { if (value == null || value.isBlank()) return List.of(); return java.util.Arrays.stream(value.split("\\||;")).map(String::trim).filter(s -> !s.isBlank()).toList(); }
    private static double parseDouble(String value, String label) { try { double v = Double.parseDouble(value.replace(',', '.')); if (!Double.isFinite(v)) throw new NumberFormatException(); return v; } catch (Exception e) { throw new IllegalArgumentException(label + " is not a number"); } }
    private static double validateDouble(String value, double min, double max, String label, int row, List<String> errors) { try { double v = parseDouble(value, label); if (v < min || v > max) errors.add("Row " + row + ": " + label + " is outside valid range"); return v; } catch (Exception e) { errors.add("Row " + row + ": " + e.getMessage()); return 0; } }
    private static Integer optionalInt(String value) { try { return value == null || value.isBlank() ? null : (int)Math.ceil(Double.parseDouble(value.replace(',', '.'))); } catch (Exception e) { return null; } }
    private static Double optionalDouble(String value) { try { return value == null || value.isBlank() ? null : Double.parseDouble(value.replace(',', '.')); } catch (Exception e) { return null; } }
    private static String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static Path unique(Path requested) { if (!Files.exists(requested)) return requested; String n = requested.getFileName().toString(); int dot = n.lastIndexOf('.'); String b = dot < 0 ? n : n.substring(0,dot), e = dot < 0 ? "" : n.substring(dot); int i=1; Path p; do { p=requested.resolveSibling(b+"-"+i+++e); } while(Files.exists(p)); return p; }
    private static String sha256(Path file) throws IOException { try { MessageDigest md = MessageDigest.getInstance("SHA-256"); try (InputStream in=Files.newInputStream(file)) { byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) md.update(b,0,n); } return HexFormat.of().formatHex(md.digest()); } catch (Exception e) { throw new IOException(e); } }
    private static void deleteTree(Path root) throws IOException { if (root == null || !Files.exists(root)) return; try (var stream=Files.walk(root)) { for (Path p : stream.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
}
