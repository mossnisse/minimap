import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;




public class Parser {
	public static class Synonym {
		public String gammalt, nytt, gAuctor;
		public Synonym(String g, String a, String n) {
			gammalt = g;
			gAuctor = a;
			nytt = n;
		}
	}
	
	public static class Genera {
		public String name, familj;
		public Genera(String n, String f) {
			name = n;
			familj = f;
		}
	}
	
	public static void main(String[] args) throws IOException {
		String filename = "Genera.txt";
		//DataInputStreamSE br = new DataInputStreamSE(new BufferedInputStream(new FileInputStream(new File(filename))));
		//Path file = new Path(filename);
		Path path = FileSystems.getDefault().getPath(filename);
		ArrayList<String> lines = (ArrayList<String>) Files.readAllLines(path, Charset.forName("UTF8"));
		
		ArrayList<Synonym> syns = new ArrayList<Synonym>();
		ArrayList<Genera> gens = new ArrayList<Genera>();
		
		for(String item : lines ){
			   //System.out.println(item);
			   if (item.contains("=")) {
				   int eq = item.indexOf('=');
				   String gammalt = item.substring(0,eq);
				   String auctor ="";
				   if(gammalt.contains(" ")) {
					   int sp3 = item.indexOf(" ");
					   auctor = gammalt.substring(sp3+1,gammalt.length());
					   gammalt = gammalt.substring(0,sp3);
					   
				   }
						   
				   syns.add(new Synonym(gammalt, auctor, item.substring(eq+1,item.length())));
			   }
			   else {
				   if (item.contains("(") & item.contains(")")) {
					   int sp = 0;
					   if (item.contains("\t")) {
						   sp = item.indexOf('\t');
					   } else if (item.contains(" ")) {
						   sp = item.indexOf(' ');
					   } else {
						   System.out.println("Error: "+item);
					   }
					   int rp = item.indexOf('(');
					   int lp = item.indexOf(')');
					   String fam = item.substring(rp+1,lp);
					   if (fam.contains(" ")) {
						   int sp2 = fam.indexOf(" ");
						   fam =  fam.substring(0,sp2);
					   }
					   
					   
					   gens.add(new Genera(item.substring(0,sp),fam)); 
				   } else {
					   System.out.println("Error: "+item);
				   }
			   }
		}
		
		/*
		for(Synonym item:syns) {
			System.out.println(item.gammalt+" - "+item.gAuctor +"\t"+item.nytt);
		}
		
		FileWriter f1 = new FileWriter("Genera_Syns.txt");
		String newLine = System.getProperty("line.separator");
		for(Synonym item : syns ) {
			f1.write(item.gammalt +"\t"+item.gAuctor+"\t"+item.nytt+newLine);
		}
		f1.close();*/
		
		/*
		System.out.println("\n Genus list \n");
		for(Genera item : gens ) {
			System.out.println(item.name +"\t"+item.familj);
		}
		
		FileWriter f0 = new FileWriter("Genera_Fam.txt");
		String newLine = System.getProperty("line.separator");
		for(Genera item : gens ) {
			f0.write(item.name +"\t"+item.familj+newLine);
		}
		f0.close();*/
	}
}


