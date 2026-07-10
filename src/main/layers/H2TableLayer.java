package main.layers;

import main.coords.*;
import main.core.MapCanvas;
import main.core.DBConnection;
import main.core.Layer;
import main.geometry.Extent;

import javax.swing.SwingUtilities;
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

public class H2TableLayer extends Layer {
	private final String tableName;
	private final MapCanvas mapCanvas;
	// volatile: the fetch swaps this in from a background thread; draw reads it on the EDT
	private volatile List<Locality> cache = Collections.emptyList();
	private volatile Extent lastQueryBounds;
	private final AtomicBoolean isFetching = new AtomicBoolean(false);

	private record Locality(Coordinate c, String name) {}
	
	public H2TableLayer(String tableName, MapCanvas mapCanvas) {
		super(tableName, false, CoordSystem.SWEREF99TM);
		this.tableName = tableName;
		this.mapCanvas = mapCanvas;
	}

	// Fetch off the EDT so panning never blocks on the database. compareAndSet
	// ensures only one fetch runs at a time even if draw() fires repeatedly.
	private void refreshCacheAsync(Extent bounds) {
		if (!isFetching.compareAndSet(false, true)) return;
		CompletableFuture.runAsync(() -> {
			try {
				List<Locality> fetched = fetch(bounds);
				this.cache = fetched;
				this.lastQueryBounds = bounds;
			} finally {
				isFetching.set(false);
				SwingUtilities.invokeLater(mapCanvas::repaint);
			}
		});
	}

	private List<Locality> fetch(Extent bounds) {
		List<Locality> result = new ArrayList<>();
		CoordSystem canvasCRS = mapCanvas.getCRS();
		String sql = "SELECT NORTH, EAST, Ortnamn FROM " + tableName +
				" WHERE NORTH BETWEEN ? AND ? AND EAST BETWEEN ? AND ?";
		try {
			Connection conn = DBConnection.getH2Conn();
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setInt(1, (int) bounds.c1.getNorth());
				pstmt.setInt(2, (int) bounds.c2.getNorth());
				pstmt.setInt(3, (int) bounds.c1.getEast());
				pstmt.setInt(4, (int) bounds.c2.getEast());

				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
						Coordinate c = new Coordinate(rs.getInt(1), rs.getInt(2));
						result.add(new Locality(getCRS().convertTo(c, canvasCRS), rs.getString(3)));
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;
	}

	public String findNearest(Coordinate c, int limit) {
		// Coordinate uses geographical North/East logic[cite: 7, 8]
		int eastVal = (int) c.getEast();
		int northVal = (int) c.getNorth();

		// Use a Bounding Box approach for the initial SQL filter
		String sql = "SELECT NORTH, EAST, Ortnamn FROM " + tableName +
				" WHERE NORTH BETWEEN ? AND ? AND EAST BETWEEN ? AND ?";

		try {
			Connection conn = DBConnection.getH2Conn();
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

				// Define the search square[cite: 7]
				pstmt.setInt(1, northVal - limit);
				pstmt.setInt(2, northVal + limit);
				pstmt.setInt(3, eastVal - limit);
				pstmt.setInt(4, eastVal + limit);

				try (ResultSet result = pstmt.executeQuery()) {
					double ndist = Double.MAX_VALUE;
					String nearest = "";

					// Create a reference point for distance calculation
					Point p = new Point(eastVal, northVal);

					while (result.next()) {
						int north = result.getInt(1);
						int east = result.getInt(2);
						String name = result.getString(3);

						// Calculate precise Euclidean distance
						Point pc = new Point(east, north);
						double dist = p.distance(pc);

						if (dist < ndist) {
							ndist = dist;
							nearest = name;
						}
					}
					return nearest;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return "";
	}

	@Override
	public Extent getBoundaries() {
		return getCRS().getBoundaries();
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
		// Fetch a slightly larger area than needed to avoid constant DB hits on small pans.
		if (lastQueryBounds == null || !lastQueryBounds.isInside(queryBounds)) {
			refreshCacheAsync(queryBounds.grow(0.25));
		}

		// Draw whatever is currently cached (may be one frame stale while a fetch runs).
		g2d.setColor(getColor());
		List<Locality> localCache = this.cache;
		for (Locality loc : localCache) {
			int x = (int) ((loc.c.getEast() * xScale) + xShift);
			int y = (int) ((loc.c.getNorth() * yScale) + yShift);
			g2d.drawOval(x - 3, y - 3, 6, 6);
			g2d.drawString(loc.name, x + 5, y);
		}
	}
}