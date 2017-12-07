import geometry.Point;


public class Coordinates {
	private Double north, east;
	
	public Coordinates(double north, double east) {
		this.north = north;
		this.east = east;
	}
	
	public Coordinates(String rubin) {
		setRUBIN(rubin);
	}
	
	private Double atanh(Double z) {
		return Math.log((1+z)/(1-z))/2;
	}
	
	public void setCoordinate(double north, double east) {
		this.north = north;
		this.east = east;
	}
	
	public double getNorth() {
		return north;
	}
	
	public double getEast() {
		return east;
	}
	
	public Coordinates convertRT90() {
		Double k0xa = 6.3674848719179137e6;
		Double FN = -667.711;
		Double FE = 1.500064274e6;
		Double A = 0.006694380021;
		Double B = 0.00003729560209;
		Double C = 2.592527517e-7;
		Double Dp = 1.971698945e-9;
		Double lambdanoll = 0.27587170754507245;
		Double beta1 = 0.0008377318249;
		Double beta2 = 7.608527793e-7;
		Double beta3 = 1.197638020e-9;
		Double beta4 = 2.443376245e-12;
		
		Double Phi = (north/180)*Math.PI;
		Double deltalambda= (east/180)*Math.PI-lambdanoll;
		
		Double Phistar = Phi-Math.sin(Phi)*Math.cos(Phi)*(A+B*Math.sin(2*Phi)+C*Math.sin(4*Phi)+Dp*Math.sin(6*Phi));

		Double xifjutt = Math.atan(Math.tan(Phistar)/Math.cos(deltalambda));
		Double etafjutt = atanh(Math.cos(Phistar)*Math.sin(deltalambda));
				
		Double rnorth =  k0xa*(xifjutt+beta1*Math.sin(2*xifjutt)*Math.cosh(2*etafjutt)+beta2*Math.sin(4*xifjutt)*Math.cosh(4*etafjutt)
						+beta3*Math.sin(6*xifjutt)*Math.cosh(6*etafjutt)+beta4*Math.sin(8*xifjutt)*Math.cosh(8*etafjutt))+FN;
		
		Double reast = k0xa*(etafjutt+beta1*Math.cos(2*xifjutt)*Math.sinh(2*etafjutt)+beta2*Math.cos(4*xifjutt)*Math.sinh(4*etafjutt)
					+beta3*Math.cos(6*xifjutt)*Math.sinh(6*etafjutt)+beta4*Math.cos(8*xifjutt)*Math.sinh(8*etafjutt))+FE;
		return new Coordinates(Math.round(rnorth), Math.round(reast));
	}
	
	public Coordinates convertWGS84() {
		double x = north;
	    double y = east;
	    
	    double xi = (x  + 667.711) / 6367484.87;
	    double ny = (y - 1500064.274) / 6367484.87;
	    
	    double s1 = 0.0008377321684;
	    double s2 = 5.905869628E-8;
	    double xp = xi - s1 * Math.sin(2*xi) * Math.cosh(2*ny) - s2 * Math.sin(4*xi) * Math.cosh(4*ny);
	    double np = ny - s1 * Math.cos(2*xi) * Math.sinh(2*ny) - s2 * Math.cos(4*xi) * Math.sinh(4*ny);
	    
	    double reast = (0.2758717076 + Math.atan(Math.sinh(np)/Math.cos(xp)))*180/Math.PI;
	    
	    double qs = Math.asin(Math.sin(xp)/Math.cosh(np));
	    double rnorth = (qs + Math.sin(qs)*Math.cos(qs)*(0.00673949676 -0.00005314390556 * Math.pow(Math.sin(qs),2)) + 5.74891275E-7 * Math.pow(Math.sin(qs),4)) * 180/Math.PI;
	    return new Coordinates(rnorth,reast);
	}
	
	static private int alphaNum(Character a) {
		if(Character.isDigit(a)) {
			return Character.getNumericValue(a)-Character.getNumericValue('0');
		} else {
			if (Character.isLowerCase(a)) {
				return Character.getNumericValue(a)-Character.getNumericValue('a');
			} else {
				return Character.getNumericValue(a)-Character.getNumericValue('A');
			}
		}
	}
	
	static private String numAlphaU(int n) {
		int i = n+(char)'A';
		return Character.toString ((char) i);
	}
	
	static private String numAlphaL(int n) {
		int i = n+(char)'a';
		return Character.toString ((char) i);
	}
	
	
	public void setRUBIN(String rubin) {
		//System.out.println("Rubin before remove \""+rubin+" \"");
		rubin = rubin.replaceAll("\\s|\u00A0","");
		if(!Character.isDigit(rubin.charAt(1))) {
			rubin = "0"+rubin;
		} 
		//System.out.println("Rubin after fix \""+rubin+" \"");
		//System.out.println("strange char \""+(int)rubin.charAt(3)+"\"");
		
		int a = Integer.parseInt(rubin.substring(0,2));
		int b = alphaNum(rubin.charAt(2));
		int c = alphaNum(rubin.charAt(3));
		int d = alphaNum(rubin.charAt(4));
		
		//System.out.println("a:"+a+", b:"+b+", c:"+c+", d:"+d);
		north = 6052500+a*50000+c*5000.0;
		east = 1202500+b*50000+d*5000.0;
	}
	
	public String getRUBIN() {
		int n = (int)Math.round(north)-6050000;
		int e = (int)Math.round(east)-1200000;
		int n1 = n/50000;
		int n2 = (n%50000)/5000;
		String es1 = numAlphaU(e/50000);
		String es2= numAlphaL((e%50000)/5000);
		return n1+es1+n2+es2;
	}
	
	public static String getRUBIN(Point p) {
		int n = (int)Math.round(p.getY())-6050000;
		int e = (int)Math.round(p.getX())-1200000;
		int n1 = n/50000;
		int n2 = (n%50000)/5000;
		String es1 = numAlphaU(e/50000);
		String es2= numAlphaL((e%50000)/5000);
		return n1+es1+n2+es2;
	}
	
	static public String getRUBIN50(int east, int north) {
		int n1 = (north-6050000)/50000;
		String es1 = numAlphaU((east-1200000)/50000);
		return n1+es1;
	}
	
	static public int getRUBIN50N(int east, int north) {
		int n1 = (north-6050000)/50000;
		int es1 = (east-1200000)/50000;
		return es1*33+n1;
	}
	
	public static void main(String[] args) {
		Coordinates coord = new Coordinates(7030372, 1595257);
		//Coordinates coord2 = coord.convertRT90();
		//coord.setRUBIN("27K9i");
		//System.out.println("north: "+coord.getNorth()+", "+coord.getEast());
		System.out.println(coord.getRUBIN());
	}
	
}

