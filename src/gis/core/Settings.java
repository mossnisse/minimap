package gis.core;

import java.io.*;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class Settings {
	private static HashMap<String, String> store;
	private static File activeFile = new File("settings.txt");

	public static synchronized void useFile(File file) {
		if (file == null) throw new IllegalArgumentException("Settings file cannot be null");
		activeFile = file;
		store = null;
	}

	public static synchronized File activeFile() {
		return activeFile;
	}

	public static synchronized String getValue(String key) {
		try {
			if (store == null) readStore();
			return store.get(key);
		} catch (IOException e) {
			return null; // Or handle as needed
		}
	}

	public static synchronized void setValue(String key, String value) throws IOException {
		if (store == null) readStore();
		// Prevent actual nulls from becoming the string "null"
		store.put(key, value == null ? "" : value);
		saveStore();
	}

	private static void saveStore() throws IOException {
		Path target = activeFile.toPath().toAbsolutePath();
		Path parent = target.getParent();
		if (parent != null) Files.createDirectories(parent);
		Path temp = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
		try {
			try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
					Files.newOutputStream(temp), StandardCharsets.UTF_8))) {
				for (HashMap.Entry<String, String> entry : store.entrySet()) {
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

	private static void readStore() throws IOException {
		store = new HashMap<>();
		File file = activeFile;

		// If file doesn't exist, just return an empty store
		if (!file.exists()) {
			return;
		}

		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

			String line;
			while ((line = br.readLine()) != null) {
				if (line.trim().isEmpty()) continue;

				// Use limit=2 to ensure values containing ": " don't get split up
				String[] parts = line.split(": ", 2);
				if (parts.length == 2) {
					store.put(parts[0], parts[1]);
				} else if (parts.length == 1) {
					store.put(parts[0], "");
				}
			}
		}
	}
}
