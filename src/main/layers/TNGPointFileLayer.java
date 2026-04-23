package main.layers;

import main.coords.*;
import main.core.Layer;
import main.geometry.BoundingBox;
import java.awt.*;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import main.shapeFile.DataInputStreamSE;

public class TNGPointFileLayer implements Layer {
	private String fileName, name;
	private Color color = Color.BLACK;
	private int maxZoom = 0; // 0 indicates unset
	private int minZoom = 0;
	private Locality[] localities;
	private boolean hidden;
	private CoordSystem cs;
	private int nameLength;
	
	public static class Locality extends Coordinate {
		public String name;
		
		public Locality(int x, int y, String name) {
			super(x,y);
			this.name = name;
		}
		
		public Locality(Coordinate c, String name) {
			super(c);
			this.name = name;
		}
		
		public double dist(int x, int y) {
			Coordinate c = new Coordinate(y, x);
			return  Math.round(distanceTM(c)/100)/10.0; //Math.round(
		}
		
		public String direction(double x, double y) {
			double v = Math.atan2(getNorth()-y, getEast()-x);
			String rikt = "";
			if 	    (v>7*Math.PI/8) rikt = "V";
			else if (v>5*Math.PI/8) rikt = "NV";
			else if (v>3*Math.PI/8) rikt = "N";
			else if (v>1*Math.PI/8) rikt = "NE";
			else if (v>-1*Math.PI/8) rikt = "E";
			else if (v>-3*Math.PI/8) rikt = "SE";
			else if (v>-5*Math.PI/8) rikt = "S";
			else if (v>-7*Math.PI/8) rikt = "SV";
	 		return rikt;
		}
		
		public String getName() {
			return name;
		}
		
		public String toString() {
			String cs = super.toString();
			return "(" + cs + ", " + name + ")";
		}
	}
	
	public TNGPointFileLayer(String fileName) throws IOException {
		this.fileName = fileName;
		this.name = fileName;
		this.cs = CoordSystem.SWEREF99TM;
		readFile();
	}
	
	public TNGPointFileLayer(ArrayList<Coordinate> loca, ArrayList<String> names, String name) {
		this.name = name;
		localities = new Locality[loca.size()];
		int i=0;
		for(Coordinate loci:loca) {
			localities[i]=new Locality(loci, names.get(i));
			i++;
		}
	}
	
	private void readFile() throws IOException {
		 DataInputStreamSE in =
			        new DataInputStreamSE(
			          new BufferedInputStream(
			            new FileInputStream(fileName)));
		in.readInt();
		int nrRecords = in.readInt();

		nameLength = in.readInt();
		localities = new Locality[nrRecords];
		for (int i = 0; i < nrRecords; i++) {
			String name = in.readString(nameLength).trim();
			int y = in.readInt();
			int x = in.readInt();
			localities[i] = new Locality(x,y,name);
		}
		in.close();
	}
	
	public Locality[] getLocalities() {
		return localities;
	}

	@Override
	public void setColor(Color color) {
		this.color = (color != null) ? color : Color.BLACK;
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
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		if (hidden) return;

		Stroke s = g2d.getStroke();
		g2d.setStroke(new BasicStroke(2));
		g2d.setColor(color);

		for (Locality koord : localities) {
			// Standard Mapping: X -> Horizontal, Y -> Vertical
			int x = (int) ((koord.getEast() * xScale) + xShift);
			int y = (int) ((koord.getNorth() * yScale) + yShift);

			// Draw a crosshair target
			g2d.drawOval(x - 6, y - 6, 12, 12);
			g2d.drawLine(x, y + 10, x, y - 10);
			g2d.drawLine(x + 10, y, x - 10, y);
		}
		g2d.setStroke(s);
	}

	@Override
	public void setMinZoomL(int zoomLevel) { this.minZoom = zoomLevel; }

	@Override
	public void setMaxZoomL(int zoomLevel) { this.maxZoom = zoomLevel; }

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		boolean meetsMin = (minZoom == 0 || zoomLevel >= minZoom);
		boolean meetsMax = (maxZoom == 0 || zoomLevel <= maxZoom);
		return meetsMin && meetsMax;
	}

	@Override
	public boolean isHidden() {
		return hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public BoundingBox getBounds() {
		if (localities.length == 0) return new BoundingBox(0,0,0,0);

		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

		for (Locality koord : localities) {
			if (koord.getEast() < minX) minX = (int) Math.round( koord.getEast());
			if (koord.getEast() > maxX) maxX = (int) Math.round(koord.getEast());
			if (koord.getNorth() < minY) minY = (int) Math.round(koord.getNorth());
			if (koord.getNorth() > maxY) maxY = (int) Math.round(koord.getNorth());
		}
		// Order: minX, minY, maxX, maxY
		return new BoundingBox(minX, minY, maxX, maxY);
	}
	
	public String toString() {
		String ans = name+": ";
		for(Locality koord:localities) {
			ans=ans+koord.toString();
		}
		return ans;
	}
	
	public int size() {
		return localities.length;
	}

	@Override
	public void setCRS(CoordSystem cs) {
		this.cs = cs;
	}

	@Override
	public CoordSystem getCRS() {
		return cs;
	}
}