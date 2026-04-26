package main.layers;

import main.coords.*;
import main.core.DBConnection;
import main.core.Layer;
import main.geometry.BoundingBox;

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

public class MYSQLTableLayer implements Layer {
	private String name;
	private Color color = Color.BLACK;
	private int maxZoom = 0; // 0 indicates unset
	private int minZoom = 0;
	private boolean hidden;
	private CoordSystem cs;
	private Integer selectedLocalityID = null;

	private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 20);

	// Volatile ensures the new list is immediately visible to the drawing thread
	private volatile List<LocalityRec> cache = Collections.emptyList();
	private volatile BoundingBox cachedBounds = null;

	// Prevents spawning 100 queries if the user drags the map wildly
	private final AtomicBoolean isFetching = new AtomicBoolean(false);

	// Callback to tell the canvas to repaint when async load finishes
	private Runnable repaintCallback;

	private record LocalityRec(int n, int e, String name, int precision, int id) {}

	public MYSQLTableLayer() {}

	/**
	 * Pass in your canvas.repaint() so the layer can trigger a refresh when data arrives.
	 */
	public void setRepaintCallback(Runnable repaintCallback) {
		this.repaintCallback = repaintCallback;
	}

	private boolean shouldRefreshCache(BoundingBox currentBounds) {
		if (cachedBounds == null) return true;
		return !cachedBounds.isInside(currentBounds);
	}

	private void refreshCacheAsync(BoundingBox bounds) {
		// CompareAndSet ensures only ONE background thread fetches at a time.
		if (!isFetching.compareAndSet(false, true)) {
			return;
		}

		BoundingBox bufferedArea = bounds.grow(0.5);

		CompletableFuture.runAsync(() -> {
			try {
				Connection conn = DBConnection.getConn();
				try (PreparedStatement pstmt = conn.prepareStatement(
								"SELECT SWTMN, SWTME, locality, Coordinateprecision, id FROM locality " +
										"WHERE SWTMN BETWEEN ? AND ? AND SWTME BETWEEN ? AND ?")) {

					pstmt.setInt(1, Math.min(bufferedArea.getY1(), bufferedArea.getY2()));
					pstmt.setInt(2, Math.max(bufferedArea.getY1(), bufferedArea.getY2()));
					pstmt.setInt(3, Math.min(bufferedArea.getX1(), bufferedArea.getX2()));
					pstmt.setInt(4, Math.max(bufferedArea.getX1(), bufferedArea.getX2()));

					try (ResultSet rs = pstmt.executeQuery()) {
						// Build a completely new list in the background
						List<LocalityRec> temp = new ArrayList<>();
						while (rs.next()) {
							temp.add(new LocalityRec(
									rs.getInt("SWTMN"),
									rs.getInt("SWTME"),
									rs.getString("locality"),
									rs.getInt("Coordinateprecision"),
									rs.getInt("id")
							));
						}

						// Swap the references atomically
						this.cache = temp;
						this.cachedBounds = bufferedArea;
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
			} catch (SQLException e) {
				System.err.println("=== Couldn't connect to the VH MySQL server ===");
				e.printStackTrace();
			}
		});
	}

	public void invalidateCache() {
		this.cachedBounds = null;
	}

	// point should be in world coordinate
	public void selectNearest(Coordinate c) {
		// We search within a "tolerance" (e.g., 10 pixels converted to world units)
		int tolerance = 1000 ;
		this.selectedLocalityID = findNearest(c, tolerance);

		// The canvas should repaint after calling this
	}

	public int findNearest(Coordinate c, int limit) {
		String sqlstmt = "SELECT SWTMN, SWTME, ID FROM locality where SWTMN BETWEEN ? AND ? AND SWTME BETWEEN ? AND ?";
		try {
			Connection conn = DBConnection.getConn();
			try (PreparedStatement statement = conn.prepareStatement(sqlstmt)) {

				statement.setInt(1, (int) Math.round(c.getNorth() - limit));
				statement.setInt(2, (int) Math.round(c.getNorth() + limit));
				statement.setInt(3, (int) Math.round(c.getEast() - limit));
				statement.setInt(4, (int) Math.round(c.getEast() + limit));

				try (ResultSet result = statement.executeQuery()) {
					double ndist = Double.MAX_VALUE;
					int nID = -1;
					while (result.next()) {
						Coordinate pc = new Coordinate(result.getInt(1), result.getInt(2));
						double dist = c.distanceTM(pc);
						if (dist < ndist) {
							ndist = dist;
							nID = result.getInt(3);
						}
					}
					return nID;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} catch(SQLException e) {
				e.printStackTrace();
		}
		return -1;
	}

	@Override
	public void setColor(Color color) {
		this.color = (color != null) ? color : Color.BLACK;
	}

	@Override
	public Color getColor() { return color; }

	@Override
	public String getName() { return name; }

	@Override
	public void setName(String name) { this.name = name; }

	@Override
	public boolean isHidden() { return hidden; }

	@Override
	public void setHidden(boolean hidden) { this.hidden = hidden; }

	@Override
	public void setCRS(CoordSystem cs) { this.cs = cs;  }

	@Override
	public CoordSystem getCRS() { return cs; }

	@Override
	public void setMinZoomL(int zoomLevel) { minZoom = zoomLevel; }

	@Override
	public void setMaxZoomL(int zoomLevel) { maxZoom = zoomLevel; }

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		boolean meetsMin = (minZoom == 0 || zoomLevel >= minZoom);
		boolean meetsMax = (maxZoom == 0 || zoomLevel <= maxZoom);
		return meetsMin && meetsMax;
	}

	@Override
	public Extent getBoundaries() {
		//Todo: implement the method
		return null;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale,
	                 double yShift, double yScale, BoundingBox bounds) {
		if (hidden) return;

		// Trigger background fetch if needed, but don't block the UI!
		if (shouldRefreshCache(bounds)) {
			refreshCacheAsync(bounds);
		}

		// Draw whatever is currently in RAM
		g2d.setColor(color);
		Font old = g2d.getFont();
		g2d.setFont(LABEL_FONT);

		// Grabbing the rectangle once is much faster than checking the Shape
		Rectangle clipBounds = g2d.getClipBounds();
		// Capture local reference to avoid list changing mid-draw
		List<LocalityRec> localCache = this.cache;

		for (LocalityRec rec : localCache) {
			int x = (int) ((rec.e * xScale) + xShift);
			int y = (int) ((rec.n * yScale) + yShift);

			// Fast clipping
			if (clipBounds != null && !clipBounds.contains(x, y)) continue;

			if (selectedLocalityID != null && rec.id == selectedLocalityID) {
				g2d.setColor(Color.RED);
				g2d.setStroke(new BasicStroke(2));
				g2d.drawOval(x - 8, y - 8, 16, 16); // Draw a larger "target" circle
				int r = (int) (rec.precision * xScale);
				g2d.drawOval(x - r, y - r, r * 2, r * 2);

				// Always draw the name for the selected item, even if zoomed out
				g2d.drawString(rec.name, x + 10, y);
				g2d.setColor(color);
				g2d.setStroke(new BasicStroke(1)); // Reset stroke
			} else {
				g2d.drawOval(x - 3, y - 3, 6, 6);
				if (rec.precision > 0) {
					int r = (int) (rec.precision * xScale);
					if (r > 1) g2d.drawOval(x - r, y - r, r * 2, r * 2);
				}
				if (xScale > 0.02) g2d.drawString(rec.name, x + 5, y);
			}
		}
		g2d.setFont(old);
	}
}