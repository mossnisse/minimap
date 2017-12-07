package geometry;

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
		return new Point(p1.getX()+(p2.getX()-p1.getX())/2,p1.getY()+(p2.getY()-p1.getY())/2);
	}
	
	public int getX1() {
		return p1.getX();
	}
	
	public int getY1() {
		return p1.getY();
	}
	
	public int getX2() {
		return p2.getX();
	}
	
	public int getY2() {
		return p2.getY();
	}
	
	public int getHeight() {
		return p2.getY() - p1.getY();
	}
	
	public int getWidth() {
		return p2.getX() - p1.getX(); 
	}

	public boolean isInside(Point p) {
		return p1.getX() < p.getX() && p.getX() < p2.getX()
				&& p1.getY() < p.getY() && p.getY() < p2.getY();
	}
	
	public boolean isInside(BoundingBox b) {
		return getX1() < b.getX1() && getX2() > b.getX2()
				&& getY1() < b.getY1() && getY2() > b.getY2();
	}
	
	public boolean intersects(BoundingBox b) {
		return (Math.abs(2* (getX1() - b.getX1())+(getWidth() - b.getWidth()))  < (getWidth() + b.getWidth())) &&
		         (Math.abs(2* (getY1() - b.getY1())+(getHeight() - b.getHeight())) < (getHeight() + b.getHeight()));
		
		
		/*ABS(2*(x1 - x2) + (w1-w2) ) < (w1+w2)) &&
		ABS(2*(y1 - y2) + (h1-h2) ) < (h1+h2));*/
		 
		/*return !(b.getX1() > getX2()
			        || b.getX2() < getX1()
			        || b.getY2() > getY1()
			        || b.getY1() < getY2());*/
	}
	
	public void setX1(int x1) {
		p1.setX(x1);
	}
	
	public void setX2(int x2) {
		p2.setX(x2);
	}
	
	public void setY1(int y1) {
		p1.setY(y1);
	}
	
	public void setY2(int y2) {
		p2.setY(y2);
	}
	
	public void focus(Point coord) {
		Point m = getMidlePoint();
		int sx = m.getX()-coord.getX();
		int sy = m.getY()-coord.getY();
		int x1 = getX1();
		int y1 = getY1();
		int x2 = getX2();
		int y2 = getY2();
		p1.setX(x1-sx);
		p1.setY(y1-sy);
		p2.setX(x2-sx);
		p2.setY(y2-sy);
	}
	
	public String toString() {
		return "BoundingBox("+p1+" "+p2+")";
	}
}
