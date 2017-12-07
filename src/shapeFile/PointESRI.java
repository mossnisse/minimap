package shapeFile;

import geometry.Point;

import java.io.IOException;


public class PointESRI extends ShapeESRI{
	double x,y;
	
	public PointESRI(DataInputStreamSE br) throws IOException {
		//super(br);
		read(br);
	}
	
	public PointESRI(double x, double y) {
		this.x = x;
		this.y = y;
	}
	
	public void read(DataInputStreamSE bs) throws IOException {
		super.read(bs);
		x = bs.readDoubleSE();
		y = bs.readDoubleSE();
		//System.out.println("Point("+x+", "+y+")");
	}
	
	public double getX() {
		return x;
	}
	
	public double getY() {
		return y;
	}
	
	public void setX(double x) {
		this.x=x;
	}
	
	public void setY(double y) {
		this.y=y;
	}
	
	
	public void print() {
		System.out.println("Point("+x+", "+y+")");
	}
	
	public String toString() {
		return "Point("+x+", "+y+")";
	}
	
	public Point toPoint() {
		return new Point((int)this.getY(), (int)this.getX());
	}

}
