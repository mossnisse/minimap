package app;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import gis.coords.Coordinate;
import gis.coords.CoordSystem;
import gis.core.LayerKey;
import gis.layers.*;
import gis.core.MapCanvas;
import app.repo.PlaceNameRepository;

/**
 * App-wide registry of the well-known layers and transient overlays, and the
 * single source of truth for how the built-in layers are configured.
 * Layers are addressed through these typed keys instead of display-name strings,
 * so renaming a layer in the UI can never break a lookup.
 */
public final class MapLayers {
	private MapLayers() {}

	public static final LayerKey<PointTableLayer> LOKAL_DB = LayerKey.of("LokalDB", PointTableLayer.class);
	public static final LayerKey<PointTableLayer> ORTNAMN = LayerKey.of("Ortnamnsdb", PointTableLayer.class);
	public static final LayerKey<TNGPolygonFileLayer> PROVINSER = LayerKey.of("provinser", TNGPolygonFileLayer.class);
	public static final LayerKey<TNGPolygonFileLayer> SOCKNAR = LayerKey.of("socknar", TNGPolygonFileLayer.class);
	public static final LayerKey<PointTableLayer> COLLECTION_EVENTS = LayerKey.of("private-collection-events", PointTableLayer.class);

	/** The single RUBIN grid-square marker; marking a new square replaces the old one. */
	public static final LayerKey<RubinLayer> RUBIN_MARKER = LayerKey.of("Rubin", RubinLayer.class);
	/** The single distance/direction vector, shared by the distance dialog, measure tool and specimen bridge. */
	public static final LayerKey<DistanceLayer> DISTANCE_OVERLAY = LayerKey.of("Distance", DistanceLayer.class);
	/** The locality search-result markers; a new search replaces the previous results. */
	public static final LayerKey<TNGPointFileLayer> SEARCH_RESULTS = LayerKey.of("Search Results", TNGPointFileLayer.class);

	/** Installs the core default stack (bottom to top: TopoWeb, socknar, provinser, ortnamn). */
	public static void installDefaultLayers(MapCanvas canvas, AppContext ctx) {
		canvas.getLayerManager().addLayerBottom(ORTNAMN, ortnamn(canvas, ctx.placeNames));
		canvas.getLayerManager().addLayerBottom(PROVINSER, provinser(canvas));
		canvas.getLayerManager().addLayerBottom(SOCKNAR, socknar(canvas));
		canvas.getLayerManager().addLayerBottom(topoweb(canvas));
	}

	/** The Lantmäteriet place-name layer: SWEREF99TM points from the embedded H2 db. */
	public static PointTableLayer ortnamn(MapCanvas canvas, PlaceNameRepository placeNames) {
		PointTableLayer l = new PointTableLayer("Ortnamnsdb", CoordSystem.SWEREF99TM, canvas,
				swerefBounds -> {
					List<PointTableLayer.LabeledPoint> points = new ArrayList<>();
					for (PlaceNameRepository.PlaceName p : placeNames.findInBounds(swerefBounds)) {
						points.add(new PointTableLayer.LabeledPoint(p.sweref(), p.name(), 0));
					}
					return points;
				},
				0.25, false);
		l.setColor(Color.BLACK);
		l.setMaxZoomL(5);
		return l;
	}

	public static TNGPolygonFileLayer provinser(MapCanvas canvas) {
		TNGPolygonFileLayer l = new TNGPolygonFileLayer("provinserSWEREF99TM.tng", canvas);
		l.setColor(Color.BLACK);
		l.setName("provinser");
		return l;
	}

	public static TNGPolygonFileLayer socknar(MapCanvas canvas) {
		TNGPolygonFileLayer l = new TNGPolygonFileLayer("socknarSWEREF99TM.tng", canvas);
		l.setColor(Color.RED);
		l.setName("socknar");
		return l;
	}

	public static TopowebLayer topoweb(MapCanvas canvas) {
		TopowebLayer tb = new TopowebLayer(canvas);
		tb.setName("TopoWeb");
		return tb;
	}

	/**
	 * Official Lantmäteriet WMTS variant, authenticated with the shared Geotorget
	 * account. Builds even when no account is configured; the layer then prompts
	 * for credentials on its first 401.
	 */
	public static TopowebLayer topowebLantmateriet(MapCanvas canvas) {
		TopowebLayer layer = new TopowebLayer(canvas, TopowebLayer.Provider.LANTMATERIET,
				LantmaterietAccount.username(), LantmaterietAccount.password());
		layer.setName("TopoWeb (Lantmäteriet)");
		return layer;
	}

	public static OSMLayer osm(MapCanvas canvas) {
		OSMLayer osm = new OSMLayer(canvas);
		osm.setName("Open Street Map");
		return osm;
	}

	/** Re-fetches and repaints the locality layer after a locality was created/edited/deleted. */
	public static void refreshLocalities(MapCanvas canvas) {
		canvas.getLayerManager().get(LOKAL_DB).ifPresent(PointTableLayer::invalidateCache);
		canvas.repaint();
	}

	/** Name of the province polygon containing {@code c} (in canvas CRS), or null. */
	public static String provinceAt(MapCanvas canvas, Coordinate c) {
		return polygonNameAt(canvas, PROVINSER, c);
	}

	/** Name of the district (socken) polygon containing {@code c} (in canvas CRS), or null. */
	public static String districtAt(MapCanvas canvas, Coordinate c) {
		return polygonNameAt(canvas, SOCKNAR, c);
	}

	private static String polygonNameAt(MapCanvas canvas, LayerKey<TNGPolygonFileLayer> key, Coordinate c) {
		return canvas.getLayerManager().get(key).map(l -> l.nameAt(c)).orElse(null);
	}
}
