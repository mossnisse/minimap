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
			int pxmin =box.getX1();  // in meters Sweref99TM
			int pymin =box.getY1();
			int pxmax = box.getX2(); // in meters Sweref99TM
			int pymax = box.getY2();
			System.out.println("px: ("+pxmin+"-"+pxmax+")");
			System.out.println("py: ("+pymin+"-"+pymax+")");
			
			
			TileIndex tp3 = new TileIndex();
			tp3.setTileIndex(pxmin, pymax, tilematrix);
			TileIndex tp4 = new TileIndex();
			tp4.setTileIndex(pxmax, pymin, tilematrix);
			System.out.println("tp3: "+tp3);
			System.out.println("tp4: "+tp4);
			int rowMin= tp3.row;
			int rowMax=	tp4.row;
			int colMin=	tp3.col;
			int colMax=	tp4.col;
			
			int numTiles = (rowMax-rowMin+1)*(colMax-colMin+1);
			System.out.println("numTiles: "+numTiles);
			TileIndex[] indexes = new TileIndex[numTiles];
			int i=0;
			for(row = rowMin; row<rowMax+1; row++) {
				for(col = colMin; col<colMax+1; col++) {
					indexes[i]= new TileIndex(tilematrix, col, row);
					i++;
				}
			}
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
				//System.out.println(path);
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
	
	private static int log(int x, int base)
	{
	    return (int) (Math.log(x) / Math.log(base));
	}
	
	private static int tileMatrix(int tileWidth) {
		//return 1048576/(int)(Math.pow((double)2,(double)tilematrix));
		int m = log(1048576/tileWidth,2);
		if (m>9) m=9;
		return m;
	}
	
	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) throws Exception {
		
		
		//if (bounds.intersects(box)) {
			int tilesize = 256; //pixlar
			//int tilerow = 862;	//South => sweref99TM 6733546  
			//int tilecol = 887;	//East  => sweref99TM 617897 
			int tilematrix = 0;  //zoomlevel?
			//NW hörnet på kartan = 6734584, 616548			
			//origo 8500000; -1200000;  // övre vänstra hörnet
			//tilematrix == 0 => 4096 m/pixel
			//tilematrix == 1 => 2048 m/pixel
			//tilematrix == 2 => 1024 m/pixel
			 
			//int resolution = 4096 /tilematrix^2; // m/pixel;
			System.out.println("xScale: "+xScale);
			System.out.println("1/xScale: "+1/xScale);
			int tileWidth = tileWidth(tilematrix);
			System.out.println("tileWidth: "+tileWidth);
			int calcTilematrix = tileMatrix((int)Math.round (1/xScale*tilesize));
			
			System.out.println("calc tile matrix: " + calcTilematrix);
			
			tilematrix = calcTilematrix;
			
			TileIndex bla = new TileIndex();
			TileIndex[] indexes = bla.getTileIndexes(bounds,tilematrix);
			
			for (TileIndex ind: indexes) {
				//System.out.println("tile index: "+ind);
				if (ind.col>-1 & ind.row >-1) {
					Image img = tileBuffer.getTile(ind);
					BoundingBox box = getTileBounds(ind);
					//System.out.println("tile bounding box: "+box);
					int x1 = (int) ((box.getX1()*xScale)+xShift);
					int y1 = (int) ((box.getY1()*yScale)+yShift);
					int x2 = (int) ((box.getX2()*xScale)+xShift);
					int y2 = (int) ((box.getY2()*yScale)+yShift);
					//System.out.println("BoundingBox/pixel: "+x1+", "+y1+", "+x2+", "+y2);
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
