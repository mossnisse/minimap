public enum CoordSystem {
	RT90("RT 90 2.5 gon V 0:-15.","Sweden",CoordType.TransverseMercator,-667.711,1500064.274,15.0 + 48.0 / 60.0 + 22.624306 / 3600.0,1.00000561024,6378137.0,1.0 / 298.257222101), 
	Sweref99TM("Sweref99TM", "Sweden",CoordType.TransverseMercator,0.0,500000.0,15.00,0.9996,6378137.0,1.0 / 298.257222101), 
	WGS84("WGS84","Global",CoordType.Sphaerical2D), 
	bessel("Bessel 1841","Global",CoordType.Sphaerical2D), 
	Sweref99("Sweref99","Global",CoordType.Sphaerical2D);
		
	static public enum CoordType {
		TransverseMercator, Sphaerical2D
	}
		
	public String name;
	public String country;
	public CoordType type;
	public double falseNorthing;
	public double falseEasting;
	public double centralMeridian;
	public double scale;
	public double axis;
	public double flattening;
	
	//limits what is valid values; 
	public double Nmin;
	public double Nmax;
	public double Emin;
	public double Emax;
	
	//rt90
	//sweref99TM 
	//wgs84 // -90 <= N <= 90, -180 <= E <= 180
	
	// Gaus Kryger formula stuff
	public double e2;
	public double n ;
	public double a_roof;
	public double A;
	public double B;
	public double C;
	public double D;
	public double beta1;
	public double beta2;
	public double beta3;
	public double beta4;
	public double phi;
    public double lambda;
    public double lambda_zero;
    double Astar;
    double Bstar;
    double Cstar;
    double Dstar;
    
	CoordSystem() {	
	}
	
	CoordSystem(String name, String country, CoordType type) {
		this.name = name;
		this.country = country;
		this.type = type;
	}

	CoordSystem(String name, String country, CoordType type, double falseNorthing, double falseEasting, double centralMeridian, double scale, double axis, double flattening) {
		this.name = name;
		this.country = country;
		this.type = type;
		this.falseNorthing = falseNorthing;
		this.falseEasting = falseEasting;
		this.centralMeridian = centralMeridian;
		this.scale = scale;
		this.axis = axis;
		this.flattening = flattening;
		
		// Gaus Kryger formula stuff
		e2 = flattening * (2.0 - flattening);
		n = flattening / (2.0 - flattening);
		a_roof = axis / (1.0 + n) * (1.0 + n * n / 4.0 + n * n * n * n / 64.0);
		A = e2;
		B = (5.0 * e2 * e2 - e2 * e2 * e2) / 6.0;
		C = (104.0 * e2 * e2 * e2 - 45.0 * e2 * e2 * e2 * e2) / 120.0;
		D = (1237.0 * e2 * e2 * e2 * e2) / 1260.0;
		Astar = e2 + e2 * e2 + e2 * e2 * e2 + e2 * e2 * e2 * e2;
	    Bstar = -(7.0 * e2 * e2 + 17.0 * e2 * e2 * e2 + 30.0 * e2 * e2 * e2 * e2) / 6.0;
	    Cstar = (224.0 * e2 * e2 * e2 + 889.0 * e2 * e2 * e2 * e2) / 120.0;
	    Dstar = -(4279.0 * e2 * e2 * e2 * e2) / 1260.0;
		beta1 = n / 2.0 - 2.0 * n * n / 3.0 + 5.0 * n * n * n / 16.0 + 41.0 * n * n * n * n / 180.0;
		beta2 = 13.0 * n * n / 48.0 - 3.0 * n * n * n / 5.0 + 557.0 * n * n * n * n / 1440.0;
		beta3 = 61.0 * n * n * n / 240.0 - 103.0 * n * n * n * n / 140.0;
		beta4 = 49561.0 * n * n * n * n / 161280.0;
		lambda_zero = centralMeridian;
	}
	
	public String getName() {
		return name;
	}
}

