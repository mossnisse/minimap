package shapeFile;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;


public class dbfRecord {
	Vector<String> fdata = new Vector<String>();
	Vector<FieldDescriptor> descriptors;
	
	public dbfRecord(Vector<FieldDescriptor> descriptors) {
		this.descriptors = descriptors;
	}
	
	public Vector<String> getRecord() {
		return fdata;
	}
	
	public String getField(int i) {
		return fdata.elementAt(i); 
	}
	
	public String getField(String fieldName) {
		Iterator<String> itr = fdata.iterator();
		for (FieldDescriptor desc : descriptors) {
			String data = itr.next();
			if (desc.name== fieldName) return data;
		}
		return "";
	}
	
	public void read(DataInputStreamSE br) throws IOException {
		//System.out.println("hej");
		br.readByte(); //byte fl = 
		//if (fl == 32) {
			for (FieldDescriptor desc : descriptors) {
				String data = br.readStringUTF8(desc.length);
				//System.out.println(desc.name+": "+data);
				fdata.add(data.trim());
			}
		//}
	}
	
	public String toString() {
		Iterator<String> itr = fdata.iterator();
		String sv ="";
		for (FieldDescriptor desc : descriptors) {
			sv += desc.name+": ";
			sv += itr.next()+"\n";
		}
		return sv;
	}
	
	public void print() {
		System.out.println("");
		Iterator<String> itr = fdata.iterator();
		for (FieldDescriptor desc : descriptors) {
			System.out.print(desc.name+": ");
			String data = itr.next();
			System.out.println(data);
		}
	}
}
