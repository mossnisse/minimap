package Geojson;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.regex.Pattern;

public class convEcZ {

	public static void main(String[] args) {
		try {
			Scanner scan = new Scanner(new File("C:/Users/nisern99/Documents/sockenkartor/Marineregions/CountriesEconomicalZonesSimplf.geojson"), "UTF-8");
			String line;
			String jsonstart = "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"MultiPolygon\",\"coordinates\":";
			String jsonend = "}}]}";
			try {
				String url = "jdbc:mysql://130.239.50.18:3306/samhall";
				String user = "root";
				Connection conn = DriverManager.getConnection(url, user, "");
				String sqlstmt = "update countries set geojson =? where english =? and geojson is null"; // and 
				 PreparedStatement statement = conn.prepareStatement(sqlstmt);
			 
			int i=1;
			while (scan.hasNext()) {
				scan.useDelimiter(Pattern.compile("\"Country\": \""));
				line = scan.next();
				//System.out.println(line);
				scan.useDelimiter(Pattern.compile("\","));
				line = scan.next();
				//System.out.println("name: "+line);
				String name = line.substring(12);
				System.out.println("Name"+i+": "+name);
				scan.useDelimiter(Pattern.compile("\"MultiPolygon\"")); //alt "} }]}"
				 scan.next();
				 scan.useDelimiter(Pattern.compile("\\} \\}"));
				 line = scan.next();
				 //System.out.println("data: "+line);
				 String data = line.substring(30);
				 
				 data = data.replace(" ", "");
				 //System.out.println("Data: "+data);
				 String json = jsonstart+data+jsonend;
				//System.out.println("Data: "+json);
				try {
				statement.setString(1, json);
				statement.setString(2, name);
				statement.execute();
				 } catch(SQLException e) {
					 System.out.println("lyckades inte sätta in: "+name);
					// System.out.println("Data: "+json);
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

