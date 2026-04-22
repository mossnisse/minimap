package main.coords;

public class TransverseMercatorStrategy implements ProjectionStrategy {
    private final double falseNorthing, falseEasting, lambdaZero, scale, aRoof;
    private final double e2, B, C, D;
    private final double beta1, beta2, beta3, beta4;
    private final double delta1, delta2, delta3, delta4;
    private final double Astar, Bstar, Cstar, Dstar;

    public TransverseMercatorStrategy(double fn, double fe, double cm, double scale, double axis, double flattening) {
        this.falseNorthing = fn;
        this.falseEasting = fe;
        this.lambdaZero = Math.toRadians(cm);
        this.scale = scale;

        // Math prep (Ported from your original Enum)
        double n = flattening / (2.0 - flattening);
        this.e2 = flattening * (2.0 - flattening);
        this.aRoof = axis / (1.0 + n) * (1.0 + n * n / 4.0 + n * n * n * n / 64.0);

        // Forward coefficients (beta)
        this.beta1 = n/2.0 - 2.0*n*n/3.0 + 5.0*n*n*n/16.0 + 41.0*n*n*n*n/180.0;
        this.beta2 = 13.0*n*n/48.0 - 3.0*n*n*n/5.0 + 557.0*n*n*n*n/1440.0;
        this.beta3 = 61.0*n*n*n/240.0 - 103.0*n*n*n*n/140.0;
        this.beta4 = 49561.0*n*n*n*n/161280.0;

        // Backward coefficients (delta)
        this.delta1 = n/2.0 - 2.0*n*n/3.0 + 37.0*n*n*n/96.0 - n*n*n*n/360.0;
        this.delta2 = n*n/48.0 + n*n*n/15.0 - 437.0*n*n*n*n/1440.0;
        this.delta3 = 17.0*n*n*n/480.0 - 37.0*n*n*n*n/840.0;
        this.delta4 = 4397.0*n*n*n*n/161280.0;

        // Ellipsoid coefficients
        this.B = (5.0*e2*e2 - e2*e2*e2)/6.0;
        this.C = (104.0*e2*e2*e2 - 45.0*e2*e2*e2*e2)/120.0;
        this.D = (1237.0*e2*e2*e2*e2)/1260.0;

        this.Astar = e2 + e2*e2 + e2*e2*e2 + e2*e2*e2*e2;
        this.Bstar = -(7.0*e2*e2 + 17.0*e2*e2*e2 + 30.0*e2*e2*e2*e2)/6.0;
        this.Cstar = (224.0*e2*e2*e2 + 889.0*e2*e2*e2*e2)/120.0;
        this.Dstar = -(4279.0*e2*e2*e2*e2)/1260.0;
    }

    @Override
    public Coordinate project(double lat, double lon) {
        double phi = Math.toRadians(lat);
        double lambda = Math.toRadians(lon);

        double phiStar = phi - Math.sin(phi) * Math.cos(phi) * (e2 +
                B * Math.pow(Math.sin(phi), 2) +
                C * Math.pow(Math.sin(phi), 4) +
                D * Math.pow(Math.sin(phi), 6));

        double deltaLambda = lambda - lambdaZero;
        double xiPrim = Math.atan(Math.tan(phiStar) / Math.cos(deltaLambda));
        double x = Math.cos(phiStar) * Math.sin(deltaLambda);
        double etaPrim = 0.5 * Math.log((1.0 + x) / (1.0 - x));

        double n = scale * aRoof * (xiPrim +
                beta1 * Math.sin(2 * xiPrim) * Math.cosh(2 * etaPrim) +
                beta2 * Math.sin(4 * xiPrim) * Math.cosh(4 * etaPrim) +
                beta3 * Math.sin(6 * xiPrim) * Math.cosh(6 * etaPrim) +
                beta4 * Math.sin(8 * xiPrim) * Math.cosh(8 * etaPrim)) + falseNorthing;

        double e = scale * aRoof * (etaPrim +
                beta1 * Math.cos(2 * xiPrim) * Math.sinh(2 * etaPrim) +
                beta2 * Math.cos(4 * xiPrim) * Math.sinh(4 * etaPrim) +
                beta3 * Math.cos(6 * xiPrim) * Math.sinh(6 * etaPrim) +
                beta4 * Math.cos(8 * xiPrim) * Math.sinh(8 * etaPrim)) + falseEasting;

        return new Coordinate(n, e);
    }

    @Override
    public Coordinate unproject(double n, double e) {
        double xi = (n - falseNorthing) / (scale * aRoof);
        double eta = (e - falseEasting) / (scale * aRoof);

        double xiPrim = xi -
                delta1 * Math.sin(2 * xi) * Math.cosh(2 * eta) -
                delta2 * Math.sin(4 * xi) * Math.cosh(4 * eta) -
                delta3 * Math.sin(6 * xi) * Math.cosh(6 * eta) -
                delta4 * Math.sin(8 * xi) * Math.cosh(8 * eta);

        double etaPrim = eta -
                delta1 * Math.cos(2 * xi) * Math.sinh(2 * eta) -
                delta2 * Math.cos(4 * xi) * Math.sinh(4 * eta) -
                delta3 * Math.cos(6 * xi) * Math.sinh(6 * eta) -
                delta4 * Math.cos(8 * xi) * Math.sinh(8 * eta);

        double phiStar = Math.asin(Math.sin(xiPrim) / Math.cosh(etaPrim));
        double deltaLambda = Math.atan(Math.sinh(etaPrim) / Math.cos(xiPrim));

        double latRad = phiStar + Math.sin(phiStar) * Math.cos(phiStar) * (Astar +
                Bstar * Math.pow(Math.sin(phiStar), 2) +
                Cstar * Math.pow(Math.sin(phiStar), 4) +
                Dstar * Math.pow(Math.sin(phiStar), 6));

        double lonRad = lambdaZero + deltaLambda;

        return new Coordinate(Math.toDegrees(latRad), Math.toDegrees(lonRad));
    }
}