package main.layers;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import javax.imageio.ImageIO;

import main.coords.*;
import main.core.MapCanvas;
import main.core.Layer;
import main.geometry.Extent;

public class OSMLayer extends Layer {
    private final MapCanvas mapCanvas;
    private final TileBuffer tileBuffer;

    // Web Mercator half-world size
    private static final double WORLD_SIZE = 20037508.34;
    private static final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
    private static final double LOG2 = Math.log(2);

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
            Image cachedImg = tiles.get(index);
            if (cachedImg != null) return cachedImg;

            File localFile = new File(String.format("%s/%d/%d/%d.png", CACHE_ROOT, index.zoom, index.x, index.y));
            if (localFile.exists()) {
                try {
                    Image img = ImageIO.read(localFile);
                    if (img != null) { tiles.put(index, img); return img; }
                } catch (IOException e) { localFile.delete(); }
            }

            synchronized (loading) {
                if (!loading.contains(index) && !tiles.containsKey(index)) {
                    loading.add(index);
                    downloadTileAsync(index, localFile);
                }
            }
            return null;
        }

        private void downloadTileAsync(TileIndex index, File localFile) {
            String tileUrl = String.format("https://tile.openstreetmap.org/%d/%d/%d.png", index.zoom, index.x, index.y);

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(tileUrl))
                    .header("User-Agent", "MinMap/0.1") // MANDATORY FOR OSM
                    .build();

            client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                byte[] data = response.body();
                                Image img = ImageIO.read(new java.io.ByteArrayInputStream(data));

                                if (img != null) {
                                    // Success path
                                    localFile.getParentFile().mkdirs();
                                    Files.write(localFile.toPath(), data);
                                    tiles.put(index, img);
                                    mapCanvas.repaint();
                                } else {
                                    // The data wasn't a valid image
                                    System.err.println("Downloaded data was not a valid image: " + index);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            System.err.println("OSM Server returned: " + response.statusCode());
                        }
                    })
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            // Log the network error (timeout, etc.)
                            throwable.printStackTrace();
                        }
                        loading.remove(index);
                    });
        }
    }

    public OSMLayer(MapCanvas mapCanvas) {
        super("OpenStreetMap", false, CoordSystem.WEB_MERCATOR);
        this.mapCanvas = mapCanvas;
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
        int zoom = (int) Math.round(Math.log( (2 * WORLD_SIZE) / (256 * resolution)) / LOG2 );
        return Math.clamp(zoom, 0, 18); // Clamp zoom 0-18
    }

    @Override
    public void invalidateCache() {

    }

    @Override
    public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, Extent bounds) {
        int zoom = calculateZoom(Math.abs(xScale));
        Extent wmbounds = bounds.convertCRS(mapCanvas.getCRS(), getCRS());
        TileIndex[] indexes = TileIndex.getTileIndexes(wmbounds, zoom);  // , mapCanvas.getCRS()

        int numTiles = 1 << zoom;
        double tileSize = (2 * WORLD_SIZE) / numTiles;

        boolean isWebMercator = (mapCanvas.getCRS() == CoordSystem.WEB_MERCATOR);

        // Save the old interpolation setting to restore later
        Object oldInterpolation = g2d.getRenderingHint(RenderingHints.KEY_INTERPOLATION);

        if (!isWebMercator) {
            if (oldInterpolation != null) {
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            } else {
                g2d.getRenderingHints().remove(RenderingHints.KEY_INTERPOLATION);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            }
        }

        for (TileIndex ind : indexes) {
            Image img = tileBuffer.getTileOrFetch(ind);
            if (img != null) {
                double webMercXLeft = -WORLD_SIZE + (ind.x * tileSize);
                double webMercYTop = WORLD_SIZE - (ind.y * tileSize);

                if (isWebMercator) {
                    // -------------------------------------------------------------
                    // FAST PATH: MapCanvas is native Web Mercator
                    // -------------------------------------------------------------
                    int screenX = (int) Math.round((webMercXLeft * xScale) + xShift);
                    int screenY = (int) Math.round((webMercYTop * yScale) + yShift);

                    double webMercXRight = webMercXLeft + tileSize;
                    double webMercYBottom = webMercYTop - tileSize;
                    int screenX2 = (int) Math.round((webMercXRight * xScale) + xShift);
                    int screenY2 = (int) Math.round((webMercYBottom * yScale) + yShift);

                    int pWidth = screenX2 - screenX;
                    int pHeight = screenY2 - screenY;

                    // Add +1 to mask integer rounding gaps
                    g2d.drawImage(img, screenX, screenY, Math.abs(pWidth) , Math.abs(pHeight) , null);

                } else {
                    // -------------------------------------------------------------
                    // WARP PATH: Calculate AffineTransform using 3 Projected Corners
                    // -------------------------------------------------------------
                    Coordinate topLeftWM = new Coordinate(webMercYTop, webMercXLeft);
                    Coordinate topRightWM = new Coordinate(webMercYTop, webMercXLeft + tileSize);
                    Coordinate bottomLeftWM = new Coordinate(webMercYTop - tileSize, webMercXLeft);

                    // Convert to Target CRS
                    Coordinate tlTarget = CoordSystem.WEB_MERCATOR.convertTo(topLeftWM, mapCanvas.getCRS());
                    Coordinate trTarget = CoordSystem.WEB_MERCATOR.convertTo(topRightWM, mapCanvas.getCRS());
                    Coordinate blTarget = CoordSystem.WEB_MERCATOR.convertTo(bottomLeftWM, mapCanvas.getCRS());

                    // Calculate double-precision screen coordinates inline
                    double tlX = (tlTarget.getEast() * xScale) + xShift;
                    double tlY = (tlTarget.getNorth() * yScale) + yShift;

                    double trX = (trTarget.getEast() * xScale) + xShift;
                    double trY = (trTarget.getNorth() * yScale) + yShift;

                    double blX = (blTarget.getEast() * xScale) + xShift;
                    double blY = (blTarget.getNorth() * yScale) + yShift;

                    // Derive the AffineTransform Matrix
                    // Tile images are strictly 256x256 pixels
                    // We divide by 255.5 instead of 256.0 to force a 0.5px overlap and kill white seams
                    double tilePx = 255.5;

                    double m00 = (trX - tlX) / tilePx; // Scale X & Skew X
                    double m10 = (trY - tlY) / tilePx; // Shear Y
                    double m01 = (blX - tlX) / tilePx; // Shear X
                    double m11 = (blY - tlY) / tilePx; // Scale Y & Skew Y

                    AffineTransform transform = new AffineTransform(m00, m10, m01, m11, tlX, tlY);

                    g2d.drawImage(img, transform, null);
                }
            }
        }

        // Cleanup: Restore original graphics state
        if (!isWebMercator && oldInterpolation != null) {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        }
    }
}