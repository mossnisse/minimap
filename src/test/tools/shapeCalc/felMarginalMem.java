package shapeCalc;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

import gis.shapefile.ShapefileReader;
import gis.shapefile.ShpGeometry;

public class felMarginalMem {
	static void main(String[] args) {
		//District Sverige
		try (ShapefileReader pointReader = new ShapefileReader("C:/Users/nisern99/Documents/sockenkartor/multi_centrwgs84.shp");
			 ShapefileReader polygonReader = new ShapefileReader("C:/Users/nisern99/Documents/sockenkartor/Socken_multiwgs84.shp");
			 PrintWriter writer = new PrintWriter("C:/Users/nisern99/Documents/sockenkartor/sockendistances.csv", "UTF-8")) {

			//Provinser Finland
			//... "C:/Users/nisern99/Documents/sockenkartor/Finland/Biologicalprovinces_centroids.shp"
			//... "C:/Users/nisern99/Documents/sockenkartor/Finland/Biologicalprovinces.shp"

			// Provinser Sverige
			//... "C:/Users/nisern99/Documents/sockenkartor/ProvinceWGS84UTF8Centroids.shp"
			//... "C:/Users/nisern99/Documents/sockenkartor/ProvinceWGS84UTF8.shp"

			// District / Provinser / Countries (gadm_v36): see git history for the path pairs

			Iterator<ShapefileReader.Feature> polygon_itr = polygonReader.iterator();

			System.out.println("Testar point filer");

			//District
			writer.println("Country,Län,District,X,Y,maxdist,maxX,maxY,minX,minY");

			for (ShapefileReader.Feature pointR : pointReader) {
				ShapefileReader.Feature polygonR = polygon_itr.next();
				ShpGeometry point = pointR.geometry;
				ShpGeometry polygon = polygonR.geometry;
				System.out.println();
				System.out.println(pointR);
				System.out.println(polygon);
				double px = point.getX(0);
				double py = point.getY(0);
				double max_dist = 0;
				double maxy = -10000;
				double maxx = -10000;
				double miny = 10000;
				double minx = 10000;
				for (int i = 0; i < polygon.getNumPoints(); i++) {
					double dist = wgs84_distance(polygon.getY(i), polygon.getX(i), py, px);
					if (max_dist < dist) max_dist = dist;
					if (maxy < polygon.getY(i)) maxy = polygon.getY(i);
					if (maxx < polygon.getX(i)) maxx = polygon.getX(i);
					if (miny > polygon.getY(i)) miny = polygon.getY(i);
					if (minx > polygon.getX(i)) minx = polygon.getX(i);
				}
				System.out.println("max distance: " + max_dist);
				System.out.println("maxX: " + maxx + " maxY: " + maxy + " minx: " + minx + " miny " + miny);

				String[] attrs = pointR.attributes;

				//District Sverige
				writer.println("\"Sweden\",\"" + attrs[5] + "\",\"" + attrs[4] + "\"," + px + "," + py + "," + max_dist + "," + maxx + "," + maxy + "," + minx + "," + miny);

				//Provinser Finland / Provinser Sverige
				//writer.println("\"Finland\",\"" + attrs[0] + "\",\"\",\"\",\"\",\"\",\"\"," + px + "," + py + "," + max_dist + "," + maxx + "," + maxy + "," + minx + "," + miny);

				// Countries
				//writer.println("\"" + attrs[1] + "\"," + attrs[0] + "," + px + "," + py + "," + max_dist + "," + maxx + "," + maxy + "," + minx + "," + miny);
			}
		} catch (IOException e) {
			System.out.println("kunde inte öppna .shp filerna");
			e.printStackTrace();
		}

	}

	public static double wgs84_distance(double lat1, double lon1, double lat2, double lon2) {
		double R = 6371000; // Radius of the earth in m
		double dLat = deg2rad(lat2-lat1);  // deg2rad below
		double dLon = deg2rad(lon2-lon1);
		double a =
		    Math.sin(dLat/2) * Math.sin(dLat/2) +
		    Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) *
		    Math.sin(dLon/2) * Math.sin(dLon/2);
		  double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
		  return R * c; // Distance in m
	}

	private static double deg2rad(double deg) {
		  return deg * (Math.PI/180);
	}

}
