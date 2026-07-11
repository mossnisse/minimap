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

		img = ImageIO.read(imageFile);
		if (img == null) throw new IOException("Could not decode image: " + fileName);

		try (Scanner s = new Scanner(worldFile)) {
			s.useLocale(java.util.Locale.US); // Handle decimal points correctly
			double pixelSizeX = s.nextDouble(); // A Pixel size in the X direction (meters per pixel)
			s.nextDouble(); // D (Rotation) about Y axis (usually 0)
			s.nextDouble(); // B (Rotation) about X axis (usually 0)
			double pixelSizeY = s.nextDouble(); // E Pixel size in the Y direction (almost always negative).
			double centerX = s.nextDouble();    // C X-coordinate (Easting) of the center of the top-left pixel.
			double centerY = s.nextDouble();    // F Y-coordinate (Northing) of the center of the top-left pixel.

			int width = img.getWidth(null);
			int height = img.getHeight(null);

			// Adjust from "center of pixel" to "outer corner of pixel"
			double xMin = centerX - (pixelSizeX / 2.0);
			double yMax = centerY - (pixelSizeY / 2.0); // Subtracting a negative Y adds it

			double xMax = xMin + (width * pixelSizeX);
			double yMin = yMax + (height * pixelSizeY); // Adding a negative Y lowers it

			// Extent usually takes (NorthMax, EastMax, NorthMin, EastMin)
			// Verify your Extent constructor order!
			box = new Extent(yMax, xMin, yMin, xMax);
		}
	}

	@Override
	public Extent getBoundaries() {
		// In the canvas CRS, like the other layers
		if (needsProjection) projectCorners();
		return projectedBox;
	}

	private void projectCorners() {
		if (getCRS() == mapCanvas.getCRS()) {
			projectedBox = box;
			// Map world coordinates directly
			x0 = box.c1.getEast();  y0 = box.c1.getNorth(); // TL
			x1 = box.c2.getEast();  y1 = box.c1.getNorth(); // TR
			x2 = box.c1.getEast();  y2 = box.c2.getNorth(); // BL
		} else {
			// Project the three corners needed for AffineTransform
			Coordinate tl = getCRS().convertTo(new Coordinate(box.c1.getNorth(), box.c1.getEast()), mapCanvas.getCRS());
			Coordinate tr = getCRS().convertTo(new Coordinate(box.c1.getNorth(), box.c2.getEast()), mapCanvas.getCRS());
			Coordinate bl = getCRS().convertTo(new Coordinate(box.c2.getNorth(), box.c1.getEast()), mapCanvas.getCRS());

			x0 = tl.getEast();  y0 = tl.getNorth();
			x1 = tr.getEast();  y1 = tr.getNorth();
			x2 = bl.getEast();  y2 = bl.getNorth();

			// Create a bounding box for the intersection check
			projectedBox = new Extent(
					Math.max(y0, y1), Math.min(x0, x2), // Rough Top-Left
					Math.min(y0, y2), Math.max(x1, x2)  // Rough Bottom-Right
			);
		}
		needsProjection = false;
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

		if (getCRS() == mapCanvas.getCRS()) {
			// OPTIMIZED: Standard drawImage for matching CRS
			int sx1 = (int) (x0 * xScale + xShift);
			int sy1 = (int) (y0 * yScale + yShift);
			int sx2 = (int) (box.c2.getEast() * xScale + xShift);
			int sy2 = (int) (box.c2.getNorth() * yScale + yShift);

			g2d.drawImage(img, sx1, sy1, sx2 - sx1, sy2 - sy1, null);
		} else {
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			// ACCURATE: AffineTransform for mismatched CRS
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
}