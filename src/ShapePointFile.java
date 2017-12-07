import geometry.BoundingBox;
import geometry.Point;

import java.awt.Color;
import java.awt.Graphics2D;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import shapeFile.DataInputStreamSE;
import shapeFile.FieldDescriptor;
import shapeFile.PointESRI;
import shapeFile.dbfRecord;

public class ShapePointFile implements Layer{
	private String name, fileName;
	private Color color;
	private int fileLength, shpVersion, shapeType, nrRecords, nrFields;
	//private byte dbfVersion;
	private boolean hidden;
	Vector<dbfRecord> data;
	Vector<FieldDescriptor> descriptors;
	// BoundingBoxESRI box;
	double minX, minY, maxX, maxY, minZ, maxZ, minM, maxM;
	// Vector<ShapeESRI> records;
	Vector<PointESRI> points;
	
	public ShapePointFile(String fileName) throws IOException {
		this.fileName = fileName;
		this.name = fileName;
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
	
	@Override
	public void setColor(Color c) {
		this.color =c;
	}

	@Override
	public Color getColor() {
		return color;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale,
			double yShift, double yScale, BoundingBox bounds) {
		if (!hidden) {
		g2d.setColor(color);
		
		/*
		for (PointESRI point : points) {
			int x = (int) ((point.getX()*xScale)+xShift);
			int y = (int) ((point.getY()*yScale)+yShift);
			g2d.drawOval(x-3,y-3,6,6);
		}*/
		
		Iterator<PointESRI> it = points.iterator();
		for (dbfRecord record : data) {
			g2d.setColor(color);
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

	@Override
	public void setMinZoomL(int zoomLevel) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setMaxZoomL(int zoomLevel) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		// TODO Auto-generated method stub
		return true;
	}
	
	public TNGPointFile find(String kname, String value) {
		value = value.trim();
		//Pattern pattern = Pattern.compile(value+"*");
		ArrayList<Point> ans = new ArrayList<Point>();
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
				ans.add(point.toPoint());
				names.add(record.getField(0)+", "+record.getField(3)+", " +record.getField(1));
			}
		}
		return new TNGPointFile(ans, names, "ans");
	}

	@Override
	public boolean isHidden() {
		return hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}
	
	public Vector<dbfRecord> getRecords() {
		return data;
	}

}
