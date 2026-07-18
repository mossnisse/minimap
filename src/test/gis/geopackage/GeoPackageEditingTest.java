package test.gis.geopackage;

import gis.coords.CoordSystem;
import gis.core.MapCanvas;
import gis.layers.GeoPackageLayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeoPackageEditingTest {

    @TempDir
    Path tempDir;

    @Test
    void editingTextPreservesBlobAndStoresEmptyText() throws Exception {
        Path file = createPackage("fid INTEGER PRIMARY KEY, geom BLOB, name TEXT, payload BLOB");
        try (Connection conn = open(file); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO features(name,payload) VALUES ('before',x'00FF8041')");
        }

        GeoPackageLayer layer = openLayer(file);
        assertFalse(layer.isCellEditable(0, 2), "Binary attributes must not be edited as text");
        layer.setAttribute(0, 1, "");
        layer.save();

        try (Connection conn = open(file); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT typeof(name), length(name), typeof(payload), hex(payload) FROM features")) {
            rs.next();
            assertEquals("text", rs.getString(1));
            assertEquals(0, rs.getInt(2));
            assertEquals("blob", rs.getString(3));
            assertEquals("00FF8041", rs.getString(4));
        }
    }

    @Test
    void failedInsertDoesNotPublishRolledBackRowids() throws Exception {
        Path file = createPackage(
                "fid INTEGER PRIMARY KEY, geom BLOB, name TEXT NOT NULL UNIQUE");
        GeoPackageLayer layer = openLayer(file);
        layer.addRow();
        layer.addRow();
        layer.setAttribute(0, 1, "duplicate");
        layer.setAttribute(1, 1, "duplicate");

        assertThrows(IOException.class, layer::save);
        assertEquals("", layer.getAttribute(0, 0), "A rolled-back id must remain provisional");

        layer.setAttribute(1, 1, "second");
        layer.save();

        List<String> names = new ArrayList<>();
        try (Connection conn = open(file); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM features ORDER BY fid")) {
            while (rs.next()) names.add(rs.getString(1));
        }
        assertEquals(List.of("duplicate", "second"), names);
    }

    private Path createPackage(String featureColumns) throws Exception {
        Class.forName("org.sqlite.JDBC");
        Path file = tempDir.resolve("editing-" + System.nanoTime() + ".gpkg");
        try (Connection conn = open(file); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE gpkg_contents (table_name TEXT PRIMARY KEY, "
                    + "data_type TEXT, identifier TEXT, last_change TEXT)");
            stmt.execute("CREATE TABLE gpkg_geometry_columns (table_name TEXT, column_name TEXT, "
                    + "geometry_type_name TEXT, srs_id INTEGER)");
            stmt.execute("CREATE TABLE features (" + featureColumns + ")");
            stmt.execute("INSERT INTO gpkg_contents VALUES ('features','features','features','')");
            stmt.execute("INSERT INTO gpkg_geometry_columns VALUES ('features','geom','POINT',4326)");
        }
        return file;
    }

    private static Connection open(Path file) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + file);
    }

    private static GeoPackageLayer openLayer(Path file) throws IOException {
        return new GeoPackageLayer(file.toString(), "features", new MapCanvas(), CoordSystem.WGS84);
    }
}
