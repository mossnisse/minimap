package gis.layers;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

import javax.swing.SwingUtilities;

import gis.coords.CoordSystem;
import gis.core.MapCanvas;
import gis.geometry.Extent;

public class TopowebLayer extends TiledLayer {
	public enum Provider {
		SLU("http://hades.slu.se/lm/topowebb/v1.1/wmts/", "tile_cache/topowebb", 12),
		LANTMATERIET("https://maps.lantmateriet.se/open/topowebb-ccby/v1/wmts",
				"tile_cache/topowebb-lantmateriet", 9);

		private final String wmtsUrl;
		private final String cacheRoot;
		private final int maxTileMatrix;

		Provider(String wmtsUrl, String cacheRoot, int maxTileMatrix) {
			this.wmtsUrl = wmtsUrl;
			this.cacheRoot = cacheRoot;
			this.maxTileMatrix = maxTileMatrix;
		}
	}

	// SWEREF99TM constants
	private static final int ORIGIN_X = -1200000;
	private static final int ORIGIN_Y = 8500000;
	private static final int BASE_TILE_WIDTH = 1048576; // Width at zoom 0 (2^20)
	private final Provider provider;
	private final AtomicBoolean httpErrorPending = new AtomicBoolean();
	private volatile String authorization;
	private volatile IntConsumer httpErrorHandler;

	public TopowebLayer(MapCanvas mapCanvas) {
		this(mapCanvas, Provider.SLU, null, null);
	}

	public TopowebLayer(MapCanvas mapCanvas, Provider provider, String username, String password) {
		super(provider == Provider.LANTMATERIET ? "Topowebkartan (Lantmäteriet)" : "Topowebkartan",
				CoordSystem.SWEREF99TM, mapCanvas, provider.cacheRoot);
		this.provider = provider;
		// A missing account is not fatal here: the layer has to be constructible on
		// any machine so a saved project still restores, and the first 401 drives
		// the credential prompt.
		this.authorization = provider == Provider.LANTMATERIET ? basicAuth(username, password) : null;
	}

	public Provider getProvider() {
		return provider;
	}

	public boolean hasCredentials() {
		return authorization != null;
	}

	/** Replace credentials after an authentication failure without recreating the layer. */
	public void setCredentials(String username, String password) {
		if (provider != Provider.LANTMATERIET) {
			throw new IllegalStateException("Credentials only apply to the Lantmäteriet provider");
		}
		String header = basicAuth(username, password);
		if (header == null) {
			throw new IllegalArgumentException("Lantmäteriet username and password are required");
		}
		this.authorization = header;
	}

	/** The Basic header for these credentials, or null if either half is missing. */
	private static String basicAuth(String username, String password) {
		if (username == null || username.isBlank() || password == null || password.isBlank()) return null;
		String credentials = username.trim() + ":" + password;
		return "Basic " + Base64.getEncoder().encodeToString(
				credentials.getBytes(StandardCharsets.UTF_8));
	}

	/** Receives 401/403 once per failed batch; it is always invoked on Swing's UI thread. */
	public void setHttpErrorHandler(IntConsumer handler) {
		this.httpErrorHandler = handler;
	}

	@Override
	protected int calculateZoom(double xScale) {
		double resolution = 1.0 / xScale;
		int tileWidthMeters = (int) Math.round(resolution * 256);

		// Use leading zeros to simulate base-2 log calculation for zoom mapping
		int log2TileWidth = 31 - Integer.numberOfLeadingZeros(tileWidthMeters);
		int zoom = 20 - log2TileWidth - 1;

		return Math.clamp(zoom, 0, provider.maxTileMatrix);
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
		return provider.wmtsUrl + "?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=topowebb"
				+ "&STYLE=default&TILEMATRIXSET=3006&TILEMATRIX=" + zoom
				+ "&TILEROW=" + row + "&TILECOL=" + col + "&FORMAT=image/png";
	}

	@Override
	protected HttpRequest httpRequest(String url) {
		HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));
		if (authorization != null) builder.header("Authorization", authorization);
		return builder.build();
	}

	@Override
	protected void handleHttpError(int statusCode) {
		IntConsumer handler = httpErrorHandler;
		boolean authFailure = statusCode == 401 || statusCode == 403;
		if (provider == Provider.LANTMATERIET && authFailure
				&& handler != null && httpErrorPending.compareAndSet(false, true)) {
			SwingUtilities.invokeLater(() -> {
				try {
					handler.accept(statusCode);
				} finally {
					httpErrorPending.set(false);
				}
			});
			return;
		}
		// Only the auth failures of the batch already being handled are redundant;
		// anything else still deserves to be reported while the prompt is open.
		if (!(authFailure && httpErrorPending.get())) super.handleHttpError(statusCode);
	}
}
