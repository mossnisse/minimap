package shapeFile;

import java.io.IOException;

public class ShapeESRI {
	private int type, nr, length;
	//private double x, y;

	/*
	static private void printHex(String name, int i) {
		System.out.print(name + ": ");
		System.out.printf("%08X ", i);
		System.out.println("");
	}*/

	/*
	static private void printHex(String name, double i) {
		System.out.println(name + ": "+i);
	}*/

	public ShapeESRI(){
		//System.out.println("super Shape");
		//reads(bs);
	}
	
	public void read(DataInputStreamSE bs) throws IOException {
		nr = bs.readInt();
		//System.out.println("record number:"+nr);
		length = bs.readInt();
		//System.out.println("record length:"+length);
		type = bs.readIntSE();
		//System.out.println("record shape type:"+type);
	}

	public void print() {
		System.out.println("record number:"+nr);
		System.out.println("record length:"+length);
		System.out.println("record shape type:"+type);
	}

}
