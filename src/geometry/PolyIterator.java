package geometry;

import java.util.Iterator;

public class PolyIterator implements Iterator<Line> {
	private int[] parts;
	private Point[] points;
	private int curPart;
	private int curPoint;

	public PolyIterator(int[] parts, Point[] points) {
		curPoint =1;
		curPart =1;
		this.parts = parts;
		this.points= points;
	}
	
	@Override
	public boolean hasNext() {
		return curPoint<points.length;
	}

	@Override
	public Line next() {
		Line l = new Line(points[curPoint-1],points[curPoint]);
		int part=0;
		if (curPart==parts.length) {
			part = points.length;
		} else {
			part = parts[curPart];
		}
		if (curPoint == part-1) {
			curPoint+=2;
			curPart++;
		} else 
			curPoint++;
		return l;
	}

	@Override
	public void remove() {
		// TODO Auto-generated method stub
	}
}
