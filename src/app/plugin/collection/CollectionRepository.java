package app.plugin.collection;

import gis.coords.CoordSystem;
import gis.coords.Coordinate;
import gis.geometry.Extent;
import gis.layers.PointTableLayer;

import java.sql.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;

import static app.plugin.collection.CollectionTypes.*;

/** Transactional persistence API for collection records. */
public final class CollectionRepository {
    public record Locality(long id, String name, String shortName, String countryCode,
                           String countryName, String province, String district, String description,
                           Double latitude, Double longitude, Integer uncertaintyMeters,
                           CoordinateSource coordinateSource, String nearestPlace,
                           Integer nearestDistanceMeters, String nearestDirection) {
        public boolean hasCoordinate() { return latitude != null && longitude != null; }
    }

    public record Event(long id, long localityId, CollectionKind kind, String fieldNumber,
                        LocalDate startDate, LocalDate endDate, LocalTime localTime, LocalTime endTime,
                        String timezone, Double latitude, Double longitude, Integer uncertaintyMeters,
                        Double elevationMeters, CoordinateSource coordinateSource, String method,
                        String trapNumber, int expectedCount, String habitat, String notes,
                        String preliminaryTaxon) {}

    public record Specimen(long id, long eventId, String accessionNumber, String taxonGroup,
                           String preliminaryTaxon, int quantity, String sex, String lifeStage,
                           String substrate, String comments, ReportIntent reportIntent,
                           boolean archived) {}

    public record Determination(long id, long specimenId, String taxonName, Long dyntaxaId,
                                Long determinerId, String determinerName, Integer year,
                                IdentificationKind kind, boolean uncertain, String notes,
                                boolean current) {}

    public record EventRow(long id, CollectionKind kind, String fieldNumber, LocalDate date,
                           String localityName, int expectedCount, int specimenCount) {}

    public record SpecimenRow(long id, String accessionNumber, String taxonName,
                              String localityName, LocalDate date, boolean determined,
                              ReportIntent reportIntent) {}

    public record Dashboard(int tubesAwaitingSpecimens, int undetermined, int unprinted,
                            int ready, int exported, int reported, int updateNeeded) {}

    /**
     * One searchable name from the Dyntaxa checklist. A synonym and a vernacular name both carry the
     * accepted taxon they belong to, so a single row answers "what should this be called instead".
     */
    public record TaxonName(String name, String authorship, long dyntaxaId, TaxonNameKind kind,
                            Long acceptedId, String acceptedName, String rank, String status) {
        /** A vernacular name always points at its taxon, so only a scientific name can be a synonym. */
        public boolean synonym() { return kind == TaxonNameKind.SCIENTIFIC && acceptedId != null; }
        /** The id a report should carry: a synonym is reported under the taxon it is a synonym of. */
        public long reportedId() { return acceptedId != null ? acceptedId : dyntaxaId; }
        /** What belongs on a label: a synonym or a Swedish name resolves to the accepted scientific name. */
        public String scientificName() { return acceptedName != null ? acceptedName : name; }
    }

    /** Receives checklist rows one at a time, so a 200 000-name import never sits in memory as a list. */
    public interface TaxonNameSink { void add(TaxonName row) throws SQLException; }
    /** Produces checklist rows into a sink; run inside the replacing transaction. */
    public interface TaxonNameFeed { void feed(TaxonNameSink sink) throws Exception; }

    public record ReportExport(long id, String outputPath, LocalDateTime createdAt, int rowCount) {
        @Override public String toString() {
            return createdAt + " — " + Path.of(outputPath).getFileName() + " (" + rowCount + " records)";
        }
    }

    /** Flattened data used by labels and Artportalen export. */
    public record ReportData(Specimen specimen, Event event, Locality locality,
                             Determination determination, List<String> collectors) {
        public Double effectiveLatitude() {
            return event.latitude() != null ? event.latitude() : locality.latitude();
        }
        public Double effectiveLongitude() {
            return event.longitude() != null ? event.longitude() : locality.longitude();
        }
        public Integer effectiveUncertainty() {
            return event.uncertaintyMeters() != null ? event.uncertaintyMeters() : locality.uncertaintyMeters();
        }
    }

    private final CollectionDatabase database;
    private Map<String, String> settingCache;

    public CollectionRepository(CollectionDatabase database) { this.database = database; }
    CollectionDatabase database() { return database; }

    private interface TransactionBody<T> { T run(Connection c) throws Exception; }

    /**
     * Runs {@code body} as one unit of work. A nested call (createSpecimenBatch
     * calling saveSpecimen) finds auto-commit already off and leaves both the
     * commit and the auto-commit reset to the outermost call, so the whole batch
     * commits or rolls back together.
     */
    private <T> T transactional(TransactionBody<T> body) throws SQLException {
        Connection c = database.connection();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            T result = body.run(c);
            if (auto) c.commit();
            return result;
        } catch (Exception e) {
            c.rollback();
            // A read inside the transaction may have cached a value that the
            // rollback just undid.
            settingCache = null;
            if (e instanceof SQLException sql) throw sql;
            throw new SQLException(e);
        } finally {
            if (auto) c.setAutoCommit(true);
        }
    }

    public synchronized String setting(String key, String fallback) throws SQLException {
        String value = settings().get(key);
        return value != null ? value : fallback;
    }

    public synchronized void setSetting(String key, String value) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "MERGE INTO collection_setting(setting_key,setting_value) KEY(setting_key) VALUES (?,?)")) {
            ps.setString(1, key); ps.setString(2, value == null ? "" : value); ps.executeUpdate();
        }
        settingCache = null;
    }

    /**
     * The whole settings table, held until the next write. Label and export
     * rendering asks for a handful of keys per specimen, so a dashboard over a
     * few thousand specimens issued thousands of identical single-key queries.
     */
    public synchronized Map<String, String> settings() throws SQLException {
        if (settingCache != null) return settingCache;
        Map<String, String> result = new LinkedHashMap<>();
        try (Statement s = database.connection().createStatement();
             ResultSet rs = s.executeQuery("SELECT setting_key,setting_value FROM collection_setting ORDER BY setting_key")) {
            while (rs.next()) result.put(rs.getString(1), rs.getString(2));
        }
        settingCache = Collections.unmodifiableMap(result);
        return settingCache;
    }

    public synchronized long ensurePerson(String fullName, String shortName) throws SQLException {
        String name = require(fullName, "Person name");
        try (PreparedStatement find = database.connection().prepareStatement("SELECT id FROM person WHERE full_name=?")) {
            find.setString(1, name);
            try (ResultSet rs = find.executeQuery()) { if (rs.next()) return rs.getLong(1); }
        }
        try (PreparedStatement insert = database.connection().prepareStatement(
                "INSERT INTO person(full_name,short_name,artportalen_name) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, name); insert.setString(2, blankToNull(shortName)); insert.setString(3, name);
            insert.executeUpdate(); return generatedId(insert);
        }
    }

    public synchronized List<String> people() throws SQLException {
        List<String> result = new ArrayList<>();
        try (Statement s = database.connection().createStatement();
             ResultSet rs = s.executeQuery("SELECT full_name FROM person ORDER BY full_name")) {
            while (rs.next()) result.add(rs.getString(1));
        }
        return result;
    }

    public synchronized Path addPhoto(long eventId, Path source) throws Exception {
        if (!Files.isRegularFile(source)) throw new IllegalArgumentException("Photo does not exist: " + source);
        Path directory = database.photosDirectory().resolve("manual"); Files.createDirectories(directory);
        Path target = uniqueFile(directory.resolve(source.getFileName().toString()));
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        String relative = database.root().relativize(target).toString().replace('\\', '/');
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO photo(event_id,file_name,relative_path,sha256) VALUES (?,?,?,?)")) {
            ps.setLong(1, eventId); ps.setString(2, target.getFileName().toString()); ps.setString(3, relative); ps.setString(4, fileHash(target)); ps.executeUpdate();
        } catch (Exception e) { Files.deleteIfExists(target); throw e; }
        return target;
    }

    public synchronized List<Path> eventPhotos(long eventId) throws SQLException {
        List<Path> result = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT relative_path FROM photo WHERE event_id=? ORDER BY id")) {
            ps.setLong(1, eventId); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(database.root().resolve(rs.getString(1)).normalize()); }
        }
        return result;
    }

    public synchronized long saveLocality(Locality v) throws SQLException {
        validateCoordinate(v.latitude(), v.longitude());
        if (v.id() == 0) {
            try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO locality " +
                    "(name,short_name,country_code,country_name,province,district,description,latitude,longitude,uncertainty_m,coordinate_source,nearest_place,nearest_distance_m,nearest_direction) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                bindLocality(ps, v); ps.executeUpdate(); return generatedId(ps);
            }
        }
        try (PreparedStatement ps = database.connection().prepareStatement("UPDATE locality SET " +
                "name=?,short_name=?,country_code=?,country_name=?,province=?,district=?,description=?,latitude=?,longitude=?,uncertainty_m=?,coordinate_source=?,nearest_place=?,nearest_distance_m=?,nearest_direction=?,modified_at=CURRENT_TIMESTAMP WHERE id=?")) {
            bindLocality(ps, v); ps.setLong(15, v.id());
            if (ps.executeUpdate() != 1) throw new SQLException("Locality no longer exists");
            return v.id();
        }
    }

    private static void bindLocality(PreparedStatement ps, Locality v) throws SQLException {
        ps.setString(1, require(v.name(), "Locality name"));
        ps.setString(2, blankToNull(v.shortName())); ps.setString(3, upperOrNull(v.countryCode()));
        ps.setString(4, blankToNull(v.countryName())); ps.setString(5, blankToNull(v.province()));
        ps.setString(6, blankToNull(v.district())); ps.setString(7, blankToNull(v.description()));
        nullableDouble(ps, 8, v.latitude()); nullableDouble(ps, 9, v.longitude());
        nullableInt(ps, 10, v.uncertaintyMeters());
        ps.setString(11, (v.coordinateSource() == null ? CoordinateSource.TEXT_ONLY : v.coordinateSource()).name());
        ps.setString(12, blankToNull(v.nearestPlace())); nullableInt(ps, 13, v.nearestDistanceMeters());
        ps.setString(14, blankToNull(v.nearestDirection()));
    }

    public synchronized Locality locality(long id) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT * FROM locality WHERE id=?")) {
            ps.setLong(1, id); try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Unknown locality: " + id); return readLocality(rs);
            }
        }
    }

    public synchronized List<Locality> localities() throws SQLException {
        List<Locality> result = new ArrayList<>();
        try (Statement s = database.connection().createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM locality ORDER BY name,province,district")) {
            while (rs.next()) result.add(readLocality(rs));
        }
        return result;
    }

    public synchronized List<Locality> duplicateLocalities(Locality v) throws SQLException {
        List<Locality> result = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT * FROM locality WHERE id<>? AND LOWER(name)=LOWER(?) AND COALESCE(country_code,'')=COALESCE(?,'') AND COALESCE(province,'')=COALESCE(?,'') AND COALESCE(district,'')=COALESCE(?,'')")) {
            ps.setLong(1, v.id()); ps.setString(2, v.name()); ps.setString(3, blankToNull(v.countryCode()));
            ps.setString(4, blankToNull(v.province())); ps.setString(5, blankToNull(v.district()));
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(readLocality(rs)); }
        }
        return result;
    }

    /** When the row was last written, or null if it no longer exists; lets an open editor notice it is stale. */
    public synchronized Timestamp localityModifiedAt(long id) throws SQLException { return modifiedAt("locality", id); }

    public synchronized Timestamp eventModifiedAt(long id) throws SQLException { return modifiedAt("collection_event", id); }

    public synchronized Timestamp specimenModifiedAt(long id) throws SQLException { return modifiedAt("specimen", id); }

    private Timestamp modifiedAt(String table, long id) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT modified_at FROM " + table + " WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getTimestamp(1) : null; }
        }
    }

    /** How many collection events are recorded at this locality. */
    public synchronized int localityEventCount(long id) throws SQLException {
        return scalar("SELECT COUNT(*) FROM collection_event WHERE locality_id=?", id);
    }

    /** How many specimens were taken at this event. */
    public synchronized int eventSpecimenCount(long id) throws SQLException {
        return scalar("SELECT COUNT(*) FROM specimen WHERE event_id=?", id);
    }

    /** Removes a locality nothing refers to; events keep the place they were collected at. */
    public synchronized void deleteLocality(long id) throws SQLException {
        int events = localityEventCount(id);
        if (events > 0) throw new SQLException("The locality is used by " + events + " collection event" + (events == 1 ? "" : "s"));
        if (execute(database.connection(), "DELETE FROM locality WHERE id=?", id) != 1) throw new SQLException("Unknown locality: " + id);
    }

    /**
     * Removes an event nothing was collected under; its collectors, photo rows and import
     * trace go with it, so a deleted import can be imported again. The photo files stay on
     * disk - they were copied in by hand and are not this repository's to throw away.
     */
    public synchronized void deleteEvent(long id) throws SQLException {
        transactional(c -> {
            int specimens = eventSpecimenCount(id);
            if (specimens > 0) throw new SQLException("The event holds " + specimens + " specimen" + (specimens == 1 ? "" : "s"));
            execute(c, "DELETE FROM source_record WHERE event_id=?", id);
            if (execute(c, "DELETE FROM collection_event WHERE id=?", id) != 1) throw new SQLException("Unknown event: " + id);
            return null;
        });
    }

    /**
     * Removes a specimen that has never been sent to Artportalen; its determinations go with it.
     * The collection number returns to the reserved pool, because {@link #nextNumberFor} counts the
     * specimens under an envelope and would otherwise hand out a number the pool still calls used.
     */
    public synchronized void deleteSpecimen(long id) throws SQLException {
        transactional(c -> {
            Specimen s = specimen(id);
            int exported = scalar("SELECT COUNT(*) FROM report_export_item WHERE specimen_id=?", id)
                    + scalar("SELECT COUNT(*) FROM report_confirmation WHERE specimen_id=?", id);
            if (exported > 0) throw new SQLException("The specimen has already been exported to Artportalen");
            execute(c, "DELETE FROM source_record WHERE specimen_id=?", id);
            if (execute(c, "DELETE FROM specimen WHERE id=?", id) != 1) throw new SQLException("Unknown specimen: " + id);
            try (PreparedStatement ps = c.prepareStatement("UPDATE accession_number SET state='RESERVED',assigned_at=NULL WHERE number=?")) {
                ps.setString(1, s.accessionNumber()); ps.executeUpdate();
            }
            return null;
        });
    }

    private static int execute(Connection c, String sql, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) { ps.setLong(1, id); return ps.executeUpdate(); }
    }

    public synchronized List<PointTableLayer.LabeledPoint> eventPoints(Extent bounds) throws SQLException {
        List<PointTableLayer.LabeledPoint> result = new ArrayList<>();
        String sql = "SELECT e.field_number,l.name,COALESCE(e.latitude,l.latitude) lat,COALESCE(e.longitude,l.longitude) lon,COALESCE(e.uncertainty_m,l.uncertainty_m,0) uncertainty FROM collection_event e JOIN locality l ON l.id=e.locality_id WHERE COALESCE(e.latitude,l.latitude) BETWEEN ? AND ? AND COALESCE(e.longitude,l.longitude) BETWEEN ? AND ?";
        try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
            ps.setDouble(1, Math.min(bounds.c1.getNorth(), bounds.c2.getNorth()));
            ps.setDouble(2, Math.max(bounds.c1.getNorth(), bounds.c2.getNorth()));
            ps.setDouble(3, Math.min(bounds.c1.getEast(), bounds.c2.getEast()));
            ps.setDouble(4, Math.max(bounds.c1.getEast(), bounds.c2.getEast()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new PointTableLayer.LabeledPoint(
                        new Coordinate(rs.getDouble("lat"), rs.getDouble("lon")),
                        firstNonBlank(rs.getString("field_number"), rs.getString("name")),
                        rs.getInt("uncertainty")));
            }
        }
        return result;
    }

    public synchronized long saveEvent(Event event, List<String> collectors) throws SQLException {
        validateCoordinate(event.latitude(), event.longitude());
        // A botanical field number is the collection number written on the envelope, so "6" is stored as NE6.
        // An insect tube number is a different thing and is kept verbatim.
        Event v = event.kind() != CollectionKind.BOTANICAL ? event
                : new Event(event.id(), event.localityId(), event.kind(), withPrefix(event.fieldNumber()),
                        event.startDate(), event.endDate(), event.localTime(), event.endTime(), event.timezone(),
                        event.latitude(), event.longitude(), event.uncertaintyMeters(), event.elevationMeters(),
                        event.coordinateSource(), event.method(), event.trapNumber(), event.expectedCount(),
                        event.habitat(), event.notes(), event.preliminaryTaxon());
        validateEventNumberingChange(v);
        try {
            return saveEventRow(v, collectors);
        } catch (SQLException e) { throw duplicateFieldNumber(e, v.fieldNumber()); }
    }

    private long saveEventRow(Event v, List<String> collectors) throws SQLException {
        return transactional(c -> {
            long id;
            if (v.id() == 0) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO collection_event " +
                        "(locality_id,kind,field_number,start_date,end_date,local_time,end_time,timezone,latitude,longitude,uncertainty_m,elevation_m,coordinate_source,method,trap_number,expected_count,habitat,notes,preliminary_taxon) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    bindEvent(ps, v); ps.executeUpdate(); id = generatedId(ps);
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement("UPDATE collection_event SET locality_id=?,kind=?,field_number=?,start_date=?,end_date=?,local_time=?,end_time=?,timezone=?,latitude=?,longitude=?,uncertainty_m=?,elevation_m=?,coordinate_source=?,method=?,trap_number=?,expected_count=?,habitat=?,notes=?,preliminary_taxon=?,modified_at=CURRENT_TIMESTAMP WHERE id=?")) {
                    bindEvent(ps, v); ps.setLong(20, v.id());
                    if (ps.executeUpdate() != 1) throw new SQLException("Event no longer exists"); id = v.id();
                }
            }
            replaceCollectors(c, id, collectors);
            // The envelope owns this number before it contains a named species. Keep the global allocator
            // past it now, otherwise an insect specimen can take the number in the meantime.
            if (v.kind() == CollectionKind.BOTANICAL) advanceCounter(v.fieldNumber());
            return id;
        });
    }

    /** A populated botanical event cannot change the number family already printed on its specimens. */
    private void validateEventNumberingChange(Event v) throws SQLException {
        if (v.id() == 0 || eventSpecimenCount(v.id()) == 0) return;
        Event old = event(v.id());
        if (old.kind() != v.kind()) {
            throw new SQLException("The event type cannot be changed after specimens have been added");
        }
        if (old.kind() == CollectionKind.BOTANICAL
                && !java.util.Objects.equals(blankToNull(old.fieldNumber()), blankToNull(v.fieldNumber()))) {
            throw new SQLException("The collection number cannot be changed after species have been added");
        }
    }

    /** A collection number identifies one collection, so the unique index has to speak plainly. */
    private static SQLException duplicateFieldNumber(SQLException e, String fieldNumber) {
        return e.getMessage() != null && e.getMessage().contains("UQ_EVENT_FIELD_NUMBER")
                ? new SQLException("Collection number already used by another collection: " + fieldNumber, e) : e;
    }

    private static void bindEvent(PreparedStatement ps, Event v) throws SQLException {
        ps.setLong(1, v.localityId()); ps.setString(2, (v.kind() == null ? CollectionKind.INSECT : v.kind()).name());
        ps.setString(3, blankToNull(v.fieldNumber())); nullableDate(ps, 4, v.startDate()); nullableDate(ps, 5, v.endDate());
        nullableTime(ps, 6, v.localTime()); nullableTime(ps, 7, v.endTime()); ps.setString(8, blankToNull(v.timezone()));
        nullableDouble(ps, 9, v.latitude()); nullableDouble(ps, 10, v.longitude()); nullableInt(ps, 11, v.uncertaintyMeters());
        nullableDouble(ps, 12, v.elevationMeters());
        ps.setString(13, (v.coordinateSource() == null ? CoordinateSource.LOCALITY_FALLBACK : v.coordinateSource()).name());
        ps.setString(14, blankToNull(v.method())); ps.setString(15, blankToNull(v.trapNumber()));
        ps.setInt(16, Math.max(0, v.expectedCount())); ps.setString(17, blankToNull(v.habitat()));
        ps.setString(18, blankToNull(v.notes())); ps.setString(19, blankToNull(v.preliminaryTaxon()));
    }

    private void replaceCollectors(Connection c, long eventId, List<String> collectors) throws SQLException {
        try (PreparedStatement delete = c.prepareStatement("DELETE FROM event_collector WHERE event_id=?")) {
            delete.setLong(1, eventId); delete.executeUpdate();
        }
        if (collectors == null) return;
        int ordinal = 0;
        for (String name : collectors) {
            if (name == null || name.isBlank()) continue;
            long personId = ensurePerson(name.trim(), null);
            try (PreparedStatement insert = c.prepareStatement("INSERT INTO event_collector(event_id,person_id,ordinal) VALUES (?,?,?)")) {
                insert.setLong(1, eventId); insert.setLong(2, personId); insert.setInt(3, ordinal++); insert.executeUpdate();
            }
        }
    }

    public synchronized Event event(long id) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT * FROM collection_event WHERE id=?")) {
            ps.setLong(1, id); try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Unknown event: " + id); return readEvent(rs);
            }
        }
    }

    public synchronized List<String> collectors(long eventId) throws SQLException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT p.full_name FROM event_collector ec JOIN person p ON p.id=ec.person_id WHERE ec.event_id=? ORDER BY ec.ordinal")) {
            ps.setLong(1, eventId); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(rs.getString(1)); }
        }
        return result;
    }

    public synchronized List<EventRow> events(String filter) throws SQLException {
        List<EventRow> result = new ArrayList<>();
        String term = "%" + (filter == null ? "" : filter.trim().toLowerCase()) + "%";
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT e.id,e.kind,e.field_number,e.start_date,l.name,e.expected_count,COALESCE(SUM(s.quantity),0) specimen_count FROM collection_event e JOIN locality l ON l.id=e.locality_id LEFT JOIN specimen s ON s.event_id=e.id AND s.archived=FALSE WHERE LOWER(COALESCE(e.field_number,'') || ' ' || l.name || ' ' || COALESCE(e.notes,'')) LIKE ? GROUP BY e.id,e.kind,e.field_number,e.start_date,l.name,e.expected_count ORDER BY e.start_date DESC,e.id DESC")) {
            ps.setString(1, term); try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new EventRow(rs.getLong(1), CollectionKind.valueOf(rs.getString(2)),
                        rs.getString(3), date(rs, 4), rs.getString(5), rs.getInt(6), rs.getInt(7)));
            }
        }
        return result;
    }

    public synchronized List<String> reserveNumbers(int count) throws SQLException {
        if (count < 1 || count > 10_000) throw new IllegalArgumentException("Reserve 1-10000 numbers");
        return transactional(c -> {
            String prefix = setting("accession.prefix", "NE");
            long next = Long.parseLong(setting("accession.next", "1"));
            List<String> result = new ArrayList<>(count);
            try (PreparedStatement insert = c.prepareStatement("INSERT INTO accession_number(number,numeric_part,state) VALUES (?,?,'RESERVED')")) {
                for (int i = 0; i < count; i++) {
                    String number = prefix + (next + i); insert.setString(1, number); insert.setLong(2, next + i);
                    insert.addBatch(); result.add(number);
                }
                insert.executeBatch();
            }
            setSetting("accession.next", Long.toString(next + count));
            return result;
        });
    }

    public synchronized List<String> reservedNumbers() throws SQLException {
        List<String> result = new ArrayList<>();
        try (Statement s = database.connection().createStatement();
             ResultSet rs = s.executeQuery("SELECT number FROM accession_number WHERE state='RESERVED' ORDER BY numeric_part,number")) {
            while (rs.next()) result.add(rs.getString(1));
        }
        return result;
    }

    public synchronized void markReservedNumbersPrinted(List<String> numbers) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement("UPDATE accession_number SET printed_at=CURRENT_TIMESTAMP WHERE number=? AND state='RESERVED'")) {
            for (String number : numbers) { ps.setString(1, number); ps.addBatch(); } ps.executeBatch();
        }
    }

    /** Whether {@link #claimAccession} would accept this number: unknown, or reserved but not yet assigned. */
    public synchronized boolean accessionAvailable(String number) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT state FROM accession_number WHERE number=?")) {
            ps.setString(1, number);
            try (ResultSet rs = ps.executeQuery()) { return !rs.next() || "RESERVED".equals(rs.getString(1)); }
        }
    }

    public synchronized long saveSpecimen(Specimen v) throws SQLException {
        return transactional(c -> {
            if (v.id() != 0) {
                Specimen old = specimen(v.id());
                if (old.eventId() != v.eventId()) {
                    Event from = event(old.eventId()), to = event(v.eventId());
                    if (from.kind() == CollectionKind.BOTANICAL || to.kind() == CollectionKind.BOTANICAL) {
                        throw new SQLException("A botanical specimen cannot be moved to another collection event");
                    }
                }
            }
            // Allocating inside the transaction means a failed insert no longer burns a number.
            String accession = blankToNull(v.accessionNumber());
            if (v.id() == 0 && accession == null) accession = nextNumberFor(v.eventId());
            validateAccessionOwner(accession, v.eventId());
            if (v.id() == 0) {
                claimAccession(c, accession);
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO specimen(event_id,accession_number,taxon_group,preliminary_taxon,quantity,sex,life_stage,substrate,comments,report_intent,archived) VALUES (?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    bindSpecimen(ps, v, accession); ps.executeUpdate(); return generatedId(ps);
                }
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE specimen SET event_id=?,accession_number=?,taxon_group=?,preliminary_taxon=?,quantity=?,sex=?,life_stage=?,substrate=?,comments=?,report_intent=?,archived=?,modified_at=CURRENT_TIMESTAMP WHERE id=?")) {
                bindSpecimen(ps, v, accession); ps.setLong(12, v.id());
                if (ps.executeUpdate() != 1) throw new SQLException("Specimen no longer exists");
                return v.id();
            }
        });
    }

    /** Ensures manually supplied numbers obey the same event ownership as automatically derived ones. */
    private void validateAccessionOwner(String accession, long eventId) throws SQLException {
        if (accession == null) return;
        Event destination = event(eventId);
        String destinationBase = destination.kind() == CollectionKind.BOTANICAL
                ? blankToNull(destination.fieldNumber()) : null;
        if (destinationBase != null) {
            if (!inNumberFamily(accession, destinationBase)) {
                throw new SQLException("Collection number " + accession + " does not belong to event " + destinationBase);
            }
            return;
        }
        // An insect or legacy unnumbered event only needs two indexed exact lookups: the accession
        // itself and, for NE6-2, its possible envelope base NE6.
        for (String candidate : List.of(accession, numberFamilyBase(accession))) {
            Event owner = eventByFieldNumber(candidate);
            if (owner != null && owner.id() != eventId && owner.kind() == CollectionKind.BOTANICAL) {
                throw new SQLException("Collection number " + accession + " belongs to collection " + owner.fieldNumber());
            }
        }
    }

    private static boolean inNumberFamily(String accession, String base) {
        if (accession.equals(base)) return true;
        if (!accession.startsWith(base + "-")) return false;
        String suffix = accession.substring(base.length() + 1);
        if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) return false;
        try { return Integer.parseInt(suffix) >= 2; }
        catch (NumberFormatException e) { return false; }
    }

    private static String numberFamilyBase(String accession) {
        int dash = accession.lastIndexOf('-');
        if (dash <= 0) return accession;
        String suffix = accession.substring(dash + 1);
        if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) return accession;
        try { return Integer.parseInt(suffix) >= 2 ? accession.substring(0, dash) : accession; }
        catch (NumberFormatException e) { return accession; }
    }

    /**
     * The number for the next specimen under this event. The number written on an envelope belongs to the
     * collection, not to one species: the first species keeps it as written (NE6), later ones get -2, -3 and
     * so on. Existing specimens are never renumbered - their labels may already be printed - so a hole left
     * by a removed species stays a hole. An insect tube number is not a collection number, so insect events
     * and events with nothing written on them draw from the global counter as before.
     */
    private String nextNumberFor(long eventId) throws SQLException {
        Event e = event(eventId);
        String base = e.kind() == CollectionKind.BOTANICAL ? blankToNull(e.fieldNumber()) : null;
        if (base == null) return reserveNumbers(1).getFirst();
        boolean baseUsed = false; int highest = 1;
        // Ask the specimens, not the number pool: a number can sit there merely RESERVED with no specimen
        // behind it, and skipping past that would orphan it.
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT accession_number FROM specimen WHERE event_id=?")) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String number = rs.getString(1);
                    if (number.equals(base)) baseUsed = true;
                    else if (number.startsWith(base + "-")) {
                        try { highest = Math.max(highest, Integer.parseInt(number.substring(base.length() + 1))); }
                        catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        return baseUsed ? base + "-" + (highest + 1) : base;
    }

    /** A bare "6" becomes NE6; anything already carrying text is kept exactly as it was written down. */
    public synchronized String withPrefix(String written) throws SQLException {
        String value = blankToNull(written); if (value == null) return null;
        return value.chars().allMatch(Character::isDigit) ? setting("accession.prefix", "NE") + value : value;
    }

    /** The event carrying this collection number, or null. */
    public synchronized Event eventByFieldNumber(String fieldNumber) throws SQLException {
        String value = blankToNull(fieldNumber); if (value == null) return null;
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT * FROM collection_event WHERE field_number=?")) {
            ps.setString(1, value); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? readEvent(rs) : null; }
        }
    }

    /**
     * Finds a numbered event and upgrades the representation used by the original botanical importer,
     * which stored the number only on the first specimen.
     */
    public synchronized Event eventByFieldOrLegacyAccession(String fieldNumber) throws SQLException {
        Event current = eventByFieldNumber(fieldNumber); if (current != null) return current;
        String value = blankToNull(fieldNumber); if (value == null) return null;
        Long legacyId = null;
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT e.id FROM collection_event e JOIN specimen s ON s.event_id=e.id " +
                        "WHERE e.kind='BOTANICAL' AND e.field_number IS NULL AND s.accession_number=?")) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) legacyId = rs.getLong(1); }
        }
        if (legacyId == null) return null;
        try (PreparedStatement ps = database.connection().prepareStatement(
                "UPDATE collection_event SET field_number=?,modified_at=CURRENT_TIMESTAMP WHERE id=? AND field_number IS NULL")) {
            ps.setString(1, value); ps.setLong(2, legacyId);
            if (ps.executeUpdate() != 1) return eventByFieldNumber(value);
        }
        return event(legacyId);
    }

    private static void bindSpecimen(PreparedStatement ps, Specimen v, String accession) throws SQLException {
        ps.setLong(1, v.eventId()); ps.setString(2, require(accession, "Collection number"));
        ps.setString(3, blankToNull(v.taxonGroup())); ps.setString(4, blankToNull(v.preliminaryTaxon()));
        ps.setInt(5, Math.max(1, v.quantity())); ps.setString(6, blankToNull(v.sex()));
        ps.setString(7, blankToNull(v.lifeStage())); ps.setString(8, blankToNull(v.substrate()));
        ps.setString(9, blankToNull(v.comments())); ps.setString(10, (v.reportIntent() == null ? ReportIntent.INCLUDE : v.reportIntent()).name());
        ps.setBoolean(11, v.archived());
    }

    private void claimAccession(Connection c, String accession) throws SQLException {
        try (PreparedStatement find = c.prepareStatement("SELECT state FROM accession_number WHERE number=?")) {
            find.setString(1, accession); try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    if (!"RESERVED".equals(rs.getString(1))) throw new SQLException("Collection number already used: " + accession);
                    try (PreparedStatement update = c.prepareStatement("UPDATE accession_number SET state='ASSIGNED',assigned_at=CURRENT_TIMESTAMP WHERE number=?")) {
                        update.setString(1, accession); update.executeUpdate(); return;
                    }
                }
            }
        }
        Long numeric = numericSuffix(accession);
        try (PreparedStatement insert = c.prepareStatement("INSERT INTO accession_number(number,numeric_part,state,assigned_at) VALUES (?,?,'ASSIGNED',CURRENT_TIMESTAMP)")) {
            insert.setString(1, accession); if (numeric == null) insert.setNull(2, Types.BIGINT); else insert.setLong(2, numeric);
            insert.executeUpdate();
        }
        advanceCounter(accession);
    }

    /**
     * Keeps the counter ahead of a generated-prefix number that arrived outside the counter. Other numbering
     * systems cannot collide with generated numbers and must not make the counter jump.
     */
    private void advanceCounter(String accession) throws SQLException {
        String prefix = setting("accession.prefix", "NE");
        if (accession == null || !accession.startsWith(prefix)) return;
        String suffix = accession.substring(prefix.length());
        int end = 0; while (end < suffix.length() && Character.isDigit(suffix.charAt(end))) end++;
        if (end == 0) return;
        if (end < suffix.length()) {
            if (suffix.charAt(end) != '-' || end + 1 == suffix.length()) return;
            for (int i = end + 1; i < suffix.length(); i++) if (!Character.isDigit(suffix.charAt(i))) return;
        }
        long claimed;
        try { claimed = Long.parseLong(suffix.substring(0, end)); }
        catch (NumberFormatException e) { return; }
        long next = Long.parseLong(setting("accession.next", "1"));
        if (next <= claimed) setSetting("accession.next", Long.toString(claimed + 1));
    }

    /**
     * Moves the allocator beyond numbers present in an import before replacements are chosen. Without
     * this, replacing source envelope 6 with NE7 can consume the number of source envelope 7.
     */
    synchronized void protectCollectionNumbers(Iterable<String> accessions) throws SQLException {
        transactional(c -> {
            for (String accession : accessions) advanceCounter(accession);
            return null;
        });
    }

    public synchronized List<Long> createSpecimenBatch(long eventId, int count) throws SQLException {
        if (count < 1 || count > 10_000) throw new IllegalArgumentException("Create 1-10000 specimens");
        return transactional(c -> {
            Event e = event(eventId); List<Long> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) result.add(saveSpecimen(new Specimen(0, eventId, null, null,
                    e.preliminaryTaxon(), 1, null, null, null, null, ReportIntent.INCLUDE, false)));
            return result;
        });
    }

    public synchronized Specimen specimen(long id) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT * FROM specimen WHERE id=?")) {
            ps.setLong(1, id); try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Unknown specimen: " + id); return readSpecimen(rs);
            }
        }
    }

    private static final String SPECIMEN_ROWS = "SELECT s.id,s.accession_number,COALESCE(d.taxon_name,s.preliminary_taxon,''),l.name,e.start_date,d.id,s.report_intent"
            + " FROM specimen s JOIN collection_event e ON e.id=s.event_id JOIN locality l ON l.id=e.locality_id"
            + " JOIN accession_number an ON an.number=s.accession_number"
            + " LEFT JOIN determination d ON d.specimen_id=s.id AND d.is_current=TRUE WHERE s.archived=FALSE";
    /** Keep number families together and sort a family's numeric child suffix naturally, including -10. */
    private static final String BY_NUMBER = " ORDER BY an.numeric_part," +
            "CASE WHEN REGEXP_LIKE(s.accession_number,'.*-[0-9]+$') " +
            "THEN CAST(REGEXP_REPLACE(s.accession_number,'.*-([0-9]+)$','$1') AS BIGINT) ELSE 1 END," +
            "s.accession_number";

    public synchronized List<SpecimenRow> specimens(String filter) throws SQLException {
        return specimenRows(SPECIMEN_ROWS + " AND LOWER(s.accession_number || ' ' || COALESCE(d.taxon_name,s.preliminary_taxon,'') || ' ' || l.name) LIKE ?" + BY_NUMBER,
                "%" + (filter == null ? "" : filter.trim().toLowerCase()) + "%");
    }

    /** The species collected under one event, in number order. */
    public synchronized List<SpecimenRow> eventSpecimens(long eventId) throws SQLException {
        return specimenRows(SPECIMEN_ROWS + " AND s.event_id=?" + BY_NUMBER, eventId);
    }

    private List<SpecimenRow> specimenRows(String sql, Object parameter) throws SQLException {
        List<SpecimenRow> result = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
            ps.setObject(1, parameter); try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new SpecimenRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), date(rs, 5), rs.getObject(6) != null, ReportIntent.valueOf(rs.getString(7))));
            }
        }
        return result;
    }

    /**
     * @param dyntaxaId the Dyntaxa taxon the name resolves to, or null for a name Dyntaxa does not have.
     *        For a synonym this is the <em>accepted</em> taxon's id - the concept a report is about - not
     *        the synonym's own id.
     */
    public synchronized long addDetermination(long specimenId, String taxonName, Long dyntaxaId, String determiner,
                                              Integer year, IdentificationKind kind,
                                              boolean uncertain, String notes) throws SQLException {
        return transactional(c -> {
            try (PreparedStatement old = c.prepareStatement("UPDATE determination SET is_current=FALSE WHERE specimen_id=? AND is_current=TRUE")) {
                old.setLong(1, specimenId); old.executeUpdate();
            }
            Long personId = determiner == null || determiner.isBlank() ? null : ensurePerson(determiner, null);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO determination(specimen_id,taxon_name,dyntaxa_id,determiner_id,determination_year,kind,uncertain,notes,is_current) VALUES (?,?,?,?,?,?,?,?,TRUE)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, specimenId); ps.setString(2, require(taxonName, "Taxon name"));
                if (dyntaxaId == null) ps.setNull(3, Types.BIGINT); else ps.setLong(3, dyntaxaId);
                if (personId == null) ps.setNull(4, Types.BIGINT); else ps.setLong(4, personId);
                nullableInt(ps, 5, year); ps.setString(6, (kind == null ? IdentificationKind.DET : kind).name());
                ps.setBoolean(7, uncertain); ps.setString(8, blankToNull(notes)); ps.executeUpdate();
                return generatedId(ps);
            }
        });
    }

    public synchronized Determination currentDetermination(long specimenId) throws SQLException {
        String sql = "SELECT d.*,p.full_name determiner_name FROM determination d LEFT JOIN person p ON p.id=d.determiner_id WHERE d.specimen_id=? AND d.is_current=TRUE ORDER BY d.id DESC LIMIT 1";
        try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
            ps.setLong(1, specimenId); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? readDetermination(rs) : null; }
        }
    }

    public synchronized List<Determination> determinations(long specimenId) throws SQLException {
        List<Determination> result = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT d.*,p.full_name determiner_name FROM determination d LEFT JOIN person p ON p.id=d.determiner_id WHERE d.specimen_id=? ORDER BY d.id DESC")) {
            ps.setLong(1, specimenId); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(readDetermination(rs)); }
        }
        return result;
    }

    private static final String TAXON_COLUMNS = "SELECT name,authorship,dyntaxa_id,kind,accepted_id,accepted_name,taxon_rank,taxonomic_status FROM taxon_name";

    /**
     * The form a name is matched on. Both the importer and every query go through this one method -
     * a name that is normalised differently on the two sides is a name that can never be found.
     */
    public static String searchKey(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    /** Names starting with what has been typed so far, for the suggestion list. */
    public synchronized List<TaxonName> suggestTaxonNames(String typed, int limit) throws SQLException {
        String prefix = searchKey(typed);
        if (prefix.length() < 2) return List.of();
        List<TaxonName> result = new ArrayList<>();
        // A prefix range, not LIKE: H2 only turns LIKE into a range scan for a literal pattern, and a
        // parameter is bound after the statement is compiled, so LIKE ? would walk the whole index.
        // ORDER BY search_name alone keeps the index order, so the scan stops at the limit instead of
        // sorting the tens of thousands of rows a two-letter prefix matches; the few results are ordered
        // in Java afterwards.
        try (PreparedStatement ps = database.connection().prepareStatement(
                TAXON_COLUMNS + " WHERE search_name>=? AND search_name<? ORDER BY search_name LIMIT ?")) {
            ps.setString(1, prefix); ps.setString(2, prefixEnd(prefix)); ps.setInt(3, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(readTaxonName(rs)); }
        }
        return result;
    }

    /**
     * Every taxon carrying exactly this name. More than one means Dyntaxa is ambiguous about it -
     * vernacular names and scientific homonyms are not unique - and picking the first would attach the
     * wrong id to a determination.
     */
    public synchronized List<TaxonName> taxonNamesExactly(String name, int limit) throws SQLException {
        List<TaxonName> result = new ArrayList<>();
        try (PreparedStatement ps = database.connection().prepareStatement(
                TAXON_COLUMNS + " WHERE search_name=? ORDER BY CASE WHEN kind='SCIENTIFIC' THEN 0 ELSE 1 END,dyntaxa_id LIMIT ?")) {
            ps.setString(1, searchKey(name)); ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(readTaxonName(rs)); }
        }
        return result;
    }

    /** How many searchable names are stored, so the UI can say the checklist was never imported. */
    public synchronized int taxonNameCount() throws SQLException { return scalar("SELECT COUNT(*) FROM taxon_name"); }

    /** Rows per commit. Small enough that the undo log stays cheap, large enough to still be one bulk load. */
    private static final int TAXON_BATCH = 5_000;

    /**
     * Replaces the whole checklist, committing as it goes.
     *
     * <p>Deliberately <em>not</em> one transaction. A quarter of a million rows of undo log grew the
     * store file past 800 MB, and when a write finally failed the MVStore panicked and took the
     * project's irreplaceable specimen records down with it. A checklist can always be downloaded
     * again; a collection cannot. So the count is cleared first and only written back once the last
     * batch lands - an import that dies partway leaves the checklist marked absent rather than
     * half-present, and the UI asks for it to be imported again.
     *
     * @return how many names were written
     */
    synchronized int replaceTaxonNames(String sourceName, TaxonNameFeed feed) throws SQLException {
        Connection c = database.connection();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        int[] written = { 0 };
        try {
            setSetting("taxonomy.count", "0");
            try (Statement s = c.createStatement()) { s.executeUpdate("DELETE FROM taxon_name"); }
            c.commit();
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO taxon_name(search_name,name,authorship,dyntaxa_id,kind,accepted_id,accepted_name,taxon_rank,taxonomic_status) VALUES (?,?,?,?,?,?,?,?,?)")) {
                feed.feed(row -> {
                    ps.setString(1, searchKey(row.name())); ps.setString(2, row.name());
                    ps.setString(3, blankToNull(row.authorship())); ps.setLong(4, row.dyntaxaId());
                    ps.setString(5, row.kind().name());
                    if (row.acceptedId() == null) ps.setNull(6, Types.BIGINT); else ps.setLong(6, row.acceptedId());
                    ps.setString(7, blankToNull(row.acceptedName())); ps.setString(8, blankToNull(row.rank()));
                    ps.setString(9, blankToNull(row.status()));
                    ps.addBatch();
                    if (++written[0] % TAXON_BATCH == 0) { ps.executeBatch(); c.commit(); }
                });
                ps.executeBatch();
            }
            setSetting("taxonomy.updatedAt", LocalDate.now().toString());
            setSetting("taxonomy.source", sourceName == null ? "" : sourceName);
            setSetting("taxonomy.count", Integer.toString(written[0]));
            c.commit();
            return written[0];
        } catch (Exception e) {
            c.rollback();
            // Clear what did land, so a half-list is never suggested. Best effort - the count already
            // says the checklist is absent even if this cannot run.
            try (Statement s = c.createStatement()) { s.executeUpdate("DELETE FROM taxon_name"); c.commit(); }
            catch (SQLException ignored) { }
            if (e instanceof SQLException sql) throw sql;
            throw new SQLException(e);
        } finally {
            c.setAutoCommit(auto);
            settingCache = null;
        }
    }

    /**
     * The exclusive end of a prefix range - the same bound H2 derives from LIKE 'abc%', but usable
     * with a bound parameter.
     */
    // ponytail: assumes H2's default code-unit collation. If anyone ever runs SET COLLATION, fall back
    // to LIKE with an escaped literal prefix and accept the full index scan.
    private static String prefixEnd(String prefix) {
        char last = prefix.charAt(prefix.length() - 1);
        if (last == Character.MAX_VALUE) return prefix + last;
        return prefix.substring(0, prefix.length() - 1) + (char)(last + 1);
    }

    private static TaxonName readTaxonName(ResultSet rs) throws SQLException {
        return new TaxonName(rs.getString("name"), rs.getString("authorship"), rs.getLong("dyntaxa_id"),
                TaxonNameKind.valueOf(rs.getString("kind")), nullableLong(rs, "accepted_id"),
                rs.getString("accepted_name"), rs.getString("taxon_rank"), rs.getString("taxonomic_status"));
    }

    public synchronized ReportData reportData(long specimenId) throws SQLException {
        Specimen s = specimen(specimenId); Event e = event(s.eventId());
        return new ReportData(s, e, locality(e.localityId()), currentDetermination(specimenId), collectors(e.id()));
    }

    public synchronized List<Long> reportCandidateIds() throws SQLException {
        List<Long> result = new ArrayList<>();
        try (Statement s = database.connection().createStatement();
             ResultSet rs = s.executeQuery("SELECT s.id FROM specimen s JOIN accession_number an ON an.number=s.accession_number WHERE s.archived=FALSE AND s.report_intent='INCLUDE'" + BY_NUMBER)) {
            while (rs.next()) result.add(rs.getLong(1));
        }
        return result;
    }

    public synchronized ReportStatus reportStatus(long specimenId, boolean complete, String payloadHash) throws SQLException {
        Specimen s = specimen(specimenId);
        if (s.reportIntent() == ReportIntent.EXCLUDE) return ReportStatus.EXCLUDED;
        if (!complete) return ReportStatus.INCOMPLETE;
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT payload_hash FROM report_confirmation WHERE specimen_id=?")) {
            ps.setLong(1, specimenId); try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return payloadHash.equals(rs.getString(1)) ? ReportStatus.REPORTED : ReportStatus.UPDATE_NEEDED;
            }
        }
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT rei.payload_hash FROM report_export_item rei JOIN report_export re ON re.id=rei.export_id WHERE rei.specimen_id=? ORDER BY re.created_at DESC,re.id DESC LIMIT 1")) {
            ps.setLong(1, specimenId); try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && payloadHash.equals(rs.getString(1))) return ReportStatus.EXPORTED;
            }
        }
        return ReportStatus.READY;
    }

    synchronized long recordExport(String path, Map<Long, String> payloadHashes) throws SQLException {
        return transactional(c -> {
            long exportId;
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO report_export(output_path,row_count) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, path); ps.setInt(2, payloadHashes.size()); ps.executeUpdate(); exportId = generatedId(ps);
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO report_export_item(export_id,specimen_id,payload_hash) VALUES (?,?,?)")) {
                for (var item : payloadHashes.entrySet()) { ps.setLong(1, exportId); ps.setLong(2, item.getKey()); ps.setString(3, item.getValue()); ps.addBatch(); }
                ps.executeBatch();
            }
            return exportId;
        });
    }

    public synchronized void confirmReported(List<Long> specimenIds, Map<Long, String> currentHashes) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement("MERGE INTO report_confirmation(specimen_id,payload_hash,confirmed_at) KEY(specimen_id) VALUES (?,?,CURRENT_TIMESTAMP)")) {
            for (long id : specimenIds) { ps.setLong(1, id); ps.setString(2, currentHashes.get(id)); ps.addBatch(); }
            ps.executeBatch();
        }
    }

    public synchronized void confirmCurrentReport(long specimenId, String payloadHash) throws SQLException {
        confirmReported(List.of(specimenId), Map.of(specimenId, payloadHash));
    }

    public synchronized List<ReportExport> pendingExports() throws SQLException {
        List<ReportExport> result = new ArrayList<>();
        String sql = "SELECT re.id,re.output_path,re.created_at,re.row_count FROM report_export re " +
                "WHERE EXISTS (SELECT 1 FROM report_export_item rei LEFT JOIN report_confirmation rc " +
                "ON rc.specimen_id=rei.specimen_id AND rc.confirmed_at>=re.created_at " +
                "WHERE rei.export_id=re.id AND rc.specimen_id IS NULL) ORDER BY re.created_at DESC,re.id DESC";
        try (Statement s = database.connection().createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) result.add(new ReportExport(rs.getLong(1), rs.getString(2),
                    rs.getTimestamp(3).toLocalDateTime(), rs.getInt(4)));
        }
        return result;
    }

    public synchronized Map<Long, String> exportHashes(long exportId) throws SQLException {
        Map<Long, String> result = new LinkedHashMap<>();
        String sql = "SELECT specimen_id,payload_hash FROM report_export_item WHERE export_id=? ORDER BY specimen_id";
        try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
            ps.setLong(1, exportId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getLong(1), rs.getString(2));
            }
        }
        return result;
    }

    public synchronized void recordLabelPrint(LabelType type, long targetId, int copies,
                                              String hash, String outputPath) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO label_print(label_type,target_id,copies,content_hash,output_path) VALUES (?,?,?,?,?)")) {
            ps.setString(1, type.name()); ps.setLong(2, targetId); ps.setInt(3, copies);
            ps.setString(4, hash); ps.setString(5, outputPath); ps.executeUpdate();
        }
    }

    public synchronized LabelStatus labelStatus(LabelType type, long targetId, String currentHash) throws SQLException {
        return labelStatus(type, targetId, currentHash, 1);
    }

    public synchronized LabelStatus labelStatus(LabelType type, long targetId, String currentHash,
                                                int requiredCopies) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT content_hash,copies FROM label_print WHERE label_type=? AND target_id=? ORDER BY printed_at DESC,id DESC LIMIT 1")) {
            ps.setString(1, type.name()); ps.setLong(2, targetId); try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return LabelStatus.NOT_PRINTED;
                return currentHash.equals(rs.getString(1)) && rs.getInt(2) >= requiredCopies
                        ? LabelStatus.PRINTED : LabelStatus.CHANGED;
            }
        }
    }

    synchronized boolean sourceExists(String sourceId, String fingerprint) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement("SELECT 1 FROM source_record WHERE source_system='SLIME' AND (source_id=? OR fingerprint=?)")) {
            ps.setString(1, sourceId); ps.setString(2, fingerprint); try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    synchronized long createImportBatch(String file, String hash, int added, int skipped, int failed) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO import_batch(source_file,source_sha256,added_count,skipped_count,failed_count) VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, file); ps.setString(2, hash); ps.setInt(3, added); ps.setInt(4, skipped); ps.setInt(5, failed);
            ps.executeUpdate(); return generatedId(ps);
        }
    }

    synchronized void recordSource(String sourceId, String fingerprint, long eventId,
                                   Long specimenId, long batchId) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement("INSERT INTO source_record(source_system,source_id,fingerprint,event_id,specimen_id,import_batch_id) VALUES ('SLIME',?,?,?,?,?)")) {
            ps.setString(1, sourceId); ps.setString(2, fingerprint); ps.setLong(3, eventId);
            if (specimenId == null) ps.setNull(4, Types.BIGINT); else ps.setLong(4, specimenId); ps.setLong(5, batchId); ps.executeUpdate();
        }
    }

    public synchronized Dashboard dashboard(ArtportalenExporter exporter, LabelGenerator labels) throws Exception {
        int awaiting = scalar("SELECT COUNT(*) FROM collection_event e WHERE e.kind='INSECT' AND e.expected_count>(SELECT COALESCE(SUM(s.quantity),0) FROM specimen s WHERE s.event_id=e.id AND s.archived=FALSE)");
        int undetermined = scalar("SELECT COUNT(*) FROM specimen s WHERE s.archived=FALSE AND NOT EXISTS (SELECT 1 FROM determination d WHERE d.specimen_id=s.id AND d.is_current=TRUE)");
        int unprinted = 0, ready = 0, exported = 0, reported = 0, update = 0;
        for (EventRow event : events("")) {
            if (event.kind() != CollectionKind.INSECT) continue;
            int requiredCopies = Math.max(event.expectedCount(), specimenQuantity(event.id()));
            if (requiredCopies == 0) continue;
            LabelGenerator.LabelDocument label = labels.eventLabel(event.id());
            if (labelStatus(label.type(), event.id(), label.contentHash(), requiredCopies) != LabelStatus.PRINTED) {
                unprinted++;
            }
        }
        for (long id : activeSpecimenIds()) {
            Event event = event(specimen(id).eventId());
            if (event.kind() == CollectionKind.BOTANICAL) {
                LabelGenerator.LabelDocument label = labels.botanicalLabel(id);
                if (labelStatus(label.type(), id, label.contentHash()) != LabelStatus.PRINTED) unprinted++;
            } else {
                LabelGenerator.LabelDocument accession = labels.accessionLabel(id);
                if (labelStatus(accession.type(), id, accession.contentHash()) != LabelStatus.PRINTED) unprinted++;
                if (currentDetermination(id) != null) {
                    LabelGenerator.LabelDocument determination = labels.determinationLabel(id);
                    if (labelStatus(determination.type(), id, determination.contentHash()) != LabelStatus.PRINTED) unprinted++;
                }
            }
        }
        for (long id : reportCandidateIds()) {
            ArtportalenExporter.PreparedRow row = exporter.prepare(id);
            switch (reportStatus(id, row.valid(), row.payloadHash())) {
                case READY -> ready++; case EXPORTED -> exported++; case REPORTED -> reported++; case UPDATE_NEEDED -> update++;
                default -> {}
            }
        }
        return new Dashboard(awaiting, undetermined, unprinted, ready, exported, reported, update);
    }

    private List<Long> activeSpecimenIds() throws SQLException {
        List<Long> result = new ArrayList<>();
        try (Statement s = database.connection().createStatement();
             ResultSet rs = s.executeQuery("SELECT id FROM specimen WHERE archived=FALSE ORDER BY id")) {
            while (rs.next()) result.add(rs.getLong(1));
        }
        return result;
    }

    private int specimenQuantity(long eventId) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT COALESCE(SUM(quantity),0) FROM specimen WHERE event_id=? AND archived=FALSE")) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private int scalar(String sql) throws SQLException {
        try (Statement s = database.connection().createStatement(); ResultSet rs = s.executeQuery(sql)) { rs.next(); return rs.getInt(1); }
    }

    private int scalar(String sql, long id) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private static Locality readLocality(ResultSet rs) throws SQLException {
        return new Locality(rs.getLong("id"), rs.getString("name"), rs.getString("short_name"),
                rs.getString("country_code"), rs.getString("country_name"), rs.getString("province"),
                rs.getString("district"), rs.getString("description"), nullableDouble(rs, "latitude"),
                nullableDouble(rs, "longitude"), nullableInt(rs, "uncertainty_m"),
                CoordinateSource.valueOf(rs.getString("coordinate_source")), rs.getString("nearest_place"),
                nullableInt(rs, "nearest_distance_m"), rs.getString("nearest_direction"));
    }

    private static Event readEvent(ResultSet rs) throws SQLException {
        return new Event(rs.getLong("id"), rs.getLong("locality_id"), CollectionKind.valueOf(rs.getString("kind")),
                rs.getString("field_number"), date(rs, "start_date"), date(rs, "end_date"),
                time(rs, "local_time"), time(rs, "end_time"), rs.getString("timezone"),
                nullableDouble(rs, "latitude"), nullableDouble(rs, "longitude"), nullableInt(rs, "uncertainty_m"),
                nullableDouble(rs, "elevation_m"), CoordinateSource.valueOf(rs.getString("coordinate_source")),
                rs.getString("method"), rs.getString("trap_number"), rs.getInt("expected_count"),
                rs.getString("habitat"), rs.getString("notes"), rs.getString("preliminary_taxon"));
    }

    private static Specimen readSpecimen(ResultSet rs) throws SQLException {
        return new Specimen(rs.getLong("id"), rs.getLong("event_id"), rs.getString("accession_number"),
                rs.getString("taxon_group"), rs.getString("preliminary_taxon"), rs.getInt("quantity"),
                rs.getString("sex"), rs.getString("life_stage"), rs.getString("substrate"),
                rs.getString("comments"), ReportIntent.valueOf(rs.getString("report_intent")), rs.getBoolean("archived"));
    }

    private static Determination readDetermination(ResultSet rs) throws SQLException {
        return new Determination(rs.getLong("id"), rs.getLong("specimen_id"), rs.getString("taxon_name"),
                nullableLong(rs, "dyntaxa_id"), nullableLong(rs, "determiner_id"), rs.getString("determiner_name"),
                nullableInt(rs, "determination_year"), IdentificationKind.valueOf(rs.getString("kind")),
                rs.getBoolean("uncertain"), rs.getString("notes"), rs.getBoolean("is_current"));
    }

    private static long generatedId(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("No generated id"); return keys.getLong(1); }
    }

    private static void validateCoordinate(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) throw new IllegalArgumentException("Latitude and longitude must both be present or absent");
        if (latitude != null && (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("Invalid WGS84 coordinate");
        }
    }

    private static String require(String text, String label) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException(label + " is required"); return text.trim();
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String upperOrNull(String value) { String s = blankToNull(value); return s == null ? null : s.toUpperCase(); }
    private static String firstNonBlank(String a, String b) { return a == null || a.isBlank() ? b : a; }
    /** The first run of digits, so a child number sorts with its family: NE6 and NE6-2 both give 6. */
    private static Long numericSuffix(String value) {
        if (value == null) return null;
        int start = 0; while (start < value.length() && !Character.isDigit(value.charAt(start))) start++;
        int end = start; while (end < value.length() && Character.isDigit(value.charAt(end))) end++;
        if (start == end) return null;
        try { return Long.parseLong(value.substring(start, end)); } catch (NumberFormatException e) { return null; }
    }
    private static Path uniqueFile(Path requested) { if (!Files.exists(requested)) return requested; String n=requested.getFileName().toString(); int dot=n.lastIndexOf('.'); String b=dot<0?n:n.substring(0,dot),e=dot<0?"":n.substring(dot); int i=1; Path p; do { p=requested.resolveSibling(b+"-"+(i++)+e); } while(Files.exists(p)); return p; }
    private static String fileHash(Path path) throws Exception { MessageDigest digest=MessageDigest.getInstance("SHA-256"); try(InputStream in=Files.newInputStream(path)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);} return HexFormat.of().formatHex(digest.digest()); }
    private static void nullableDouble(PreparedStatement ps, int i, Double v) throws SQLException { if (v == null) ps.setNull(i, Types.DOUBLE); else ps.setDouble(i, v); }
    private static void nullableInt(PreparedStatement ps, int i, Integer v) throws SQLException { if (v == null) ps.setNull(i, Types.INTEGER); else ps.setInt(i, v); }
    private static void nullableDate(PreparedStatement ps, int i, LocalDate v) throws SQLException { if (v == null) ps.setNull(i, Types.DATE); else ps.setDate(i, Date.valueOf(v)); }
    private static void nullableTime(PreparedStatement ps, int i, LocalTime v) throws SQLException { if (v == null) ps.setNull(i, Types.TIME); else ps.setTime(i, Time.valueOf(v)); }
    private static Double nullableDouble(ResultSet rs, String column) throws SQLException { double v = rs.getDouble(column); return rs.wasNull() ? null : v; }
    private static Integer nullableInt(ResultSet rs, String column) throws SQLException { int v = rs.getInt(column); return rs.wasNull() ? null : v; }
    private static Long nullableLong(ResultSet rs, String column) throws SQLException { long v = rs.getLong(column); return rs.wasNull() ? null : v; }
    private static LocalDate date(ResultSet rs, String column) throws SQLException { Date v = rs.getDate(column); return v == null ? null : v.toLocalDate(); }
    private static LocalDate date(ResultSet rs, int column) throws SQLException { Date v = rs.getDate(column); return v == null ? null : v.toLocalDate(); }
    private static LocalTime time(ResultSet rs, String column) throws SQLException { Time v = rs.getTime(column); return v == null ? null : v.toLocalTime(); }
}
