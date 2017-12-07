import geometry.BoundingBox;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class TNGRaster implements Layer {
	private String fileName, name;
	private Color color;
	private boolean hidden;
	private int maxZoom, minZoom;
	Image[] chunks;
	int[] chunknr;
	
	public TNGRaster(String fileName) throws IOException {
		this.fileName=fileName;
		this.name = fileName;
		chunks = new BufferedImage[2000];
		chunknr = new int[2000];
		maxZoom = 0;
		minZoom = 0;
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
	public void setMinZoomL(int zoomLevel) {
		this.minZoom = zoomLevel;
	}

	@Override
	public void setMaxZoomL(int zoomLevel) {
		this.maxZoom = zoomLevel;
	}

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		if (zoomLevel > maxZoom && maxZoom != 0) return false;
		return true;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}
	
	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds) {
		int chunkSize = 50000;
		int imageWidth = (int) (chunkSize*xScale);
		int imageHeigth = (int) (chunkSize*yScale);
		//int chunkNum = bounds.getWidth()/chunkSize * bounds.getHeight()/chunkSize;
		//System.out.println(box+"number cunks: "+chunkNum);
		//String[] names = new String[chunkNum];
		System.out.println(bounds);  // TODO: print trace
		int xStart = (bounds.getX1()/chunkSize)*chunkSize;
		int xStop = (bounds.getX2()/chunkSize)*chunkSize;
		int yStart= (bounds.getY1()/chunkSize)*chunkSize;
		int yStop= (bounds.getY2()/chunkSize)*chunkSize;
		
		for (int ix = xStart; ix<=xStop; ix+=chunkSize) {
			for (int iy = yStart; iy<=yStop; iy+=chunkSize) {
				//System.out.println("Ystart "+yStart+"YStop" +yStop+ "iy"+ iy +"ix"+ix );
				int num = Coordinates.getRUBIN50N(ix,iy);
				Image img = null;
				if ( chunks[num] != null) {
					img = chunks[num];
				}
				else {
					String name = Coordinates.getRUBIN50(ix,iy);

					try {
						System.out.println("open: "+fileName+name+"(0).jpg");  //TODO print trace
						img = ImageIO.read(new File(fileName+name+"(0).jpg"));
						chunks[num] = img;
					} catch (IOException e) {
						System.err.println("cant open: "+fileName+name+"(0).jpg" );
					}
				}
				if(img!=null) {
					int x1 = (int)  ((ix*xScale)+xShift);
					int y1 = (int)  ((iy*yScale)+yShift);
					g2d.drawImage(img,x1,y1+imageHeigth,imageWidth,-imageHeigth,null);
				}
			}
		}
	}

	@Override
	public boolean isHidden() {
		return hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

}
