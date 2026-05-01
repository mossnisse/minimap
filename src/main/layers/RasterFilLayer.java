package main.layers;

import main.core.Layer;
import main.coords.*;
import java.awt.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Scanner;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.swing.JOptionPane;

import main.geometry.Extent;
import main.shapeFile.DataInputStreamSE;

public class RasterFilLayer extends Layer {
	private final String fileName;
	private Image img;
	private Extent box;

	public RasterFilLayer(String fileName) throws IOException {
		super(fileName, false, CoordSystem.SWEREF99TM);
		this.fileName = fileName;
		readFile();
	}

	public boolean canGetTiffDecoder()
	{
		Iterator<ImageReader> reader = ImageIO.getImageReadersByFormatName("TIFF");
		return reader.hasNext();
		//assertNotNull(reader);
		//assertTrue("No tiff decoder", reader.hasNext());
	}

	public void readFile() throws IOException {

		String fileName1 = fileName.substring(0,fileName.length()-3).concat("tif");
		String fileName2 = fileName.substring(0,fileName.length()-3).concat("tfw");

		if (!canGetTiffDecoder()) {
			JOptionPane.showMessageDialog(null, "hittar inte ImageIO", "InfoBox: ", JOptionPane.INFORMATION_MESSAGE);
		} 
		/*else {
			JOptionPane.showMessageDialog(null, "hittar ImageIO", "InfoBox: ", JOptionPane.INFORMATION_MESSAGE);
		}*/
		//System.out.println(fileName2+", "+fileName1);

		//System.out.println(canGetTiffDecoder());
		//JOptionPane.showMessageDialog(null, "innan läsa fil: "+fileName1 + " " + fileName2, "InfoBox: ", JOptionPane.INFORMATION_MESSAGE);
		img = ImageIO.read(new File(fileName1));
		//JOptionPane.showMessageDialog(null, "efter läsa fil "+fileName1 + " " + fileName2, "InfoBox: ", JOptionPane.INFORMATION_MESSAGE);
		DataInputStreamSE in =
				new DataInputStreamSE(
						new BufferedInputStream(
								new FileInputStream(fileName2)));
		Scanner s = new Scanner(in);

		double xp =  Double.parseDouble(s.nextLine());
		//System.out.println("xp: "+xp);
		Double.parseDouble(s.nextLine());  // double rotrow = 
		//System.out.println("rotrow: "+rotrow);
		Double.parseDouble(s.nextLine());   // double rotcol = 
		//System.out.println("rotcol: "+rotcol);
		double yp = Double.parseDouble(s.nextLine());
		double x =  Double.parseDouble(s.nextLine());
		double y = Double.parseDouble(s.nextLine());
		s.close();

		//System.out.println("xp: "+xp+", yp: "+yp+", x: "+x+", y: "+y);
		int height = img.getHeight(null);
		int width = img.getWidth(null);
		double x2 = x+width*xp;
		double y2 = y+height*yp;
		box = new Extent( y2, x, y, x2 );
		//System.out.println("raster box: "+box);
	}

	@Override
	public Extent getBoundaries() {
		//Todo: implement the method
		return null;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale,
	                 double yShift, double yScale, Extent bounds) {
		if (bounds.intersects(box)) {
			int x1 = (int) ((box.c1.getEast()*xScale)+xShift);
			int y1 = (int) ((box.c1.getNorth()*yScale)+yShift);
			int x2 = (int) ((box.c2.getNorth()*xScale)+xShift);
			int y2 = (int) ((box.c2.getNorth()*yScale)+yShift);
			//System.out.println("x1:"+x1+", y1:"+(y2)+", x2:"+(x2-x1)+", y2:"+(y1-y2));
			g2d.drawImage(img,x1,y2,x2-x1,y1-y2,null);
		}
	}
}
