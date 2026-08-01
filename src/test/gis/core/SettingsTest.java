package test.gis.core;

import gis.core.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SettingsTest {

    @Test
    void readsLegacyColonFilesAndRoundTripsUtf8(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.txt");
        // Written by the pre-Properties store: "key: value", raw UTF-8, no escaping
        Files.writeString(file, "landskap: Bohuslän\nsocken: Tjörnö\ncnr: 240\nempty: \n"
                        + "password: a\\tb\\c\\\n",
                StandardCharsets.UTF_8);

        Settings.useFile(file.toFile());
        assertEquals("Bohuslän", Settings.getValue("landskap"));
        assertEquals("Tjörnö", Settings.getValue("socken"));
        assertEquals("240", Settings.getValue("cnr"));
        assertEquals("", Settings.getValue("empty"));
        assertEquals("a\\tb\\c\\", Settings.getValue("password"));
        assertNull(Settings.getValue("absent"));

        // A write rewrites the whole file; every value must survive the rewrite
        Settings.setValue("view.crs", "SWEREF99TM");
        Settings.useFile(file.toFile()); // drop the in-memory copy, re-read from disk
        assertEquals("SWEREF99TM", Settings.getValue("view.crs"));
        assertEquals("Bohuslän", Settings.getValue("landskap"));
        assertEquals("240", Settings.getValue("cnr"));
        assertEquals("a\\tb\\c\\", Settings.getValue("password"));
    }

    @Test
    void missingFileReadsAsEmptyAndIsCreatedOnWrite(@TempDir Path dir) throws Exception {
        File file = dir.resolve("nested/settings.txt").toFile();
        Settings.useFile(file);
        assertNull(Settings.getValue("user"));

        Settings.setValue("user", "Nils Ericson");
        Settings.useFile(file);
        assertEquals("Nils Ericson", Settings.getValue("user"));
    }
}
