package gis.layers;

import gis.coords.*;
import gis.core.Layer;
import gis.core.MapCanvas;
import java.awt.*;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import gis.geometry.Extent;
import gis.shapefile.DataInputStreamSE;

public class TNGPointFileLayer extends Layer {
	private String fileName;
	private Locality[] localities = new Locality[0];
	private Locality[] sourceLocalities;
	private final MapCanvas mapCanvas;
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
		this.mapCanvas = null;
		readFile();
	}
	
	public TNGPointFileLayer(ArrayList<Coordinate> loca, ArrayList<String> names, String name) {
		this(loca, names, name, null, CoordSystem.SWEREF99TM);
	}

	/** Creates an in-memory layer whose source coordinates can be reprojected with the canvas. */
	public TNGPointFileLayer(ArrayList<Coordinate> loca, ArrayList<String> names, String name,
	                         MapCanvas mapCanvas, CoordSystem sourceCRS) {
		super(name, false, sourceCRS);
		this.mapCanvas = mapCanvas;
		sourceLocalities = new Locality[loca.size()];
		int i=0;
		for(Coordinate loci:loca) {
			sourceLocalities[i]=new Locality(loci, names.get(i));
			i++;
		}
		reproject();
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
			sourceLocalities = localities;
		}
	}

	private void reproject() {
		if (sourceLocalities == null) return;
		localities = new Locality[sourceLocalities.length];
		for (int i = 0; i < sourceLocalities.length; i++) {
			Locality source = sourceLocalities[i];
			Coordinate projected = (mapCanvas == null)
					? new Coordinate(source)
					: getCRS().convertTo(source, mapCanvas.getCRS());
			Locality copy = new Locality(projected, source.getName());
			copy.setId(source.getId());
			localities[i] = copy;
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
		reproject();
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
