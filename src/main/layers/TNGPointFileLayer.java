package main.layers;

import main.coords.*;
import main.core.Layer;
import java.awt.*;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import main.geometry.Extent;
import main.shapeFile.DataInputStreamSE;

public class TNGPointFileLayer extends Layer {
	private String fileName;
	private Locality[] localities;
	static final Stroke LINE_STROKE = new BasicStroke(2);
	
	public static class Locality extends Coordinate {
		public String name;
		public int id;
		
		public Locality(int x, int y, String name) {
			super(x,y);
			this.name = name;
		}
		
		public Locality(Coordinate c, String name) {
			super(c);
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public int getId() { return id; }

		public void setId(int id) { this.id = id; }
		
		public String toString() {
			String cs = super.toString();
			return "(" + cs + ", " + name + ")";
		}
	}
	
	public TNGPointFileLayer(String fileName) throws IOException {
		super(fileName, false, CoordSystem.SWEREF99TM);
		this.fileName = fileName;
		readFile();
	}
	
	public TNGPointFileLayer(ArrayList<Coordinate> loca, ArrayList<String> names, String name) {
		super(name, false, CoordSystem.SWEREF99TM);
		localities = new Locality[loca.size()];
		int i=0;
		for(Coordinate loci:loca) {
			localities[i]=new Locality(loci, names.get(i));
			i++;
		}
	}
	
	private void readFile() throws IOException {
		 try (DataInputStreamSE in =
			        new DataInputStreamSE(
			          new BufferedInputStream(
			            new FileInputStream(fileName))))
		 {
			in.readInt();
			int nrRecords = in.readInt();

			int nameLength = in.readInt();
			localities =new Locality[nrRecords];
			for( int i = 0; i<nrRecords; i++) {
				String name = in.readString(nameLength).trim();
				int y = in.readInt();
				int x = in.readInt();
				localities[i] = new Locality(x, y, name);
			}
		}
	}
	
	public Locality[] getLocalities() {
		return localities;
	}
	
	public String toString() {
		String ans = getName() + ": ";
		for(Locality coord: localities) {
			ans = ans + coord.toString();
		}
		return ans;
	}
	
	public int size() {
		return localities.length;
	}

	@Override
	public Extent getBoundaries() {
		if (localities.length == 0) return null;

		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
		double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

		for (Locality coord : localities) {
			if (coord.getEast() < minX) minX = coord.getEast();
			if (coord.getEast() > maxX) maxX = coord.getEast();
			if (coord.getNorth() < minY) minY = coord.getNorth();
			if (coord.getNorth() > maxY) maxY = coord.getNorth();
		}
		return new Extent(minY, minX, maxY, maxX);
	}

	@Override
	public void invalidateCache() {

	}

	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, Extent bounds) {
		if (isHidden()) return;

		Stroke s = g2d.getStroke();
		g2d.setStroke(LINE_STROKE);
		g2d.setColor(getColor());

		for (Locality coord : localities) {
			// Standard Mapping: X -> Horizontal, Y -> Vertical
			int x = (int) ((coord.getEast() * xScale) + xShift);
			int y = (int) ((coord.getNorth() * yScale) + yShift);

			// Draw a crosshair target
			g2d.drawOval(x - 6, y - 6, 12, 12);
			g2d.drawLine(x, y + 10, x, y - 10);
			g2d.drawLine(x + 10, y, x - 10, y);
		}
		g2d.setStroke(s);
	}
}