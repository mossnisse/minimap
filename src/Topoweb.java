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
	private CoordSystem cs;
	
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
		
		public TileIndex[] getTileIndexes(BoundingBox box,int tilematrix) {
			int pxmin =box.getX1();  // in meters Sweref99TM
			int pymin =box.getY1();
			int pxmax = box.getX2(); // in meters Sweref99TM
			int pymax = box.getY2();
			//System.out.println("px: ("+pxmin+"-"+pxmax+")");
			//System.out.println("py: ("+pymin+"-"+pymax+")");
			
			TileIndex tp3 = new TileIndex();
			tp3.setTileIndex(pxmin, pymax, tilematrix);
			TileIndex tp4 = new TileIndex();
			tp4.setTileIndex(pxmax, pymin, tilematrix);
			//System.out.println("tp3: "+tp3);
			//System.out.println("tp4: "+tp4);
			int rowMin= tp3.row;
			int rowMax=	tp4.row;
			int colMin=	tp3.col;
			int colMax=	tp4.col;
			
			int numTiles = (rowMax-rowMin+1)*(colMax-colMin+1);
			//System.out.println("numTiles: "+numTiles);
			TileIndex[] indexes = new TileIndex[numTiles];
			int i=0;
			for(row = rowMin; row<rowMax+1; row++) {
				for(col = colMin; col<colMax+1; col++) {
					indexes[i]= new TileIndex(tilematrix, col, row);
					i++;
				}
			}
			return indexes;
		}
		
		// Overriding equals() to compare two TileIndex
	    @Override
	    public boolean equals(Object o) { 
	        // If the object is compared with itself then return true   
	        if (o == this) { 
	            return true; 
	        } 
	        /* Check if o is an instance of TileIndex or not "null instanceof [type]" also returns false */
	        if (!(o instanceof TileIndex)) { 
	            return false; 
	        } 
	        // typecast o to Complex so that we can compare data members  
	        TileIndex tile = (TileIndex) o; 
	        // Compare the data members and return accordingly  
	        return tile.col == this.col && tile.row == this.row && tile.zoomLevel == this.zoomLevel;
	    } 
		
	    @Override
	    public int hashCode() {
	        return col*row*zoomLevel;
	    }
	    
	    @Override
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
				System.out.println(path);
				Image img = ImageIO.read(path);
				tiles.put(index, img);
				return img;
			}
		}
	}
	
	public Topoweb() {
		tileBuffer = new TileBuffer();
		cs = CoordSystem.Sweref99TM;
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
		int tilesize = 256; //pixlar
		int tilematrix = 0;  //zoomlevel?		
		//origo 8500000; -1200000;  // övre vänstra hörnet
		//tilematrix == 0 => 4096 m/pixel  tilematrix == 1 => 2048 m/pixel  tilematrix == 2 => 1024 m/pixel
		 
		//System.out.println("xScale: "+xScale);
		//System.out.println("1/xScale: "+1/xScale);
		int tileWidth = tileWidth(tilematrix);
		//System.out.println("tileWidth: "+tileWidth);
		tilematrix = tileMatrix((int)Math.round (1/xScale*tilesize));	
		//System.out.println("calc tile matrix: " + calcTilematrix);
		TileIndex bla = new TileIndex();
		TileIndex[] indexes = bla.getTileIndexes(bounds,tilematrix);

		for (TileIndex ind: indexes) {
			if (ind.col>-1 & ind.row >-1) {
				Image img = tileBuffer.getTile(ind);
				BoundingBox box = getTileBounds(ind);
				int x1 = (int) ((box.getX1()*xScale)+xShift);
				int y1 = (int) ((box.getY1()*yScale)+yShift);
				int x2 = (int) ((box.getX2()*xScale)+xShift);
				int y2 = (int) ((box.getY2()*yScale)+yShift);
				g2d.drawImage(img,x1,y2,x2-x1,y1-y2,null);
			}
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

	@Override
	public void setCRS(CoordSystem cs) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public CoordSystem getCRS() {
		return cs;
	}

}
