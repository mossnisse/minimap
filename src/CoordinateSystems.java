import java.util.EnumMap;

public class CoordinateSystems {
	static public enum CoordSystems {
	    RT90, Sweref99TM, WGS84, bessel, Sweref99
	}
	
	static public enum CoordType {
		TransverseMercator, Sphaerical2D
	}
	
	public class CoordinateSystem {
		public String name;
		public String country;
		public CoordType type;
		public double falseNorthing;
		public double falseEasting;
		public double centralMeridian;
		public double scale;
		public double axis;
		public double flattening;
		
		CoordinateSystem(String name, String country, CoordType type, double falseNorthing, double falseEasting, double centralMeridian, double scale, double axis, double flattening) {
			this.name = name;
			this.country = country;
			this.type = type;
			this.falseNorthing = falseNorthing;
			this.falseEasting = falseEasting;
			this.centralMeridian = centralMeridian;
			this.scale = scale;
			this.axis = axis;
			this.flattening= flattening;
		}
		
		CoordinateSystem(String name, String country, CoordType type) {
			this.name = name;
			this.country = country;
			this.type = type;
		}
	}
	
	private static EnumMap<CoordSystems,CoordinateSystem> coordsys = new EnumMap<CoordSystems,CoordinateSystem>(CoordSystems.class);
	
	public CoordinateSystems() {
		coordsys.put(CoordSystems.RT90,new CoordinateSystem("RT 90 2.5 gon V 0:-15.", "Sweden",CoordType.TransverseMercator
				,-667.711,1500064.274,15.0 + 48.0 / 60.0 + 22.624306 / 3600.0,1.00000561024,6378137.0,1.0 / 298.257222101));
		coordsys.put(CoordSystems.Sweref99TM,new CoordinateSystem("Sweref99TM", "Sweden",CoordType.TransverseMercator
				,0.0,500000.0,15.00,0.9996,6378137.0,1.0 / 298.257222101));
		coordsys.put(CoordSystems.WGS84,new CoordinateSystem("WGS84","Global",CoordType.Sphaerical2D));
		coordsys.put(CoordSystems.WGS84, new CoordinateSystem("Bessel 1841","Global",CoordType.Sphaerical2D));
		coordsys.put(CoordSystems.WGS84, new CoordinateSystem("Sweref99","Global",CoordType.Sphaerical2D));
	}
	
	static CoordinateSystem getType(CoordSystems c) {
		return  coordsys.get(c);
	}
	
	
	
}

