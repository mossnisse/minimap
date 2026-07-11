package shapeCalc;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;

import gis.shapefile.ShapefileReader;
import gis.shapefile.ShpGeometry;

public class felMarginal {
	static void main(String[] args) {
		try {
			List<ShapefileReader.Feature> polygons = ShapefileReader.readAll("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/gadm36_1.shp");
			List<ShapefileReader.Feature> points = ShapefileReader.readAll("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/Centroids/ProvinceCentroids.shp");
			PrintWriter writer = new PrintWriter("C:/Users/nisern99/Documents/sockenkartor/gadm_v36/ProvinsList.csv", "UTF-8");
			Iterator<ShapefileReader.Feature> poly_itr = polygons.iterator();
			System.out.println("Testar point filer");
			writer.println("Country, X, Y, maxdist, maxX, maxY, minX, minY");
			for (ShapefileReader.Feature pointR : points) {
				ShpGeometry point = pointR.geometry;
				ShpGeometry polygon = poly_itr.next().geometry;
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
				writer.println(pointR.attributes[1] + ", " + px + ", " + py + ", " + max_dist + ", " + maxx + ", " + maxy + ", " + minx + ", " + miny);
			}
			writer.close();
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
