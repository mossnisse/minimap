package gis.shapefile;

/**
 * The shape types defined by the ESRI shapefile specification.
 * Z (3D) and M (measured) variants carry the same X/Y data as their base
 * type followed by extra ordinates, so a reader that only needs X/Y can
 * parse them via {@link #base()}.
 */
public enum ShapeType {
	NULL(0),
	POINT(1),
	POLYLINE(3),
	POLYGON(5),
	MULTIPOINT(8),
	POINT_Z(11),
	POLYLINE_Z(13),
	POLYGON_Z(15),
	MULTIPOINT_Z(18),
	POINT_M(21),
	POLYLINE_M(23),
	POLYGON_M(25),
	MULTIPOINT_M(28);

	private final int code;

	ShapeType(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	/** The X/Y-only type this type's records start with. */
	public ShapeType base() {
		switch (this) {
			case POINT: case POINT_Z: case POINT_M:
				return POINT;
			case POLYLINE: case POLYLINE_Z: case POLYLINE_M:
				return POLYLINE;
			case POLYGON: case POLYGON_Z: case POLYGON_M:
				return POLYGON;
			case MULTIPOINT: case MULTIPOINT_Z: case MULTIPOINT_M:
				return MULTIPOINT;
			default:
				return NULL;
		}
	}

	/** @return the type with this spec code, or null if the code is unknown. */
	public static ShapeType fromCode(int code) {
		for (ShapeType t : values()) {
			if (t.code == code) return t;
		}
		return null;
	}
}