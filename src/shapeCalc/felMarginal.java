package shapeCalc;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Vector;
import shapeFile.PointESRI;
import shapeFile.PolygonESRI;
import shapeFile.dbfRecord;
import shapeFile.shapeFile;

public class felMarginal {
	public static void main(String[] args) {
		try {
			shapeFile poly = new shapeFile("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/gadm36_1.shp");
			shapeFile points = new shapeFile("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/Centroids/ProvinceCentroids.shp");
			PrintWriter writer = new PrintWriter("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/ProvinsList.csv", "UTF-8");
			Vector<PolygonESRI> polyV = poly.getPlygons();
			//Vector<dbfRecord> polyDBF = poly.getDBFRecords();
			Vector<PointESRI> pointsV = points.getPoints();
			Vector<dbfRecord> pointsDBF = points.getDBFRecords();
			Iterator<dbfRecord> pointdbf_itr = pointsDBF.iterator();
			Iterator<PolygonESRI> polypoly_itr = polyV.iterator();
			//Iterator<dbfRecord> polydbf_itr = polyDBF.iterator();
			System.out.println("Testar point filer");
			writer.println("Country, X, Y, maxdist, maxX, maxY, minX, minY");
			for (PointESRI item : pointsV) {
				
				dbfRecord pointdbf = pointdbf_itr.next();
				System.out.println(pointdbf);
			    System.out.println(item.toString());
			    PolygonESRI polygon = polypoly_itr.next();
			    //System.out.println(polydbf_itr.next());
			    System.out.println(polygon);
			    PointESRI[] poly_points = polygon.getPoints();
			    double max_dist = 0;
			    double maxy =-10000;
			    double maxx =-10000;
			    double miny =10000;
			    double minx =10000;
			    for (PointESRI poly_point: poly_points) {
			    	double dist = wgs84_distance(poly_point, item);
			    	if (max_dist<dist) max_dist=dist;
			    	if (maxy<poly_point.getY()) maxy=poly_point.getY();
			    	if (maxx<poly_point.getX()) maxx=poly_point.getX();
			    	if (miny>poly_point.getY()) miny=poly_point.getY();
			    	if (minx>poly_point.getX()) minx=poly_point.getX();
			    }
			    System.out.println("max distance: "+max_dist);
			    writer.println(pointdbf.getField(1)+", "+item.getX()+", "+item.getY()+", "+max_dist+", "+maxx+", "+maxy+", "+minx+", "+miny);
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
