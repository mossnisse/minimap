package main.layers;

import main.coords.*;
import main.core.Canvas;
import main.core.DBConnection;
import main.core.Layer;
import main.geometry.Extent;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class MYSQLTableLayer extends Layer {
    private final Canvas canvas;
	private Integer selectedLocalityID = null;
	private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 20);

	// Volatile ensures the new list is immediately visible to the drawing thread
	private volatile List<LocalityRec> cache = Collections.emptyList();
	private volatile Extent cachedBounds = null;

	// Prevents spawning 100 queries if the user drags the map wildly
	private final AtomicBoolean isFetching = new AtomicBoolean(false);

	// Callback to tell the canvas to repaint when async load finishes
	private Runnable repaintCallback;

	private record LocalityRec(Coordinate c, String name, int precision, int id) {}

	public MYSQLTableLayer(Canvas canvas) {
        super("MySQL Layer", false, CoordSystem.WGS84);
        this.canvas = canvas;
	}

	/**
	 * Pass in your canvas.repaint() so the layer can trigger a refresh when data arrives.
	 */
	public void setRepaintCallback(Runnable repaintCallback) {
		this.repaintCallback = repaintCallback;
	}

	private boolean shouldRefreshCache(Extent currentBounds) {
		if (cachedBounds == null) return true;
		return !cachedBounds.isInside(currentBounds);
	}

	private void refreshCacheAsync(Extent bounds) {
		// CompareAndSet ensures only ONE background thread fetches at a time.
		if (!isFetching.compareAndSet(false, true)) {
			return;
		}

		Extent bufferedArea = bounds.grow(0.5);
		// convert to wgs84 for the sql query
		Extent querryAarea = bufferedArea.convertCRS(canvas.getCRS(), getCRS());
		CoordSystem canvasCRS = canvas.getCRS();

		CompletableFuture.runAsync(() -> {
			try {
				Connection conn = DBConnection.getConn();
				try (PreparedStatement pstmt = conn.prepareStatement(
								"SELECT lat, `long`, locality, Coordinateprecision, id FROM locality " +
										"WHERE lat BETWEEN ? AND ? AND `long` BETWEEN ? AND ?")) {

					pstmt.setDouble(1, Math.min(querryAarea.c1.getNorth(), querryAarea.c2.getNorth()));
					pstmt.setDouble(2, Math.max(querryAarea.c1.getNorth(), querryAarea.c2.getNorth()));
					pstmt.setDouble(3, Math.min(querryAarea.c1.getEast(), querryAarea.c2.getEast()));
					pstmt.setDouble(4, Math.max(querryAarea.c1.getEast(), querryAarea.c2.getEast()));

					try (ResultSet rs = pstmt.executeQuery()) {
						// Build a completely new list in the background
						List<LocalityRec> temp = new ArrayList<>();
						while (rs.next()) {
                            Coordinate wgs = new Coordinate(rs.getDouble("lat"), rs.getDouble("long"));
							temp.add(new LocalityRec(
                                    getCRS().convertTo(wgs, canvasCRS),
									rs.getString("locality"),
									rs.getInt("Coordinateprecision"),
									rs.getInt("id")
							));
						}

						// Swap the references atomically
						this.cache = temp;
						this.cachedBounds = bufferedArea;
					}
				}
			} catch (SQLException e) {
				System.err.println("=== SQL ERROR IN layers.MYSQLTableLayer ===");
				System.err.println("Message: " + e.getMessage());
				e.printStackTrace();
			} finally {
				// Always release the lock so future pan/zooms can trigger fetches
				isFetching.set(false);

				// Tell the UI thread that new data is ready to be drawn
				if (repaintCallback != null) {
					repaintCallback.run();
				}
			}
		});
	}

	// point should be in world coordinate
	public void selectNearest(Coordinate c) {
		// We search within a "tolerance" (e.g., 10 pixels converted to world units)
		int tolerance = 1000 ;
		this.selectedLocalityID = findNearest(c, tolerance);

		// The canvas should repaint after calling this
	}

	public int findNearest(Coordinate c, int limitInMeters) {
		// Ensure we are working with WGS84 for DB comparison
		Coordinate clickWgs84 = canvas.getCRS().toWGS84(c);

		// Approximate degree offset (very rough: 1 degree ? 111km)
		// For better accuracy at high latitudes, longitude needs a cos(lat) adjustment
		double degOffset = limitInMeters / 111320.0;
		double latRad = Math.toRadians(clickWgs84.getNorth());
		double lonOffset = degOffset / Math.cos(latRad);

		String sqlstmt = "SELECT lat, `long`, ID FROM locality WHERE lat BETWEEN ? AND ? AND `long` BETWEEN ? AND ?";

		try {
			Connection conn = DBConnection.getConn();
			try (PreparedStatement statement = conn.prepareStatement(sqlstmt)) {
				statement.setDouble(1, clickWgs84.getNorth() - degOffset);
				statement.setDouble(2, clickWgs84.getNorth() + degOffset);
				statement.setDouble(3, clickWgs84.getEast() - lonOffset);
				statement.setDouble(4, clickWgs84.getEast() + lonOffset);

				try (ResultSet result = statement.executeQuery()) {
					double minDistance = limitInMeters; // Don't accept anything outside the limit
					int nearestID = -1;

					while (result.next()) {
						Coordinate recordWgs84 = new Coordinate(result.getDouble(1), result.getDouble(2));

						// Use the WGS84 specific distance helper (Haversine or similar)
						double actualDist = clickWgs84.distanceWGS84(recordWgs84);

						if (actualDist < minDistance) {
							minDistance = actualDist;
							nearestID = result.getInt(3);
						}
					}
					return nearestID;
				}
			}
		} catch(SQLException e) {
			e.printStackTrace();
		}
		return -1;
	}

	@Override
	public Extent getBoundaries() {
		// todo conver coordinate to canvas crs
		return CoordSystem.WEB_MERCATOR.getBoundaries();
	}

	@Override
	public void invalidateCache() {
		this.cachedBounds = null;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale,
	                 double yShift, double yScale, Extent bounds) {
		if (isHidden()) return;

		// Trigger background fetch if needed, but don't block the UI!
		if (shouldRefreshCache(bounds)) {
			refreshCacheAsync(bounds);
		}

		// Draw whatever is currently in RAM
		g2d.setColor(getColor());
		Font old = g2d.getFont();
		g2d.setFont(LABEL_FONT);

		// Grabbing the rectangle once is much faster than checking the Shape
		Rectangle clipBounds = g2d.getClipBounds();
		// Capture local reference to avoid list changing mid-draw
		List<LocalityRec> localCache = this.cache;


		for (LocalityRec rec : localCache) {
			int x = (int) ((rec.c.getEast() * xScale) + xShift);
			int y = (int) ((rec.c.getNorth() * yScale) + yShift);

			if (clipBounds != null && !clipBounds.contains(x, y)) continue;

			// Calculate metric radius once for both branches
			double k = canvas.getCRS().getScaleFactor(rec.c);
			int r = (int) Math.round(rec.precision * k * xScale);

			if (selectedLocalityID != null && rec.id == selectedLocalityID) {
				g2d.setColor(getColor());
				g2d.setStroke(new BasicStroke(2));
				g2d.drawOval(x - 8, y - 8, 16, 16);

				if (r > 1) g2d.drawOval(x - r, y - r, r * 2, r * 2);

				g2d.drawString(rec.name, x + 10, y);
				g2d.setStroke(new BasicStroke(1));
			} else {
				g2d.drawOval(x - 3, y - 3, 6, 6);
				if (r > 1) g2d.drawOval(x - r, y - r, r * 2, r * 2);
				if (xScale > 0.02) g2d.drawString(rec.name, x + 5, y);
			}
		}
		g2d.setFont(old);
	}
}