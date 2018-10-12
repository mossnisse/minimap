import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.net.URL;

import javax.imageio.ImageIO;

import geometry.BoundingBox;

public class Topoweb implements Layer{
	private String key = "007d0995-da35-38ed-81b6-2a11e9c29d10";
	private String url = "https://api.lantmateriet.se/open/topowebb-ccby/v1/wmts/token/";
	private String name;
	private boolean hidden;
	private Color color;
	private int minZoomL;
	private int maxZoomL;
 ///?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=topowebb&STYLE=default&TILEMATRIXSET=3006&TILEMATRIX=9&TILEROW=862&TILECOL=887&FORMAT=image/png";

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
		this.minZoomL = zoomLevel;
		
	}

	@Override
	public void setMaxZoomL(int zoomLevel) {
		this.maxZoomL = zoomLevel;
		
	}

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		if (zoomLevel > maxZoomL && maxZoomL != 0) return false;
		return true;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	// return the resolution in m/pixel
	/*private static int resolution (int tilematrix) {
		return 4096/(2^tilematrix); 
	}*/
	
	private static int tileWidth (int tilematrix) {
		return 1048576/(int)(Math.pow((double)2,(double)tilematrix));
	}
	
	private static BoundingBox tileBounds(int tilerow, int tilecol, int tilematrix) {
		int origoY = 8500000;
		int origoX = -1200000;
		int tileWidth = tileWidth(tilematrix); // meters
		//System.out.println("tileWidth: "+tileWidth);
		//System.out.println("tilecol: "+tilecol+" rilerow: "+tilerow);
		return new BoundingBox(origoX+tileWidth*tilecol, origoY-tileWidth*tilerow,  origoX+tileWidth*(tilecol+1), origoY-tileWidth*(tilerow+1));
	}
	
	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) throws Exception {
		
		
		//if (bounds.intersects(box)) {
			int tilesize = 256; //pixlar
			//int tilerow = 862;	//South => sweref99TM 6733546  
			//int tilecol = 887;	//East  => sweref99TM 617897 
			int tilematrix = 1;  //zoomlevel?
			//NW hörnet på kartan = 6734584, 616548
			int tilerow = 2;	//South => sweref99TM 6733546  
			int tilecol = 4;	//East  => sweref99TM 617897   
			
			
			
			//origo 8500000; -1200000;  // övre vänstra hörnet
			//tilematrix == 0 => 4096 m/pixel
			//tilematrix == 1 => 2048 m/pixel
			//tilematrix == 2 => 1024 m/pixel
			 
			//int resolution = 4096 /tilematrix^2; // m/pixel;
			
			BoundingBox box = tileBounds(tilerow, tilecol, tilematrix);
			
			int x1 = (int) ((box.getX1()*xScale)+xShift);
			int y1 = (int) ((box.getY1()*yScale)+yShift);
			int x2 = (int) ((box.getX2()*xScale)+xShift);
			int y2 = (int) ((box.getY2()*yScale)+yShift);
			
			//System.out.println("x1:"+x1+", y1:"+(y2)+", x2:"+(x2-x1)+", y2:"+(y1-y2));
			 ///?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=topowebb&STYLE=default&TILEMATRIXSET=3006&TILEMATRIX=9&TILEROW=862&TILECOL=887&FORMAT=image/png";
			URL path = new URL(url+key
					+"/?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=topowebb&STYLE=default&TILEMATRIXSET=3006&TILEMATRIX="
					+tilematrix+"&TILEROW="+tilerow+"&TILECOL="+tilecol+"&FORMAT=image/png");
			System.out.println(path);
			System.out.println("BoundingBox/m: "+box);
			System.out.println("xScale: "+xScale+" yScale: "+yScale+ " xShift: "+ xShift+" yShift: "+ yShift);
			System.out.println("BoundingBox/pixel: "+x1+", "+y1+", "+x2+", "+y2);
			//URL url = new URL("http://bks6.books.google.ca/books?id=5VTBuvfZDyoC&printsec=frontcover&img=1& zoom=5&edge=curl&source=gbs_api");
			Image img = ImageIO.read(path);
			g2d.drawImage(img,x1,y2,x2-x1,y1-y2,null);
		//}
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
