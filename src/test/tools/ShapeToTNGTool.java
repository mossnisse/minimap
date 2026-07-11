package test.tools;

import gis.shapefile.ShapeType;
import gis.shapefile.ShapefileReader;
import gis.shapefile.ShpGeometry;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Converts a point or polygon shapefile to the app's .tng format
 * (read by TNGPointFileLayer/TNGPolygonFileLayer). Replaces the old
 * gis.shapefile.shapeFile.writeTNGfile.
 */
public class ShapeToTNGTool {

	/**
	 * @param nameField index of the .dbf column whose value becomes the record name
	 * @param skipValue records whose name equals this are dropped (may be null)
	 */
	public static void writeTNG(String shpFile, String tngFile, int nameField, String skipValue)
			throws IOException {
		List<ShapefileReader.Feature> features = new ArrayList<ShapefileReader.Feature>();
		ShapeType type;
		int nameLength;
		try (ShapefileReader reader = new ShapefileReader(shpFile)) {
			type = reader.getShapeType().base();
			if (type != ShapeType.POINT && type != ShapeType.POLYGON) {
				throw new IOException("TNG supports point and polygon files, not " + reader.getShapeType());
			}
			nameLength = reader.getFields().get(nameField).length;
			for (ShapefileReader.Feature f : reader) {
				if (skipValue != null && f.attributes[nameField].equals(skipValue)) continue;
				features.add(f);
			}
		}

		try (DataOutputStream out = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(tngFile)))) {
			out.writeInt(type.getCode());
			out.writeInt(features.size());
			out.writeInt(nameLength);
			for (ShapefileReader.Feature f : features) {
				out.write(fixedWidthName(f.attributes[nameField], nameLength));
				ShpGeometry g = f.geometry;
				if (type == ShapeType.POINT) {
					out.writeInt((int) Math.round(g.getX(0)));
					out.writeInt((int) Math.round(g.getY(0)));
				} else {
					out.writeInt((int) Math.round(g.getMinX()));
					out.writeInt((int) Math.round(g.getMinY()));
					out.writeInt((int) Math.round(g.getMaxX()));
					out.writeInt((int) Math.round(g.getMaxY()));
					out.writeInt(g.getNumParts());
					out.writeInt(g.getNumPoints());
					for (int i = 0; i < g.getNumParts(); i++) {
						out.writeInt(g.partStart(i));
					}
					for (int i = 0; i < g.getNumPoints(); i++) {
						out.writeInt((int) Math.round(g.getX(i)));
						out.writeInt((int) Math.round(g.getY(i)));
					}
				}
			}
		}
	}

	/** UTF-8 encodes {@code name}, space-padded/truncated to exactly {@code length} bytes. */
	private static byte[] fixedWidthName(String name, int length) {
		byte[] raw = name.getBytes(StandardCharsets.UTF_8);
		byte[] fixed = new byte[length];
		Arrays.fill(fixed, (byte) ' ');
		System.arraycopy(raw, 0, fixed, 0, Math.min(raw.length, length));
		return fixed;
	}

	public static void main(String[] args) {
		try {
			// Example conversions kept from the old tool:
			// writeTNG("..\\Lokalnamn\\tx_svk.shp", "..\\orter.tng", 5, "ingen");
			writeTNG("c:\\shape\\landskap_swref.shp", "provinserSWEREF99TM.tng", 1, null);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
