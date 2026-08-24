package app.plugin.collection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static app.plugin.collection.CollectionTypes.TaxonNameKind;

/**
 * Loads the Dyntaxa checklist from the Darwin Core Archive downloaded by hand from
 * artfakta.se/metadata/dyntaxa. Everything is streamed line by line: the taxon file is tens of
 * megabytes, and holding it as parsed rows would cost more heap than the map tiles do.
 *
 * <p>The archive's text files declare {@code fieldsEnclosedBy=""}, so a quote character is ordinary
 * data and a tab-separated line can never wrap - splitting on tabs is both the cheapest and the
 * correct way to read them, where a general CSV parser would eat the quotes out of an authorship.
 */
public final class DyntaxaImporter {
    /** How many names were stored, and how many rows carried nothing usable. */
    public record Result(int scientificNames, int vernacularNames, int skippedRows, String archiveName) {}

    /** Artdatabanken's Darwin Core export; the subscription key goes in a header, not the query string. */
    private static final String DOWNLOAD_URL = "https://api.artdatabanken.se/taxonservice/v1/DarwinCore/Download";
    private static final long MAX_ROWS = 2_000_000;
    private static final long MAX_BYTES = 200L << 20;

    private final CollectionRepository repository;

    public DyntaxaImporter(CollectionRepository repository) { this.repository = repository; }

    /**
     * Fetches the current archive from Artdatabanken and imports it, so the checklist can be brought
     * up to date without a trip through a browser.
     *
     * <p>The key is the caller's own, from api-portal.artdatabanken.se. The URL Dyntaxa registers with
     * GBIF carries Artdatabanken's key in the query string; using someone else's subscription for our
     * traffic is not ours to do, and a key we do not control can be withdrawn without warning.
     */
    public Result importFromArtdatabanken(String subscriptionKey) throws IOException, SQLException {
        if (subscriptionKey == null || subscriptionKey.isBlank()) {
            throw new IOException("No Artdatabanken subscription key is set. Register a free one at api-portal.artdatabanken.se and enter it under Settings.");
        }
        Path downloaded = Files.createTempFile("dyntaxa-", ".zip");
        try {
            download(subscriptionKey.trim(), downloaded);
            return importFrom(downloaded, "Artdatabanken, downloaded " + LocalDate.now());
        } finally {
            Files.deleteIfExists(downloaded);
        }
    }

    /** Streamed straight to disk: the archive is tens of megabytes and never needs to be in memory. */
    private static void download(String subscriptionKey, Path target) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(DOWNLOAD_URL))
                .header("Ocp-Apim-Subscription-Key", subscriptionKey)
                .timeout(Duration.ofMinutes(20)).GET().build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
            int code = response.statusCode();
            if (code == 401 || code == 403) throw new IOException("Artdatabanken did not accept the subscription key (HTTP " + code + "). Check it under Settings.");
            if (code != 200) throw new IOException("Artdatabanken returned HTTP " + code + " for the checklist download");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("The checklist download was interrupted");
        }
    }

    public Result importFrom(Path archive) throws IOException, SQLException {
        return importFrom(archive, archive.getFileName().toString());
    }

    private Result importFrom(Path archive, String name) throws IOException, SQLException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry taxa = find(zip, "taxon"), vernacular = find(zip, "vernacularname");
            if (taxa == null) throw new IOException("The archive has no Taxon file. Download the Darwin Core Archive from artfakta.se/metadata/dyntaxa.");
            guardSize(taxa, vernacular);

            // First pass collects the recommended name of every taxon, which is what a synonym and a
            // Swedish name both have to resolve to. Only rows in the Taxon namespace define a taxon:
            // a row keyed by a TaxonName id is one of that taxon's other names, not a taxon of its own.
            Map<Long, String> recommendedById = new HashMap<>();
            Map<Long, Long> mergedInto = new HashMap<>();
            try (Table table = new Table(zip, taxa)) {
                String[] cells;
                while ((cells = table.next()) != null) {
                    Long id = taxonId(table.value(cells, "taxonid")); if (id == null) continue;
                    String scientific = table.value(cells, "scientificname");
                    if (!scientific.isEmpty()) recommendedById.put(id, scientific);
                    // Dyntaxa points accepted taxa at themselves; pointing somewhere else means this
                    // whole taxon was folded into that one. Both ends are Taxon ids, so this is safe.
                    Long accepted = taxonId(table.value(cells, "acceptednameusageid"));
                    if (accepted != null && !accepted.equals(id)) mergedInto.put(id, accepted);
                }
            }

            int[] counts = new int[3]; // scientific, vernacular, skipped
            repository.replaceTaxonNames(name, sink -> {
                try (Table table = new Table(zip, taxa)) {
                    String[] cells;
                    while ((cells = table.next()) != null) {
                        String scientific = table.value(cells, "scientificname");
                        Long own = taxonId(table.value(cells, "taxonid"));
                        Long accepted = taxonId(table.value(cells, "acceptednameusageid"));
                        long id; Long acceptedId;
                        if (own != null) {
                            id = own; acceptedId = mergedInto.get(own);
                        } else if (accepted != null) {
                            // Another name for a taxon, recorded under that same taxon - its own id is a
                            // NamnID, a separate numbering that must never be read as a DyntaxaID.
                            id = current(mergedInto, accepted); acceptedId = id;
                        } else { counts[2]++; continue; }
                        String acceptedName = acceptedId == null ? null : recommendedById.get(acceptedId);
                        // Without a name for the taxon we cannot say what this is a synonym of, and
                        // guessing is how a liverwort ends up recommended as a lichen.
                        if (scientific.isEmpty() || (acceptedId != null && acceptedName == null)) { counts[2]++; continue; }
                        sink.add(new CollectionRepository.TaxonName(scientific, table.value(cells, "scientificnameauthorship"),
                                id, TaxonNameKind.SCIENTIFIC, acceptedId, acceptedName,
                                table.value(cells, "taxonrank"), table.value(cells, "taxonomicstatus")));
                        counts[0]++;
                    }
                }
                if (vernacular == null) return;
                try (Table table = new Table(zip, vernacular)) {
                    String[] cells;
                    while ((cells = table.next()) != null) {
                        Long taxon = taxonId(table.value(cells, "taxonid"));
                        String written = taxon == null ? "" : table.value(cells, "vernacularname");
                        // A Swedish name on a taxon that was folded into another belongs to the survivor.
                        Long id = taxon == null ? null : current(mergedInto, taxon);
                        String acceptedName = id == null ? null : recommendedById.get(id);
                        if (written.isEmpty() || acceptedName == null) { counts[2]++; continue; }
                        sink.add(new CollectionRepository.TaxonName(written, null, id, TaxonNameKind.VERNACULAR,
                                id, acceptedName, table.value(cells, "taxonrank"), null));
                        counts[1]++;
                    }
                }
            });
            return new Result(counts[0], counts[1], counts[2], name);
        }
    }

    /**
     * Finds a file by its bare name, whatever case it is written in and whichever folder of the
     * archive it sits in.
     */
    // ponytail: filenames matched by hand and columns read by header name, so meta.xml is never parsed.
    // If artfakta ever ships headerless files or renames them, read meta.xml's <location> and <field index>.
    private static ZipEntry find(ZipFile zip, String baseName) {
        return zip.stream().filter(e -> !e.isDirectory()).filter(e -> {
            String file = e.getName().replace('\\', '/');
            file = file.substring(file.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            int dot = file.lastIndexOf('.');
            return baseName.equals(dot < 0 ? file : file.substring(0, dot));
        }).findFirst().orElse(null);
    }

    private static void guardSize(ZipEntry... entries) throws IOException {
        long total = 0;
        for (ZipEntry entry : entries) if (entry != null && entry.getSize() > 0) total += entry.getSize();
        if (total > MAX_BYTES) throw new IOException("The archive expands to more than " + (MAX_BYTES >> 20) + " MB - that is not a Dyntaxa checklist");
    }

    /** The taxon a name should be recorded under. One hop only - Dyntaxa does not chain these. */
    private static long current(Map<Long, Long> mergedInto, long taxon) {
        Long into = mergedInto.get(taxon);
        return into == null ? taxon : into;
    }

    /**
     * A DyntaxaID, written urn:lsid:dyntaxa.se:Taxon:2191. The namespace is half the identifier:
     * urn:lsid:dyntaxa.se:TaxonName:319302 is a NamnID, a completely separate numbering, and reading
     * one as the other silently makes Barbilophozia barbata a synonym of whichever lichen happens to
     * carry the same bare number. Anything that is not a Taxon id is not a taxon here.
     */
    private static Long taxonId(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return null;
        int colon = text.lastIndexOf(':');
        if (colon >= 0) {
            if (!"Taxon".equalsIgnoreCase(text.substring(text.lastIndexOf(':', colon - 1) + 1, colon))) return null;
            text = text.substring(colon + 1);
        }
        try { return Long.valueOf(text); } catch (NumberFormatException e) { return null; }
    }

    /** One tab-separated file inside the archive: header read once, data lines handed out one at a time. */
    private static final class Table implements AutoCloseable {
        private final BufferedReader in;
        private final Map<String, Integer> columns = new LinkedHashMap<>();
        private long read;

        Table(ZipFile zip, ZipEntry entry) throws IOException {
            in = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8));
            String header = in.readLine();
            if (header == null) throw new IOException(entry.getName() + " is empty");
            if (!header.isEmpty() && header.charAt(0) == '\uFEFF') header = header.substring(1);
            String[] cells = header.split("\t", -1);
            for (int i = 0; i < cells.length; i++) columns.putIfAbsent(term(cells[i]), i);
            if (!columns.containsKey("taxonid")) {
                throw new IOException(entry.getName() + " has no taxonId column - this archive names its columns"
                        + " in meta.xml, which MiniMap does not read.");
            }
        }

        /** The next data row split on tabs, or null at the end of the file. */
        String[] next() throws IOException {
            String line;
            while ((line = in.readLine()) != null) {
                if (++read > MAX_ROWS) throw new IOException("The checklist has more than " + MAX_ROWS + " rows");
                if (!line.isBlank()) return line.split("\t", -1);
            }
            return null;
        }

        String value(String[] cells, String column) {
            Integer at = columns.get(column);
            return at == null || at >= cells.length ? "" : cells[at].trim();
        }

        /** Header cells may be bare terms or full Darwin Core URIs; only the last segment identifies them. */
        private static String term(String header) {
            String text = header.trim();
            int cut = Math.max(text.lastIndexOf('/'), text.lastIndexOf('#'));
            return text.substring(cut + 1).toLowerCase(Locale.ROOT);
        }

        @Override public void close() throws IOException { in.close(); }
    }
}
