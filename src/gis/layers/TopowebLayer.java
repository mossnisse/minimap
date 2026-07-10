package gis.layers;

import gis.coords.CoordSystem;
import gis.core.MapCanvas;
import gis.geometry.Extent;

public class TopowebLayer extends TiledLayer {
	private static final String WMTS_URL = "http://hades.slu.se/lm/topowebb/v1.1/wmts/";

	// SWEREF99TM constants
	private static final int ORIGIN_X = -1200000;
	private static final int ORIGIN_Y = 8500000;
	private static final int BASE_TILE_WIDTH = 1048576; // Width at zoom 0 (2^20)
	private static final int TILEMATRIX_LIMIT = 12;

	public TopowebLayer(MapCanvas mapCanvas) {
		super("Topowebkartan", CoordSystem.SWEREF99TM, mapCanvas, "tile_cache/topowebb");
	}

	@Override
	protected int calculateZoom(double xScale) {
		double resolution = 1.0 / xScale;
		int tileWidthMeters = (int) Math.round(resolution * 256);

		// Use leading zeros to simulate base-2 log calculation for zoom mapping
		int log2TileWidth = 31 - Integer.numberOfLeadingZeros(tileWidthMeters);
		int zoom = 20 - log2TileWidth - 1;

		return Math.clamp(zoom, 0, TILEMATRIX_LIMIT);
	}

	@Override
	protected int[] tileRange(Extent box, int zoom) {
		int tileWidth = BASE_TILE_WIDTH / (1 << zoom);

		// Convert SWEREF99TM meters to Tile XY (Y is inverted, rows increase South)
		int colMin = (int) Math.floor((box.c1.getEast() - ORIGIN_X) / tileWidth);
		int colMax = (int) Math.floor((box.c2.getEast() - ORIGIN_X) / tileWidth);
		int rowMin = (int) Math.floor((ORIGIN_Y - box.c2.getNorth()) / tileWidth);
		int rowMax = (int) Math.floor((ORIGIN_Y - box.c1.getNorth()) / tileWidth);

		return new int[]{
				Math.max(colMin, 0), Math.max(colMax, 0),
				Math.max(rowMin, 0), Math.max(rowMax, 0)
		};
	}

	@Override
	protected double tileSizeMeters(int zoom) {
		return BASE_TILE_WIDTH / (1 << zoom);
	}

	@Override
	protected double tileLeftX(int col, int zoom) {
		return ORIGIN_X + (col * tileSizeMeters(zoom));
	}

	@Override
	protected double tileTopY(int row, int zoom) {
		return ORIGIN_Y - (row * tileSizeMeters(zoom));
	}

	@Override
	protected String tileUrl(int zoom, int col, int row) {
		return WMTS_URL + "?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=topowebb"
				+ "&STYLE=default&TILEMATRIXSET=3006&TILEMATRIX=" + zoom
				+ "&TILEROW=" + row + "&TILECOL=" + col + "&FORMAT=image/png";
	}
}
