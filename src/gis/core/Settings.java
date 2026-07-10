package gis.core;

import java.io.*;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;

public class Settings {
	private static HashMap<String, String> store;
	private static final String FILENAME = "settings.txt";

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
		try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
				new FileOutputStream(FILENAME), StandardCharsets.UTF_8))) {
			for (HashMap.Entry<String, String> entry : store.entrySet()) {
				writer.println(entry.getKey() + ": " + entry.getValue());
			}
		}
	}

	private static void readStore() throws IOException {
		store = new HashMap<>();
		File file = new File(FILENAME);

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