package shapeFile;

import java.io.IOException;

public class PolygonESRI extends ShapeESRI{
	private BoundingBoxESRI box;
	private int numParts, numPoints;
	private int[] parts;
	private PointESRI[] points;

	PolygonESRI(DataInputStreamSE bs) throws IOException {
		//super(bs);
		read(bs);
	}
	
	public BoundingBoxESRI getBoundingBox() {
		return box;
	}
	
	public int getNumParts() {
		return numParts;
	}
	
	public int getNumPoints() {
		return numPoints;
	}
	
	public int[] getParts() {
		return parts;
	}
	
	public PointESRI[] getPoints() {
		return points;
	}
	
	public void read(DataInputStreamSE bs) throws IOException {
		super.read(bs);
		box = new BoundingBoxESRI(bs);
		//System.out.println(box);
		numParts = bs.readIntSE();
		//System.out.println();
		numPoints = bs.readIntSE();
		//System.out.println("NumParts: "+numParts+" NumPoints: "+numPoints);
		parts = new int[numParts];
		for (int i=0;i<numParts;i++) {
			parts[i] = bs.readIntSE();
			//System.out.println("Part"+i+": "+parts[i]);
		}
		
		points = new PointESRI[numPoints];
		for (int i=0;i<numPoints;i++) {
			double x = bs.readDoubleSE();
			double y = bs.readDoubleSE();
			points[i] = new PointESRI(x, y);
			//System.out.println(i+": "+points[i]);
		}
	}

	public void print() {
		System.out.println("Polygon");
		System.out.println(box);
		System.out.println("NumParts: "+numParts);
		System.out.println("numPoints: "+numPoints);
	}
	
	public String toString() {
		return "Polygon("+box+" numParts:"+numParts+" numPoints"+numPoints+")";
	}
}
