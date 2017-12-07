package shapeFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Vector;

public class dbf {
	String filename;
	DataInputStreamSE br;
	Vector<FieldDescriptor> descriptors;
	Vector<dbfRecord> data;
	byte version;
	int nrRecords, nrFields;
	
	
	static private void printHex(String name, int i) {
		System.out.print(name+": ");
		System.out.printf("%08X ",i);
		System.out.println("");
	}
	
	static private void printN(String name, int i) {
		System.out.print(name+": ");
		System.out.print(i);
		System.out.println("");
	}
	
	/*
	static private void printN(String name, double i) {
		System.out.print(name+": ");
		System.out.print(i);
		System.out.println("");
	}*/
	
	/*
	static private void printHex(String name, double i) {
		System.out.print(name+": ");
		System.out.print(i);
		System.out.println("");
	}*/
	
	public dbf(String filename) throws IOException {
		this.filename = filename;
		br = new DataInputStreamSE(
		        new BufferedInputStream(
		            new FileInputStream(new File(this.filename))));
		version = br.readByte();  //0
		printHex("version",version);
		byte y = br.readByte(); //1
		br.readByte(); //2  // byte m = 
		br.readByte(); //3  // byte d = 
		printHex("year: ",y);
		nrRecords = br.readIntSE();  //4-7
		printN("Nr Records", nrRecords);
		int headerSize = br.readInt16(); //8-9
		printN("Header size", headerSize);
		
		nrFields =  (headerSize-32)/32;
		printN("Nr Fields", nrFields);
		
		int recordSize = br.readInt16(); //10-11
		printN("Record size", recordSize);

		br.skipBytes(20);
		
		descriptors = new Vector<FieldDescriptor>();
		for (int i=0; i<nrFields; i++) {
			FieldDescriptor desc = new FieldDescriptor();
			desc.read3(br);
			descriptors.add(desc);
		}
		
		for (FieldDescriptor desc : descriptors) {
			desc.print();
		}
		
		br.skipBytes(1);
		
		data = new Vector<dbfRecord>();
		for (int i=0; i<10; i++) {
			dbfRecord rdata  = new dbfRecord(descriptors);
			rdata.read(br);
			data.add(rdata);
		}
		for (dbfRecord record : data) {
			record.print();
		}
	}
	
	/*
	public static void main(String[] args) {
		try {
			dbf dbfFile = new dbf( "..\\Lokalnamn\\tx_svk.dbf");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}*/
}
