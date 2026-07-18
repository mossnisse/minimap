package test.app.service;

import app.db.Database;
import app.model.Specimen;
import app.service.SpecimenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
        assertEquals(1, service.getSpecimenAt(0).getId());
    }

    @Test
    void cacheLinkUpdateUsesSpecimenIdRatherThanSharedAccessionNumber() throws Exception {
        insertSpecimen(1, "DUP", "S", "ONE");
        insertSpecimen(2, "DUP", "GB", "TWO");
        assertEquals(2, refresh());

        Specimen target = new Specimen();
        target.setId(1);
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
            statement.executeUpdate("""
                    CREATE TABLE specimen_locality (
                        specimen_ID INT, locality_ID INT, distance INT, direction VARCHAR(4),
                        oDistrict VARCHAR(32), oProvince VARCHAR(40)
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
