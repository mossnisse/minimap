package geojson;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.regex.Pattern;

public class convDistr {
	public static void main(String[] args) {
		try {
			
			//BufferedReader br = new BufferedReader(new FileReader("C:/Users/nisern99/Documents/sockenkartor/Provinces.geojson"));
			Scanner scan = new Scanner(new File("C:/Users/nisern99/Documents/sockengr/socken1935.geojson"), "UTF-8");
			//Scanner sc = new Scanner(new FileInputStream(file), "UTF-8");
			String line;
			//String jsonstart = "{\"type\":\"FeatureCollection\",\"features\":[{\"geometry\":{\"type\":\"\"MultiPolygon\",\"coordinates\":\",\"coordinates\":";
			//String jsonstart = "{\"type\": \"MultiPolygon\",\"coordinates\":";
			String jsonstart = "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"MultiPolygon\",\"coordinates\":";
			String jsonend = "}}]}";
			//String jsonend = "}";
			
			try {
				//Connection conn = MYSQLConnection.getConn();
				String url = "jdbc:mysql://172.18.144.38:3306/samhall";
				String user = "root";
				String password = System.getenv("MINIMAP_DB_PASSWORD");
				if (password == null || password.isBlank()) {
					throw new SQLException("Set MINIMAP_DB_PASSWORD before running this utility");
				}
				Connection conn = DriverManager.getConnection(url, user, password);
				String sqlstmt = "update district1935 set geojson =? where district =? and province =? and country =?";
				PreparedStatement statement = conn.prepareStatement(sqlstmt);
				String country = "Sweden";
			int i=1;
			while (scan.hasNext()) {
				scan.useDelimiter(Pattern.compile("\"name\": \""));
				scan.next();
				scan.useDelimiter(Pattern.compile("\","));
				line = scan.next();
				String name = line.substring(9);
				System.out.println("District"+i+": "+name);
				
				scan.useDelimiter(Pattern.compile("\"floraprovins\": \""));
				scan.next();
				scan.useDelimiter(Pattern.compile("\" }, \""));
				line = scan.next();
				String province = line.substring(17);
				System.out.println("Province"+i+": "+province);
				
				scan.useDelimiter(Pattern.compile("\"coordinates\":"));
				scan.next();
				scan.useDelimiter(Pattern.compile(" } },"));
				line = scan.next();
				String geojson = line.substring(15);
				geojson = geojson.replaceAll("\\s+","");
				geojson = jsonstart+geojson+jsonend;
				System.out.println("Geojosn"+i+": "+geojson);
				
				
				/*
				String country = line.substring(11);
				System.out.println("Country"+i+": "+country);
				scan.useDelimiter(Pattern.compile("\"NAME_1\": \""));
				scan.next();
				scan.useDelimiter(Pattern.compile("\","));
				line = scan.next();
				String province = line.substring(11);
				System.out.println("Province"+i+": "+province);
				scan.useDelimiter(Pattern.compile("\"NAME_2\": \""));
				scan.next();
				scan.useDelimiter(Pattern.compile("\","));
				line = scan.next();
				String name = line.substring(11);
				System.out.println("Name"+i+": "+name);
				 scan.useDelimiter(Pattern.compile("\"MultiPolygon\"|} }]}")); //alt "} }]}"

				 scan.next();
				 scan.useDelimiter(Pattern.compile("\\} \\}"));
				 line = scan.next();
				 String data = line.substring(30);
				 data = data.replace(" ", "");
				 //System.out.println("Data: "+data);
				 String json = jsonstart+data+jsonend;
				// System.out.println("Data: "+json);*/
				
				
				try {
				statement.setString(1, geojson);
				statement.setString(2, name);
				statement.setString(3, province);
				statement.setString(4, country);
				statement.execute();
				//System.out.println("query:"+statement.toString());
				 } catch(SQLException e) {
					 System.out.println("lyckades inte sätta in: "+name);
					 e.printStackTrace();
				 }
				 
			    i++;
			    
			}
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			scan.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
