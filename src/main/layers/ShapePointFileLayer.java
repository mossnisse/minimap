package main.layers;

import main.core.Layer;
import main.coords.*;
import java.awt.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import main.geometry.Extent;
import main.shapeFile.DataInputStreamSE;
import main.shapeFile.FieldDescriptor;
import main.shapeFile.PointESRI;
import main.shapeFile.dbfRecord;

public class ShapePointFileLayer extends Layer {
	private final String fileName;

	private int fileLength, shpVersion, shapeType, nrRecords, nrFields;
	//private byte dbfVersion;
	Vector<dbfRecord> data;
	Vector<FieldDescriptor> descriptors;
	// BoundingBoxESRI box;
	double minX, minY, maxX, maxY, minZ, maxZ, minM, maxM;
	// Vector<ShapeESRI> records;
	Vector<PointESRI> points;
	
	public ShapePointFileLayer(String fileName) throws IOException {
		super(fileName, false, CoordSystem.SWEREF99TM);
		this.fileName = fileName;
		readShapeFile();
		readDBF();
		//printDBFFeidlDescriptors();
	}
	
	private void readShapeFile() throws IOException {
		String shpFilename = fileName.substring(0, fileName.length() - 4)
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
		} else {
			System.out.println("Not a Point file");
		}

	}
	
	private void readDBF() throws IOException {
		String dbfFilename = fileName.substring(0, fileName.length() - 4)
				+ ".dbf";
		DataInputStreamSE br = new DataInputStreamSE(new BufferedInputStream(
				new FileInputStream(new File(dbfFilename))));
		br.readByte(); // 0  // dbfVersion = 
		br.readByte(); // 1  // byte y = 
		br.readByte(); // 2  // byte m = 
		br.readByte(); // 3  // byte d = 
		// printHex("year: ", y);
		nrRecords = br.readIntSE(); // 4-7
		int headerSize = br.readInt16(); // 8-9
		nrFields = (headerSize - 32) / 32;
		br.readInt16(); // 10-11  // int recordSize = 
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
	
	public void printDBFFeidlDescriptors() {
		System.out.println("DBF-file field descriptors");
		for (FieldDescriptor desc : descriptors) {
			desc.print();
		}
	}
	
	public TNGPointFileLayer find(String kname, String value) {
		value = value.trim();
		//Pattern pattern = Pattern.compile(value+"*");
		ArrayList<Coordinate> ans = new ArrayList<Coordinate>();
		ArrayList<String> names = new ArrayList<String>();
		Iterator<PointESRI> it = points.iterator();
		for (dbfRecord record : data) {
			PointESRI point = it.next();
			 //Matcher matcher = pattern.matcher(record.getField(0));
			//System.out.println("field: "+record.getField(0)+" search:"+value);
			//TNGPointFile x = new TNGPointFile("");
			if (record.getField(0).equals(value)) {
			//if(matcher.find()) {
				//System.out.println("träff");
				ans.add(new Coordinate(point.toPoint()));
				names.add(record.getField(0)+", "+record.getField(3)+", " +record.getField(1));
			}
		}
		return new TNGPointFileLayer(ans, names, "ans");
	}
	
	public Vector<dbfRecord> getRecords() {
		return data;
	}

	@Override
	public Extent getBoundaries() {
		//Todo: implement the method
		return null;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, Extent bounds) {
		if (!isHidden()) {
			g2d.setColor(getColor());

		/*
		for (PointESRI point : points) {
			int x = (int) ((point.getX()*xScale)+xShift);
			int y = (int) ((point.getY()*yScale)+yShift);
			g2d.drawOval(x-3,y-3,6,6);
		}*/

			Iterator<PointESRI> it = points.iterator();
			for (dbfRecord record : data) {
				PointESRI point = it.next();
				int x = (int) ((point.getX()*xScale)+xShift);
				int y = (int) ((point.getY()*yScale)+yShift);
				g2d.drawOval(x-3,y-3,6,6);
				//String rnamn = record.getField(0);
				//System.out.println(rnamn);
				//record.print();

			/*g2d.setColor(Color.black);
			g2d.drawString(rnamn,x,y);*/
			}
		}

	}
}