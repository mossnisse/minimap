package gis.layers;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import javax.imageio.ImageIO;

import gis.core.MapCanvas;
import gis.core.Layer;
import gis.coords.*;
import gis.geometry.Extent;

public class RasterFileLayer extends Layer {
	private final String fileName;
	private final MapCanvas mapCanvas;
	private Image img;
	private Extent box;
	private Extent projectedBox;
	private double x0, y0, x1, y1, x2, y2; // Cached projected world coords
	private double sourceX0, sourceY0, sourceX1, sourceY1, sourceX2, sourceY2, sourceX3, sourceY3;
	private boolean needsProjection = true;

	public RasterFileLayer(String fileName, MapCanvas mapCanvas) throws IOException {
		super(fileName, false, CoordSystem.SWEREF99TM);
		this.fileName = fileName;
		this.mapCanvas = mapCanvas;
		readFile();
	}

	public void readFile() throws IOException {
		File imageFile = new File(fileName);
		String baseName = fileName.substring(0, fileName.lastIndexOf('.'));

		// Determine world file extension (e.g., .tfw, .pgw, .wld)
		String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
		String worldExtension = extension.charAt(0) + extension.substring(extension.length() - 1) + "w";
		File worldFile = new File(baseName + "." + worldExtension);
		if (!worldFile.isFile()) {
			worldFile = new File(baseName + ".wld");
		}

		img = ImageIO.read(imageFile);
		if (img == null) throw new IOException("Could not decode image: " + fileName);

		try (Scanner s = new Scanner(worldFile)) {
			s.useLocale(java.util.Locale.US); // Handle decimal points correctly
			double pixelSizeX = s.nextDouble(); // A
			double rotationY = s.nextDouble();  // D
			double rotationX = s.nextDouble();  // B
			double pixelSizeY = s.nextDouble(); // E
			double centerX = s.nextDouble();    // C X-coordinate (Easting) of the center of the top-left pixel.
			double centerY = s.nextDouble();    // F Y-coordinate (Northing) of the center of the top-left pixel.

			int width = img.getWidth(null);
			int height = img.getHeight(null);

			// World files locate pixel centers. Shift half a pixel in both axes to
			// obtain the outer image corner used by Graphics2D's image transform.
			sourceX0 = centerX - (pixelSizeX + rotationX) / 2.0;
			sourceY0 = centerY - (rotationY + pixelSizeY) / 2.0;
			sourceX1 = sourceX0 + width * pixelSizeX;
			sourceY1 = sourceY0 + width * rotationY;
			sourceX2 = sourceX0 + height * rotationX;
			sourceY2 = sourceY0 + height * pixelSizeY;
			sourceX3 = sourceX1 + height * rotationX;
			sourceY3 = sourceY1 + height * pixelSizeY;

			box = extentOf(sourceX0, sourceY0, sourceX1, sourceY1,
					sourceX2, sourceY2, sourceX3, sourceY3);
		}
		needsProjection = true;
	}

	@Override
	public Extent getBoundaries() {
		// In the canvas CRS, like the other layers
		if (needsProjection) projectCorners();
		return projectedBox;
	}

	private void projectCorners() {
		Coordinate tl = project(sourceX0, sourceY0);
		Coordinate tr = project(sourceX1, sourceY1);
		Coordinate bl = project(sourceX2, sourceY2);
		Coordinate br = project(sourceX3, sourceY3);

		x0 = tl.getEast();  y0 = tl.getNorth();
		x1 = tr.getEast();  y1 = tr.getNorth();
		x2 = bl.getEast();  y2 = bl.getNorth();
		projectedBox = extentOf(x0, y0, x1, y1, x2, y2, br.getEast(), br.getNorth());
		needsProjection = false;
	}

	private Coordinate project(double east, double north) {
		Coordinate source = new Coordinate(north, east);
		return (getCRS() == mapCanvas.getCRS()) ? source : getCRS().convertTo(source, mapCanvas.getCRS());
	}

	private static Extent extentOf(double x0, double y0, double x1, double y1,
	                               double x2, double y2, double x3, double y3) {
		double minX = Math.min(Math.min(x0, x1), Math.min(x2, x3));
		double maxX = Math.max(Math.max(x0, x1), Math.max(x2, x3));
		double minY = Math.min(Math.min(y0, y1), Math.min(y2, y3));
		double maxY = Math.max(Math.max(y0, y1), Math.max(y2, y3));
		return new Extent(minY, minX, maxY, maxX);
	}

	@Override
	public void invalidateCache() {
		// Re-project the corners next draw (e.g. after a canvas CRS change)
		needsProjection = true;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale,
	                 double yShift, double yScale, Extent bounds) {
		if (box == null || img == null) return;
		if (needsProjection) projectCorners();

		// Use the cached projectedBox for the intersection check
		if (!bounds.intersects(projectedBox)) return;

		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		double screenX0 = x0 * xScale + xShift;
		double screenY0 = y0 * yScale + yShift;
		double screenX1 = x1 * xScale + xShift;
		double screenY1 = y1 * yScale + yShift;
		double screenX2 = x2 * xScale + xShift;
		double screenY2 = y2 * yScale + yShift;

		double w = img.getWidth(null);
		double h = img.getHeight(null);
		java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform(
				(screenX1 - screenX0) / w, (screenY1 - screenY0) / w,
				(screenX2 - screenX0) / h, (screenY2 - screenY0) / h,
				screenX0, screenY0
		);
		g2d.drawImage(img, at, null);
	}
}
