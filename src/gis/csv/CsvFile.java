package gis.csv;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple RFC-4180-ish CSV container: a header row plus data rows, all fields
 * as strings. Handles quoted fields (embedded delimiters, quotes and
 * newlines), auto-detects the delimiter among comma/semicolon/tab, and
 * normalizes ragged rows so every row has exactly one field per header column.
 */
public class CsvFile {

	private final List<String> header;
	private final List<List<String>> rows;
	private char delimiter;

	public CsvFile(List<String> header, char delimiter) {
		this.header = new ArrayList<>(header);
		this.rows = new ArrayList<>();
		this.delimiter = delimiter;
	}

	public static CsvFile read(Path path) throws IOException {
		String text;
		try {
			text = Files.readString(path, StandardCharsets.UTF_8);
		} catch (MalformedInputException e) {
			// Not valid UTF-8: assume a Windows-1252 export (Excel "ANSI" CSV).
			// Every byte maps in Cp1252, so this decode cannot fail.
			text = new String(Files.readAllBytes(path), Charset.forName("windows-1252"));
		}
		if (text.startsWith("﻿")) {
			text = text.substring(1); // Excel writes a UTF-8 BOM
		}
		char delimiter = detectDelimiter(firstLine(text));
		List<List<String>> records = parse(text, delimiter);

		// Drop the phantom records trailing newlines/blank lines produce
		while (!records.isEmpty()) {
			List<String> last = records.get(records.size() - 1);
			if (last.size() != 1 || !last.get(0).isEmpty()) break;
			records.remove(records.size() - 1);
		}

		if (records.isEmpty()) {
			return new CsvFile(List.of(), delimiter);
		}

		CsvFile csv = new CsvFile(records.get(0), delimiter);
		for (int i = 1; i < records.size(); i++) {
			csv.rows.add(records.get(i));
		}
		csv.normalize();
		return csv;
	}

	public void write(Path path) throws IOException {
		StringBuilder sb = new StringBuilder();
		appendRecord(sb, header);
		for (List<String> row : rows) {
			appendRecord(sb, row);
		}

		Path target = path.toAbsolutePath();
		Path temp = Files.createTempFile(target.getParent(), "minimap-csv-", ".tmp");
		boolean replaced = false;
		try {
			Files.writeString(temp, sb.toString(), StandardCharsets.UTF_8);
			try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
				channel.force(true);
			}
			try {
				Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}
			replaced = true;
		} finally {
			if (!replaced) Files.deleteIfExists(temp);
		}
	}

	public List<String> getHeader() {
		return header;
	}

	public List<List<String>> getRows() {
		return rows;
	}

	public char getDelimiter() {
		return delimiter;
	}

	public void setDelimiter(char delimiter) {
		this.delimiter = delimiter;
	}

	/**
	 * Picks the most frequent of comma/semicolon/tab on the (quote-aware)
	 * first line. Ties prefer semicolon: Swedish CSVs pair a semicolon
	 * delimiter with comma decimals, so a line with both is semicolon-split.
	 */
	static char detectDelimiter(String firstLine) {
		int commas = 0, semis = 0, tabs = 0;
		boolean inQuotes = false;
		for (int i = 0; i < firstLine.length(); i++) {
			char c = firstLine.charAt(i);
			if (c == '"') inQuotes = !inQuotes;
			else if (!inQuotes) {
				if (c == ',') commas++;
				else if (c == ';') semis++;
				else if (c == '\t') tabs++;
			}
		}
		if (semis >= commas && semis >= tabs && semis > 0) return ';';
		if (commas >= tabs && commas > 0) return ',';
		if (tabs > 0) return '\t';
		return ',';
	}

	private static String firstLine(String text) {
		// Quote-aware: a quoted field may contain newlines
		boolean inQuotes = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '"') inQuotes = !inQuotes;
			else if (!inQuotes && (c == '\n' || c == '\r')) return text.substring(0, i);
		}
		return text;
	}

	private static List<List<String>> parse(String text, char delimiter) {
		List<List<String>> records = new ArrayList<>();
		List<String> record = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (inQuotes) {
				if (c == '"') {
					if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
						field.append('"'); // "" is a literal quote
						i++;
					} else {
						inQuotes = false;
					}
				} else {
					field.append(c);
				}
			} else if (c == '"') {
				inQuotes = true;
			} else if (c == delimiter) {
				record.add(field.toString());
				field.setLength(0);
			} else if (c == '\n' || c == '\r') {
				if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
					i++;
				}
				record.add(field.toString());
				field.setLength(0);
				records.add(record);
				record = new ArrayList<>();
			} else {
				field.append(c);
			}
		}
		record.add(field.toString());
		records.add(record);
		return records;
	}

	/** Pads short rows; extends the header with generated names for long rows. */
	private void normalize() {
		int columns = header.size();
		for (List<String> row : rows) {
			columns = Math.max(columns, row.size());
		}
		while (header.size() < columns) {
			header.add("col" + (header.size() + 1));
		}
		for (List<String> row : rows) {
			while (row.size() < columns) {
				row.add("");
			}
		}
	}

	private void appendRecord(StringBuilder sb, List<String> record) {
		for (int i = 0; i < record.size(); i++) {
			if (i > 0) sb.append(delimiter);
			sb.append(quote(record.get(i)));
		}
		sb.append('\n');
	}

	private String quote(String field) {
		if (field.indexOf(delimiter) < 0 && field.indexOf('"') < 0
				&& field.indexOf('\n') < 0 && field.indexOf('\r') < 0) {
			return field;
		}
		return '"' + field.replace("\"", "\"\"") + '"';
	}
}
