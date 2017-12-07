import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;


public class Settings {
	private static HashMap<String,String> store;
	//= new HashMap<String, String> 
	private static String filename = "settings.txt";

	public static String getValue(String key) throws IOException {
		if (store == null) readStore();
		return store.get(key);
	}

	public static void setValue(String key, String value) throws IOException {
		if (store== null) readStore();
		store.put(key, value);
		PrintWriter writer = new PrintWriter(filename, "UTF-8");
		Iterator<Entry<String,String>> it = store.entrySet().iterator();
		//Set<Entry<String,String>> storList = store.entrySet();
		while (it.hasNext()) {
			Entry<String, String> e = it.next();
			//System.out.println("key: "+e.getKey()+" value: "+e.getValue());
			writer.println(e.getKey()+": "+e.getValue());
		}
		writer.close();

	}

	private static void readStore() throws IOException {
		store = new HashMap<String,String>();
		//BufferedReader br = new BufferedReader(new FileReader(filename, "UTF-8"));
		BufferedReader br = new BufferedReader(
				   new InputStreamReader(
		                      new FileInputStream(filename), "UTF-8"));
		String line = br.readLine();
		while (line != null) {
			//System.out.println(line);
			String[] parts = line.split(": ");
			String key = parts[0]; 
			String value = parts[1];
			store.put(key, value);
			line = br.readLine();
		}
		br.close();
	}
}
