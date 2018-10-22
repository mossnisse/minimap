import geometry.BoundingBox;
import geometry.Point;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.JPanel;


public class Canvas extends JPanel {
	private static final long serialVersionUID = 1L;
	BoundingBox bounds;
	Point coord;
	private ArrayList<Layer> layers;
	//private Image img;
	//Connection conn;
	
	public Canvas() {
		
		//this.conn = conn;
		// Sverige RT90
		/*int xMin = 1200000;
		int xMax = 1900000;
		int yMin = 6100000;
		int yMax = 7693900;*/
		
		// Sverige Sweref99TM
		int xMin = 194181;
		int xMax = 812496;
		int yMin = 6113836;
		int yMax = 7700000;
		bounds = new BoundingBox(xMin,yMin,xMax,yMax);
		coord = null;
		
		layers = new ArrayList<Layer>();
		try {
			
			TNGPolygonFile prFile = new TNGPolygonFile("provinserSWEREF99TM.tng");
			prFile.setColor(Color.black);
			prFile.setName("provinser");
			addLayerBotom(prFile);
			
			/*
			TNGPolygonFile prFile = new TNGPolygonFile("provinser.tng");
			prFile.setColor(Color.blue);
			prFile.setName("provinser");
			addLayerBotom(prFile);
			*/
			
			/*
			TNGPolygonFile socFile = new TNGPolygonFile("socknar.tng");
			socFile.setColor(Color.red);
			socFile.setName("socknar");
			addLayerBotom(socFile);*/
			
			
			TNGPolygonFile socFile = new TNGPolygonFile("socknarSWEREF99TM.tng");
			socFile.setColor(Color.red);
			socFile.setName("socknar");
			addLayerBotom(socFile);
			

			H2Table od = new H2Table("ortnamnsDB");
			od.setColor(Color.green);
			od.setName("Ortnamnsdb");
			od.setHidden(false);
			od.setMaxZoomL(5);
			addLayerBotom(od);
			
			MYSQLTable md = new MYSQLTable();
			md.setColor(Color.red);
			md.setName("LokalDB");
			md.setHidden(false);
			md.setMaxZoomL(40);
			addLayerBotom(md);
			
			Topoweb tb = new Topoweb();
			tb.setName("TopoWeb");
			md.setHidden(false);
			addLayerBotom(tb);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//System.out.println("sss");
			e.printStackTrace();
		}
	}
	
	public void setCoordinate(Point p) {
		coord = p;
		repaint();
	}
	
	public Point getCoordinate() {
		return coord;
	}
	
	public void hideCooirdinate() {
		coord = null;
		repaint();
	}
	
	public void addLayerBotom(Layer l) {
		layers.add(0,l);
		repaint();
	}
	
	public void addLayerTop(Layer l) {
		layers.add(l);
		repaint();
	}
	
	public void delLayer(String name) {
		Iterator<Layer> itr = layers.iterator();
	      while(itr.hasNext()) {
	         Layer l = itr.next();
	         if (l.getName().equals(name)) {
					itr.remove();
				}
	      }
	}
	
	public Layer getLayer(String name) {
		for(Layer l: layers) {
			if (l.getName().equals(name)) {
				return l;
			}
		}
		return null;
	}
	
	public ArrayList<Layer> getLayers() {
		return layers;
	}
	
	public void zoom(double stepp) {
		Point middle = bounds.getMidlePoint();
		int xMax = (int) (middle.getX()+stepp*(bounds.getWidth())/2);
		int xMin = (int) (middle.getX()-stepp*(bounds.getWidth())/2);
		int yMax = (int) (middle.getY()+stepp*(bounds.getHeight())/2);
		int yMin = (int) (middle.getY()-stepp*(bounds.getHeight())/2);
		bounds = new BoundingBox(xMin, yMin, xMax, yMax);
		
		//Dimension size = getSize();
		
		//System.out.println("Scale:"+bounds.getHeight()/size.getHeight()+" m/pix");
		//System.out.println("xMax"+xMax+"xMin"+xMin);
		repaint();
	}
	
	public void setBounds(BoundingBox b) {
		bounds = b;
		repaint();
	}
	
	public BoundingBox getBoundingBox() {
		return bounds;
	}
	
	public void panPixel(int x, int y) {
		Dimension size = getSize();
		double yscale = (bounds.getHeight())/size.getHeight();
		int xMax = (int) (bounds.getX2()-x*yscale);
		int xMin = (int) (bounds.getX1()-x*yscale);
		int yMax = (int) (bounds.getY2()+y*yscale);
		int yMin = (int) (bounds.getY1()+y*yscale);
		bounds = new BoundingBox(xMin, yMin, xMax, yMax);
		repaint();
	}
	
	public void focus(Point coord) {
		bounds.focus(coord);
		System.out.println(bounds);
		repaint();
	}
	
	public void setAreaRT90(int xMax, int xMin, int yMax, int yMin) {
		
	}
	
	public void setAreaPixlar(int xMax, int xMin, int yMax, int yMin) {
		
	}
	
	public Point translatePoint(Point p) {
		Dimension size = getSize();
		double yScale = size.getHeight()/-bounds.getHeight();
		
		Point m = bounds.getMidlePoint();
		int x1 = (int) (m.getX()+size.getWidth()/yScale);
		int x2 = (int) (m.getX()-size.getWidth()/yScale);
		bounds.setX1(x1);
		bounds.setX2(x2);
		
		int xMin = bounds.getX1();
		int xMax = bounds.getX2();
		int yMin = bounds.getY1();
		int yMax = bounds.getY2();
		
		double xShift1 = -xMin-(xMax-xMin)/2;
		double yShift1 = -yMin-(yMax-yMin)/2;
		double xScale = size.getHeight()/bounds.getHeight();
		
		int xShift2 =  (int) size.getWidth()/2;
		int yShift2 = (int) size.getHeight()/2;
		int x = (int) ((int) ((p.getX()+xShift1)*xScale)+xShift2);
		int y = (int) ((int) ((p.getY()+yShift1)*yScale)+yShift2);
		return new Point(x,y);
	}
	
	public Point translatePoint2(Point p) {
		Dimension size = getSize();
		
		double yScale = size.getHeight()/-bounds.getHeight();
		double xScale = size.getHeight()/bounds.getHeight();
		
		Point m = bounds.getMidlePoint();
		int x1 = (int) (m.getX()+size.getWidth()/yScale);
		int x2 = (int) (m.getX()-size.getWidth()/yScale);
		bounds.setX1(x1);
		bounds.setX2(x2);
		
		int xMin = bounds.getX1();
		int xMax = bounds.getX2();
		int yMin = bounds.getY1();
		int yMax = bounds.getY2();

		double xShift = (-xMin-(xMax-xMin)/2)*xScale + size.getWidth()/2;
		double yShift = (-yMin-(yMax-yMin)/2)*yScale + size.getHeight()/2;
		
		int x = (int) ((p.getX()-xShift)/xScale);
		int y = (int) ((p.getY()-yShift)/yScale);
		return new Point(x,y);
	}
	
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		
		Dimension size = getSize();
		
		double yScale = size.getHeight()/-bounds.getHeight();
		double xScale = size.getHeight()/bounds.getHeight();
		int zoomL = (int)bounds.getHeight()/(int)size.getHeight();
		
		Point m = bounds.getMidlePoint();
		int x1 = (int) (m.getX()+size.getWidth()/yScale);
		int x2 = (int) (m.getX()-size.getWidth()/yScale);
		bounds.setX1(x1);
		bounds.setX2(x2);
		
		int xMin = bounds.getX1();
		int xMax = bounds.getX2();
		int yMin = bounds.getY1();
		int yMax = bounds.getY2();

		double xShift = (-xMin-(xMax-xMin)/2)*xScale + size.getWidth()/2;
		double yShift = (-yMin-(yMax-yMin)/2)*yScale + size.getHeight()/2;
		
		Graphics2D g2d = (Graphics2D) g;
		
		//g2d.drawImage(img, 100,100,null);
		try {
			for(Layer l: layers) {
				//System.out.println("drawing: "+l.getName());
				//System.out.println("ZoomL: "+zoomL);
				if (l.isInZoomLevel(zoomL)) {
					g2d.setColor(l.getColor());
					l.draw(g2d, xShift, xScale, yShift, yScale, bounds);
				}
			}
		} 
		catch(Exception e) {
			System.out.println("error in Layer");
			e.printStackTrace();
			delLayer("LokalDB");
		}
		
		if (coord != null) {
			//System.out.println("Draw coord");
			g2d.setColor(Color.red);
			int x = (int) ((int) ((coord.getX()*xScale)+xShift));
			int y = (int) ((int) ((coord.getY()*yScale)+yShift));
			//System.out.println("x: "+x+", y:"+y);
			g2d.drawOval(x-10, y-10, 20, 20);
			g2d.drawLine(x-10,y-10,x+10,y+10);
			g2d.drawLine(x-10,y+10,x+10,y-10);
		}
	}
}
