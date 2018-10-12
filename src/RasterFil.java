import geometry.BoundingBox;
import geometry.Point;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Scanner;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.swing.JOptionPane;

import shapeFile.DataInputStreamSE;

public class RasterFil implements Layer {
	private String name;
	private Color color;
	private String fileName;
	private Image img;
	private BoundingBox box;
	private int maxZoom, minZoom;
	private boolean hidden;

	public RasterFil(String fileName) throws IOException {
		this.fileName = fileName;
		this.name = fileName;
		readFile();
		maxZoom = 0;
		minZoom = 0;
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
		box = new BoundingBox(new Point((int) x,(int) y2), new Point((int) x2, (int) y) );
		//System.out.println("raster box: "+box);
	}

	@Override
	public void setColor(Color c) {
		this.color = c;
	}

	@Override
	public Color getColor() {
		return color;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale,
			double yShift, double yScale, BoundingBox bounds) {
		if (bounds.intersects(box)) {
			int x1 = (int) ((box.getX1()*xScale)+xShift);
			int y1 = (int) ((box.getY1()*yScale)+yShift);
			int x2 = (int) ((box.getX2()*xScale)+xShift);
			int y2 = (int) ((box.getY2()*yScale)+yShift);
			//System.out.println("x1:"+x1+", y1:"+(y2)+", x2:"+(x2-x1)+", y2:"+(y1-y2));
			g2d.drawImage(img,x1,y2,x2-x1,y1-y2,null);
		}
	}

	@Override
	public void setMinZoomL(int zoomLevel) {
		this.minZoom = zoomLevel;
	}

	@Override
	public void setMaxZoomL(int zoomLevel) {
		this.maxZoom = zoomLevel;
	}

	public boolean isInZoomLevel(int zoomLevel) {
		if (zoomLevel > maxZoom && maxZoom != 0) return false;
		return true;
	}

	@Override
	public boolean isHidden() {
		return hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	/*
	public static void main(String[] args) {
		// Schedule a job for the event-dispatching thread:
		// creating and showing this application's GUI.
		try {
			System.out.println("hej");
			RasterFil s = new RasterFil("..\\Oland_Terängkarta\\Oland.tif");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("hittar inte kartan");
		}
	}*/

}
