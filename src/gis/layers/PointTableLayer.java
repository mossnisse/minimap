package gis.layers;

import gis.coords.CoordSystem;
import gis.coords.Coordinate;
import gis.core.Layer;
import gis.core.MapCanvas;
import gis.geometry.Extent;

import javax.swing.SwingUtilities;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generic labeled-point layer backed by any point source (typically a
 * repository query). Fetches asynchronously off the EDT, caches the points
 * converted to the canvas CRS, and re-fetches when the view leaves the cached
 * area. Contains no SQL and no knowledge of what the points represent.
 */
public class PointTableLayer extends Layer {

	/** A point in the LAYER's CRS; {@code radiusMeters <= 0} means no precision circle. */
	public record LabeledPoint(Coordinate c, String label, int radiusMeters) {}

	/** Supplies the points inside the given bounds (in the layer's CRS). */
	public interface PointSource {
		List<LabeledPoint> fetch(Extent boundsInLayerCrs) throws Exception;
	}

	private static final Font BIG_LABEL_FONT = new Font("SansSerif", Font.PLAIN, 20);
	/** With big labels, only label points when zoomed in past this scale (pixels per meter). */
	private static final double LABEL_MIN_SCALE = 0.02;

	private final MapCanvas mapCanvas;
	private final PointSource source;
	private final double growFactor;
	private final boolean bigLabels;

	// volatile: the fetch swaps these in from a background thread; draw reads them on the EDT
	private volatile List<LabeledPoint> cache = Collections.emptyList(); // in canvas CRS
	private volatile Extent lastQueryBounds; // in layer CRS
	private final AtomicBoolean isFetching = new AtomicBoolean(false);

	/**
	 * @param growFactor how much extra area to fetch around the view, so small pans
	 *                   don't hit the source again
	 * @param bigLabels  true for the locality style: 20pt labels shown only when
	 *                   zoomed in, plus precision circles
	 */
	public PointTableLayer(String name, CoordSystem cs, MapCanvas mapCanvas,
	                       PointSource source, double growFactor, boolean bigLabels) {
		super(name, false, cs);
		this.mapCanvas = mapCanvas;
		this.source = source;
		this.growFactor = growFactor;
		this.bigLabels = bigLabels;
	}

	// Fetch off the EDT so panning never blocks on the source. compareAndSet
	// ensures only one fetch runs at a time even if draw() fires repeatedly.
	private void refreshCacheAsync(Extent boundsInLayerCrs) {
		if (!isFetching.compareAndSet(false, true)) return;
		CoordSystem canvasCRS = mapCanvas.getCRS();
		CompletableFuture.runAsync(() -> {
			try {
				List<LabeledPoint> fetched = source.fetch(boundsInLayerCrs);
				List<LabeledPoint> converted = new ArrayList<>(fetched.size());
				for (LabeledPoint p : fetched) {
					converted.add(new LabeledPoint(getCRS().convertTo(p.c(), canvasCRS), p.label(), p.radiusMeters()));
				}
				this.cache = converted;
			} catch (Exception e) {
				System.err.println("Error fetching points for layer " + getName() + ": " + e.getMessage());
				e.printStackTrace();
			} finally {
				// Remember the attempted bounds even on failure, so an unreachable
				// database doesn't retrigger a fetch on every repaint. A pan/zoom
				// outside the bounds or invalidateCache() will retry.
				this.lastQueryBounds = boundsInLayerCrs;
				isFetching.set(false);
				SwingUtilities.invokeLater(mapCanvas::repaint);
			}
		});
	}

	@Override
	public Extent getBoundaries() {
		return getCRS().getBoundaries().convertCRS(getCRS(), mapCanvas.getCRS());
	}

	@Override
	public void invalidateCache() {
		lastQueryBounds = null;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, Extent bounds) {
		if (isHidden()) return;

		Extent queryBounds = new Extent(mapCanvas.getCRS().convertTo(bounds.c1, getCRS()),
				mapCanvas.getCRS().convertTo(bounds.c2, getCRS()));

		// Refresh (off the EDT) if we have no cache or the view left the cached area.
		if (lastQueryBounds == null || !lastQueryBounds.isInside(queryBounds)) {
			refreshCacheAsync(queryBounds.grow(growFactor));
		}

		// Draw whatever is currently cached (may be one frame stale while a fetch runs).
		g2d.setColor(getColor());
		Font oldFont = g2d.getFont();
		if (bigLabels) g2d.setFont(BIG_LABEL_FONT);

		// Grabbing the rectangle once is much faster than checking the Shape
		Rectangle clipBounds = bigLabels ? g2d.getClipBounds() : null;

		List<LabeledPoint> localCache = this.cache;
		for (LabeledPoint p : localCache) {
			int x = (int) ((p.c().getEast() * xScale) + xShift);
			int y = (int) ((p.c().getNorth() * yScale) + yShift);

			if (clipBounds != null && !clipBounds.contains(x, y)) continue;

			g2d.drawOval(x - 3, y - 3, 6, 6);

			if (p.radiusMeters() > 0) {
				// Precision circle: metric radius converted to screen pixels
				double k = mapCanvas.getCRS().getScaleFactor(p.c());
				int r = (int) Math.round(p.radiusMeters() * k * xScale);
				if (r > 1) g2d.drawOval(x - r, y - r, r * 2, r * 2);
			}

			if (!bigLabels || xScale > LABEL_MIN_SCALE) {
				g2d.drawString(p.label(), x + 5, y);
			}
		}
		g2d.setFont(oldFont);
	}
}
