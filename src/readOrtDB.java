import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Vector;

import org.h2.jdbcx.JdbcDataSource;

import shapeFile.DataInputStreamSE;
import shapeFile.FieldDescriptor;
import shapeFile.PointESRI;
import shapeFile.dbfRecord;


public class readOrtDB {
		private String fileName;
		private int shapeType, nrRecords, nrFields;
		Vector<FieldDescriptor> descriptors;
		double minX, minY, maxX, maxY, minZ, maxZ, minM, maxM;
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//printDBFFeidlDescriptors();
		}
		
		private void readShapeFile() throws IOException {
			// Read .shp header mm.
			String shpFilename = fileName.substring(0, fileName.length() - 4)
					+ ".shp";
			DataInputStreamSE br = new DataInputStreamSE(new BufferedInputStream(
					new FileInputStream(new File(shpFilename))));
			br.skipBytes(24);

			br.readInt();
			br.readIntSE();
			shapeType = br.readIntSE();
			minX = br.readDoubleSE();
			minY = br.readDoubleSE();
			maxX = br.readDoubleSE();
			maxY = br.readDoubleSE();
			minZ = br.readDoubleSE();
			maxZ = br.readDoubleSE();
			minM = br.readDoubleSE();
			maxM = br.readDoubleSE();
			
			// read .dbf header m.m.

			String dbfFilename = fileName.substring(0, fileName.length() - 4)
					+ ".dbf";
			DataInputStreamSE br2 = new DataInputStreamSE(new BufferedInputStream(
					new FileInputStream(new File(dbfFilename))));
			br2.readByte(); // 0  // byte dbfVersion = 
			br2.readByte(); // 1  // byte y = 
			br2.readByte(); // 2  // byte m = 
			br2.readByte(); // 3  // byte d = 
			// printHex("year: ", y);
			nrRecords = br2.readIntSE(); // 4-7
			int headerSize = br2.readInt16(); // 8-9
			nrFields = (headerSize - 32) / 32;
			br2.readInt16(); // 10-11  // int recordSize = 
			// printN("Record size", recordSize);
			br2.skipBytes(20);
			descriptors = new Vector<FieldDescriptor>();
			for (int i = 0; i < nrFields; i++) {
				FieldDescriptor desc = new FieldDescriptor();
				desc.read3(br2);
				descriptors.add(desc);
			}
			br2.skipBytes(1);

			// read data
			if (shapeType == 1.0) {
				
				int i=0;
				while (br.available() != 0) {
					PointESRI point = new PointESRI(br);
					dbfRecord rdata = new dbfRecord(descriptors);
					rdata.read(br2);
					runStatement("insert into ortnamnsDB values ("+i+", "+Math.round(point.getX())+", "+Math.round(point.getY())+", '"+rdata.getField(0)+"', '"+rdata.getField(1)+"', "+rdata.getField(2)+", '"+rdata.getField(3)+"')");
					i++;
				}
			} else {
				System.out.println("Not a Point file");
			}
		}
		
		
		public void printDBFFeidlDescriptors() {
			System.out.println("DBF-file field descriptors");
			for (FieldDescriptor desc : descriptors) {
				desc.print();
			}
		}
		
		/*
		 public static void main(String[] args) throws IOException {
			 readOrtDB db = new readOrtDB("..\\shp\\Ortnamnsdatabasen\\Ortnamn_landskap_socken_alla.shp");
		 }*/
		
		
}
