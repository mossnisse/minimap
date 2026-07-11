package gis.shapefile;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * The geometry of one shapefile record: a flat list of X/Y points split
 * into parts (rings for polygons, line strings for polylines). Points and
 * multipoints have a single part. Z and M ordinates are skipped.
 */
public class ShpGeometry {
	private static final int[] SINGLE_PART = {0};
	private static final int[] NO_PARTS = {};
	private static final double[] NO_ORDINATES = {};

	private final ShapeType type;
	private final int[] parts;
	private final double[] xs, ys;
	private final double minX, minY, maxX, maxY;

	private ShpGeometry(ShapeType type, int[] parts, double[] xs, double[] ys,
			double minX, double minY, double maxX, double maxY) {
		this.type = type;
		this.parts = parts;
		this.xs = xs;
		this.ys = ys;
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
	}

	public ShapeType getType() {
		return type;
	}

	public boolean isNull() {
		return type == ShapeType.NULL;
	}

	public int getNumParts() {
		return parts.length;
	}

	public int getNumPoints() {
		return xs.length;
	}

	/** Index into the point arrays where part {@code i} starts. */
	public int partStart(int i) {
		return parts[i];
	}

	/** Index into the point arrays just past the end of part {@code i}. */
	public int partEnd(int i) {
		return (i + 1 < parts.length) ? parts[i + 1] : xs.length;
	}

	public double getX(int i) {
		return xs[i];
	}

	public double getY(int i) {
		return ys[i];
	}

	public double getMinX() { return minX; }
	public double getMinY() { return minY; }
	public double getMaxX() { return maxX; }
	public double getMaxY() { return maxY; }

	public String toString() {
		return type + "(" + xs.length + " points, " + parts.length + " parts)";
	}

	/**
	 * Parses one record's content (everything after the 8-byte record
	 * header). The buffer must be little-endian. Trailing Z/M data is
	 * left unread.
	 */
	static ShpGeometry parse(ByteBuffer buf) throws IOException {
		int code = buf.getInt();
		ShapeType type = ShapeType.fromCode(code);
		if (type == null) {
			throw new IOException("Unsupported shape type code: " + code);
		}
		switch (type.base()) {
			case NULL: {
				return new ShpGeometry(type, NO_PARTS, NO_ORDINATES, NO_ORDINATES, 0, 0, 0, 0);
			}
			case POINT: {
				double x = buf.getDouble();
				double y = buf.getDouble();
				return new ShpGeometry(type, SINGLE_PART,
						new double[]{x}, new double[]{y}, x, y, x, y);
			}
			case MULTIPOINT: {
				double minX = buf.getDouble();
				double minY = buf.getDouble();
				double maxX = buf.getDouble();
				double maxY = buf.getDouble();
				int numPoints = buf.getInt();
				double[] xs = new double[numPoints];
				double[] ys = new double[numPoints];
				readPoints(buf, xs, ys);
				return new ShpGeometry(type, SINGLE_PART, xs, ys, minX, minY, maxX, maxY);
			}
			default: { // POLYLINE and POLYGON share the same layout
				double minX = buf.getDouble();
				double minY = buf.getDouble();
				double maxX = buf.getDouble();
				double maxY = buf.getDouble();
				int numParts = buf.getInt();
				int numPoints = buf.getInt();
				int[] parts = new int[numParts];
				for (int i = 0; i < numParts; i++) {
					parts[i] = buf.getInt();
				}
				double[] xs = new double[numPoints];
				double[] ys = new double[numPoints];
				readPoints(buf, xs, ys);
				return new ShpGeometry(type, parts, xs, ys, minX, minY, maxX, maxY);
			}
		}
	}

	private static void readPoints(ByteBuffer buf, double[] xs, double[] ys) {
		for (int i = 0; i < xs.length; i++) {
			xs[i] = buf.getDouble();
			ys[i] = buf.getDouble();
		}
	}
}