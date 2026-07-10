package main.layers;

import main.coords.Coordinate;
import main.core.LayerKey;
import main.core.MapCanvas;

/**
 * App-wide registry of the well-known layers and transient overlays.
 * Layers are addressed through these typed keys instead of display-name strings,
 * so renaming a layer in the UI can never break a lookup.
 */
public final class MapLayers {
	private MapLayers() {}

	public static final LayerKey<MYSQLTableLayer> LOKAL_DB = LayerKey.of("LokalDB", MYSQLTableLayer.class);
	public static final LayerKey<H2TableLayer> ORTNAMN = LayerKey.of("Ortnamnsdb", H2TableLayer.class);
	public static final LayerKey<TNGPolygonFileLayer> PROVINSER = LayerKey.of("provinser", TNGPolygonFileLayer.class);
	public static final LayerKey<TNGPolygonFileLayer> SOCKNAR = LayerKey.of("socknar", TNGPolygonFileLayer.class);

	/** The single RUBIN grid-square marker; marking a new square replaces the old one. */
	public static final LayerKey<RubinLayer> RUBIN_MARKER = LayerKey.of("Rubin", RubinLayer.class);
	/** The single distance/direction vector, shared by the distance dialog, measure tool and specimen bridge. */
	public static final LayerKey<DistanceLayer> DISTANCE_OVERLAY = LayerKey.of("Distance", DistanceLayer.class);
	/** The locality search-result markers; a new search replaces the previous results. */
	public static final LayerKey<TNGPointFileLayer> SEARCH_RESULTS = LayerKey.of("Search Results", TNGPointFileLayer.class);

	/** Name of the province polygon containing {@code c} (in canvas CRS), or null. */
	public static String provinceAt(MapCanvas canvas, Coordinate c) {
		return polygonNameAt(canvas, PROVINSER, c);
	}

	/** Name of the district (socken) polygon containing {@code c} (in canvas CRS), or null. */
	public static String districtAt(MapCanvas canvas, Coordinate c) {
		return polygonNameAt(canvas, SOCKNAR, c);
	}

	private static String polygonNameAt(MapCanvas canvas, LayerKey<TNGPolygonFileLayer> key, Coordinate c) {
		return canvas.layerManager.get(key).map(l -> l.nameAt(c)).orElse(null);
	}
}
