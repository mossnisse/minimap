package main.layers;

import java.net.URI;
import java.net.http.HttpRequest;

import main.coords.CoordSystem;
import main.core.MapCanvas;
import main.geometry.Extent;

public class OSMLayer extends TiledLayer {
    private static final double WORLD_SIZE = 20037508.34; // Web Mercator half-world size
    private static final double LOG2 = Math.log(2);

    public OSMLayer(MapCanvas mapCanvas) {
        super("OpenStreetMap", CoordSystem.WEB_MERCATOR, mapCanvas, "tile_cache/osm");
    }

    @Override
    protected int calculateZoom(double xScale) {
        // Screen pixels per meter. A tile is 256px; world width is 2 * WORLD_SIZE.
        double resolution = 1.0 / Math.abs(xScale);
        int zoom = (int) Math.round(Math.log((2 * WORLD_SIZE) / (256 * resolution)) / LOG2);
        return Math.clamp(zoom, 0, 18);
    }

    @Override
    protected int[] tileRange(Extent box, int zoom) {
        int numTiles = 1 << zoom;

        // Convert Web Mercator meters to Tile XY (Y is inverted)
        int colMin = (int) Math.floor(((box.c1.getEast() + WORLD_SIZE) / (2 * WORLD_SIZE)) * numTiles);
        int colMax = (int) Math.floor(((box.c2.getEast() + WORLD_SIZE) / (2 * WORLD_SIZE)) * numTiles);
        int rowMin = (int) Math.floor(((WORLD_SIZE - box.c2.getNorth()) / (2 * WORLD_SIZE)) * numTiles);
        int rowMax = (int) Math.floor(((WORLD_SIZE - box.c1.getNorth()) / (2 * WORLD_SIZE)) * numTiles);

        return new int[]{
                Math.clamp(colMin, 0, numTiles - 1), Math.clamp(colMax, 0, numTiles - 1),
                Math.clamp(rowMin, 0, numTiles - 1), Math.clamp(rowMax, 0, numTiles - 1)
        };
    }

    @Override
    protected double tileSizeMeters(int zoom) {
        return (2 * WORLD_SIZE) / (1 << zoom);
    }

    @Override
    protected double tileLeftX(int col, int zoom) {
        return -WORLD_SIZE + (col * tileSizeMeters(zoom));
    }

    @Override
    protected double tileTopY(int row, int zoom) {
        return WORLD_SIZE - (row * tileSizeMeters(zoom));
    }

    @Override
    protected String tileUrl(int zoom, int col, int row) {
        return String.format("https://tile.openstreetmap.org/%d/%d/%d.png", zoom, col, row);
    }

    @Override
    protected HttpRequest httpRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "MinMap/0.1") // MANDATORY FOR OSM
                .build();
    }
}
