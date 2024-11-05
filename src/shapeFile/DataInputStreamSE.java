package shapeFile;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class DataInputStreamSE extends DataInputStream {

	public DataInputStreamSE(InputStream in) {
		super(in);
		// TODO Auto-generated constructor stub
	}

	public double readDoubleSE() throws IOException {
		byte[] t = new byte[8];
		read(t, 0, 8);
		return ByteBuffer.wrap(t).order(ByteOrder.LITTLE_ENDIAN ).getDouble();
	}
	
	public int readIntSE() throws IOException {
		int t = readInt();
		return (0x000000ff & (t>>24)) | 
	            (0x0000ff00 & (t>> 8)) | 
	            (0x00ff0000 & (t<< 8)) | 
	            (0xff000000 & (t<<24));
	}
	
	public int readInt16() throws IOException {
		byte b1 = readByte(); 
		byte b2 = readByte();
		int i1 = (int) b1 & 0xff;
		int i2 = (int) b2 & 0xff;
		//System.out.println("i1: "+i1);
		//System.out.println("i2: "+i2);
		return (int) i2 << 8 | i1;
	}
	
	public String readString(int i) throws IOException {
		byte[] n = new byte[i];
		read(n, 0, i);
		return new String(n, "latin1");
	}
	
	public String readStringUTF8(int i) throws IOException {
		byte[] n = new byte[i];
		read(n, 0, i);
		return new String(n, "UTF8");
	}
	
	public byte[] readBytes(int i) throws IOException {
		byte[] n = new byte[i];
		read(n, 0, i);
		return n;
	}
}
