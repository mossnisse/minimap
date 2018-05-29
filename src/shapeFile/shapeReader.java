package shapeFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.text.AttributeSet;
import javax.swing.text.html.HTML.Tag;

public class shapeReader implements Iterable<ReccordESRI>{
	int fileLength, shpVersion, shapeType, nrRecords, nrFields;
	double minX, minY, maxX, maxY, minZ, maxZ, minM, maxM;
	Vector<FieldDescriptor> descriptors;
	byte dbfVersion;
	DataInputStreamSE br;
	
	DataInputStreamSE DBFbr;
	
	public shapeReader(String filename) throws IOException {
		readShapeHead(filename);
		readDBFHead(filename);
	}
	
	public void readShapeHead(String filename) throws IOException {
		String shpFilename = filename.substring(0, filename.length() - 4)
				+ ".shp";
		br = new DataInputStreamSE(new BufferedInputStream(
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
	}
	
	public void readDBFHead(String filename) throws IOException {
		String dbfFilename = filename.substring(0, filename.length() - 4) + ".dbf";
		DataInputStreamSE DBFbr = new DataInputStreamSE(new BufferedInputStream(
				new FileInputStream(new File(dbfFilename))));
		dbfVersion = DBFbr.readByte(); // 0
		DBFbr.readByte(); // 1 // byte y = 
		DBFbr.readByte(); // 2  // byte m = 
		DBFbr.readByte(); // 3  // byte d = 
		// printHex("year: ", y);
		nrRecords = DBFbr.readIntSE(); // 4-7
		int headerSize = DBFbr.readInt16(); // 8-9
		nrFields = (headerSize - 32) / 32;
		DBFbr.readInt16(); // 10-11 // int recordSize = 
		// printN("Record size", recordSize);
		DBFbr.skipBytes(20);
		descriptors = new Vector<FieldDescriptor>();
		for (int i = 0; i < nrFields; i++) {
			FieldDescriptor desc = new FieldDescriptor();
			desc.read3(DBFbr);
			descriptors.add(desc);
		}
		DBFbr.skipBytes(1);
	}
	
	public PointESRI readPoint() throws IOException {
		if (shapeType == 1.0) throw new IOException(); 
		if (br.available() != 0) 
			return new PointESRI(br);
		else return null;
	}
	
	public PolygonESRI readPolygon() throws IOException {
		if (shapeType != 5) throw new IOException();
		if (br.available() != 0) 
			return new PolygonESRI(br);
		else return null;
	}
	
	public boolean hasNext() throws IOException {
		return (br.available() != 0);
	}
	
	public ReccordESRI readNext() throws IOException {
		if (shapeType == 1.0) {
			return new ReccordESRI(readPoint(),readDBF());
		}
		else if (shapeType ==5) {
			return new ReccordESRI(readPolygon(),readDBF());
		} else {
			return null;
		}
	}
	
	private Vector<dbfRecord> readDBF() throws IOException {
		Vector<dbfRecord> data = new Vector<dbfRecord>();
		for (int i = 0; i < nrRecords; i++) {
			dbfRecord rdata = new dbfRecord(descriptors);
			rdata.read(DBFbr);
			data.add(rdata);
		}
		return data;
	}
	
	 private class ShpReadIterator implements Iterator<ReccordESRI> {
		 private shapeReader sr;

		public ShpReadIterator(shapeReader sr) {
			this.sr = sr;
		}
		@Override
		public boolean hasNext() {
			// TODO Auto-generated method stub
			try {
				return sr.hasNext();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return false;
		}

		@Override
		public ReccordESRI next() {
			// TODO Auto-generated method stub
			try {
				return sr.readNext();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}
		 
	 }

	@Override
	public Iterator<ReccordESRI> iterator() {
		// TODO Auto-generated method stub
		return new ShpReadIterator(this);
	}

	
}
