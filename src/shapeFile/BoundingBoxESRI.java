package shapeFile;

import java.io.IOException;

public class BoundingBoxESRI {
	double x1,y1, x2, y2;

	public BoundingBoxESRI(DataInputStreamSE bs) throws IOException {
		read(bs);
	}

	public double getX1() {
		return x1;
	}
	
	public double getY1() {
		return y1;
	}
	
	public double getX2() {
		return x2;
	}
	
	public double getY2() {
		return y2;
	}
	
	public void read(DataInputStreamSE bs) throws IOException {
		x1 = bs.readDoubleSE();
		y1 = bs.readDoubleSE();
		x2 = bs.readDoubleSE();
		y2 = bs.readDoubleSE();
	}
	
	public  String toString(){
		return "Bounding box ("+x1+", "+y1+") ("+x2+", "+y2+")";
	}
	
	public void print() {
		System.out.println("Bounding box ("+x1+", "+y1+") ("+x2+", "+y2+")");
	}
	
}
