package main.geometry;

import java.awt.*;

public class BoundingBox {
	protected Point p1;
	protected Point p2;

	public BoundingBox() {
	}
	
	public BoundingBox(Point p1, Point p2) {
		this.p1 = p1;
		this.p2 = p2;
	}

	public BoundingBox(int x1, int y1, int x2, int y2) {
		this.p1 = new Point(x1, y1);
		this.p2 = new Point(x2, y2);
	}
	
	public Point getP1() {
		return p1;
	}
	
	public Point getP2() {
		return p2;
	}
	
	public Point getMidlePoint() {
		return new Point(p1.x+(p2.x-p1.x)/2,p1.y+(p2.y-p1.y)/2);
	}
	
	public int getX1() {
		return p1.x;
	}
	
	public int getY1() {
		return p1.y;
	}
	
	public int getX2() {
		return p2.x;
	}
	
	public int getY2() {
		return p2.y;
	}
	
	public int getHeight() {
		return p2.y - p1.y;
	}
	
	public int getWidth() {
		return p2.x - p1.x;
	}

	public boolean isInside(Point p) {
		return p1.getX() <= p.getX() && p.getX() <= p2.getX()
				&& p1.getY() <= p.getY() && p.getY() <= p2.getY();
	}
	
	public boolean isInside(BoundingBox b) {
		return getX1() <= b.getX1() && getX2() >= b.getX2()
				&& getY1() <= b.getY1() && getY2() >= b.getY2();
	}
	
	public boolean intersects(BoundingBox b) {
		return (Math.abs(2* (getX1() - b.getX1())+(getWidth() - b.getWidth()))  < (getWidth() + b.getWidth())) &&
		         (Math.abs(2* (getY1() - b.getY1())+(getHeight() - b.getHeight())) < (getHeight() + b.getHeight()));
	}
	
	public void setX1(int x1) {
		p1.x = x1;
	}
	
	public void setX2(int x2) {
		p2.x =x2;
	}
	
	public void setY1(int y1) {
		p1.y = y1;
	}
	
	public void setY2(int y2) {
		p2.y = y2;
	}
	
	public void focus(Point coord) {
		Point m = getMidlePoint();
		int sx = m.x-coord.x;
		int sy = m.y-coord.y;
		int x1 = getX1();
		int y1 = getY1();
		int x2 = getX2();
		int y2 = getY2();
		p1.x = x1-sx;
		p1.y = y1-sy;
		p2.x = x2-sx;
		p2.y = y2-sy;
	}

	public BoundingBox expand(int amount) {
		return new BoundingBox(
				this.getX1() - amount,
				this.getY1() - amount,
				this.getX2() + amount,
				this.getY2() + amount
		);
	}

	public BoundingBox grow(double percentage) {
		int width = Math.abs(getX2() - getX1());
		int height = Math.abs(getY2() - getY1());

		int xBuffer = (int) (width * percentage);
		int yBuffer = (int) (height * percentage);

		return new BoundingBox(
				getX1() - xBuffer,
				getY1() - yBuffer,
				getX2() + xBuffer,
				getY2() + yBuffer
		);
	}

	public String toString() {
		return "BoundingBox("+p1+" "+p2+")";
	}
}