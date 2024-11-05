package shapeFile;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;


public class dbfRecord {
	Vector<byte[]> fdata = new Vector<byte[]>();
	Vector<FieldDescriptor> descriptors;
	
	public dbfRecord(Vector<FieldDescriptor> descriptors) {
		this.descriptors = descriptors;
	}
	
	public Vector<byte[]> getRecord() {
		return fdata;
	}
	
	public byte[] getField(int i) {
		return fdata.elementAt(i); 
	}
	
	public byte[] getField(String fieldName) {
		Iterator<byte[]> itr = fdata.iterator();
		for (FieldDescriptor desc : descriptors) {
			return itr.next();
			//return data;
			//if (desc.name == fieldName) {
				//int n = desc.length;
				//return String.format("%-"+n+"s", data) ;
			//}
		}
		return null;
	}
	
	public void read(DataInputStreamSE br) throws IOException {
		//System.out.println("hej");
		br.readByte(); //byte fl = 
		//if (fl == 32) {
			for (FieldDescriptor desc : descriptors) {
				byte[] data = br.readBytes(desc.length);
				//String[] data = br.re
				//System.out.println(desc.name+": "+data);
				fdata.add(data);  //.trim()
			}
		//}
	}
	
	public String toString() {
		Iterator<byte[]> itr = fdata.iterator();
		String sv ="";
		for (FieldDescriptor desc : descriptors) {
			sv += desc.name+": ";
			sv += itr.next()+"\n";
		}
		return sv;
	}
	
	public void print() {
		//System.out.println("");
		Iterator<byte[]> itr = fdata.iterator();
		for (FieldDescriptor desc : descriptors) {
			//System.out.print(desc.name+": ");
			byte[] data = itr.next();
			System.out.println("print"+desc.name+": ["+data+"]");
		}
	}
}
