package main.core;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Transitional static facade over {@link Database}. New code should take a
 * {@code Database} (or a repository) as a constructor dependency instead.
 *
 * @deprecated use {@link Database} via {@link AppContext}
 */
@Deprecated
public class DBConnection {
	private static Database installed;

	public static void install(Database db) {
		installed = db;
	}

	@Deprecated
	public static Connection getConn() throws SQLException {
		return installed.mysql();
	}

	@Deprecated
	public static Connection getH2Conn() throws SQLException {
		return installed.h2();
	}
}
