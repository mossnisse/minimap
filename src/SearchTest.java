import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import org.h2.jdbcx.JdbcDataSource;


public class SearchTest {
	static Connection conn;
	
	public SearchTest() {
		createConnection();
		doQuery("SELECT * FROM ortnamnsDB where Ortnamn = 'Stormyran'");
	}
	
	public static void createConnection() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:˜/test");
        ds.setUser("sa");
        ds.setPassword("sa");
        try {
            conn = ds.getConnection();
        } catch (Exception e) {
            System.err.println("Caught IOException: " + e.getMessage());
        } finally {
        }
    }
 
    public static void runStatement(String sqlstmt) {
       System.out.println(sqlstmt);
 
        Statement stmt;
        try {
            stmt = conn.createStatement();
            stmt.executeUpdate(sqlstmt);
            stmt.close();
        } catch (SQLException ex) {
            System.err.println("SQLException: " + ex.getMessage());
            System.exit(1);
        }
    }
 
    public static void doQuery(String sqlstmt) {
        try {
            Statement select = conn.createStatement();
            ResultSet result = select.executeQuery(sqlstmt);
            ResultSetMetaData resultMetaData = result.getMetaData();
            int numberOfColumns = resultMetaData.getColumnCount();
            int rownum = 0;
 
            
            System.out.println(sqlstmt);
            System.out.println("Got results:");
            while (result.next()) { // process results one row at a time
                rownum++;
                System.out.print(" Row " + rownum + " | ");
                for (int i = 1; i <= numberOfColumns; i++) {
                    System.out.print( resultMetaData.getColumnName(i) + " : " + 
                            result.getString(i) );
                    if (i < numberOfColumns) {
                        System.out.print(", ");
                    }
                }
                System.out.println("");
 
            }
        } catch (Exception e) {
            System.err.println("SQLException: " + e.getMessage());
            System.exit(1);
        }
 
    }
    
    /*
    public static void main(String[] args) throws IOException {
    	SearchTest db = new SearchTest();
	 }*/
}
