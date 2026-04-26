package main.coords;

public class Extent {
    public Coordinate c1, c2;

    public Extent(double nStart, double eStart, double nEnd, double eEnd) {
        this.c1 = new Coordinate(nStart, eStart);
        this.c2 = new Coordinate(nEnd, eEnd);
    }

    public Extent(Coordinate c1, Coordinate c2) {
        this.c1 = c1;
        this.c2 = c2;
    }

    public boolean isInside(Coordinate c) {
        return c1.getEast() < c.getEast() && c.getEast() < c2.getEast()
                && c1.getNorth()  < c.getNorth()  && c.getNorth()  < c2.getNorth() ;
    }

    public boolean isInside(Extent b) {
        return c1.getEast() < b.c1.getEast() && c2.getEast() > b.c2.getEast()
                && c1.getNorth() < b.c1.getNorth() && c2.getNorth() > b.c2.getNorth();
    }

    /*
    public boolean intersects(Extent b) {
        return (Math.abs(2* (getX1() - b.getX1())+(getWidth() - b.getWidth()))  < (getWidth() + b.getWidth())) &&
                (Math.abs(2* (getY1() - b.getY1())+(getHeight() - b.getHeight())) < (getHeight() + b.getHeight()));
    }*/

    public Coordinate getMidlePoint() {
        return new Coordinate(
                c1.getNorth() + (c2.getNorth() - c1.getNorth()) / 2,
                c1.getEast() + (c2.getEast() - c1.getEast()) / 2
        );
    }

    public Extent expand(double amount) {
        return new Extent(
                c1.getNorth() - amount,
                c1.getEast() - amount,
                c2.getNorth() + amount,
                c2.getEast() + amount
        );
    }

    public double getWidth() {
        return Math.abs(c2.getEast() - c1.getEast());
    }

    public double getHeight() {
        return Math.abs(c2.getNorth() - c1.getNorth());
    }

    public void focus(Coordinate coord) {
        Coordinate m = getMidlePoint();
        double sx = m.getEast() - coord.getEast();
        double sy = m.getNorth() - coord.getNorth();
        c1 = new Coordinate(c1.getNorth() - sy, c1.getEast() - sx);
        c2 = new Coordinate(c2.getNorth() - sy, c2.getEast() - sx);
    }
}