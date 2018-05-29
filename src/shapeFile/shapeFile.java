package shapeFile;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

public class shapeFile {
	private String filename;
	int fileLength, shpVersion, shapeType, nrRecords, nrFields;
	// BoundingBoxESRI box;
	double minX, minY, maxX, maxY, minZ, maxZ, minM, maxM;
	// Vector<ShapeESRI> records;
	Vector<PointESRI> points;
	Vector<PolygonESRI> polygons;
	Vector<FieldDescriptor> descriptors;
	Vector<dbfRecord> data;
	byte dbfVersion;

	public shapeFile(String filename) throws IOException {
		this.filename = filename;
		readShapeFile();
		readDBF();
	}
	
	static private void printHex(String name, int i) {
		System.out.print(name + ": ");
		System.out.printf("%08X ", i);
		System.out.println("");
	}

	static private void printHex(String name, double i) {
		System.out.print(name + ": ");
		System.out.print(i);
		System.out.println("");
	}

	static private void printN(String name, double i) {
		System.out.print(name + ": ");
		System.out.print(i);
		System.out.println("");
	}


	public Vector<FieldDescriptor> getFieldDescriptors() {
		return descriptors;
	}

	public Vector<dbfRecord> getDBFRecords() {
		return data;
	}

	public Vector<PointESRI> getPoints() {
		return points;
	}

	public Vector<PolygonESRI> getPlygons() {
		return polygons;
	}

	private void readShapeFile() throws IOException {
		String shpFilename = filename.substring(0, filename.length() - 4)
				+ ".shp";
		DataInputStreamSE br = new DataInputStreamSE(new BufferedInputStream(
				new FileInputStream(new File(shpFilename))));
		br.skipBytes(24);

		fileLength = br.readInt();
		shpVersion = br.readIntSE();
		shapeType = br.readIntSE();
		minX = br.readDoubleSE();
		minY = br.readDoubleSE();
		maxX = br.readDoubleSE();
		maxY = br.readDoubleSE();
		minZ = br.readDoubleSE();
		maxZ = br.readDoubleSE();
		minM = br.readDoubleSE();
		maxM = br.readDoubleSE();

		// printShpHeader();

		if (shapeType == 1.0) {
			//System.out.println("Point file");
			points = new Vector<PointESRI>();
			while (br.available() != 0) {
				// System.out.println("Point");
				points.add(new PointESRI(br));
			}
		}

		if (shapeType == 5) {
			// System.out.println("Polygon file");
			polygons = new Vector<PolygonESRI>();
			while (br.available() != 0) {
				PolygonESRI polygon = new PolygonESRI(br);
				polygons.add(polygon);
			}
		}

	}

	public void delPosts(String skipp, int nameField) {
		Vector<dbfRecord> tdata = new Vector<dbfRecord>();
		Vector<PointESRI> tpoints = new Vector<PointESRI>();
		Iterator<PointESRI> it = points.iterator();
		for (dbfRecord record : data) {
			PointESRI point = it.next();
			if (!record.getField(nameField).equals(skipp)) {
				tdata.add(record);
				tpoints.add(point);
			}
		}
		data = tdata;
		points = tpoints;
		nrRecords = data.size();

	}

	private void readDBF() throws IOException {
		String dbfFilename = filename.substring(0, filename.length() - 4)
				+ ".dbf";
		DataInputStreamSE br = new DataInputStreamSE(new BufferedInputStream(
				new FileInputStream(new File(dbfFilename))));
		dbfVersion = br.readByte(); // 0
		br.readByte(); // 1 // byte y = 
		br.readByte(); // 2  // byte m = 
		br.readByte(); // 3  // byte d = 
		// printHex("year: ", y);
		nrRecords = br.readIntSE(); // 4-7
		int headerSize = br.readInt16(); // 8-9
		nrFields = (headerSize - 32) / 32;
		br.readInt16(); // 10-11 // int recordSize = 
		// printN("Record size", recordSize);
		br.skipBytes(20);
		descriptors = new Vector<FieldDescriptor>();
		for (int i = 0; i < nrFields; i++) {
			FieldDescriptor desc = new FieldDescriptor();
			desc.read3(br);
			descriptors.add(desc);
		}
		br.skipBytes(1);
		data = new Vector<dbfRecord>();
		for (int i = 0; i < nrRecords; i++) {
			dbfRecord rdata = new dbfRecord(descriptors);
			rdata.read(br);
			data.add(rdata);
		}

	}

	public void printShpHeader() {
		System.out.println("Shapefile header");
		printHex("file length", fileLength);
		printHex("version", shpVersion);
		printHex("shape type", shapeType);
		printN("shape type", shapeType);
		printHex("min X", minX);
		printHex("min Y", minY);
		printHex("maxX", maxX);
		printHex("maxY", maxY);
		printHex("minZ", minZ);
		printHex("maxZ", maxZ);
		printHex("minM", minM);
		printHex("maxM", maxM);
	}

	public void printShpRecords() {
		System.out.println("Shapefile Records");
		if (shapeType == 1) {// points
			for (PointESRI point : points) {
				point.print();
			}
		}

		if (shapeType == 5) { // polygons
			for (ShapeESRI polygon : polygons) {
				polygon.print();
			}
		}

	}

	public void printDFBHeader() {
		System.out.println("DBF file header");
		printHex("version", dbfVersion);
		printN("Nr Records", nrRecords);
		printN("Nr Fields", nrFields);
	}

	public void printDBFFeidlDescriptors() {
		System.out.println("DBF-file field descriptors");
		for (FieldDescriptor desc : descriptors) {
			desc.print();
		}
	}

	public void printDBFRecords() {
		System.out.println("DBF-file records");
		for (dbfRecord record : data) {
			record.print();
		}
	}

	public static void writeTNGfile(String filename, shapeFile shp,
			int nameField) throws IOException {
		DataOutputStream out = new DataOutputStream(new FileOutputStream(
				filename));
		out.writeInt(shp.shapeType);
		// System.out.println(shp.shapeType);
		out.writeInt(shp.nrRecords);
		int nameLength = shp.getFieldDescriptors().get(nameField).length;
		out.writeInt(nameLength);

		if (shp.shapeType == 5) { // Polygons
			Iterator<PolygonESRI> it = shp.polygons.iterator();
			for (dbfRecord record : shp.data) {

				out.writeBytes(record.getField(nameField));
				// System.out.println(record.getField(nameField));
				PolygonESRI poly = it.next();
				BoundingBoxESRI box = poly.getBoundingBox();
				out.writeInt((int) Math.round(box.getX1()));
				out.writeInt((int) Math.round(box.getY1()));
				out.writeInt((int) Math.round(box.getX2()));
				out.writeInt((int) Math.round(box.getY2()));
				out.writeInt(poly.getNumParts());
				out.writeInt(poly.getNumPoints());
				for (int part : poly.getParts()) {
					out.writeInt(part);
				}
				for (PointESRI point : poly.getPoints()) {
					out.writeInt((int) Math.round(point.getX()));
					out.writeInt((int) Math.round(point.getY()));
				}
			}
		}

		if (shp.shapeType == 1) { // Points
			Iterator<PointESRI> it = shp.points.iterator();
			for (dbfRecord record : shp.data) {
				out.writeBytes(record.getField(nameField));
				// System.out.println(record.getField(nameField));
				PointESRI point = it.next();
				out.writeInt((int) Math.round(point.getX()));
				out.writeInt((int) Math.round(point.getY()));
			}
		}
		out.close();
	}
	
	public static void writeSpecial(String filename, shapeFile shp,
			int nameField) throws IOException {
		//DataOutputStream out = new DataOutputStream(new FileOutputStream(filename));
		BufferedWriter out = new BufferedWriter(new FileWriter(filename));
		//out.writeInt(shp.shapeType);
		// System.out.println(shp.shapeType);
		//out.writeInt(shp.nrRecords);
		int nameLength = shp.getFieldDescriptors().get(nameField).length;
		//out.writeInt(nameLength);

		if (shp.shapeType == 5) { // Polygons
			Iterator<PolygonESRI> it = shp.polygons.iterator();
			for (dbfRecord record : shp.data) {

				out.write(record.getField(nameField)+"\n\r");
				// System.out.println(record.getField(nameField));
				PolygonESRI poly = it.next();
				/*BoundingBoxESRI box = poly.getBoundingBox();
				out.writeInt((int) Math.round(box.getX1()));
				out.writeInt((int) Math.round(box.getY1()));
				out.writeInt((int) Math.round(box.getX2()));
				out.writeInt((int) Math.round(box.getY2()));*/
				//out.writeInt(poly.getNumParts());
				//out.writeInt(poly.getNumPoints());
				for (int part : poly.getParts()) {
					out.writeInt(part);
				}
				for (PointESRI point : poly.getPoints()) {
					out.writeInt((int) Math.round(point.getX()));
					out.writeInt((int) Math.round(point.getY()));
				}
			}
		}

		if (shp.shapeType == 1) { // Points
			Iterator<PointESRI> it = shp.points.iterator();
			for (dbfRecord record : shp.data) {
				out.writeBytes(record.getField(nameField));
				// System.out.println(record.getField(nameField));
				PointESRI point = it.next();
				out.writeInt((int) Math.round(point.getX()));
				out.writeInt((int) Math.round(point.getY()));
			}
		}
		out.close();
	}

	public static void readTNGfile(String filename) throws IOException {
		DataInputStreamSE in = new DataInputStreamSE(new FileInputStream(
				filename));
		int shapeType = in.readInt();
		// System.out.println(shapeType);
		in.readInt();  // int nrRecords = 
		// System.out.println(nrRecords);
		int nameLength = in.readInt();
		// System.out.println(nameLength);
		if (shapeType == 1) { // Points
			while (in.available() != 0) {
				String name = in.readString(nameLength).trim();
				int x = in.readInt();
				int y = in.readInt();
				// System.out.println(name + " (" + x + ", " + y + ")");
			}
		}
		if (shapeType == 5) { // Polygons
			while (in.available() != 0) {
				String name = in.readString(nameLength).trim();
				int x1 = in.readInt();
				int y1 = in.readInt();
				int x2 = in.readInt();
				int y2 = in.readInt();
				int numParts = in.readInt();
				int numPoints = in.readInt();
				int[] parts = new int[numParts];
				for (int i = 0; i < numParts; i++) {
					parts[i] = in.readInt();
				}
				int[] px = new int[numPoints];
				int[] py = new int[numPoints];
				for (int i = 0; i < numPoints; i++) {
					px[i] = in.readInt();
					py[i] = in.readInt();
				}

				/*System.out.println(name + " (" + x1 + ", " + y1 + ") (" + x2
						+ ", " + y2 + ") " + numPoints);*/
			}
		}
		in.close();
	}

	public static void main(String[] args) {
		try {
			// readTNGfile("..\\orter.tng");
			// readTNGfile("..\\provinser.tng");

			/*
			shapeFile hej = new shapeFile("..\\Lokalnamn\\tx_svk.shp");
			hej.delPosts("ingen                                   ", 5);
			writeTNGfile("..\\orter.tng", hej, 5);*/

			//shapeFile hej = new shapeFile("..\\FloraProvinser\\provinser.shp");
			//writeTNGfile("..\\provinser.tng",hej,12);
			shapeFile hej = new shapeFile("..\\socknar_sverige\\socknar_sverige.shp");
			writeSpecial("..\\socknar.txt",hej,4);
			//writeTNGfile("..\\socknar.tng",hej,4);

			// hej.printShpHeader();
			// hej.printShpRecords();
			// hej.printDBFFeidlDescriptors();

			// 
			//

			/*
			 * System.out.println(); hej.printDFBHeader(); System.out.println();
			 * hej.printDBFFeidlDescriptors(); System.out.println();
			 * hej.printDBFRecords();
			 */

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
