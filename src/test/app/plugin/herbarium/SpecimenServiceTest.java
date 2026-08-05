package test.app.plugin.herbarium;

import app.db.Database;
import app.plugin.herbarium.Specimen;
import app.plugin.herbarium.SpecimenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecimenServiceTest {
    private Connection mysql;
    private Connection cache;
    private SpecimenService service;

    @BeforeEach
    void setUp() throws Exception {
        mysql = DriverManager.getConnection(
                "jdbc:h2:mem:mysql;MODE=MySQL;NON_KEYWORDS=YEAR,MONTH,DAY");
        cache = DriverManager.getConnection("jdbc:h2:mem:cache");
        createRemoteSchema(mysql);
        service = new SpecimenService(new TestDatabase(mysql, cache));
    }

    @AfterEach
    void tearDown() throws Exception {
        cache.close();
        mysql.close();
    }

    @Test
    void failedRefreshPreservesTheLastCommittedCache() throws Exception {
        insertSpecimen(1, "A-1", "S", "HERB");
        assertEquals(1, refresh());
        assertEquals(1, service.getCacheCount());

        try (Statement statement = mysql.createStatement()) {
            statement.executeUpdate("DROP TABLE specimens");
        }

        assertThrows(SQLException.class, this::refresh);
        assertEquals(1, service.getCacheCount());
        assertEquals(1, service.getSpecimenAt(0).id());
    }

    @Test
    void cacheLinkUpdateUsesSpecimenIdRatherThanSharedAccessionNumber() throws Exception {
        insertSpecimen(1, "DUP", "S", "ONE");
        insertSpecimen(2, "DUP", "GB", "TWO");
        assertEquals(2, refresh());

        // updateH2CacheLink reads only id/institutionCode/collectionCode/accessionNo
        Specimen target = new Specimen(1,
                null, null, null, null, null,
                0, 0, 0, null, null, null, null, null,
                null, null, null, 0, 0,
                null, null, null, null, null, null, null, null,
                0, 0, null, null, null);
        Method update = SpecimenService.class.getDeclaredMethod("updateH2CacheLink",
                Specimen.class, int.class, String.class, String.class, int.class, String.class);
        update.setAccessible(true);
        update.invoke(service, target, 77, "", "", 0, "");

        try (Statement statement = cache.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT specimens_ID, locality_ID FROM tempspecimens ORDER BY specimens_ID")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("specimens_ID"));
            assertEquals(77, rs.getInt("locality_ID"));
            assertTrue(rs.next());
            assertEquals(2, rs.getInt("specimens_ID"));
            rs.getInt("locality_ID");
            assertTrue(rs.wasNull());
            assertFalse(rs.next());
        }
    }

    @Test
    void deletingOneSpecimensLinkLeavesAnAccessionTwinsLinkAlone() throws Exception {
        // specimen_locality is unique on the accession triple, so when two
        // specimens share one, linking the second reassigns the row to it.
        // Deleting the first must then not carry off the second's link.
        insertSpecimen(1, "DUP", "S", "ONE");
        insertSpecimen(2, "DUP", "S", "ONE");
        assertEquals(2, refresh());
        Specimen first = service.getSpecimenAt(0);
        Specimen second = service.getSpecimenAt(1);

        assertTrue(service.linkSpecimenToLocality(first, 55, "", "", 0, ""));
        assertTrue(service.linkSpecimenToLocality(second, 66, "", "", 0, ""));
        assertEquals(List.of(2), linkedSpecimenIds()); // the row now belongs to specimen 2

        assertFalse(service.deleteSpecimenLink(first)); // nothing of its own to delete

        assertEquals(List.of(2), linkedSpecimenIds());
        assertEquals(66, cachedLocalityId(2));
    }

    @Test
    void aFailedCacheWriteDoesNotTurnACommittedRemoteWriteIntoFailure() throws Exception {
        insertSpecimen(1, "A-1", "S", "HERB");
        assertEquals(1, refresh());
        Specimen target = service.getSpecimenAt(0);

        // Cache gone underneath us: MySQL still commits, but the cache cannot follow.
        try (Statement statement = cache.createStatement()) {
            statement.executeUpdate("DROP TABLE tempspecimens");
        }

        assertTrue(service.linkSpecimenToLocality(target, 77, "", "", 0, ""));
        assertEquals(List.of(1), linkedSpecimenIds());
    }

    @Test
    void aFailedCacheClearDoesNotTurnACommittedRemoteDeleteIntoFailure() throws Exception {
        insertSpecimen(1, "A-1", "S", "HERB");
        assertEquals(1, refresh());
        Specimen target = service.getSpecimenAt(0);
        assertTrue(service.linkSpecimenToLocality(target, 77, "", "", 0, ""));

        try (Statement statement = cache.createStatement()) {
            statement.executeUpdate("DROP TABLE tempspecimens");
        }

        assertTrue(service.deleteSpecimenLink(target));
        assertEquals(List.of(), linkedSpecimenIds());
    }

    private List<Integer> linkedSpecimenIds() throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (Statement statement = mysql.createStatement();
             ResultSet rs = statement.executeQuery("SELECT specimen_ID FROM specimen_locality ORDER BY specimen_ID")) {
            while (rs.next()) ids.add(rs.getInt(1));
        }
        return ids;
    }

    private int cachedLocalityId(int specimenId) throws SQLException {
        try (var ps = cache.prepareStatement("SELECT locality_ID FROM tempspecimens WHERE specimens_ID = ?")) {
            ps.setInt(1, specimenId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private int refresh() throws SQLException {
        return service.refreshCache("*", "*", "*", "*", "*", "*", "*", "*", "*", 0, false);
    }

    private void insertSpecimen(int id, String accession, String institution, String collection) throws SQLException {
        try (var ps = mysql.prepareStatement(
                "INSERT INTO specimens (ID, AccessionNo, InstitutionCode, CollectionCode) VALUES (?,?,?,?)")) {
            ps.setInt(1, id);
            ps.setString(2, accession);
            ps.setString(3, institution);
            ps.setString(4, collection);
            ps.executeUpdate();
        }
    }

    private static void createRemoteSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE specimens (
                        AccessionNo VARCHAR(16), `Year` SMALLINT, `Month` TINYINT, `Day` TINYINT,
                        original_text CLOB, Genus VARCHAR(32), Species VARCHAR(42), Collector CLOB,
                        InstitutionCode VARCHAR(8), locality CLOB, district CLOB, province CLOB,
                        ID INT PRIMARY KEY, RUBIN VARCHAR(16), RiketsN VARCHAR(9), RiketsO VARCHAR(9),
                        Sweref99TMN INT, Sweref99TME INT, Lat_dir VARCHAR(1), Lat_deg VARCHAR(32),
                        Lat_min VARCHAR(16), Lat_sec VARCHAR(16), Long_dir VARCHAR(1), Long_deg VARCHAR(32),
                        Long_min VARCHAR(16), Long_sec VARCHAR(16), CollectionCode VARCHAR(10),
                        CSource VARCHAR(32), CPrec INT
                    )
                    """);
            // The unique key is the accession triple, matching the ON DUPLICATE
            // KEY UPDATE that linkSpecimenToLocality relies on.
            statement.executeUpdate("""
                    CREATE TABLE specimen_locality (
                        specimen_ID INT, locality_ID INT, distance INT, direction VARCHAR(4),
                        oDistrict VARCHAR(32), oProvince VARCHAR(40),
                        InstitutionCode VARCHAR(8), CollectionCode VARCHAR(10), AccessionNo VARCHAR(16),
                        createdby VARCHAR(32), modifiedby VARCHAR(32), modified TIMESTAMP,
                        CONSTRAINT specimen_locality_accession UNIQUE (InstitutionCode, CollectionCode, AccessionNo)
                    )
                    """);
        }
    }

    private static final class TestDatabase extends Database {
        private final Connection mysql;
        private final Connection h2;

        TestDatabase(Connection mysql, Connection h2) {
            super(() -> null);
            this.mysql = mysql;
            this.h2 = h2;
        }

        @Override
        public synchronized Connection mysql() {
            return mysql;
        }

        @Override
        public synchronized Connection h2() {
            return h2;
        }
    }
}
