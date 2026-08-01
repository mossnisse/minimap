package app.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectManagerFileTest {
    @TempDir Path temp;

    @Test void copiesAllProjectOwnedPluginData() throws Exception {
        Path source = temp.resolve("source");
        Path target = temp.resolve("target");
        Path photos = source.resolve("private_collection/photos/import-1");
        Files.createDirectories(photos);
        Files.createDirectories(target);
        Files.writeString(source.resolve("settings.txt"), "project.plugins: private-collection");
        Files.writeString(source.resolve("layers.txt"), "COLLECTION_EVENTS");
        Files.write(source.resolve("private_collection/collection.mv.db"), new byte[]{1, 2, 3});
        Files.write(photos.resolve("voucher.jpg"), new byte[]{4, 5, 6});

        ProjectManager.copyProjectTree(source, target);

        assertEquals("project.plugins: private-collection", Files.readString(target.resolve("settings.txt")));
        assertEquals("COLLECTION_EVENTS", Files.readString(target.resolve("layers.txt")));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(target.resolve("private_collection/collection.mv.db")));
        assertArrayEquals(new byte[]{4, 5, 6}, Files.readAllBytes(target.resolve("private_collection/photos/import-1/voucher.jpg")));
        assertTrue(Files.isDirectory(target.resolve("private_collection/photos")));
    }
}
