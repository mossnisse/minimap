package gis.shapefile;

/** One column descriptor from a dBASE (.dbf) attribute table. */
public class DbfField {
	public final String name;
	public final char type; // C=character, N=numeric, F=float, D=date, L=logical
	public final int length;
	public final int decimalCount;

	public DbfField(String name, char type, int length, int decimalCount) {
		this.name = name;
		this.type = type;
		this.length = length;
		this.decimalCount = decimalCount;
	}

	public String toString() {
		return name + " (" + type + ", " + length +
				(decimalCount > 0 ? "." + decimalCount : "") + ")";
	}
}