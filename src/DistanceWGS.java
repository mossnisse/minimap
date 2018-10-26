import geometry.BoundingBox;
import geometry.Point;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;

public class DistanceWGS implements Layer {
	private Color color;
	private String name, direction;
	private boolean hidden;
	private Point c;
	private int dist;
	private CoordSystem cs;
	

	DistanceWGS(String name, Point c, int dist, String direction, CoordSystem cs) {
		this.name = name;
		this.c=c;
		this.dist = dist;
		this.direction = direction;
		this.color = Color.orange;
		hidden = false;
		this.cs = cs;
	}
	
	@Override
	public void setColor(Color c) {
		this.color = c;
	}

	@Override
	public Color getColor() {
		return color;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setMinZoomL(int zoomLevel) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setMaxZoomL(int zoomLevel) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale,
			double yShift, double yScale, BoundingBox bounds) {
			g2d.setColor(color);
			Stroke s = g2d.getStroke();
			g2d.setStroke(new BasicStroke(2));
			
			
			
			int x1 = (int) (((c.getX())*xScale)+xShift);
			int y1 = (int) (((c.getY())*yScale)+yShift);
			int ds = (int) (dist * xScale);
			int x2 = x1;
			int y2 = y1;
			if (direction.equals("E")) {
				x2+= ds;
			}
			
			if (direction.equals("W")) {
				x2-= ds;
			}
			
			if (direction.equals("N")) {
				y2-= ds;
			}
			
			if (direction.equals("S")) {
				y2+= ds;
			}
			
			if (direction.equals("NE")) {
				y2-= ds/Math.sqrt(2);
				x2+= ds/Math.sqrt(2);
			}
			
			if (direction.equals("SE")) {
				y2+= ds/Math.sqrt(2);
				x2+= ds/Math.sqrt(2);
			}
			
			if (direction.equals("NW")) {
				y2-= ds/Math.sqrt(2);
				x2-= ds/Math.sqrt(2);
			}
			
			if (direction.equals("SW")) {
				y2+= ds/Math.sqrt(2);
				x2-= ds/Math.sqrt(2);
			}
			
			g2d.drawLine(x1,y1,x2,y2);
			//System.out.println("Draw Dist x1: "+ x1 + " x2: "+ x2);
			g2d.setStroke(s);
	}

	@Override
	public boolean isHidden() {
		return hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	@Override
	public void setCRS(CoordSystem cs) {
		this.cs = cs;
	}

	@Override
	public CoordSystem getCRS() {
		return cs;
	}

}
