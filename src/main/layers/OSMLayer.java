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

public class OSMLayer extends Layer {
    private final Canvas canvas;
    private final TileBuffer tileBuffer;

    // Web Mercator half-world size
    private static final double WORLD_SIZE = 20037508.34;
    private static final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();

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
            int numTiles = 1 << zoom;

            // Convert Web Mercator meters to Tile XY
            int colMin = (int) Math.floor(((box.c1.getEast() + WORLD_SIZE) / (2 * WORLD_SIZE)) * numTiles);
            int colMax = (int) Math.floor(((box.c2.getEast() + WORLD_SIZE) / (2 * WORLD_SIZE)) * numTiles);

            // Y is inverted in TMS/Web Mercator
            int rowMin = (int) Math.floor(((WORLD_SIZE - box.c2.getNorth()) / (2 * WORLD_SIZE)) * numTiles);
            int rowMax = (int) Math.floor(((WORLD_SIZE - box.c1.getNorth()) / (2 * WORLD_SIZE)) * numTiles);

            // Clamp to valid range (0 to 2^zoom - 1)
            colMin = Math.clamp(colMin, 0, numTiles - 1);
            colMax = Math.clamp(colMax, 0, numTiles - 1);
            rowMin = Math.clamp(rowMin, 0, numTiles - 1);
            rowMax = Math.clamp(rowMax, 0, numTiles - 1);

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
    }

    private class TileBuffer {
        private final int MAX_TILES = 1000;
        private final String CACHE_ROOT = "tile_cache/osm";
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
            String tileUrl = String.format("https://tile.openstreetmap.org/%d/%d/%d.png", index.zoom, index.x, index.y);

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(tileUrl))
                    .header("User-Agent", "MinMap/0.1 (https://github.com/mossnisse/minimap)") // MANDATORY FOR OSM
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

    public OSMLayer(Canvas canvas) {
        super("OpenStreetMap", false, CoordSystem.WEB_MERCATOR);
        this.canvas = canvas;
        tileBuffer = new TileBuffer();
    }

    @Override
    public Extent getBoundaries() {
        return getCRS().getBoundaries();
    }

    private static int calculateZoom(double xScale) {
        // Screen pixels per meter.
        // A tile is 256px. World width is 2 * WORLD_SIZE.
        double resolution = 1.0 / xScale;
        int zoom = (int) Math.round(Math.log( (2 * WORLD_SIZE) / (256 * resolution)) / Math.log(2));
        return Math.clamp(zoom, 0, 18); // Clamp zoom 0-18
    }

    @Override
    public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, Extent bounds) {
        int zoom = calculateZoom(xScale);
        TileIndex[] indexes = TileIndex.getTileIndexes(bounds, zoom);

        // Pre-calculate constants for this render pass
        int numTiles = 1 << zoom;
        double worldSize = 20037508.34; // Local copy of constant
        double tileSize = (2 * worldSize) / numTiles;

        // Calculate size based on tile dimensions at this zoom
        int pixelWidth = (int) Math.round(tileSize * xScale);
        int pixelHeight = (int) Math.round(tileSize * Math.abs(yScale)); // Use abs to handle Y-flip

        for (TileIndex ind : indexes) {
            Image img = tileBuffer.getTileOrFetch(ind);
            if (img != null) {
                // Calculate screen coordinates directly as double
                // Formula: (WorldPos * Scale) + Shift
                double mapX1 = -worldSize + (ind.x * tileSize);
                double mapY2 = worldSize - (ind.y * tileSize); // Top Y

                // Apply projection/transformation to get screen pixel coordinates
                int screenX = (int) Math.round((mapX1 * xScale) + xShift);
                int screenY = (int) Math.round((mapY2 * yScale) + yShift);

                g2d.drawImage(img, screenX, screenY, pixelWidth + 1, pixelHeight + 1, null);  // +1 avoids white lines between tiles
            }
        }
    }
}