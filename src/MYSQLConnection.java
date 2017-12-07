import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.h2.jdbcx.JdbcDataSource;

public class MYSQLConnection {
	private static Connection conn;
	private static Connection h2Conn;
	private static String password;

	public static Connection getConn() throws SQLException {
		if (conn == null) createConn();
		return conn;
	}

	private static void createConn() throws SQLException {
		if (password == null) {
			PasswDialog l = new PasswDialog();
			password = l.open();
		}
		String url = "jdbc:mysql://130.239.50.18:3306/samhall";
		String user = "MiniMap";
		conn = DriverManager.getConnection(url, user, password);
	}
	
	public static void close() throws SQLException {
		if (conn != null) {
			conn.close();
			conn = null;
		}
	}
	
	
	public static Connection getH2Conn() throws SQLException {
		if (h2Conn == null) createH2Conn();
		return h2Conn;
	}
	
	private static void createH2Conn() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:˜/test");
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
