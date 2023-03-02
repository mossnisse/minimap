import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.swing.JOptionPane;

import org.h2.jdbcx.JdbcDataSource;

public class MYSQLConnection {
	private static Connection conn;
	private static Connection h2Conn;
	private static String password;

	public static Connection getConn() throws SQLException {
		if (conn == null) createConn();
		return conn;
	}

	
	private static void openConn(String pass) throws SQLException {
		//String url = "jdbc:mysql://130.239.50.18:3306/samhall";
		String url = "jdbc:mysql://172.18.144.38:3306/samhall?connectionCollation=utf8_general_ci";
		String user = "MiniMap";
		conn = DriverManager.getConnection(url, user, password);
	}
	
	private static void createConn() throws SQLException {
		if (password == null) {
			try {
				password = Settings.getValue("password");
				System.out.println("Password: "+password);
				if (password == null) {
					PasswDialog l = new PasswDialog();
					password = l.open();
					if(!password.equals("codeCancel")) {
						try {
							openConn(password);
							Settings.setValue("password", password);
						} catch(SQLException e2) {
								JOptionPane.showMessageDialog(null, e2.getMessage());
								password = null;
								createConn();
						} catch(IOException e1) {
							System.out.println("Couldn't save password");
							e1.printStackTrace();
						}
					}
				} else {
					openConn(password);
				}
			} catch (IOException e) {
				
			}
			
		}
		
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
