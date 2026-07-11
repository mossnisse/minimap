package test.tools;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import org.h2.jdbcx.JdbcDataSource;
import gis.shapefile.ShapeType;
import gis.shapefile.ShapefileReader;

public class readOrtDB {
		private final String fileName;
		static Connection conn;

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

		public readOrtDB(String fileName) throws IOException {
			this.fileName = fileName;
			createConnection();
			runStatement("DROP table IF EXISTS ortnamnsDB");
			runStatement("create table ortnamnsDB (Ort_ID INTEGER, East INTEGER, North Integer, Ortnamn VARCHAR(55), Detaljtyp VARCHAR(9), FPNummer INTEGER, Socken VARCHAR(19))");
			readShapeFile();
			try {
				conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		private void readShapeFile() throws IOException {
			try (ShapefileReader reader = new ShapefileReader(fileName)) {
				if (reader.getShapeType().base() != ShapeType.POINT) {
					System.out.println("Not a Point file");
					return;
				}
				int i = 0;
				for (ShapefileReader.Feature f : reader) {
					String[] a = f.attributes;
					runStatement("insert into ortnamnsDB values (" + i + ", "
							+ Math.round(f.geometry.getX(0)) + ", "
							+ Math.round(f.geometry.getY(0)) + ", '"
							+ esc(a[0]) + "', '" + esc(a[1]) + "', "
							+ a[2] + ", '" + esc(a[3]) + "')");
					i++;
				}
			}
		}

		private static String esc(String s) {
			return s.replace("'", "''");
		}

		/*
		 public static void main(String[] args) throws IOException {
			 tools.readOrtDB db = new tools.readOrtDB("..\\shp\\Ortnamnsdatabasen\\Ortnamn_landskap_socken_alla.shp");
		 }*/


}
