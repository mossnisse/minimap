package test.gis.shapefile;

import gis.shapefile.DbfField;
import gis.shapefile.DbfReader;
import gis.shapefile.ShapefileReader;
import gis.shapefile.ShapefileWriter;
import gis.shapefile.ShpGeometry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapefileWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void atomicSetWritePreservesDbfDeletionFlags() throws Exception {
        Path dbf = tempDir.resolve("points.dbf");
        Path cpg = tempDir.resolve("points.cpg");
        Path shp = tempDir.resolve("points.shp");
        Path shx = tempDir.resolve("points.shx");
        List<DbfField> fields = List.of(new DbfField("name", 'C', 8, 0));
        List<List<String>> rows = List.of(List.of("deleted"), List.of("active"));

        ShapefileWriter.writeAtomically(dbf.toFile(), cpg.toFile(), fields,
                List.of(List.of("old-one"), List.of("old-two")),
                List.of(false, false), shp.toFile(), shx.toFile(),
                List.of(ShpGeometry.point(9, 9), ShpGeometry.point(8, 8)));
        ShapefileWriter.writeAtomically(dbf.toFile(), cpg.toFile(), fields, rows,
                List.of(true, false), shp.toFile(), shx.toFile(),
                List.of(ShpGeometry.point(1, 2), ShpGeometry.point(3, 4)));

        try (DbfReader reader = new DbfReader(dbf.toFile(), null)) {
            assertEquals("deleted", reader.next()[0]);
            assertTrue(reader.wasLastRecordDeleted());
            assertEquals("active", reader.next()[0]);
            assertFalse(reader.wasLastRecordDeleted());
        }
        try (ShapefileReader reader = new ShapefileReader(shp.toString())) {
            assertTrue(reader.next().deleted);
            assertFalse(reader.next().deleted);
        }
        assertTrue(Files.exists(cpg));
        assertTrue(Files.exists(shx));
        try (var files = Files.list(tempDir)) {
            assertEquals(4, files.count(), "Staging and backup files must be cleaned up");
        }
    }

    @Test
    void overlongUtf8ValueIsRejectedBeforeDestinationIsOpened() {
        Path dbf = tempDir.resolve("too-long.dbf");
        Path cpg = tempDir.resolve("too-long.cpg");
        List<DbfField> fields = List.of(new DbfField("value", 'C', 1, 0));

        assertThrows(IOException.class, () -> ShapefileWriter.writeDbf(
                dbf.toFile(), cpg.toFile(), fields,
                List.of(List.of("a".repeat(255))), List.of(false)));

        assertFalse(Files.exists(dbf));
        assertFalse(Files.exists(cpg));
    }
}
