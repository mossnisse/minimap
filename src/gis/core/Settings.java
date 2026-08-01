package gis.core;

import java.io.File;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Key/value settings backed by simple {@code key: value} text files.
 * <p>
 * There are two stores. The <em>project</em> store is rebound every time the
 * active project changes and holds everything specific to that project (view
 * extent, window size, enabled plugins). The <em>shared</em> store is
 * machine-wide and holds the few values that must survive a project switch,
 * such as service credentials.
 */
public class Settings {
	private static final Store project = new Store(new File("settings.txt"));
	private static final Store shared = new Store(new File("shared-settings.txt"));

	public static synchronized void useFile(File file) { project.useFile(file); }

	public static synchronized File activeFile() { return project.file(); }

	public static synchronized String getValue(String key) { return project.get(key); }

	public static synchronized void setValue(String key, String value) throws IOException {
		project.set(key, value);
	}

	public static synchronized void useSharedFile(File file) { shared.useFile(file); }

	public static synchronized File activeSharedFile() { return shared.file(); }

	public static synchronized String getShared(String key) { return shared.get(key); }

	public static synchronized void setShared(String key, String value) throws IOException {
		shared.set(key, value);
	}

	/** One settings file, loaded lazily and rewritten in full on every change. */
	private static final class Store {
		private Map<String, String> entries;
		private File file;

		Store(File file) {
			this.file = file;
		}

		File file() { return file; }

		void useFile(File newFile) {
			if (newFile == null) throw new IllegalArgumentException("Settings file cannot be null");
			file = newFile;
			entries = null;
		}

		String get(String key) {
			try {
				if (entries == null) read();
				return entries.get(key);
			} catch (IOException e) {
				return null;
			}
		}

		void set(String key, String value) throws IOException {
			if (entries == null) read();
			// Prevent actual nulls from becoming the string "null"
			entries.put(key, value == null ? "" : value);
			save();
		}

		private void save() throws IOException {
			Path target = file.toPath().toAbsolutePath();
			Path parent = target.getParent();
			if (parent != null) Files.createDirectories(parent);
			Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
			try {
				try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
					for (Map.Entry<String, String> entry : entries.entrySet()) {
						writer.write(entry.getKey());
						writer.write(": ");
						writer.write(entry.getValue());
						writer.newLine();
					}
				}
				try {
					Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				} catch (AtomicMoveNotSupportedException e) {
					Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
				}
			} finally {
				Files.deleteIfExists(temp);
			}
		}

		private void read() throws IOException {
			entries = new LinkedHashMap<>();

			// If file doesn't exist, just keep an empty store
			if (!file.exists()) return;

			// Keep the legacy format literal. java.util.Properties would treat
			// backslashes in existing passwords as escapes or line continuations.
			try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.trim().isEmpty()) continue;
					String[] parts = line.split(": ", 2);
					entries.put(parts[0], parts.length == 2 ? parts[1] : "");
				}
			}
		}
	}
}
