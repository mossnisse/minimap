import geometry.BoundingBox;
import coords.*;
import java.awt.Color;
import java.awt.Graphics2D;

public class WMSLayer implements Layer{
	private final String url = "http://hades.slu.se/lm/topowebb/wms/v1/";
	private String name;
	private boolean hidden;
	private Color color;
	private int minZoomL;
	private int maxZoomL;
	private CoordSystem cs = CoordSystem.SWEREF99TM;

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
		this.minZoomL = zoomLevel;
		
	}

	@Override
	public void setMaxZoomL(int zoomLevel) {
		this.maxZoomL = zoomLevel;
	}

	@Override
	public boolean isInZoomLevel(int zoomLevel) {
		return (zoomLevel > maxZoomL && maxZoomL != 0);
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public boolean isHidden() {
		return hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	@Override
	public void setCRS(CoordSystem cs) {
		this.cs = cs;
	}

	@Override
	public CoordSystem getCRS() {
		return cs;
	}
	
	@Override
	public void draw(Graphics2D g2d, double xShift, double xScale, double yShift, double yScale, BoundingBox bounds)
			throws Exception {
		// TODO Auto-generated method stub
		// url  
		String str = "?SERVICE=WMS&GetMap&Layer=topowebbkartan";
		//bounding box 
		// maps size in pixel
		// bounding box in Sweref99TM
		//cs.getSRS();
		
	}

}
