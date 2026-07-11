package gis.shapefile;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Streaming reader for the geometry part (.shp) of an ESRI shapefile.
 * Records are framed by their record headers, so Z/M variants and mixed
 * record sizes are handled without guesswork.
 */
public class ShpReader implements Closeable {
	private static final int MAGIC = 9994;
	private static final int HEADER_SIZE = 100;

	private final DataInputStream in;
	private final ShapeType shapeType;
	private final double minX, minY, maxX, maxY;
	private long remaining; // bytes left after the file header

	public ShpReader(File shpFile) throws IOException {
		in = new DataInputStream(new BufferedInputStream(new FileInputStream(shpFile)));
		byte[] header = new byte[HEADER_SIZE];
		try {
			in.readFully(header);
		} catch (IOException e) {
			in.close();
			throw new IOException("Not a shapefile (file too short): " + shpFile, e);
		}
		ByteBuffer buf = ByteBuffer.wrap(header);
		if (buf.getInt(0) != MAGIC) {
			in.close();
			throw new IOException("Not a shapefile (bad magic number): " + shpFile);
		}
		long fileLengthWords = buf.getInt(24); // big-endian, in 16-bit words
		buf.order(ByteOrder.LITTLE_ENDIAN);
		int typeCode = buf.getInt(32);
		shapeType = ShapeType.fromCode(typeCode);
		if (shapeType == null) {
			in.close();
			throw new IOException("Unsupported shape type code " + typeCode + " in " + shpFile);
		}
		minX = buf.getDouble(36);
		minY = buf.getDouble(44);
		maxX = buf.getDouble(52);
		maxY = buf.getDouble(60);
		remaining = fileLengthWords * 2 - HEADER_SIZE;
	}

	public ShapeType getShapeType() {
		return shapeType;
	}

	public double getMinX() { return minX; }
	public double getMinY() { return minY; }
	public double getMaxX() { return maxX; }
	public double getMaxY() { return maxY; }

	public boolean hasNext() {
		return remaining >= 8;
	}

	/** @return the next record's geometry, or null at end of file. */
	public ShpGeometry next() throws IOException {
		if (!hasNext()) return null;
		in.readInt(); // record number, unused
		int contentWords = in.readInt();
		byte[] content = new byte[contentWords * 2];
		in.readFully(content);
		remaining -= 8 + content.length;
		return ShpGeometry.parse(ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN));
	}

	@Override
	public void close() throws IOException {
		in.close();
	}
}