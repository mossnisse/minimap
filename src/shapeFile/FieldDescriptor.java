package shapeFile;

import java.io.IOException;


public class FieldDescriptor {
	public String name;
	public char type;
	public int length;
	public int decimalCount;
	
	public FieldDescriptor(String name, char type, int length, int decimalCount) {
		this.name = name;
		this.type = type;
		this.length = length;
		this.decimalCount = decimalCount;
	}
	
	public FieldDescriptor() {
	}
	
	public void print() {
		System.out.println("");
		System.out.println("Field name: ["+name+']');
		System.out.println("Field type: "+type);
		System.out.println("Field length: "+length);
		System.out.println("Field_decimal_count: "+decimalCount);
	}
	
	public void read3(DataInputStreamSE br) throws IOException {
		name = br.readString(11);  //0-10
		type = (char) br.readByte();  //11
		br.skipBytes(4);
		length = br.readByte() & 0xFF;  //16
		decimalCount = br.readByte();  //17
		br.skipBytes(14);
	}
}
