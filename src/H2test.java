import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.h2.jdbcx.JdbcDataSource;

public class H2test {
    public static void main(String... args) throws Exception {
        // delete the database named 'test' in the user home directory
        //DeleteDbFiles.execute("~", "test", true);

        Class.forName("org.h2.Driver");
        //Connection conn = DriverManager.getConnection("jdbc:h2:tcp~/test", "sa", "sa");
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:˜/test");
        ds.setUser("sa");
        ds.setPassword("sa");
        try {
            Connection conn = ds.getConnection();
            ResultSet rs;
            Statement stat = conn.createStatement();
			rs = stat.executeQuery("update ortnamnsDB set FPNUMMER = 'Västergötland' where FPNUMMER = 'Göteborg';");
            
            
           // rs = stat.executeQuery("select * from test");
            while (rs.next()) {
                System.out.println(rs.getString("name"));
            }
            
            //findNearest(stat, 42, 24);
            stat.close();
            conn.close();
        } catch (Exception e) {
            System.err.println("Caught IOException: " + e.getMessage());
        } finally {
        }
       // Statement stat = conn.createStatement();

        // this line would initialize the database
        // from the SQL script file 'init.sql'
        // stat.execute("runscript from 'init.sql'");

       // stat.execute("create table test(id int primary key, name varchar(255))");
       // stat.execute("insert into test values(2, 'Mullk', 3,45)");
      
    }
    
    public static void findNearest(Statement stat, int x, int y) throws SQLException {
    	System.out.println("distances: "+x+", "+y);
    	String query = "select * from test"; // , SQRT(POWER(x-"+x+",2) + POWER(y-"+y+",2)) as dist
    	System.out.println(query);
    	ResultSet rs = stat.executeQuery(query);
    	 while (rs.next()) {
             System.out.println(rs.getString(rs.getString("name")));
         }
    }

}