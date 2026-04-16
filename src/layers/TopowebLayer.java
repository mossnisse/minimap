package layers;

import coords.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import javax.imageio.ImageIO;

import core.Canvas;
import core.Layer;
import geometry.BoundingBox;

public class TopowebLayer implements Layer {
	private final Canvas canvas;
	//private final String key = "007d0995-da35-38ed-81b6-2a11e9c29d10";
	//private final String url = "https://api.lantmateriet.se/open/topowebb-ccby/v1/wmts/token/";
	private final String url = "http://hades.slu.se/lm/topowebb/v1.1/wmts/";
	// http://hades.slu.se/lm/topowebb/wms/v1/?SERVICE=WMS&REQUEST=GetCapabilities
	private final static int TILEMATRIX_LIMIT = 12;
	private String name;
	private boolean hidden;
	private Color color = Color.BLACK;
	private int maxZoom = 0; // 0 indicates unset
	private int minZoom = 0;
	private final TileBuffer tileBuffer;
	private CoordSystem cs;
	
	public static class TileIndex {
		public int zoomLevel;  // == tilematrix;
		public int col;		// tile column increase East
		public int row;			// tile row increase South
		
		TileIndex(int zoomLevel, int col, int row) {
			this.zoomLevel = zoomLevel;
			this.col = col;
			this.row = row;
		}

		public static TileIndex[] getTileIndexes(BoundingBox box, int tilematrix) {
			int origoY = 8500000;
			int origoX = -1200000;
			int tileWidth = tileWidth(tilematrix);

			// Calculate min/max directly from bounds
			int colMin = (box.getX1() - origoX) / tileWidth;
			int colMax = (box.getX2() - origoX) / tileWidth;

			// Note: Rows increase South (down), so Y1 (North/Higher) is a smaller row index
			int rowMin = (origoY - box.getY2()) / tileWidth;
			int rowMax = (origoY - box.getY1()) / tileWidth;

			int numTiles = (rowMax - rowMin + 1) * (colMax - colMin + 1);
			TileIndex[] indexes = new TileIndex[numTiles];

			int i = 0;
			for (int r = rowMin; r <= rowMax; r++) {
				for (int c = colMin; c <= colMax; c++) {
					indexes[i++] = new TileIndex(tilematrix, c, r);
				}
			}
			return indexes;
		}
		
		// Overriding equals() to compare two TileIndex
	    @Override
	    public boolean equals(Object o) {
	        if (o == this) { return true; }
	        if (!(o instanceof TileIndex)) { return false; }
	        TileIndex tile = (TileIndex) o;
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
		private final String CACHE_ROOT = "tile_cache/topowebb";
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
			// Check RAM
			if (tiles.containsKey(index)) {
				return tiles.get(index);
			}

			// Check Disk (Synchronous check, but ImageIO.read is fast enough for local disk)
			File localFile = getLocalPath(index);
			if (localFile.exists()) {
				try {
					Image img = ImageIO.read(localFile);
					if (img != null) {
						tiles.put(index, img);
						return img;
					} else {
						localFile.delete();
					}
				} catch (IOException e) {
					System.err.println("Failed to read cached tile: " + localFile);
				}
			}

			// Fetch from Network
			if (loading.add(index)) {
				downloadTileAsync(index, localFile);
			}
			return null;
		}

		private File getLocalPath(TileIndex index) {
			// Path: cache/z/col/row.png
			return new File(String.format("%s/%d/%d/%d.png",
					CACHE_ROOT, index.zoomLevel, index.col, index.row));
		}

		private void downloadTileAsync(TileIndex index, File localFile) {
			String tileUrl = url + "?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=topowebb"
					+ "&STYLE=default&TILEMATRIXSET=3006&TILEMATRIX=" + index.zoomLevel
					+ "&TILEROW=" + index.row + "&TILECOL=" + index.col + "&FORMAT=image/png";

			java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
					.uri(java.net.URI.create(tileUrl))
					.build();

			client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray())
					.thenAccept(response -> {
						byte[] data = response.body();
						if (data != null && data.length > 0) {
							try {
								// Save to Disk
								localFile.getParentFile().mkdirs();
								Files.write(localFile.toPath(), data);

								// Load into RAM
								Image img = ImageIO.read(new java.io.ByteArrayInputStream(data));
								if (img != null) {
									tiles.put(index, img);
									canvas.repaint();
								}
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						loading.remove(index);
					});
		}
	}
	
	public TopowebLayer(Canvas canvas) {
		this.canvas = canvas;
		tileBuffer = new TileBuffer();
		cs = CoordSystem.SWEREF99TM;
	}

	@Override
	public void setColor(Color color) {
		this.color = (color != null) ? color : Color.BLACK;
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
		this.minZoom = zoomLevel;
	}

	@Override
	public void setMaxZoomL(int zoomLevel) {
		this.maxZoom = zoomLevel;
	}

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		boolean meetsMin = (minZoom == 0 || zoomLevel >= minZoom);
		boolean meetsMax = (maxZoom == 0 || zoomLevel <= maxZoom);
		return meetsMin && meetsMax;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}
	
	private static int tileWidth (int tilematrix) {
		//return 1048576/(int)(Math.pow(2, tilematrix));
		return 1048576 / (1 << tilematrix);
	}
	
	private static BoundingBox getTileBounds(TileIndex ind) {
		int origoY = 8500000;
		int origoX = -1200000;
		int tileWidth = tileWidth(ind.zoomLevel); // meters
		return new BoundingBox(origoX + tileWidth * ind.col, origoY - tileWidth * (ind.row + 1),  origoX + tileWidth * (ind.col + 1), origoY - tileWidth * (ind.row));
	}

	private static int tileMatrix(int tileWidth) {
		// 1048576 is 2^20.
		// Integer.numberOfLeadingZeros(tileWidth) gives us the log2 indirectly.
		// In a 32-bit integer, log2(x) is 31 - numberOfLeadingZeros(x).

		int log2TileWidth = 31 - Integer.numberOfLeadingZeros(tileWidth) ;
		int m = 20 - log2TileWidth -1;

		if (m > TILEMATRIX_LIMIT) m = TILEMATRIX_LIMIT;
		if (m < 0) m = 0; // Safety check
		return m;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		int tilesize = 256;
		int tilematrix = tileMatrix((int)Math.round(1/xScale * tilesize));

		TileIndex[] indexes = TileIndex.getTileIndexes(bounds, tilematrix);

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

					g2d.drawImage(img, x1, y2, x2 - x1, Math.abs(y1 - y2), null);
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