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
import java.util.ArrayList;
import java.util.List;

public class SpecimenService {
    private final Database db;
    private final java.util.Map<String, List<LocalityRecord>> localityCache = new java.util.HashMap<>();

    public SpecimenService(Database db) {
        this.db = db;
    }

    // creates the H2 cache and reports found specimens
    public int refreshCache(String province, String district, String collector, String accession, String year, String locality, String genus, String herbarium, String coordSource, int coordPrecision, boolean lackBridgeOnly) {
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

        String h2Insert = "INSERT INTO tempspecimens (AccessionNo, \"Year\", \"Month\", \"Day\", original_text, Genus, Species, Collector, "
                + "InstitutionCode, specimen_locality, district, province, specimens_ID, "
                + "RUBIN, RiketsN, RiketsO, SwerefN, SwerefE, Lat_dir, Lat_deg, Lat_min, "
                + "Lat_sec, Long_dir, Long_deg, Long_min, Long_sec, CollectionCode, "
                + "locality_ID,  distance, direction, oDistrict, oProvince) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";


        try {
            Connection mysqlConn = db.mysql();
            Connection h2Conn = db.h2();

            try (PreparedStatement selectStmt = mysqlConn.prepareStatement(mysqlSql.toString())) {
                prepareH2Table(h2Conn);

                // Map dynamic parameters to the PreparedStatement
                for (int i = 0; i < params.size(); i++) {
                    selectStmt.setObject(i + 1, params.get(i));
                }

                try (ResultSet rs = selectStmt.executeQuery();
                     PreparedStatement insertStmt = h2Conn.prepareStatement(h2Insert)) {

                    h2Conn.setAutoCommit(false); // Start transaction
                    try {
                        while (rs.next()) {
                            for (int i = 1; i <= 32; i++) {
                                insertStmt.setObject(i, rs.getObject(i));
                            }
                            insertStmt.addBatch();
                            count++;

                            // Execute batch every 500 records to manage memory
                            if (count % 500 == 0) {
                                insertStmt.executeBatch();
                            }
                        }

                        // Finalize remaining records
                        insertStmt.executeBatch();
                        h2Conn.commit(); // Single commit at the very end

                    } catch (SQLException e) {
                        h2Conn.rollback(); // Critical: Roll back if something goes wrong!
                        throw e;
                    } finally {
                        h2Conn.setAutoCommit(true); // Reset state for the connection pool
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return count;
    }

    private void prepareH2Table(Connection h2Conn) throws SQLException {
        try (PreparedStatement drop = h2Conn.prepareStatement("DROP TABLE IF EXISTS tempspecimens;")) {
            drop.executeUpdate();
        }

        String createSql = "CREATE TABLE tempspecimens ("
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

        try (PreparedStatement create = h2Conn.prepareStatement(createSql)) {
            create.executeUpdate();
        }
    }

    public Specimen getSpecimenAt(int index) {
        // index 0 maps to cache_id 1
        String sql = "SELECT * FROM tempspecimens WHERE cache_id = ?;";
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
        return null;
    }

    public int getCacheCount() {
        try {
            Connection conn = db.h2();
            try (
                    java.sql.Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM tempspecimens")) {
                if (rs.next()) return rs.getInt(1);
            }
        }catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private Specimen mapResultSetToSpecimen(ResultSet rs) throws SQLException {
        Specimen s = new Specimen();

        // Identifiers
        s.setId(rs.getInt("specimens_ID"));


        // Strings & Taxonomic Info
        s.setAccessionNo(rs.getString("AccessionNo"));
        s.setInstitutionCode(rs.getString("InstitutionCode"));
        s.setCollectionCode(rs.getString("CollectionCode"));
        s.setGenus(rs.getString("Genus"));
        s.setSpecies(rs.getString("Species"));
        s.setCollector(rs.getString("Collector"));
        s.setOriginalText(rs.getString("original_text"));
        s.setSpecimenLocality(rs.getString("specimen_locality"));
        s.setDistrict(rs.getString("district"));
        s.setProvince(rs.getString("province"));

        // Date (Using the quoted "Year", "Month", "Day" logic from H2)
        s.setYear(rs.getInt("Year"));
        s.setMonth(rs.getInt("Month"));
        s.setDay(rs.getInt("Day"));

        // Grid & Coordinates
        s.setRubin(rs.getString("RUBIN"));
        s.setRiketsN(rs.getString("RiketsN"));
        s.setRiketsO(rs.getString("RiketsO"));
        s.setSwerefN(rs.getInt("SwerefN"));
        s.setSwerefE(rs.getInt("SwerefE"));

        // DMS (Degrees, Minutes, Seconds)
        s.setLatDir(rs.getString("Lat_dir"));
        s.setLatDeg(rs.getString("Lat_deg"));
        s.setLatMin(rs.getString("Lat_min"));
        s.setLatSec(rs.getString("Lat_sec"));

        s.setLongDir(rs.getString("Long_dir"));
        s.setLongDeg(rs.getString("Long_deg"));
        s.setLongMin(rs.getString("Long_min"));
        s.setLongSec(rs.getString("Long_sec"));

        // Bridge / Override Data
        s.setLocalityId(rs.getInt("locality_ID"));
        s.setDistance(rs.getInt("distance"));
        s.setDirection(rs.getString("direction"));
        s.setODistrict(rs.getString("oDistrict"));
        s.setOProvince(rs.getString("oProvince"));

        return s;
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
        System.out.println("linkSpecimenToLocality() method called");

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

                ps.setInt(1, s.getId());
                ps.setInt(2, localityId);
                ps.setString(3, s.getInstitutionCode() != null ? s.getInstitutionCode() : "");
                ps.setString(4, s.getCollectionCode() != null ? s.getCollectionCode() : "");
                ps.setString(5, s.getAccessionNo() != null ? s.getAccessionNo() : "");

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

                // If MySQL updated successfully, update the local H2 Cache!
                if (mysqlSuccess) {
                    updateH2CacheLink(s.getAccessionNo(), localityId, oDist, oProv, dist, dir);
                }

                return mysqlSuccess;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteSpecimenLink(Specimen s) {
        System.out.println("deleteSpecimenLink() method called");

        String sql = "DELETE FROM specimen_locality "
                + "WHERE InstitutionCode = ? AND CollectionCode = ? AND AccessionNo = ?";

        try {
            Connection conn = db.mysql();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, s.getInstitutionCode() != null ? s.getInstitutionCode() : "");
                ps.setString(2, s.getCollectionCode() != null ? s.getCollectionCode() : "");
                ps.setString(3, s.getAccessionNo() != null ? s.getAccessionNo() : "");

                boolean mysqlSuccess = ps.executeUpdate() > 0;

                // Clear it from the local H2 Cache as well
                if (mysqlSuccess) {
                    updateH2CacheLink(s.getAccessionNo(), -1, "", "", 0, "");
                }
                return mysqlSuccess;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void updateH2CacheLink(String accessionNo, int locId, String oDist, String oProv, int dist, String dir) {
        String h2Update = "UPDATE tempspecimens SET "
                + "locality_ID = ?, distance = ?, direction = ?, oDistrict = ?, oProvince = ? "
                + "WHERE AccessionNo = ?";
        try {
            Connection h2Conn = db.h2();
            try (
                    PreparedStatement ps = h2Conn.prepareStatement(h2Update)) {

                if (locId > 0) ps.setInt(1, locId);
                else ps.setNull(1, java.sql.Types.INTEGER);

                if (dist > 0) ps.setInt(2, dist);
                else ps.setNull(2, java.sql.Types.INTEGER);

                if (dir != null && !dir.isEmpty()) ps.setString(3, dir);
                else ps.setNull(3, java.sql.Types.VARCHAR);

                ps.setString(4, oDist);
                ps.setString(5, oProv);
                ps.setString(6, accessionNo);

                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
