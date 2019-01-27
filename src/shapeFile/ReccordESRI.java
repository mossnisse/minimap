package shapeFile;

public class ReccordESRI {
	public PointESRI point;
	public PolygonESRI polygon;
	public dbfRecord DBF;
	
	public ReccordESRI(PointESRI point, dbfRecord DBF) {
		polygon = null;
		this.point = point;
		this.DBF = DBF;	
	}
	
	public ReccordESRI(PolygonESRI polygon, dbfRecord DBF) {
		point = null;
		this.polygon = polygon;
		this.DBF = DBF;
	}
	
	public String toString() {
		return "["+polygon+"]["+point+"]["+DBF+"]\n";
	}
}
