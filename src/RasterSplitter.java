import geometry.BoundingBox;
import geometry.Point;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;
import javax.imageio.ImageIO;
import shapeFile.DataInputStreamSE;

public class RasterSplitter {
	
	public static void main(String[] args) throws IOException {
		//chunkifyFile("..\\Översiktskartan\\oversiktskartan_Gsr.tif", "..\\test\\");
		//combineSq("..\\test\\");
		
		chunkifyAll("F:\\Kartor\\Vägkartan\\", "pyramids\\");
		//combine("..\\test\\185(0).jpg","..\\test\\185(1).jpg");
	}

	private static String getFileExtension(File file) {
		String name = file.getName();
		try {
			return name.substring(name.lastIndexOf(".") + 1);
		} catch (Exception e) {
			return "";
		}
	}

	private static String getCopyNumber(File file) {
		String name = file.getName();
		String numb = name.substring(name.length() - 6,name.length() - 5);
		return numb;
	}

	private static String getnumber(File file) {
		String name = file.getName();
		String numb = name.substring(0,name.length() - 7);
		return numb;
	}


	public static void combineSq(String directory) {
		File folder = new File(directory);
		for (final File fileEntry : folder.listFiles()) {
			if (fileEntry.getName().toLowerCase().endsWith(".jpg")) {

				String cnumb = getCopyNumber(fileEntry);
				if (cnumb.equals("0")) {
					String number = getnumber(fileEntry);
					for (int i = 0; i<10; i++) {
						File tname = new File(number+"("+Integer.toString(i)+")"+".jpg");
						try {
							BufferedImage img = ImageIO.read(new File(directory+tname));
							System.out.println("reading file: "+tname );
						} catch (IOException e) {
							System.out.println("no file: "+tname );
						}
					}
				}

			}
		}
	}
	
	
	public static void combine(String bild1, String bild2) {
		try {
			BufferedImage img1 = ImageIO.read(new File(bild1));
			BufferedImage img2 = ImageIO.read(new File(bild2));
			int height = img1.getHeight();
			int width = img1.getWidth();
			int height2 = img2.getHeight();
			int width2 = img2.getWidth();
			System.out.println("height: "+height+" height2: "+height2+" width: "+width+" width2: "+width2);
			if (height>height2) height = height2;
			if (width>width2) width = width2;
			//int height = 100;
			//int width = 100;
					//createGraphics()
					//getSubimage
			for (int x=0; x<width; x++) {
				for (int y=0; y<height; y++) {
					int rgb1 = img1.getRGB(x, y);
					//if (x==564 && y==894) System.out.println("x:"+x+"y:"+y+"rgb"+rgb1);
					//int rgb2 = img2.getRGB(x, y);
					//System.out.println("x:"+x+"y:"+y+"rgb"+rgb1);
					if (rgb1 == -4934476 || rgb1 == -1) {
						//System.out.println("x:"+x+"y:"+y+"rgb"+rgb1);
						img1.setRGB(x, y, img2.getRGB(x, y));
					} else if (rgb1 != img2.getRGB(x, y)) {
						//System.out.println("x:"+x+"y:"+y+"rgb1"+rgb1+"rgb2"+img2.getRGB(x, y));
					}
				}
			}
			ImageIO.write(img1, "jpg", new File("..\\test\\test.jpg") );
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("cant fine images: "+bild1 + " - " +bild2);
			
		}
		
	}
	
	public static BufferedImage combine(BufferedImage img1, BufferedImage img2) {
			int height = img1.getHeight();
			int width = img1.getWidth();
			int height2 = img2.getHeight();
			int width2 = img2.getWidth();
			if (height>height2) height = height2;
			if (width>width2) width = width2;
			for (int x=0; x<width; x++) {
				for (int y=0; y<height; y++) {
					int rgb1 = img1.getRGB(x, y);
					if (rgb1 == -4934476 || rgb1 == -1) {
						img1.setRGB(x, y, img2.getRGB(x, y));
					} else if (rgb1 != img2.getRGB(x, y)) {
					}
				}
			}
			return img1;
	}

	static public void chunkifyAll(String inDir, String outDir) throws IOException {
		File folder = new File(inDir);
		for (final File fileEntry : folder.listFiles()) {
			
			if (fileEntry.getName().toLowerCase().endsWith(".tif")) {
				chunkifyFile(inDir+fileEntry.getName(),outDir);
			}
		}
		System.out.println("done al files");
	}
	
	static public void chunkifyFile(String mapFile, String outDir) throws IOException {
		System.out.println(mapFile);
		String fileName = mapFile;
		String tnrFileName = outDir+"chunkNames.tnr";

		int chunkSize = 50000;  //meter

		BoundingBox layerlimits = new BoundingBox(new Point(1210000,6130000), new Point(1900000, 7680000));

		FileWriter tnrFile = new FileWriter(tnrFileName);
		String newLine = System.getProperty("line.separator");


		String fileName1 = fileName.substring(0,fileName.length()-3).concat("tif");
		String fileName2 = fileName.substring(0,fileName.length()-3).concat("tfw");

		BufferedImage img = ImageIO.read(new File(fileName1));
		DataInputStreamSE in =
				new DataInputStreamSE(
						new BufferedInputStream(
								new FileInputStream(fileName2)));
		Scanner s = new Scanner(in);

		double xp =  Double.parseDouble(s.nextLine());
		System.out.println("xp: "+xp);
		double rotrow = Double.parseDouble(s.nextLine());
		System.out.println("rotrow: "+rotrow);
		double rotcol = Double.parseDouble(s.nextLine());
		System.out.println("rotcol: "+rotcol);
		double yp = Double.parseDouble(s.nextLine());
		double x =  Double.parseDouble(s.nextLine());
		double y = Double.parseDouble(s.nextLine());
		s.close();

		System.out.println("xp: "+xp+", yp: "+yp+", x: "+x+", y: "+y);
		int height = img.getHeight(null);
		int width = img.getWidth(null);
		double x2 = x+width*xp;
		double y2 = y+height*yp;
		BoundingBox box = new BoundingBox(new Point((int) x,(int) y2), new Point((int) x2, (int) y) );
		//int x1 = box.getX1();
		int xstart = (box.getX1()/chunkSize)*chunkSize;
		int xstop = (box.getX2()/chunkSize)*chunkSize;
		int ystart = (box.getY1()/chunkSize)*chunkSize+chunkSize;
		int ystop = (box.getY2()/chunkSize)*chunkSize+chunkSize;
		double chunkSizeIY = -chunkSize/yp;
		System.out.println("chunkSizeIY:"+chunkSizeIY);
		double chunkSizeIX = chunkSize/xp;
		System.out.println("chunkSizeIX:"+chunkSizeIX);
		System.out.println("xstart:"+xstart);
		System.out.println("xstop:"+xstop);
		int chunks = ((xstop-xstart)/chunkSize)*((ystop-ystart)/chunkSize);
		System.out.println("nr chunks"+chunks);
		//BufferedImage imgs[] = new BufferedImage[chunks];
		BufferedImage chunkImg = new BufferedImage((int) chunkSizeIX, (int) chunkSizeIY, img.getType());

		for (int xb=xstart; xb<=xstop; xb+=chunkSize) {
			for (int yb=ystart; yb<=ystop; yb+=chunkSize){
				System.out.println("chunk x:"+xb+" y:"+yb);
				int xi=(int) ((xb-box.getX1())/xp);
				int yi=(int) ((yb-box.getY2())/yp);
				//String chunkname = "chunkI x:"+xi+" yi:"+yi+newLine;
				//String chunkname = Coordinates.getRUBIN50(xb+chunkSize/2,yb-chunkSize/2);
				String chunkname = chName(xb, yb, layerlimits, chunkSize);
				System.out.println(chunkname);

				tnrFile.write(chunkname+newLine);


				Graphics2D gr = chunkImg.createGraphics();
				gr.setColor(Color.white);
				gr.fill3DRect(0,0,(int) chunkSizeIX,(int) chunkSizeIY,false);
				gr.drawImage(img, 0, 0, (int) chunkSizeIX, (int) chunkSizeIY, (int) xi, (int) yi, (int) (xi + chunkSizeIX), (int) (yi + chunkSizeIY), null);  
				gr.dispose();
				String fname = "";
				
				File f = new File(outDir + chunkname + ".jpg");
				if(f.exists()) {
					//System.out.println("file:" + outDir + chunkname + ina +".jpg");
					BufferedImage img2 = ImageIO.read(f);
					chunkImg = combine(chunkImg, img2);
				} 
				/*
				for (int ina=0; ina<10; ina++) {
					File f = new File(outDir + chunkname + "("+ ina +").jpg");
					//System.out.println("testfil:" + outDir + chunkname + ina +".jpg");
					
				}*/
				System.out.println(fname);
				ImageIO.write(chunkImg, "jpg", f);

			}
		}

		tnrFile.close();
		System.out.println("Splitting done");
	}


	public String[] chNames(BoundingBox box, int chunkSize) {
		int chunkNum = box.getWidth()/chunkSize * box.getHeight()/chunkSize;
		//System.out.println(box+"number cunks: "+chunkNum);
		String[] names = new String[chunkNum];
		int i = 0;
		for (int ix = box.getX1(); ix<=box.getX2(); ix+=chunkSize) {
			for (int iy = box.getY1(); iy<=box.getY2(); iy+=chunkSize) {
				String name = Coordinates.getRUBIN50(ix,iy);
				names[i]=name;
			}

		}
		return names;
	}

	public static String chName(int x, int y, BoundingBox box, int chunkSize) {
		//int numberOfChunks = box.getWidth()/chunkSize * box.getHeight()/chunkSize;
		int xi=(int) ((x-box.getX1()));
		int yi=(int) ((y-box.getY1()));
		int chunkNum = ((box.getWidth()/chunkSize)+1)*(yi/chunkSize) + xi/chunkSize;
		//System.out.println(box+"number cunks: "+numberOfChunks);
		//System.out.println("Chunk number: "+chunkNum+ " box.getX1() "+box.getX1()+ " box.getY1() "+box.getY1()+" X "+x +"  y "+y);
		return (Integer.toString(chunkNum)); //+"_"+Integer.toString(xi/chunkSize)+"_"+Integer.toString(yi/chunkSize)
	}
}