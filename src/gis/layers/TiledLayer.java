package gis.layers;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.imageio.ImageIO;

import gis.coords.Coordinate;
import gis.coords.CoordSystem;
import gis.core.Layer;
import gis.core.MapCanvas;
import gis.geometry.Extent;

/**
 * Base class for web map layers served as 256px raster tiles (OSM, WMTS, ...).
 * Handles the tile cache, async download and on-screen placement (both the
 * native fast path and the warped AffineTransform path for foreign CRSs).
 * Subclasses only supply the tile addressing math, the URL and the zoom mapping.
 */
public abstract class TiledLayer extends Layer {
    protected final MapCanvas mapCanvas;
    private final TileBuffer tileBuffer;
    protected static final HttpClient client = HttpClient.newHttpClient();

    // Tiles are 256px; we divide by 255.5 to force a 0.5px overlap and kill white seams.
    private static final double TILE_PX = 255.5;

    protected TiledLayer(String name, CoordSystem cs, MapCanvas mapCanvas, String cacheRoot) {
        super(name, false, cs);
        this.mapCanvas = mapCanvas;
        this.tileBuffer = new TileBuffer(cacheRoot);
    }

    // --- Subclass hooks ------------------------------------------------------

    /** Pick a tile zoom level for the given horizontal screen scale. */
    protected abstract int calculateZoom(double xScale);

    /** {colMin, colMax, rowMin, rowMax} covering box (in this layer's CRS) at zoom. */
    protected abstract int[] tileRange(Extent box, int zoom);

    /** Tile edge length in this layer's CRS meters at the given zoom. */
    protected abstract double tileSizeMeters(int zoom);

    /** Left (west) edge of a tile column, in this layer's CRS meters. */
    protected abstract double tileLeftX(int col, int zoom);

    /** Top (north) edge of a tile row, in this layer's CRS meters. */
    protected abstract double tileTopY(int row, int zoom);

    /** URL for a single tile. */
    protected abstract String tileUrl(int zoom, int col, int row);

    /** Build the HTTP request; override to add headers (e.g. a mandatory User-Agent). */
    protected HttpRequest httpRequest(String url) {
        return HttpRequest.newBuilder().uri(URI.create(url)).build();
    }

    /** Handle a non-successful HTTP response; subclasses may surface provider-specific UI. */
    protected void handleHttpError(int statusCode) {
        System.err.println(getName() + " server returned: " + statusCode);
    }

    // --- Layer ---------------------------------------------------------------

    @Override
    public Extent getBoundaries() {
        // In the canvas CRS, like the other layers
        return getCRS().getBoundaries().convertCRS(getCRS(), mapCanvas.getCRS());
    }

    @Override
    public void invalidateCache() {
    }

    @Override
    public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, Extent bounds) {
        int zoom = calculateZoom(xScale);
        boolean isNative = (mapCanvas.getCRS() == getCRS());
        // Approximate conversion: this runs on every repaint and the tile
        // range tolerates slack, so skip the exact edge-sampled envelope
        Extent layerBounds = bounds.convertCRSApprox(mapCanvas.getCRS(), getCRS());
        double tileSize = tileSizeMeters(zoom);

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        for (TileIndex ind : tileIndexes(layerBounds, zoom)) {
            Image img = tileBuffer.getTileOrFetch(ind);
            if (img == null) continue;

            double leftX = tileLeftX(ind.x, zoom);
            double topY = tileTopY(ind.y, zoom);

            if (isNative) {
                int screenX = (int) Math.round((leftX * xScale) + xShift);
                int screenY = (int) Math.round((topY * yScale) + yShift);
                // +1 overlap to avoid white gridlines between tiles
                int w = (int) Math.round(tileSize * xScale) + 1;
                int h = (int) Math.round(tileSize * Math.abs(yScale)) + 1;
                g2d.drawImage(img, screenX, screenY, w, h, null);
            } else {
                drawWarped(g2d, img, leftX, topY, tileSize, xShift, xScale, yShift, yScale);
            }
        }
    }

    /** Place a tile in a foreign CRS by deriving an AffineTransform from 3 projected corners. */
    private void drawWarped(Graphics2D g2d, Image img, double leftX, double topY, double tileSize,
                            double xShift, double xScale, double yShift, double yScale) {
        Coordinate tl = getCRS().convertTo(new Coordinate(topY, leftX), mapCanvas.getCRS());
        Coordinate tr = getCRS().convertTo(new Coordinate(topY, leftX + tileSize), mapCanvas.getCRS());
        Coordinate bl = getCRS().convertTo(new Coordinate(topY - tileSize, leftX), mapCanvas.getCRS());

        double tlX = (tl.getEast() * xScale) + xShift;
        double tlY = (tl.getNorth() * yScale) + yShift;
        double trX = (tr.getEast() * xScale) + xShift;
        double trY = (tr.getNorth() * yScale) + yShift;
        double blX = (bl.getEast() * xScale) + xShift;
        double blY = (bl.getNorth() * yScale) + yShift;

        AffineTransform transform = new AffineTransform(
                (trX - tlX) / TILE_PX, (trY - tlY) / TILE_PX,
                (blX - tlX) / TILE_PX, (blY - tlY) / TILE_PX,
                tlX, tlY);
        g2d.drawImage(img, transform, null);
    }

    private TileIndex[] tileIndexes(Extent box, int zoom) {
        int[] r = tileRange(box, zoom);
        int colMin = r[0], colMax = r[1], rowMin = r[2], rowMax = r[3];
        TileIndex[] indexes = new TileIndex[(rowMax - rowMin + 1) * (colMax - colMin + 1)];
        int i = 0;
        for (int row = rowMin; row <= rowMax; row++) {
            for (int col = colMin; col <= colMax; col++) {
                indexes[i++] = new TileIndex(zoom, col, row);
            }
        }
        return indexes;
    }

    protected static final class TileIndex {
        final int zoom, x, y;

        TileIndex(int zoom, int x, int y) {
            this.zoom = zoom;
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TileIndex t && t.x == x && t.y == y && t.zoom == zoom;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, zoom);
        }

        @Override
        public String toString() {
            return "zoom: " + zoom + " col: " + x + " row: " + y;
        }
    }

    private class TileBuffer {
        private static final int MAX_TILES = 1000;
        private final String cacheRoot;
        private final Set<TileIndex> loading = Collections.synchronizedSet(new HashSet<>());
        private final Map<TileIndex, Image> tiles = Collections.synchronizedMap(
                new LinkedHashMap<>(MAX_TILES, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<TileIndex, Image> eldest) {
                        return size() > MAX_TILES;
                    }
                });

        TileBuffer(String cacheRoot) {
            this.cacheRoot = cacheRoot;
        }

        Image getTileOrFetch(TileIndex index) {
            Image cached = tiles.get(index);
            if (cached != null) return cached;

            File localFile = new File(String.format("%s/%d/%d/%d.png", cacheRoot, index.zoom, index.x, index.y));
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
            HttpRequest request = httpRequest(tileUrl(index.zoom, index.x, index.y));
            client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                byte[] data = response.body();
                                Image img = ImageIO.read(new ByteArrayInputStream(data));
                                if (img != null) {
                                    localFile.getParentFile().mkdirs();
                                    Files.write(localFile.toPath(), data);
                                    tiles.put(index, img);
                                    mapCanvas.repaint();
                                } else {
                                    System.err.println("Downloaded data was not a valid image: " + index);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            handleHttpError(response.statusCode());
                        }
                    })
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) throwable.printStackTrace();
                        loading.remove(index);
                    });
        }
    }
}
