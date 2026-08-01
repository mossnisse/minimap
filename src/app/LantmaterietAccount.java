package app;

import gis.core.Settings;

import java.io.IOException;

/**
 * The Geotorget account used for Lantmäteriet's authenticated WMTS service.
 * <p>
 * The account belongs to the user, not to a project, so it lives in the shared
 * settings store and every project reuses it. Credentials written into a
 * project's own settings by an older build are migrated up on first use, and an
 * environment variable can still override an unconfigured install.
 */
public final class LantmaterietAccount {
	public static final String USERNAME_KEY = "lantmateriet.username";
	public static final String PASSWORD_KEY = "lantmateriet.password";
	private static final String USERNAME_ENV = "MINIMAP_LANTMATERIET_USERNAME";
	private static final String PASSWORD_ENV = "MINIMAP_LANTMATERIET_PASSWORD";

	private LantmaterietAccount() {}

	public static String username() { return credential(USERNAME_KEY, USERNAME_ENV); }

	public static String password() { return credential(PASSWORD_KEY, PASSWORD_ENV); }

	public static boolean isConfigured() {
		return !isBlank(username()) && !isBlank(password());
	}

	public static void save(String username, String password) throws IOException {
		Settings.setShared(USERNAME_KEY, username);
		Settings.setShared(PASSWORD_KEY, password);
	}

	/**
	 * Lifts credentials that an older build stored in the active project's settings
	 * into the shared store, then clears the project-local copy so the password is
	 * kept in exactly one file. Safe to call after every project switch.
	 */
	public static void migrateProjectLocal() {
		String user = Settings.getValue(USERNAME_KEY);
		String secret = Settings.getValue(PASSWORD_KEY);
		if (isBlank(user) && isBlank(secret)) return;
		try {
			boolean sharedIsConfigured = !isBlank(Settings.getShared(USERNAME_KEY))
					&& !isBlank(Settings.getShared(PASSWORD_KEY));
			if (!sharedIsConfigured && !isBlank(user) && !isBlank(secret)) save(user, secret);
			Settings.setValue(USERNAME_KEY, "");
			Settings.setValue(PASSWORD_KEY, "");
		} catch (IOException e) {
			System.err.println("Could not migrate Lantmäteriet credentials: " + e.getMessage());
		}
	}

	private static String credential(String settingsKey, String environmentKey) {
		String value = Settings.getShared(settingsKey);
		if (isBlank(value)) value = Settings.getValue(settingsKey);
		if (isBlank(value)) value = System.getenv(environmentKey);
		return value;
	}

	private static boolean isBlank(String value) { return value == null || value.isBlank(); }
}
