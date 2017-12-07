import geometry.BoundingBox;
import geometry.Point;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;


public class Rubin implements Layer {
	private String name, rubin;
	private Color color;
	private boolean hidden;
	
	public Rubin(String rubin, String name, Color c) {
		this.rubin = rubin;
		this.name = name;
		this.color = c;
	}
	
	public void setRubin(String rubin) {
		this.rubin = rubin;
	}
	
	public Point getMiddle() {
		Coordinates c = new Coordinates(rubin);
		return new Point((int)c.getEast(),(int)c.getNorth());
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
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		//System.out.println("Drawing rubin");
		if (!hidden) {
		g2d.setColor(color);
		Stroke s = g2d.getStroke();
		g2d.setStroke(new BasicStroke(2));
		Coordinates c = new Coordinates(rubin);
		//System.out.println("x:"+c.getEast()+" Y:"+c.getNorth());
		int x1 = (int) (((c.getEast()-2500)*xScale)+xShift);
		int y1 = (int) (((c.getNorth()+2500)*yScale)+yShift);
		int xWidth = (int) (5000 * xScale);
		int yWidth = -(int) (5000 * yScale);
		//System.out.println("x1:"+x1+" y1:"+y1+" xWidth:"+xWidth+" yWidth:"+yWidth);
		g2d.drawRect(x1,y1,xWidth,yWidth);
		g2d.setStroke(s);
		}
		
	}

	@Override
	public boolean isHidden() {
		return hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}
	
	

}
