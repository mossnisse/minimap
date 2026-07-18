package gis.geopackage;

import gis.shapefile.ShapeType;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * One geometry parsed from a GeoPackage geometry BLOB: the "GP" binary
 * header followed by standard WKB. Exposes the same flat points-and-parts
 * shape as {@link gis.shapefile.ShpGeometry}, with the WKB type mapped
 * onto the shapefile {@link ShapeType} base types so layers can draw both
 * formats identically. Z and M ordinates are read and discarded.
 * GeometryCollections are not supported and parse as an empty geometry.
 */
public class GpkgGeometry {
	private final ShapeType baseType;
	private final int[] parts;
	private final double[] xs;
	private final double[] ys;
	private final double minX, minY, maxX, maxY;

	private GpkgGeometry(ShapeType baseType, int[] parts, double[] xs, double[] ys,
			double minX, double minY, double maxX, double maxY) {
		this.baseType = baseType;
		this.parts = parts;
		this.xs = xs;
		this.ys = ys;
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
	}

	public ShapeType getType() {
		return baseType;
	}

	public int getNumParts() {
		return parts.length;
	}

	/** Index into the point arrays where part {@code i} starts. */
	public int partStart(int i) {
		return parts[i];
	}

	public int getNumPoints() {
		return xs.length;
	}

	/** Easting/longitude of point {@code i}. */
	public double getX(int i) {
		return xs[i];
	}

	/** Northing/latitude of point {@code i}. */
	public double getY(int i) {
		return ys[i];
	}

	public boolean isEmpty() {
		return xs.length == 0;
	}

	public double getMinX() { return minX; }
	public double getMinY() { return minY; }
	public double getMaxX() { return maxX; }
	public double getMaxY() { return maxY; }

	/** A single-point geometry, for editing point tables. */
	public static GpkgGeometry point(double x, double y) {
		return new GpkgGeometry(ShapeType.POINT, new int[]{0},
				new double[]{x}, new double[]{y}, x, y, x, y);
	}

	/**
	 * Encodes a single point as a GeoPackage geometry BLOB: a minimal "GP"
	 * header (little-endian, no envelope) followed by WKB.
	 */
	public static byte[] encodePoint(double x, double y, int srsId) {
		ByteBuffer buf = ByteBuffer.allocate(8 + 21).order(ByteOrder.LITTLE_ENDIAN);
		buf.put((byte) 'G').put((byte) 'P');
		buf.put((byte) 0);    // version
		buf.put((byte) 0x01); // flags: little-endian, no envelope
		buf.putInt(srsId);
		buf.put((byte) 1);    // WKB little-endian
		buf.putInt(1);        // WKB point
		buf.putDouble(x);
		buf.putDouble(y);
		return buf.array();
	}

	/** Parses a GeoPackage geometry BLOB (GP header + WKB). */
	public static GpkgGeometry parse(byte[] blob) throws IOException {
		try {
			return doParse(blob);
		} catch (BufferUnderflowException | IllegalArgumentException e) {
			throw new IOException("Truncated or malformed GeoPackage geometry blob", e);
		}
	}

	private static GpkgGeometry doParse(byte[] blob) throws IOException {
		if (blob == null || blob.length < 8 || blob[0] != 'G' || blob[1] != 'P') {
			throw new IOException("Not a GeoPackage geometry blob");
		}
		int flags = blob[3] & 0xFF;

		ByteBuffer buf = ByteBuffer.wrap(blob);
		buf.position(4);
		buf.order(((flags & 0x01) != 0) ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);

		int envelopeBytes;
		switch ((flags >> 1) & 0x07) {
			case 0: envelopeBytes = 0; break;
			case 1: envelopeBytes = 32; break; // min/max X,Y
			case 2: // min/max X,Y,Z
			case 3: envelopeBytes = 48; break; // min/max X,Y,M
			case 4: envelopeBytes = 64; break; // min/max X,Y,Z,M
			default:
				throw new IOException("Invalid GeoPackage envelope indicator in flags: " + flags);
		}
		buf.getInt(); // srs_id; CRS is resolved from gpkg_geometry_columns instead
		buf.position(buf.position() + envelopeBytes);

		Accum acc = new Accum();
		ShapeType baseType = ((flags & 0x10) != 0) ? ShapeType.NULL : parseWkb(buf, acc, 0);
		return acc.build(baseType);
	}

	/** Valid WKB nests at most 2 deep; corrupt blobs must not overflow the stack. */
	private static final int MAX_WKB_DEPTH = 8;

	/**
	 * Parses one WKB geometry (byte-order byte, type, payload) into the
	 * accumulator and returns its base shape type. Recurses for multi types,
	 * whose members are complete WKB geometries with their own byte order.
	 */
	private static ShapeType parseWkb(ByteBuffer buf, Accum out, int depth) throws IOException {
		if (depth > MAX_WKB_DEPTH) {
			throw new IOException("WKB geometry nested deeper than " + MAX_WKB_DEPTH + " levels");
		}
		int base = readTypeHeader(buf, out);
		switch (base) {
			case 1: // Point
				readPointPart(buf, out);
				return ShapeType.POINT;
			case 2: // LineString
				readLine(buf, out);
				return ShapeType.POLYLINE;
			case 3: // Polygon
				readPolygon(buf, out);
				return ShapeType.POLYGON;
			case 4: { // MultiPoint: members are full WKB points, all in one part
				int n = buf.getInt();
				out.newPart();
				for (int i = 0; i < n; i++) {
					int memberBase = readTypeHeader(buf, out);
					if (memberBase != 1) {
						throw new IOException("MultiPoint member is not a point: " + memberBase);
					}
					readCoordinate(buf, out, false);
				}
				return ShapeType.MULTIPOINT;
			}
			case 5: { // MultiLineString
				int n = buf.getInt();
				for (int i = 0; i < n; i++) {
					parseWkb(buf, out, depth + 1);
				}
				return ShapeType.POLYLINE;
			}
			case 6: { // MultiPolygon
				int n = buf.getInt();
				for (int i = 0; i < n; i++) {
					parseWkb(buf, out, depth + 1);
				}
				return ShapeType.POLYGON;
			}
			case 7: // GeometryCollection: mixed types don't fit one feature; treat as empty
				if (depth > 0) {
					// A nested clear-out would wipe sibling geometry already parsed
					throw new IOException("GeometryCollection nested inside another geometry");
				}
				return ShapeType.NULL;
			default:
				throw new IOException("Unsupported WKB geometry type: " + base);
		}
	}

	/**
	 * Reads a WKB byte-order byte and geometry type, setting the buffer
	 * order and the accumulator's Z/M ordinate count. Accepts both ISO
	 * 1000-offset Z/M type codes and PostGIS EWKB flag bits.
	 */
	private static int readTypeHeader(ByteBuffer buf, Accum out) throws IOException {
		int orderByte = buf.get() & 0xFF;
		if (orderByte > 1) {
			throw new IOException("Invalid WKB byte order marker: " + orderByte);
		}
		buf.order(orderByte == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
		long raw = buf.getInt() & 0xFFFFFFFFL;
		boolean ewkbZ = (raw & 0x80000000L) != 0;
		boolean ewkbM = (raw & 0x40000000L) != 0;
		boolean ewkbSrid = (raw & 0x20000000L) != 0;
		int type = (int) (raw & 0x0FFFFFFFL);
		boolean hasZ = ewkbZ || type / 1000 == 1 || type / 1000 == 3;
		boolean hasM = ewkbM || type / 1000 == 2 || type / 1000 == 3;
		if (ewkbSrid) {
			buf.getInt(); // embedded SRID
		}
		out.extraOrdinates = (hasZ ? 1 : 0) + (hasM ? 1 : 0);
		return type % 1000;
	}

	private static void readPointPart(ByteBuffer buf, Accum out) {
		out.newPart();
		readCoordinate(buf, out, true);
		// An "empty point" is encoded as NaN ordinates; drop its part again
		if (out.n == out.parts[out.nParts - 1]) {
			out.nParts--;
		}
	}

	private static void readLine(ByteBuffer buf, Accum out) {
		int n = buf.getInt();
		out.newPart();
		for (int i = 0; i < n; i++) {
			readCoordinate(buf, out, false);
		}
	}

	private static void readPolygon(ByteBuffer buf, Accum out) {
		int rings = buf.getInt();
		for (int r = 0; r < rings; r++) {
			readLine(buf, out);
		}
	}

	private static void readCoordinate(ByteBuffer buf, Accum out, boolean allowEmpty) {
		double x = buf.getDouble();
		double y = buf.getDouble();
		for (int i = 0; i < out.extraOrdinates; i++) {
			buf.getDouble(); // skip Z/M
		}
		if (allowEmpty && (Double.isNaN(x) || Double.isNaN(y))) {
			return;
		}
		out.addPoint(x, y);
	}

	/** Growable point/part arrays shared across the recursive WKB walk. */
	private static class Accum {
		double[] xs = new double[16];
		double[] ys = new double[16];
		int n = 0;
		int[] parts = new int[4];
		int nParts = 0;
		int extraOrdinates = 0;
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
		double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

		void newPart() {
			if (nParts == parts.length) {
				parts = Arrays.copyOf(parts, parts.length * 2);
			}
			parts[nParts++] = n;
		}

		void addPoint(double x, double y) {
			if (n == xs.length) {
				xs = Arrays.copyOf(xs, xs.length * 2);
				ys = Arrays.copyOf(ys, ys.length * 2);
			}
			xs[n] = x;
			ys[n] = y;
			n++;
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
		}

		GpkgGeometry build(ShapeType baseType) {
			return new GpkgGeometry(baseType, Arrays.copyOf(parts, nParts),
					Arrays.copyOf(xs, n), Arrays.copyOf(ys, n), minX, minY, maxX, maxY);
		}
	}
}