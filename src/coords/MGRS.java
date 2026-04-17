package coords;

import java.util.Locale;

// Todo: write an valid checker

public class MGRS {
    private static char mgrsNumToAlpha(int num) {
        int code = num + 'A';
        if (code >= 'I') {
            code++;
        }
        if (code >= 'O') {
            code++;
        }
        return (char) code;
    }

    private static int mgrsAlphaToNum(char c) {
        char upper = Character.toUpperCase(c);
        int res = upper - 'A';
        if (upper > 'O') {
            return res - 2;
        } else if (upper > 'I') {
            return res - 1;
        }
        return res;
    }

    // Helper: Reverse the 1-indexed UTM band logic (e.g. 'C' -> 1)
    private static int utmNumToAlphaRev(char c) {
        int code = c;
        if (code > 'O') code--;
        if (code > 'I') code--;
        return code - 66;
    }

    // convert wgs84 to and MGRS string
    public static String fromUTM(UTM utm) {
        if (utm == null) return "OUTSIDE UTM RANGE";

        int zone = Integer.parseInt(utm.gzd.replaceAll("[^0-9]", ""));
        double e = utm.easting;
        double n = utm.northing;

        // Identify the 100km Square Column (East-West)
        int set = zone % 3;
        int e100k = (int) Math.floor(e / 100000);
        int colBase = 0;

        // colBase relies on A=0, J=8, S=16
        if (set == 1) colBase = mgrsAlphaToNum('A');
        else if (set == 2) colBase = mgrsAlphaToNum('J');
        else if (set == 0) colBase = mgrsAlphaToNum('S');

        char columnId = mgrsNumToAlpha(colBase + e100k - 1);

        // Identify the 100km Square Row (North-South)
        // FIX: Changed mgrsNumToAlpha('F') to mgrsAlphaToNum('F')
        int rowBase = (zone % 2 != 0) ? mgrsAlphaToNum('A') : mgrsAlphaToNum('F');

        // The northing math works perfectly for both hemispheres because
        // the Southern Hemisphere False Northing (10,000,000) is a clean multiple of 2,000,000 (20 * 100k).
        int n100k = (int) Math.floor(n / 100000) % 20;

        char rowId = mgrsNumToAlpha((rowBase + n100k) % 20);

        // Calculate final numerical values
        int finalE = (int) Math.round(e % 100000);
        int finalN = (int) Math.round(n % 100000);

        return String.format(Locale.US, "%s%c%c%05d%05d", utm.gzd, columnId, rowId, finalE, finalN);
    }

    /**
     * Parses an MGRS string and returns a WGS84 Coordinates object.
     * Example input: "33V UC 12345 67890" or "33VUC1234567890"
     */
    public static UTM toUTM(String mgrsStr) {
        // Clean the string
        String cleanStr = mgrsStr.replaceAll("\\s+", "").toUpperCase();
        if (cleanStr.length() < 5) throw new IllegalArgumentException("Invalid MGRS string");

        // Extract components
        // Find where the letters start (usually index 1 or 2)
        int firstLetterIdx = Character.isLetter(cleanStr.charAt(1)) ? 1 : 2;
        String zonePart = cleanStr.substring(0, firstLetterIdx);
        int zone = Integer.parseInt(zonePart);
        char latBand = cleanStr.charAt(firstLetterIdx);
        String gzd = zonePart + latBand;

        // Ensure it's a valid UTM band (Polar UPS areas not supported in this basic parser)
        if (latBand < 'C' || latBand > 'X') throw new IllegalArgumentException("Unsupported UTM Latitude Band");

        // Extract the 100km square letters (e.g. 'U' and 'C')
        char colLetter = cleanStr.charAt(firstLetterIdx + 1);
        char rowLetter = cleanStr.charAt(firstLetterIdx + 2);

        // Extract precision coordinates (e.g. '12345' and '67890')
        String numPart = cleanStr.substring(firstLetterIdx + 3);
        if (numPart.length() % 2 != 0) throw new IllegalArgumentException("Invalid MGRS numerical part length");
        int precisionLength = numPart.length() / 2;

        String eStr = numPart.substring(0, precisionLength);
        String nStr = numPart.substring(precisionLength);

        // Scale precision back up to meters (e.g. '123' becomes '12300')
        double eMeters = Double.parseDouble(eStr) * Math.pow(10, 5 - precisionLength);
        double nMeters = Double.parseDouble(nStr) * Math.pow(10, 5 - precisionLength);

        // Calculate Easting
        // MGRS Columns repeat every 3 zones: A, J, S
        int setCol = zone % 3;
        int e100kBase = (setCol == 1) ? mgrsAlphaToNum('A') :
                (setCol == 2) ? mgrsAlphaToNum('J') : mgrsAlphaToNum('S');

        // Number of 100km steps from the base
        int e100kSteps = mgrsAlphaToNum(colLetter) - e100kBase;
        // Wrap around logic if negative
        if (e100kSteps < 0) e100kSteps += 8; // MGRS columns (East-West) are groups of 8 per zone set

        // Total UTM Easting
        double utmEasting = (e100kSteps + 1) * 100000.0 + eMeters;

        // Calculate Northing
        // MGRS Rows start at A or F and repeat every 2,000,000 meters (20 letters * 100k)
        int rowBase = (zone % 2 != 0) ? mgrsAlphaToNum('A') : mgrsAlphaToNum('F');
        int n100kSteps = mgrsAlphaToNum(rowLetter) - rowBase;
        if (n100kSteps < 0) n100kSteps += 20;

        double utmNorthing = n100kSteps * 100000.0 + nMeters;

        // Resolving the 2,000km ambiguity using actual Latitude
        // We calculate the minimum possible northing for this UTM Latitude Band.
        // 'C' starts at -80 deg. Each band is 8 degrees.
        int bandIndex = utmNumToAlphaRev(latBand); // 1-indexed (C=1, D=2, etc.)
        double minLatitudeDeg = (bandIndex * 8.0) - 88.0;

        // Convert this rough minimum latitude to rough WGS84 northing
        // 1 deg latitude ? 111,132 meters (WGS84 average)
        // We add a safety buffer so we don't accidentally pick a band too low.
        double minNorthingEstimate = (latBand < 'N') ?
                10000000.0 + (minLatitudeDeg * 111132.0) : // Southern Hemisphere
                (minLatitudeDeg * 111132.0) - 100000.0; // Northern Hemisphere

        // Keep adding 2,000,000 until we exceed the minimum possible northing for the band
        while (utmNorthing < minNorthingEstimate) {
            utmNorthing += 2000000.0;
        }
        return new UTM(gzd, utmNorthing, utmEasting);
    }
}