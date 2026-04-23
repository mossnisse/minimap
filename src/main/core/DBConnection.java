package main.core;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import javax.swing.JOptionPane;
import org.h2.jdbcx.JdbcDataSource;

import main.dialogs.PasswDialog;

public class DBConnection {
	private static Connection conn;
	private static Connection h2Conn;
	private static String password;

	public static Connection getConn() throws SQLException {
		if (conn == null || conn.isClosed() || !conn.isValid(2)) {
			createConn();
		}
		return conn;
	}

	private static void openConn(String pass) throws SQLException {
		String url = "jdbc:mysql://172.18.144.38:3306/samhall?connectionCollation=utf8_general_ci";
		String user = "MiniMap";
		conn = DriverManager.getConnection(url, user, pass);
	}

	private static void createConn() throws SQLException {
		// If we already have the password but connection is null/closed
		if (password != null) {
			openConn(password);
			return;
		}

		password = Settings.getValue("password");
		if (password == null) {
			PasswDialog l = new PasswDialog();
			String input = l.open();
			if (input != null && !input.equals("codeCancel")) {
				password = input;
				try {
					openConn(password);
					Settings.setValue("password", password);
				} catch (SQLException e2) {
					JOptionPane.showMessageDialog(null, "Login failed: " + e2.getMessage());
					password = null; // Reset so we ask again
					createConn();
				} catch (IOException e1) {
					System.err.println("Couldn't save password");
				}
			}
		} else {
			openConn(password);
		}
	}
	
	public static void close() throws SQLException {
		if (conn != null) {
			conn.close();
			conn = null;
		}
	}

	public static Connection getH2Conn() throws SQLException {
		// If it's null OR if it was closed by a previous try-with-resources block
		if (h2Conn == null || h2Conn.isClosed()) {
			createH2Conn();
		}
		return h2Conn;
	}
	
	private static void createH2Conn() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:./h2/test");
        ds.setUser("sa");
        ds.setPassword("sa");
        h2Conn = ds.getConnection();
    }
	
	public static void closeH2() throws SQLException {
		if (h2Conn != null) {
			h2Conn.close();
			h2Conn = null;
		}
	}
}