package geometry;

import java.util.Iterator;

public class Polygon implements Iterable<Line> {
	private int[] parts;
	private Point[] points;
	
	public Polygon(int[] parts, Point[] points) {
		this.parts = parts;
		this.points = points;
	}
	
	public int getNumParts() {
		return parts.length;
	}
	
	public int getNumPoints() {
		return points.length;
	}
	
	public int[] getParts() {
		return parts;
	}
	
	public Point[] getPoints() {
		return points;
	}
	
	public boolean isInside(Point p) {
		Point outside = new Point(10000000,10000000);
		Line t = new Line(outside, p);
		int intersects = 0;
		for (int i=0; i<parts.length; i++) {
			int jmax =0;
			if (i==parts.length-1) {
				jmax = points.length;
			} else {
				jmax = parts[i+1];
			}
			for (int j = parts[i]+1; j<jmax;j++) {
				
				Line l = new Line(points[j-1],points[j]);
				
				if (l.intersects(t)) intersects++;
			}
		}
		//System.out.println("intersects:"+intersects);
		return ((intersects & 1) == 1 );
	}
	
	public Line[] getLines() {
		int nrlines = points.length-parts.length;
		Line[] lines = new Line[nrlines];
		int lnr=0;
		for (int i=0; i<parts.length; i++) {
			//System.out.println("i: "+i);
			int jmax =0;
			if (i==parts.length-1) {
				jmax = points.length;
			} else {
				jmax = parts[i+1];
			}
			for (int j = parts[i]+1; j<jmax;j++) {
				
				lines[lnr] = new Line(points[j-1],points[j]);
				lnr++;
			}
		}
		return lines;
	}

	@Override
	public Iterator<Line> iterator() {
		// TODO Auto-generated method stub
		return new PolyIterator(parts, points);
	}
	
}


