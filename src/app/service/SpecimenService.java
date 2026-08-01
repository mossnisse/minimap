package app.service;

import gis.coords.Coordinate;
import app.db.Database;
import gis.core.Settings;
import app.model.LocalityRecord;
import app.model.Specimen;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SpecimenService {
    private static final String CACHE_TABLE = "tempspecimens";

    private final Database db;
    private final Object cacheLock = new Object();
    private final java.util.Map<String, List<LocalityRecord>> localityCache = new java.util.HashMap<>();

    public SpecimenService(Database db) {
        this.db = db;
    }

    // creates the H2 cache and reports found specimens
    public int refreshCache(String province, String district, String collector, String accession, String year, String locality, String genus, String herbarium, String coordSource, int coordPrecision, boolean lackBridgeOnly) throws SQLException {
        int count = 0;
        List<Object> params = new ArrayList<>();

        // Base SQL
        StringBuilder mysqlSql = new StringBuilder(
                "SELECT specimens.AccessionNo, Year, Month, Day, original_text, Genus, Species, Collector, "
                        + "specimens.InstitutionCode, specimens.locality as specimen_locality, specimens.district, specimens.province, specimens.ID as specimen_ID, "
                        + "RUBIN, RiketsN, RiketsO, Sweref99TMN as SwerefN, Sweref99TME as SwerefE, Lat_dir, Lat_deg, Lat_min, "
                        + "Lat_sec, Long_dir, Long_deg, Long_min, Long_sec, specimens.CollectionCode, "
                        + "specimen_locality.locality_ID, distance, direction, oDistrict, oProvince "
                        + "FROM specimens "
                        + "LEFT JOIN specimen_locality ON specimens.ID = specimen_locality.specimen_ID "
                        + "WHERE 1=1 "
        );

        // Dynamic Filtering Logic
        if (province != null && !province.equals("*")) {
            mysqlSql.append(" AND specimens.Province = ?");
            params.add(province);
        }
        if (district != null && !district.equals("*")) {
            mysqlSql.append(" AND specimens.district = ?");
            params.add(district);
        }
        if (collector != null && !collector.isEmpty() && !collector.equals("*")) {
            // MySQL Fulltext Search
            mysqlSql.append(" AND MATCH (Collector) AGAINST (? IN BOOLEAN MODE)");
            params.add(collector);
        }
        if (accession != null && !accession.isEmpty() && !accession.equals("*")) {
            mysqlSql.append(" AND specimens.AccessionNo = ?");
            params.add(accession);
        }
        if (year != null  && !year.equals("*")) {
            mysqlSql.append(" AND specimens.Year = ?");
            params.add(year);
        }
        if (locality != null && !locality.equals("*")) {
            mysqlSql.append(" AND specimens.Locality = ?");
            params.add(locality);
        }
        if (genus != null && !genus.equals("*")) {
            mysqlSql.append(" AND specimens.Genus = ?");
            params.add(genus);
        }
        if (herbarium != null && !herbarium.equals("*")) {
            mysqlSql.append(" AND specimens.InstitutionCode = ?");
            params.add(herbarium);
        }
        if (coordSource!= null && !coordSource.equals("*")) {
            mysqlSql.append(" AND specimens.CSource = ?");
            params.add(coordSource);
        }
        if (coordPrecision > 0) {
            mysqlSql.append(" AND (specimens.CPrec >= ? OR CPrec = 0 OR CPrec IS NULL)");
            params.add(coordPrecision);
        }
        if (lackBridgeOnly) {
            mysqlSql.append(" AND specimen_locality.locality_id IS NULL");
        }

        mysqlSql.append(" ORDER BY Year ASC, Month ASC, Day ASC, original_text ASC");

        String h2Insert = "INSERT INTO " + CACHE_TABLE + " (cache_id, AccessionNo, \"Year\", \"Month\", \"Day\", original_text, Genus, Species, Collector, "
                + "InstitutionCode, specimen_locality, district, province, specimens_ID, "
                + "RUBIN, RiketsN, RiketsO, SwerefN, SwerefE, Lat_dir, Lat_deg, Lat_min, "
                + "Lat_sec, Long_dir, Long_deg, Long_min, Long_sec, CollectionCode, "
                + "locality_ID,  distance, direction, oDistrict, oProvince) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";


        Connection mysqlConn = db.mysql();
        Connection h2Conn = db.h2();

        synchronized (cacheLock) {
            ensureCacheTable(h2Conn);

            try (PreparedStatement selectStmt = mysqlConn.prepareStatement(mysqlSql.toString())) {

                // Map dynamic parameters to the PreparedStatement
                for (int i = 0; i < params.size(); i++) {
                    selectStmt.setObject(i + 1, params.get(i));
                }

                try (ResultSet rs = selectStmt.executeQuery();
                     PreparedStatement insertStmt = h2Conn.prepareStatement(h2Insert)) {

                    boolean oldAutoCommit = h2Conn.getAutoCommit();
                    h2Conn.setAutoCommit(false);
                    try {
                        // Refill the cache in place inside one transaction: a
                        // failure rolls back to the previous cache, so each row
                        // is written once instead of staged and copied.
                        try (Statement clear = h2Conn.createStatement()) {
                            clear.executeUpdate("DELETE FROM " + CACHE_TABLE);
                        }
                        while (rs.next()) {
                            // Explicit dense cache_id (1..count in result order):
                            // getSpecimenAt relies on cache_id = index + 1
                            insertStmt.setInt(1, ++count);
                            for (int i = 1; i <= 32; i++) {
                                insertStmt.setObject(i + 1, rs.getObject(i));
                            }
                            insertStmt.addBatch();

                            // Execute batch every 500 records to manage memory
                            if (count % 500 == 0) {
                                insertStmt.executeBatch();
                            }
                        }

                        insertStmt.executeBatch();
                        h2Conn.commit();
                    } catch (SQLException e) {
                        h2Conn.rollback();
                        throw e;
                    } finally {
                        h2Conn.setAutoCommit(oldAutoCommit);
                    }
                }
            }
        }
        return count;
    }

    /**
     * Drops the persistent cache table. The next search recreates it with the
     * current schema, so the cache never leaks rows from another project's
     * database or breaks on a table left behind by an older app version.
     */
    public void clearCache() {
        synchronized (cacheLock) {
            try (Statement drop = db.h2().createStatement()) {
                drop.executeUpdate("DROP TABLE IF EXISTS " + CACHE_TABLE);
            } catch (SQLException e) {
                System.err.println("Couldn't clear the specimen cache: " + e.getMessage());
            }
        }
    }

    private void ensureCacheTable(Connection h2Conn) throws SQLException {
        String createSql = "CREATE TABLE IF NOT EXISTS " + CACHE_TABLE + " ("
                + "cache_id INT AUTO_INCREMENT PRIMARY KEY, "
                + "AccessionNo VARCHAR(16), "
                + "\"Year\" SMALLINT, "   // Quoted
                + "\"Month\" TINYINT, "   // Quoted
                + "\"Day\" TINYINT, "     // Quoted
                + "original_text TEXT, "
                + "Genus VARCHAR(32), "
                + "Species VARCHAR(42), "
                + "Collector TEXT, "
                + "InstitutionCode VARCHAR(3), "
                + "locality_ID INT, "
                + "specimen_locality TEXT, "
                + "district TEXT, "
                + "province TEXT, "
                + "specimens_ID INT, "
                + "RUBIN VARCHAR(16), "
                + "RiketsN VARCHAR(9), "
                + "RiketsO VARCHAR(9), "
                + "SwerefN INT, "
                + "SwerefE INT, "
                + "Lat_dir VARCHAR(1), "
                + "Lat_deg VARCHAR(32), "
                + "Lat_min VARCHAR(16), "
                + "Lat_sec VARCHAR(16), "
                + "Long_dir VARCHAR(1), "
                + "Long_deg VARCHAR(32), "
                + "Long_min VARCHAR(16), "
                + "Long_sec VARCHAR(16), "
                + "CollectionCode VARCHAR(10), "
                + "distance INT, "
                + "direction VARCHAR(4), "
                + "oDistrict VARCHAR(32), "
                + "oProvince VARCHAR(40));";

        try (Statement create = h2Conn.createStatement()) {
            create.executeUpdate(createSql);
        }
    }

    public Specimen getSpecimenAt(int index) {
        // refreshCache assigns dense cache_ids (1..count in result order), so
        // this is an indexed key probe instead of an O(n) OFFSET row skip
        String sql = "SELECT * FROM " + CACHE_TABLE + " WHERE cache_id = ?";
        synchronized (cacheLock) {
            try {
                Connection conn = db.h2();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {

                    ps.setInt(1, index + 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return mapResultSetToSpecimen(rs);
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public int getCacheCount() {
        synchronized (cacheLock) {
            try {
                Connection conn = db.h2();
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + CACHE_TABLE)) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    /** Argument order must match the component order of {@link Specimen}. */
    private Specimen mapResultSetToSpecimen(ResultSet rs) throws SQLException {
        return new Specimen(
                rs.getInt("specimens_ID"),
                // Taxonomic info
                rs.getString("AccessionNo"), rs.getString("Genus"), rs.getString("Species"),
                rs.getString("InstitutionCode"), rs.getString("CollectionCode"),
                // Date (using the quoted "Year", "Month", "Day" logic from H2)
                rs.getInt("Year"), rs.getInt("Month"), rs.getInt("Day"),
                rs.getString("Collector"), rs.getString("original_text"), rs.getString("specimen_locality"),
                rs.getString("district"), rs.getString("province"),
                // Grid & coordinates
                rs.getString("RUBIN"), rs.getString("RiketsN"), rs.getString("RiketsO"),
                rs.getInt("SwerefN"), rs.getInt("SwerefE"),
                // DMS (degrees, minutes, seconds)
                rs.getString("Lat_dir"), rs.getString("Lat_deg"),
                rs.getString("Lat_min"), rs.getString("Lat_sec"),
                rs.getString("Long_dir"), rs.getString("Long_deg"),
                rs.getString("Long_min"), rs.getString("Long_sec"),
                // Bridge / override data
                rs.getInt("locality_ID"), rs.getInt("distance"), rs.getString("direction"),
                rs.getString("oDistrict"), rs.getString("oProvince"));
    }

    public List<LocalityRecord> getLocalitiesInDistrict(String district, String province) {
        String cacheKey = district + "|" + province;

        // Check if we already fetched these localities
        if (localityCache.containsKey(cacheKey)) {
            return localityCache.get(cacheKey);
        }

        // If not, fetch from MySQL
        List<LocalityRecord> list = new ArrayList<>();
        String sql = "SELECT ID, locality FROM locality WHERE District = ? AND Province = ? ORDER BY locality ASC";
        try {
            Connection conn = db.mysql();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, district);
                ps.setString(2, province);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new LocalityRecord(rs.getInt("ID"), rs.getString("locality")));
                    }
                }
                // Save to cache
                localityCache.put(cacheKey, list);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void invalidateLocalityCache() {
        localityCache.clear();
    }

    // Handles the bridging and coordinate calculation
    public boolean linkSpecimenToLocality(Specimen s, int localityId, String oDist, String oProv, int dist, String dir) {
        // Assuming core.Settings.getValue("user") is available in your scope
        String raw = Settings.getValue("user");
        String user = (raw != null && !raw.isBlank()) ? raw : "unknown";

        String sql = "INSERT INTO specimen_locality "
                + "(specimen_ID, locality_ID, InstitutionCode, CollectionCode, AccessionNo, "
                + "distance, direction, oDistrict, oProvince, createdby, modifiedby, modified) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) "
                + "ON DUPLICATE KEY UPDATE "
                + "locality_ID = VALUES(locality_ID), "
                + "specimen_ID = VALUES(specimen_ID), " // Update to latest specimen_ID just in case
                + "distance = VALUES(distance), "
                + "direction = VALUES(direction), "
                + "oDistrict = VALUES(oDistrict), "
                + "oProvince = VALUES(oProvince), "
                + "modifiedby = VALUES(modifiedby), "
                + "modified = CURRENT_TIMESTAMP;";

        try {
            Connection conn = db.mysql();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, s.id());
                ps.setInt(2, localityId);
                ps.setString(3, s.institutionCode() != null ? s.institutionCode() : "");
                ps.setString(4, s.collectionCode() != null ? s.collectionCode() : "");
                ps.setString(5, s.accessionNo() != null ? s.accessionNo() : "");

                // Handle NULL for distance
                if (dist > 0) ps.setInt(6, dist);
                else ps.setNull(6, java.sql.Types.INTEGER);

                // Handle NULL for direction
                if (dir != null && !dir.isEmpty()) ps.setString(7, dir);
                else ps.setNull(7, java.sql.Types.VARCHAR);

                ps.setString(8, oDist);
                ps.setString(9, oProv);
                ps.setString(10, user); // createdby
                ps.setString(11, user); // modifiedby

                boolean mysqlSuccess = ps.executeUpdate() > 0;

                // MySQL is authoritative. A cache failure must not turn a
                // committed remote write into a reported save failure.
                if (mysqlSuccess) try {
                    updateH2CacheLink(s, localityId, oDist, oProv, dist, dir);
                } catch (SQLException cacheError) {
                    reportCacheSyncFailure(s.id(), cacheError);
                }

                return mysqlSuccess;
            }
        } catch (SQLException e) {
            System.err.println("Could not link specimen " + s.id() + " to locality " + localityId
                    + ": " + e.getMessage());
            return false;
        }
    }

    public boolean deleteSpecimenLink(Specimen s) {
        // Keyed on specimen_ID, matching the cache update and the refreshCache
        // join. Accession numbers repeat across institutions, so the old
        // (InstitutionCode, CollectionCode, AccessionNo) key could delete a
        // sibling specimen's link and leave its cache row untouched.
        String sql = "DELETE FROM specimen_locality WHERE specimen_ID = ?";

        try {
            Connection conn = db.mysql();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, s.id());

                boolean mysqlSuccess = ps.executeUpdate() > 0;

                if (mysqlSuccess) try {
                    updateH2CacheLink(s, -1, "", "", 0, "");
                } catch (SQLException cacheError) {
                    reportCacheSyncFailure(s.id(), cacheError);
                }
                return mysqlSuccess;
            }
        } catch (SQLException e) {
            System.err.println("Could not delete the locality link for specimen " + s.id()
                    + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Mirrors a bridge write into the local H2 cache. Throws rather than
     * swallowing: the caller reports success to the UI, and a cache that
     * silently missed the update would serve stale rows to getSpecimenAt.
     */
    private void updateH2CacheLink(Specimen specimen, int locId, String oDist, String oProv, int dist, String dir)
            throws SQLException {
        String h2Update = "UPDATE tempspecimens SET "
                + "locality_ID = ?, distance = ?, direction = ?, oDistrict = ?, oProvince = ? "
                + "WHERE specimens_ID = ?";
        synchronized (cacheLock) {
            Connection h2Conn = db.h2();
            try (PreparedStatement ps = h2Conn.prepareStatement(h2Update)) {

                if (locId > 0) ps.setInt(1, locId);
                else ps.setNull(1, java.sql.Types.INTEGER);

                if (dist > 0) ps.setInt(2, dist);
                else ps.setNull(2, java.sql.Types.INTEGER);

                if (dir != null && !dir.isEmpty()) ps.setString(3, dir);
                else ps.setNull(3, java.sql.Types.VARCHAR);

                ps.setString(4, oDist);
                ps.setString(5, oProv);
                ps.setInt(6, specimen.id());

                if (ps.executeUpdate() != 1) {
                    throw new SQLException("Specimen " + specimen.id() + " is no longer present in the local cache");
                }
            }
        }
    }

    private static void reportCacheSyncFailure(int specimenId, SQLException error) {
        System.err.println("The bridge change for specimen " + specimenId + " was committed in MySQL, but the local cache could not be updated; "
                + "refresh Search & Cache before revisiting it: " + error.getMessage());
    }

    public Coordinate getLocalityPoint(int localityID) {
        try {
            String query = "SELECT SWTMN, SWTME FROM locality WHERE ID = ?";
            Connection conn = db.mysql();
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setInt(1, localityID);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    int swN = rs.getInt("SWTMN");
                    int swE = rs.getInt("SWTME");
                    return new Coordinate(swN, swE);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
