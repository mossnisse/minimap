import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

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
	private TileBuffer tileBuffer; 
	
	
 ///?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=topowebb&STYLE=default&TILEMATRIXSET=3006&TILEMATRIX=9&TILEROW=862&TILECOL=887&FORMAT=image/png";

	public class TileIndex {
		public int zoomLevel;  // == tilematrix;
		public int col;		// tile column increase East
		public int row;			// tile row increase South
		
		TileIndex(int zoomLevel, int col, int row) {
			this.zoomLevel = zoomLevel;
			this.col = col;
			this.row = row;
		}
		
		TileIndex(){			
		}
		
		public void setTileIndex(int x, int y, int tilematrix) {
			int origoY = 8500000;
			int origoX = -1200000;
			int tileWidth = tileWidth(tilematrix);
			col = (x-origoX)/tileWidth;
			row = (origoY-y)/tileWidth;
			zoomLevel = tilematrix;
		}
		
		//return new BoundingBox(origoX+tileWidth*tilecol, origoY-tileWidth*(tilerow+1),  origoX+tileWidth*(tilecol+1), origoY-tileWidth*(tilerow));
		
		
		public TileIndex[] getTileIndexes(BoundingBox box,int tilematrix) {
			int x1 =box.getX1();  // in meters Sweref99TM
			int y1 =box.getY1();
			int x2 = box.getX2(); // in meters Sweref99TM
			int y2 = box.getY2();
			TileIndex[] indexes = new TileIndex[2];
			
			TileIndex tile = new TileIndex();
			tile.setTileIndex(x1, y1, tilematrix);
			indexes[0]=tile;
			TileIndex tile2 = new TileIndex();
			tile2.setTileIndex(x2, y2, tilematrix);
			indexes[1]=tile2;
			//transform sweref99TM coordinate to tileIndex
			return indexes;
		}
		
		public String toString() {
			return "tilematrix: "+zoomLevel+" Tile column: "+col+" Tile row: "+row;
		}
	}
	
	private class TileBuffer {
		private HashMap<TileIndex,Image> tiles;
		
		TileBuffer() {
			tiles = new HashMap<TileIndex,Image>();
		}
		
		public Image getTile(TileIndex index) throws IOException {
			if (tiles.containsKey(index)) {
				return tiles.get(index);
			} else {
				URL path = new URL(url+key
					+"/?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=topowebb&STYLE=default&TILEMATRIXSET=3006&TILEMATRIX="
					+index.zoomLevel+"&TILEROW="+index.row+"&TILECOL="+index.col+"&FORMAT=image/png");
				Image img = ImageIO.read(path);
				tiles.put(index, img);
				return img;
			}
		}
	}
	
	
	public Topoweb() {
		tileBuffer = new TileBuffer();
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
		return new BoundingBox(origoX+tileWidth*tilecol, origoY-tileWidth*(tilerow+1),  origoX+tileWidth*(tilecol+1), origoY-tileWidth*(tilerow));
	}
	
	private static BoundingBox getTileBounds(TileIndex ind) {
		int origoY = 8500000;
		int origoX = -1200000;
		int tileWidth = tileWidth(ind.zoomLevel); // meters
		//System.out.println("tileWidth: "+tileWidth);
		//System.out.println("tilecol: "+tilecol+" rilerow: "+tilerow);
		return new BoundingBox(origoX+tileWidth*ind.col, origoY-tileWidth*(ind.row+1),  origoX+tileWidth*(ind.col+1), origoY-tileWidth*(ind.row));
	}
	
	
	/*
	private static TileIndex[] getTileIndexes(BoundingBox box,int tilematrix) {
		int x1 =box.getX1();  // in meters Sweref99TM
		int y1 =box.getY1();
		box.getX2(); // in meters Sweref99TM
		TileIndex[] indexes = new TileIndex[1];
		
		TileIndex tile = new TileIndex();
		tile.setTileIndex(x1, y1, tilematrix);
		indexes[0]=tile;
		//transform sweref99TM coordinate to tileIndex
		return indexes;
	}*/
	
	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) throws Exception {
		
		
		//if (bounds.intersects(box)) {
			int tilesize = 256; //pixlar
			//int tilerow = 862;	//South => sweref99TM 6733546  
			//int tilecol = 887;	//East  => sweref99TM 617897 
			int tilematrix = 5;  //zoomlevel?
			//NW hörnet på kartan = 6734584, 616548
			int tilerow = 2;	//South => sweref99TM 6733546  
			int tilecol = 3;	//East  => sweref99TM 617897   
			
			TileIndex test = new TileIndex(tilematrix,tilecol,tilerow);
			
			//origo 8500000; -1200000;  // övre vänstra hörnet
			//tilematrix == 0 => 4096 m/pixel
			//tilematrix == 1 => 2048 m/pixel
			//tilematrix == 2 => 1024 m/pixel
			 
			//int resolution = 4096 /tilematrix^2; // m/pixel;
			
			/*
			BoundingBox box = tileBounds(tilerow, tilecol, tilematrix);
			
			int x1 = (int) ((box.getX1()*xScale)+xShift);
			int y1 = (int) ((box.getY1()*yScale)+yShift);
			int x2 = (int) ((box.getX2()*xScale)+xShift);
			int y2 = (int) ((box.getY2()*yScale)+yShift);*/
			
			//System.out.println("x1:"+x1+", y1:"+(y2)+", x2:"+(x2-x1)+", y2:"+(y1-y2));
			 ///?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=topowebb&STYLE=default&TILEMATRIXSET=3006&TILEMATRIX=9&TILEROW=862&TILECOL=887&FORMAT=image/png";
			/*URL path = new URL(url+key
					+"/?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=topowebb&STYLE=default&TILEMATRIXSET=3006&TILEMATRIX="
					+tilematrix+"&TILEROW="+tilerow+"&TILECOL="+tilecol+"&FORMAT=image/png");
			System.out.println(path);*/
			//System.out.println("BoundingBox/m: "+box);
			//System.out.println("xScale: "+xScale+" yScale: "+yScale+ " xShift: "+ xShift+" yShift: "+ yShift);
			//System.out.println("BoundingBox/pixel: "+x1+", "+y1+", "+x2+", "+y2);
			//URL url = new URL("http://bks6.books.google.ca/books?id=5VTBuvfZDyoC&printsec=frontcover&img=1& zoom=5&edge=curl&source=gbs_api");
			//Image img = ImageIO.read(path);
			
			//Image img = tileBuffer.getTile(new TileIndex(tilematrix,tilecol,tilerow));
			//g2d.drawImage(img,x1,y2,x2-x1,y1-y2,null);
			
			TileIndex bla = new TileIndex();
			TileIndex[] indexes = bla.getTileIndexes(bounds,tilematrix);
			//TileIndex[] indexes = new TileIndex[3];
			//indexes[0] = new TileIndex(1,3,1);
			//indexes[1] = new TileIndex(1,3,2);
			//indexes[2] = new TileIndex(1,3,3);
			
			for (TileIndex ind: indexes) {
				System.out.println("tile index: "+ind);
				if (ind.col>-1 & ind.row >-1) {
					Image img = tileBuffer.getTile(ind);
					BoundingBox box = getTileBounds(ind);
					System.out.println("tile bounding box: "+box);
					int x1 = (int) ((box.getX1()*xScale)+xShift);
					int y1 = (int) ((box.getY1()*yScale)+yShift);
					int x2 = (int) ((box.getX2()*xScale)+xShift);
					int y2 = (int) ((box.getY2()*yScale)+yShift);
					System.out.println("BoundingBox/pixel: "+x1+", "+y1+", "+x2+", "+y2);
					g2d.drawImage(img,x1,y2,x2-x1,y1-y2,null);
				}
			}
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
