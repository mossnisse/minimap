package test.gis.csv;

import gis.csv.CsvFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvFileTest {
    @TempDir
    Path tempDir;

    @Test
    void writeReplacesExistingFileAndCleansItsStagingFile() throws Exception {
        Path destination = tempDir.resolve("points.csv");
        Files.writeString(destination, "old data");
        CsvFile csv = new CsvFile(List.of("name", "north"), ',');
        csv.getRows().add(List.of("A", "12"));

        csv.write(destination);

        CsvFile reloaded = CsvFile.read(destination);
        assertEquals(List.of("name", "north"), reloaded.getHeader());
        assertEquals(List.of(List.of("A", "12")), reloaded.getRows());
        try (var files = Files.list(tempDir)) {
            assertEquals(List.of(destination), files.toList());
        }
    }
}
