package app.db;

import gis.core.Settings;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import org.h2.jdbcx.JdbcDataSource;

/**
 * Owns the two database connections: the remote MySQL locality/specimen db
 * and the embedded H2 place-name db. Instance-based so repositories can be
 * constructed against it; connection details can be overridden in settings.txt
 * ("db host", "db name", "db user").
 */
public class Database {
	private final Supplier<String> passwordPrompt;
	private Connection mysqlConn;
	private Connection h2Conn;
	private String password;

	/** @param passwordPrompt asks the user for the MySQL password; returns null on cancel. */
	public Database(Supplier<String> passwordPrompt) {
		this.passwordPrompt = passwordPrompt;
	}

	public synchronized Connection mysql() throws SQLException {
		if (mysqlConn == null || mysqlConn.isClosed() || !mysqlConn.isValid(2)) {
			createMysqlConn();
		}
		if (mysqlConn == null) {
			// Happens when the user cancels the password prompt
			throw new SQLException("No MySQL connection (login cancelled)");
		}
		return mysqlConn;
	}

	private void openMysqlConn(String pass) throws SQLException {
		String host = setting("db host", "172.18.144.38:3306");
		String dbName = setting("db name", "samhall");
		String user = setting("db user", "MiniMap");
		String url = "jdbc:mysql://" + host + "/" + dbName + "?connectionCollation=utf8_general_ci";
		mysqlConn = DriverManager.getConnection(url, user, pass);
	}

	private static String setting(String key, String defaultValue) {
		String value = Settings.getValue(key);
		return (value == null || value.isEmpty()) ? defaultValue : value;
	}

	private void createMysqlConn() throws SQLException {
		// If we already have the password but connection is null/closed
		if (password != null) {
			openMysqlConn(password);
			return;
		}

		password = Settings.getValue("password");
		if (password == null) {
			String input = passwordPrompt.get();
			if (input != null) {
				password = input;
				try {
					openMysqlConn(password);
					Settings.setValue("password", password);
				} catch (SQLException e2) {
					JOptionPane.showMessageDialog(null, "Login failed: " + e2.getMessage());
					password = null; // Reset so we ask again
					createMysqlConn();
				} catch (IOException e1) {
					System.err.println("Couldn't save password");
				}
			}
		} else {
			openMysqlConn(password);
		}
	}

	public synchronized Connection h2() throws SQLException {
		// If it's null OR if it was closed by a previous try-with-resources block
		if (h2Conn == null || h2Conn.isClosed()) {
			JdbcDataSource ds = new JdbcDataSource();
			ds.setURL("jdbc:h2:./h2/test");
			ds.setUser("sa");
			ds.setPassword("sa");
			h2Conn = ds.getConnection();
		}
		return h2Conn;
	}

	public synchronized void close() throws SQLException {
		if (mysqlConn != null) {
			mysqlConn.close();
			mysqlConn = null;
		}
		if (h2Conn != null) {
			h2Conn.close();
			h2Conn = null;
		}
	}
}
