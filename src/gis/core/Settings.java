package gis.core;

import java.io.*;
import java.util.HashMap;
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
		private HashMap<String, String> entries;
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
				try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
						Files.newOutputStream(temp), StandardCharsets.UTF_8))) {
					for (HashMap.Entry<String, String> entry : entries.entrySet()) {
						writer.println(entry.getKey() + ": " + entry.getValue());
					}
					if (writer.checkError()) throw new IOException("Could not write settings file " + target);
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
			entries = new HashMap<>();

			// If file doesn't exist, just keep an empty store
			if (!file.exists()) return;

			try (BufferedReader br = new BufferedReader(
					new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

				String line;
				while ((line = br.readLine()) != null) {
					if (line.trim().isEmpty()) continue;

					// Use limit=2 to ensure values containing ": " don't get split up
					String[] parts = line.split(": ", 2);
					if (parts.length == 2) {
						entries.put(parts[0], parts[1]);
					} else if (parts.length == 1) {
						entries.put(parts[0], "");
					}
				}
			}
		}
	}
}
