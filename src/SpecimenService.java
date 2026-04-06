import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SpecimenService {

    // creates the H2 chache and reports found specimens
    public int refreshCache(String province, String district) {
        int count = 0;

        String mysqlSql = "SELECT specimens.AccessionNo, Year, Month, Day, original_text, Genus, Species, Collector, "
                + "specimens.InstitutionCode, specimens.locality as specimen_locality, specimens.district, specimens.province, specimens.ID as specimen_ID, "
                + "RUBIN, RiketsN, RiketsO, Sweref99TMN as SwerefN, Sweref99TME as SwerefE, Lat_dir, Lat_deg, Lat_min, "
                + "Lat_sec, Long_dir, Long_deg, Long_min, Long_sec, specimens.CollectionCode, "
                + "specimen_locality.locality_ID, distance, direction, oDistrict, oProvince "
                + "FROM specimens "
                + "LEFT JOIN specimen_locality ON specimens.ID = specimen_locality.specimen_ID "
                + "WHERE specimens.Province = ? AND specimens.district = ? "
                + "ORDER BY Year ASC, Month ASC, Day ASC";

        //     + "LEFT JOIN locality ON specimen_locality.locality_ID = locality.ID "

        String h2Insert = "INSERT INTO tempspecimens (AccessionNo, \"Year\", \"Month\", \"Day\", original_text, Genus, Species, Collector, "
                + "InstitutionCode, specimen_locality, district, province, specimens_ID, "
                + "RUBIN, RiketsN, RiketsO, SwerefN, SwerefE, Lat_dir, Lat_deg, Lat_min, "
                + "Lat_sec, Long_dir, Long_deg, Long_min, Long_sec, CollectionCode, "
                + "locality_ID,  distance, direction, oDistrict, oProvince) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";

        try (Connection mysqlConn = DBConnection.getConn();
             Connection h2Conn = DBConnection.getH2Conn();
             PreparedStatement selectStmt = mysqlConn.prepareStatement(mysqlSql)) {

            // Setup H2 Table
            prepareH2Table(h2Conn);

            // Fetch from MySQL
            selectStmt.setString(1, province);
            selectStmt.setString(2, district);

            try (ResultSet rs = selectStmt.executeQuery();
                 PreparedStatement insertStmt = h2Conn.prepareStatement(h2Insert)) {

                h2Conn.setAutoCommit(false); // Enable batching

                while (rs.next()) {
                    for (int i = 1; i <= 32; i++) {
                        insertStmt.setString(i, rs.getString(i));
                    }
                    insertStmt.addBatch();
                    count++;

                    // Execute batch every 100 rows to keep memory stable
                    if (count % 100 == 0) insertStmt.executeBatch();
                }

                insertStmt.executeBatch(); // Final batch
                h2Conn.commit();
                h2Conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return count;
    }

    private void prepareH2Table(Connection h2Conn) throws SQLException {
        try (PreparedStatement drop = h2Conn.prepareStatement("DROP TABLE IF EXISTS tempspecimens;")) {
            drop.executeUpdate();
        }

        String createSql = "CREATE TABLE tempspecimens ("
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
        String sql = "SELECT * FROM tempspecimens LIMIT 1 OFFSET ?;";
        try (Connection conn = DBConnection.getH2Conn();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, index);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToSpecimen(rs); // <--- Clean and simple
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public int getCacheCount() {
        try (Connection conn = DBConnection.getH2Conn();
             ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM tempspecimens")) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
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
        s.setSwerefN(rs.getString("SwerefN"));
        s.setSwerefE(rs.getString("SwerefE"));

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
        List<LocalityRecord> list = new ArrayList<>();
        String sql = "SELECT ID, locality FROM locality WHERE District = ? AND Province = ? ORDER BY locality ASC";
        try (Connection conn = DBConnection.getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, district);
            ps.setString(2, province);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new LocalityRecord(rs.getInt("ID"), rs.getString("locality")));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    // Handles the bridging and coordinate calculation
    public boolean linkSpecimenToLocality(int sId, int lId, String oDist, String oProv, int dist, String dir) {
        System.out.println("linkSpecimenToLocality() method called");
        // Update the specimen_locality bridge table
        // Note: You may also need to update the 'specimens' table directly if
        // distance/direction/overrides are stored there.
        /*
        String sql = "REPLACE INTO specimen_locality (specimen_ID, locality_ID, oDistrict, oProvince, distance, direction) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = MYSQLConnection.getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sId);
            ps.setInt(2, lId);
            ps.setString(3, oDist);
            ps.setString(4, oProv);
            ps.setInt(5, dist);
            ps.setString(6, dir);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }*/
        return false;
    }

    public boolean deleteSpecimenLink(int specimenId) {
        System.out.println("deleteSpecimenLink() method called");
        /*
        String sql = "DELETE FROM specimen_locality WHERE specimen_ID = ?";
        try (Connection conn = MYSQLConnection.getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, specimenId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }*/
        return false;
    }
}
