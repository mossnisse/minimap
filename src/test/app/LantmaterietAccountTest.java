package test.app;

import app.LantmaterietAccount;
import gis.core.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LantmaterietAccountTest {
    @TempDir Path tempDir;

    private File previousProject;
    private File previousShared;

    @BeforeEach
    void redirectSettings() {
        previousProject = Settings.activeFile();
        previousShared = Settings.activeSharedFile();
        Settings.useFile(tempDir.resolve("project-settings.txt").toFile());
        Settings.useSharedFile(tempDir.resolve("shared.txt").toFile());
    }

    @AfterEach
    void restoreSettings() {
        Settings.useFile(previousProject);
        Settings.useSharedFile(previousShared);
    }

    @Test
    void theAccountIsSharedByEveryProject() throws Exception {
        LantmaterietAccount.save("map-user", "map-password");

        Settings.useFile(tempDir.resolve("another-project.txt").toFile());

        assertEquals("map-user", LantmaterietAccount.username());
        assertEquals("map-password", LantmaterietAccount.password());
        assertTrue(LantmaterietAccount.isConfigured());
    }

    @Test
    void credentialsWrittenByAnOlderBuildAreLiftedOutOfTheProject() throws Exception {
        Settings.setValue(LantmaterietAccount.USERNAME_KEY, "legacy-user");
        Settings.setValue(LantmaterietAccount.PASSWORD_KEY, "legacy-password");

        LantmaterietAccount.migrateProjectLocal();

        assertEquals("legacy-user", Settings.getShared(LantmaterietAccount.USERNAME_KEY));
        assertEquals("legacy-password", Settings.getShared(LantmaterietAccount.PASSWORD_KEY));
        assertEquals("", Settings.getValue(LantmaterietAccount.PASSWORD_KEY));
        assertFalse(Files.readString(tempDir.resolve("project-settings.txt")).contains("legacy-password"));
    }

    @Test
    void migrationNeverDowngradesAnAlreadySharedAccount() throws Exception {
        LantmaterietAccount.save("current-user", "current-password");
        Settings.setValue(LantmaterietAccount.USERNAME_KEY, "stale-user");
        Settings.setValue(LantmaterietAccount.PASSWORD_KEY, "stale-password");

        LantmaterietAccount.migrateProjectLocal();

        assertEquals("current-user", LantmaterietAccount.username());
        assertEquals("current-password", LantmaterietAccount.password());
        assertEquals("", Settings.getValue(LantmaterietAccount.USERNAME_KEY));
    }
}
