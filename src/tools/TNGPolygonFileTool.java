package tools;

import coords.Coordinates;
import geometry.BoundingBox;
import geometry.Line;

import java.awt.*;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/*
public class TNGPolygonFileTool {
    public void calcSizes() {
        for(layers.TNGPolygonFileLayer.Province pr:provinces) {
            int xmax =0;
            int xmin = 100000000;
            int ymax = 0;
            int ymin = 100000000;
            for(Line ln:pr) {
                Point p1 = ln.getPoint1();
                Point p2 = ln.getPoint2();
                if (xmax<p1.getX()) xmax = p1.x;
                if (xmax<p2.getX()) xmax = p2.x;
                if (ymax<p1.getY()) ymax = p1.y;
                if (ymax<p2.getY()) ymax = p2.y;
                if (xmin>p1.getX()) xmin = p1.x;
                if (xmin>p2.getX()) xmin = p2.x;
                if (ymin>p1.getY()) ymin = p1.y;
                if (ymin>p2.getY()) ymin = p2.y;
            }
            System.out.println(pr.getName()+" x("+xmin+"-"+xmax+") y("+ymin+"-"+ymax+")");
        }
    }*/

	/*
	public void convertCoordsysrt90toSweref99TM() {
		for(Polygon pr:provinces) {
			for(Point p:pr.getPoints()) {
				Coordinates c = new Coordinates((double)p.getY(),(double)p.getX());
				Coordinates wgs84 = c.convertWGS84();
				Coordinates sweref99TM= Coordinates.convertToSweref99TMFromWGS84(wgs84);
				Point p2 = new Point((int)Math.round(sweref99TM.getEast()),(int)Math.round(sweref99TM.getNorth()));
				//System.out.println(p);
				//System.out.println(p2);
			}
		}
	}*/

/*
    // saves an .tng file with RT90 coordinates in Sweref99TM coordinates
    public void saveFileConvert(String filename) throws IOException {
        DataOutputStream out = new DataOutputStream(new FileOutputStream(filename));
        out.writeInt(5);  // shape type == Polygon
        out.writeInt(provinces.length);  // number of Polygons
        out.writeInt(nameLength);  // name field length
        System.out.println("Save length: "+provinces.length);
        System.out.println("Save namelength: "+provinces.length);
        for (layers.TNGPolygonFileLayer.Province prov : provinces) {
            //int padlength = 50-prov.getName().length();
            String name = String.format("%1$-" +  nameLength + "s", prov.getName());
            //System.out.println("padded name:" + "\""+name+ "\"" + " lenght =" + prov.getName().length()+ " padlenght: "+padlength+ " padded lenght: "+  name.length());
            //System.out.println(name);
            out.writeBytes(name);
            // System.out.println(record.getField(nameField));
            BoundingBox box = prov.getBoundingBox();
            Point p1 = box.getP1();
            Point p2 = box.getP2();
            Coordinates c1 = new Coordinates(p1.getY(), p1.getX());
            Coordinates sweref99TM_1 = c1.convertToSweref99TMFromRT90();
            Point ps1 = new Point((int)Math.round(sweref99TM_1.getEast()), (int)Math.round(sweref99TM_1.getNorth()));
            Coordinates c2 = new Coordinates(p2.getY(), p2.getX());
            Coordinates sweref99TM_2 = c2.convertToSweref99TMFromRT90();
            Point ps2 = new Point((int)Math.round(sweref99TM_2.getEast()), (int)Math.round(sweref99TM_2.getNorth()));
            out.writeInt(ps1.x);
            out.writeInt(ps1.y);
            out.writeInt(ps2.x);
            out.writeInt(ps2.y);
            out.writeInt(prov.getNumParts());
            out.writeInt(prov.getNumPoints());
            for (int part : prov.getParts()) {
                out.writeInt(part);
            }
            for (Point p : prov.getPoints()) {
                Coordinates c = new Coordinates(p.getY(), p.getX());
                Coordinates sweref99TM= c.convertToSweref99TMFromRT90();
                Point ps = new Point((int)Math.round(sweref99TM.getEast()),(int)Math.round(sweref99TM.getNorth()));
                out.writeInt(ps.x);
                out.writeInt(ps.y);
            }
        }
        System.out.println("Save length: "+provinces.length);
        out.close();
    }

    static void main(String[] args) {
		/*TNGPolygonFile poly;
		try {
			//poly = new TNGPolygonFile("provinser.tng");
			//poly = new TNGPolygonFile("socknar.tng");
			//poly.saveFileConvert("provinserSWEREF99TM.tng");
			//poly.saveFileConvert("socknarSWEREF99TM.tng");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
//    }
//}
