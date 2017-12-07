package shapeFile;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Vector;

public class shape {
	private String filename;
	int fileLength, version, shapeType;
	double minX, minY, maxX, maxY, minZ, maxZ, minM, maxM;
	Vector<ShapeESRI> records = new Vector<ShapeESRI>(); 
	
	static private void printHex(String name, int i) {
		System.out.print(name+": ");
		System.out.printf("%08X ",i);
		System.out.println("");
	}
	
	/*
	static private void printN(String name, int i) {
		System.out.print(name+": ");
		System.out.print(i);
		System.out.println("");
	}*/
	
	static private void printHex(String name, double i) {
		System.out.print(name+": ");
		System.out.print(i);
		System.out.println("");
	}
	
	public shape(String filename) {
		this.filename = filename;
	}
	
	public void readFile() throws IOException{
		DataInputStreamSE br = new DataInputStreamSE(
		        new BufferedInputStream(
		            new FileInputStream(new File(filename))));
		br.readInt();
		br.readInt();
		br.readInt();
		br.readInt();
		br.readInt();
		br.readInt();
		
		fileLength = br.readInt();
		version = br.readIntSE();
		shapeType = br.readIntSE();
		minX = br.readDoubleSE();
		minY = br.readDoubleSE();
		maxX = br.readDoubleSE();
		maxY = br.readDoubleSE();
		minZ = br.readDoubleSE();
		maxZ = br.readDoubleSE();
		minM = br.readDoubleSE();
		maxM = br.readDoubleSE();
		
		boolean t = true;
		while (t) {
			ShapeESRI record = new ShapeESRI();
			//t = record.read(br);
			if(t) records.add(record);
		}
		br.close();
	}
	
	public void printHeader() {
		printHex("file length", fileLength);
		printHex("version", version);
		printHex("shape type",shapeType);
		printHex("min X",minX);
		printHex("min Y",minY);
		printHex("maxX",maxX);
		printHex("maxY",maxY);
		printHex("minZ",minZ);
		printHex("maxZ",maxZ);
		printHex("minM",minM);
		printHex("maxM",maxM);
	}
	
	public void printRecords() {
		for (ShapeESRI record : records) {
			record.print();
		}
	}

	public static void main(String[] args) {
		shape hej = new shape("..\\Lokalnamn\\tx_svk.shp");
		try {
			hej.readFile();
			hej.printHeader();
			hej.printRecords();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
