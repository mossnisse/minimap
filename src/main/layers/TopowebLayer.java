package main.layers;

import java.awt.Graphics2D;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import javax.imageio.ImageIO;

import main.coords.*;
import main.core.Canvas;
import main.core.Layer;
import main.geometry.Extent;

public class TopowebLayer extends Layer {
	private final Canvas canvas;
	private final TileBuffer tileBuffer;

	private static final String WMTS_URL = "http://hades.slu.se/lm/topowebb/v1.1/wmts/";
	private static final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();

	// SWEREF99TM Constants
	private static final int ORIGIN_X = -1200000;
	private static final int ORIGIN_Y = 8500000;
	private static final int BASE_TILE_WIDTH = 1048576; // Width at zoom 0 (2^20)
	private static final int TILEMATRIX_LIMIT = 12;

	public static class TileIndex {
		public int zoom;
		public int x; // col
		public int y; // row

		TileIndex(int zoom, int x, int y) {
			this.zoom = zoom;
			this.x = x;
			this.y = y;
		}

		public static TileIndex[] getTileIndexes(Extent box, int zoom) {
			int tileWidth = BASE_TILE_WIDTH / (1 << zoom);

			// Convert SWEREF99TM meters to Tile XY
			int colMin = (int) Math.floor((box.c1.getEast() - ORIGIN_X) / tileWidth);
			int colMax = (int) Math.floor((box.c2.getEast() - ORIGIN_X) / tileWidth);

			// Y is inverted (Rows increase South)
			int rowMin = (int) Math.floor((ORIGIN_Y - box.c2.getNorth()) / tileWidth);
			int rowMax = (int) Math.floor((ORIGIN_Y - box.c1.getNorth()) / tileWidth);

			// Clamp to valid positive indices
			colMin = Math.max(colMin, 0);
			colMax = Math.max(colMax, 0);
			rowMin = Math.max(rowMin, 0);
			rowMax = Math.max(rowMax, 0);

			int num = (rowMax - rowMin + 1) * (colMax - colMin + 1);
			TileIndex[] indexes = new TileIndex[num];
			int i = 0;
			for (int r = rowMin; r <= rowMax; r++) {
				for (int c = colMin; c <= colMax; c++) {
					indexes[i++] = new TileIndex(zoom, c, r);
				}
			}
			return indexes;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) return true;
			if (!(o instanceof TileIndex)) return false;
			TileIndex tile = (TileIndex) o;
			return tile.x == this.x && tile.y == this.y && tile.zoom == this.zoom;
		}

		@Override
		public int hashCode() {
			return java.util.Objects.hash(x, y, zoom);
		}

		@Override
		public String toString() {
			return "zoom: " + zoom + " col: " + x + " row: " + y;
		}
	}

	private class TileBuffer {
		private final int MAX_TILES = 1000;
		private final String CACHE_ROOT = "tile_cache/topowebb";
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
			if (tiles.containsKey(index)) return tiles.get(index);

			File localFile = new File(String.format("%s/%d/%d/%d.png", CACHE_ROOT, index.zoom, index.x, index.y));
			if (localFile.exists()) {
				try {
					Image img = ImageIO.read(localFile);
					if (img != null) { tiles.put(index, img); return img; }
				} catch (IOException e) { localFile.delete(); }
			}

			if (loading.add(index)) downloadTileAsync(index, localFile);
			return null;
		}

		private void downloadTileAsync(TileIndex index, File localFile) {
			String tileUrl = WMTS_URL + "?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=topowebb"
					+ "&STYLE=default&TILEMATRIXSET=3006&TILEMATRIX=" + index.zoom
					+ "&TILEROW=" + index.y + "&TILECOL=" + index.x + "&FORMAT=image/png";

			java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
					.uri(java.net.URI.create(tileUrl))
					.build();

			client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray())
					.thenAccept(response -> {
						if (response.statusCode() == 200) {
							try {
								byte[] data = response.body();
								localFile.getParentFile().mkdirs();
								Files.write(localFile.toPath(), data);
								tiles.put(index, ImageIO.read(new java.io.ByteArrayInputStream(data)));
								canvas.repaint();
							} catch (IOException e) { e.printStackTrace(); }
						}
						loading.remove(index);
					});
		}
	}

	public TopowebLayer(Canvas canvas) {
		super("Topowebkartan", false, CoordSystem.SWEREF99TM);
		this.canvas = canvas;
		tileBuffer = new TileBuffer();
	}

	@Override
	public Extent getBoundaries() {
		return getCRS().getBoundaries();
	}

	private static int calculateZoom(double xScale) {
		double resolution = 1.0 / xScale;
		int tileWidthMeters = (int) Math.round(resolution * 256);

		// Use leading zeros to simulate base-2 log calculation for zoom mapping
		int log2TileWidth = 31 - Integer.numberOfLeadingZeros(tileWidthMeters);
		int zoom = 20 - log2TileWidth - 1;

		return Math.clamp(zoom, 0, TILEMATRIX_LIMIT);
	}

	@Override
	public void invalidateCache() {

	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, Extent bounds) {
		int zoom = calculateZoom(xScale);
		TileIndex[] indexes = TileIndex.getTileIndexes(bounds, zoom);

		// Pre-calculate constants for this render pass
		int tileWidth = BASE_TILE_WIDTH / (1 << zoom);

		// Calculate size based on tile dimensions at this zoom
		int pixelWidth = (int) Math.round(tileWidth * xScale);
		int pixelHeight = (int) Math.round(tileWidth * Math.abs(yScale));

		for (TileIndex ind : indexes) {
			Image img = tileBuffer.getTileOrFetch(ind);
			if (img != null) {
				// Calculate map coordinates
				double mapX1 = ORIGIN_X + (ind.x * tileWidth);
				double mapY2 = ORIGIN_Y - (ind.y * tileWidth); // Top Y

				// Apply projection/transformation to get screen pixel coordinates
				int screenX = (int) Math.round((mapX1 * xScale) + xShift);
				int screenY = (int) Math.round((mapY2 * yScale) + yShift);

				// +1 overlap trick to avoid white gridlines between map tiles
				g2d.drawImage(img, screenX, screenY, pixelWidth + 1, pixelHeight + 1, null);
			}
		}
	}
}