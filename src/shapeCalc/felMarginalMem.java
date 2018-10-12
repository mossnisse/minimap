package shapeCalc;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import shapeFile.PointESRI;
import shapeFile.PolygonESRI;
import shapeFile.ReccordESRI;
import shapeFile.dbfRecord;
import shapeFile.shapeReader;

public class felMarginalMem {
	public static void main(String[] args) {
		try {
			
			//District Sverige
			shapeReader shapeReaderPoint = new shapeReader("C:/Users/nisern99/Documents/sockenkartor/multi_centrwgs84.shp");
			shapeReader shapeReaderPolygon = new shapeReader("C:/Users/nisern99/Documents/sockenkartor/Socken_multiwgs84.shp");
			PrintWriter writer = new PrintWriter("C:/Users/nisern99/Documents/sockenkartor/sockendistances.csv", "UTF-8");
			
			//Provinser Finland
			//shapeReader shapeReaderPoint = new shapeReader("C:/Users/nisern99/Documents/sockenkartor/Finland/Biologicalprovinces_centroids.shp");
			//shapeReader shapeReaderPolygon = new shapeReader("C:/Users/nisern99/Documents/sockenkartor/Finland/Biologicalprovinces.shp");
			//PrintWriter writer = new PrintWriter("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/FinlandProvinceList.csv", "UTF-8");
			
			// Provinser Sverige
			//shapeReader shapeReaderPoint = new shapeReader("C:/Users/nisern99/Documents/sockenkartor/ProvinceWGS84UTF8Centroids.shp");
			//shapeReader shapeReaderPolygon = new shapeReader("C:/Users/nisern99/Documents/sockenkartor/ProvinceWGS84UTF8.shp");
			//PrintWriter writer = new PrintWriter("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/SwedenProvinceList.csv", "UTF-8");
			
			// District
			//shapeReader shapeReaderPoint = new shapeReader("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/Centroids/DistrictCentroids.shp");
			//shapeReader shapeReaderPolygon = new shapeReader("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/gadm36_2.shp");
			//PrintWriter writer = new PrintWriter("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/DistrictList.csv", "UTF-8");
			
			
			// Provinser
			//shapeReader shapeReaderPoint = new shapeReader("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/Centroids/ProvinceCentroids.shp");
			//shapeReader shapeReaderPolygon = new shapeReader("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/gadm36_1.shp");
			//PrintWriter writer = new PrintWriter("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/ProvinsList.csv", "UTF-8");
			
			//Countries
			//shapeReader shapeReaderPoint = new shapeReader("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/Centroids/CountryCentroids.shp");
			//shapeReader shapeReaderPolygon = new shapeReader("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/gadm36_0.shp");
			//PrintWriter writer = new PrintWriter("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/CountryList.csv", "UTF-8");
			
			
			Iterator<ReccordESRI> polypoly_itr = shapeReaderPolygon.iterator();
			
			System.out.println("Testar point filer");
			
			
			
			
			//District
			writer.println("Country,Län,District,X,Y,maxdist,maxX,maxY,minX,minY");
			
			//Provinser
			//writer.println("\"Country\",\"Province\",\"alt_names\",\"native_name\",\"TypeEng\",\"TypeNative\",Code,X,Y,maxdist,maxX,maxY,minX,minY");
			
			//Countries
			//writer.println("\"Country\",Code,X,Y,maxdist,maxX,maxY,minX,minY");
			
			for (ReccordESRI pointR : shapeReaderPoint) {
				ReccordESRI polygonRec = polypoly_itr.next();
				dbfRecord pointdbf = pointR.DBF;
				PointESRI pointRecord = pointR.point;
				PolygonESRI polygonRecord = polygonRec.polygon;
				System.out.println();
				System.out.println(pointdbf);
				System.out.println(pointRecord);
				System.out.println(polygonRecord);
			    PointESRI[] poly_points = polygonRecord.getPoints();
			    double max_dist = 0;
			    double maxy =-10000;
			    double maxx =-10000;
			    double miny =10000;
			    double minx =10000;
			    for (PointESRI poly_point: poly_points) {
			    	double dist = wgs84_distance(poly_point, pointRecord);
			    	if (max_dist<dist) max_dist=dist;
			    	if (maxy<poly_point.getY()) maxy=poly_point.getY();
			    	if (maxx<poly_point.getX()) maxx=poly_point.getX();
			    	if (miny>poly_point.getY()) miny=poly_point.getY();
			    	if (minx>poly_point.getX()) minx=poly_point.getX();
			    }
			    System.out.println("max distance: "+max_dist);
			    System.out.println("maxX: "+maxx+" maxY: "+maxy+" minx: "+minx+" miny "+miny );
			    
			    
			  //District Sverige
				  writer.println("\"Sweden\",\""+pointdbf.getField(5)+"\",\""+pointdbf.getField(4)+"\","+pointRecord.getX()+","+pointRecord.getY()+","+max_dist+","+maxx+","+maxy+","+minx+","+miny);
			    
			    //Provinser Finland
			   // writer.println("\"Finland\",\""+pointdbf.getField(0)+"\",\"\",\"\",\"\",\"\",\"\","+pointRecord.getX()+","+pointRecord.getY()+","+max_dist+","+maxx+","+maxy+","+minx+","+miny);
			    
			    //Provinser Sverige
			   //writer.println("\"Sweden\",\""+pointdbf.getField(0)+"\",\"\",\"\",\"\",\"\",\"\","+pointRecord.getX()+","+pointRecord.getY()+","+max_dist+","+maxx+","+maxy+","+minx+","+miny);
			    
			    // Countries
			   // writer.println("\""+pointdbf.getField(1)+"\","+pointdbf.getField(0)+","+pointRecord.getX()+","+pointRecord.getY()+","+max_dist+","+maxx+","+maxy+","+minx+","+miny);
			    //Provinser
			    //writer.println("\""+pointdbf.getField(1)+"\",\""+pointdbf.getField(3)+"\",\""+pointdbf.getField(4)+"\",\""+pointdbf.getField(5)+"\",\""+pointdbf.getField(7)+"\",\""+ pointdbf.getField(6)+"\","+pointdbf.getField(9)+","+pointRecord.getX()+","+pointRecord.getY()+","+max_dist+","+maxx+","+maxy+","+minx+","+miny);
			   //District 
			   // writer.println("\""+pointdbf.getField(1)+"\",\""+pointdbf.getField(3)+"\",\""+pointdbf.getField(6)+"\",\""+pointdbf.getField(7)+"\",\""+pointdbf.getField(8)+"\",\""+pointdbf.getField(10)+"\",\""+ pointdbf.getField(9)+"\","+pointdbf.getField(12)+","+pointRecord.getX()+","+pointRecord.getY()+","+max_dist+","+maxx+","+maxy+","+minx+","+miny);
			}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("kunde inte öppna .shp filerna");
			e.printStackTrace();
		}
		
	}
	
	public static double wgs84_distance(PointESRI p1, PointESRI p2) {
		double lat1=p1.getY();
		double lat2=p2.getY();
		double lon1=p1.getX();
		double lon2=p2.getX();
		double R = 6371000; // Radius of the earth in m
		double dLat = deg2rad(lat2-lat1);  // deg2rad below
		double dLon = deg2rad(lon2-lon1); 
		double a = 
		    Math.sin(dLat/2) * Math.sin(dLat/2) +
		    Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * 
		    Math.sin(dLon/2) * Math.sin(dLon/2); 
		  double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
		  double d = R * c; // Distance in m
		  return d;
	}
	
	private static double deg2rad(double deg) {
		  return deg * (Math.PI/180);
	}
	
}
