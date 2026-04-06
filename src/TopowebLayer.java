import coords.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.io.IOException;
import java.util.LinkedHashMap;
import javax.imageio.ImageIO;
import geometry.BoundingBox;

public class TopowebLayer implements Layer {
	private final Canvas canvas;
	//private final String key = "007d0995-da35-38ed-81b6-2a11e9c29d10";
	//private final String url = "https://api.lantmateriet.se/open/topowebb-ccby/v1/wmts/token/";
	private final String url = "http://hades.slu.se/lm/topowebb/v1.1/wmts/";
	// http://hades.slu.se/lm/topowebb/wms/v1/?SERVICE=WMS&REQUEST=GetCapabilities
	private String name;
	private boolean hidden;
	private Color color;
	private int minZoomL;
	private int maxZoomL;
	private final TileBuffer tileBuffer;
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
			return java.util.Objects.hash(col, row, zoomLevel);
	    }
	    
	    @Override
		public String toString() {
			return "tilematrix: "+zoomLevel+" Tile column: "+col+" Tile row: "+row;
		}
	}
	
	private class TileBuffer {
		private final int MAX_TILES = 1000;
		private static final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
		private final java.util.Set<TileIndex> loading = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

		private final java.util.Map<TileIndex, Image> tiles = java.util.Collections.synchronizedMap(
				new LinkedHashMap<TileIndex, Image>(MAX_TILES, 0.75f, true) {
					@Override
					protected boolean removeEldestEntry(java.util.Map.Entry<TileIndex, Image> eldest) {
						return size() > MAX_TILES;
					}
				}
		);

		public Image getTileOrFetch(TileIndex index) {
			if (tiles.containsKey(index)) {
				return tiles.get(index);
			}

			// If not already loading, start the download
			if (loading.add(index)) {
				downloadTileAsync(index);
			}
			return null; // Return null immediately; we'll draw it once it arrives
		}

		private void downloadTileAsync(TileIndex index) {
			String tileUrl = url + "?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=topowebb"
					+ "&STYLE=default&TILEMATRIXSET=3006&TILEMATRIX=" + index.zoomLevel
					+ "&TILEROW=" + index.row + "&TILECOL=" + index.col + "&FORMAT=image/png";

			java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
					.uri(java.net.URI.create(tileUrl))
					.build();

			client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream())
					.thenApply(response -> {
						try (var is = response.body()) {
							return ImageIO.read(is);
						} catch (IOException e) {
							return null;
						}
					})
					.thenAccept(img -> {
						if (img != null) {
							tiles.put(index, img);
							canvas.repaint();
						}
						loading.remove(index); // Done loading
					});
		}
	}
	
	public TopowebLayer(Canvas canvas) {
		this.canvas = canvas;
		tileBuffer = new TileBuffer();
		cs = CoordSystem.SWEREF99TM;
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
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		int tilesize = 256;
		int tilematrix = tileMatrix((int)Math.round(1/xScale * tilesize));

		TileIndex[] indexes = new TileIndex().getTileIndexes(bounds, tilematrix);

		for (TileIndex ind : indexes) {
			if (ind.col > -1 && ind.row > -1) {
				// This call is now lightning fast
				Image img = tileBuffer.getTileOrFetch(ind);

				if (img != null) {
					BoundingBox box = getTileBounds(ind);
					int x1 = (int) ((box.getX1() * xScale) + xShift);
					int y1 = (int) ((box.getY1() * yScale) + yShift);
					int x2 = (int) ((box.getX2() * xScale) + xShift);
					int y2 = (int) ((box.getY2() * yScale) + yShift);

					g2d.drawImage(img, x1, y2, x2 - x1, y1 - y2, null);
				}
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
		this.cs = cs;
	}

	@Override
	public CoordSystem getCRS() {
		return cs;
	}
}