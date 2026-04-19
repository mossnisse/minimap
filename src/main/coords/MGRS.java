package main.coords;

import java.util.Locale;
import java.util.regex.Pattern;

// Todo: write a function that returns the corners, handle different precision, 100km to 1m squares
// handle the old AL sheme
// coordinates should be truncated not rounded but I think the midle of the square should be returned when converting to UTM.
// Porar regions instead uses "Universal Polar Stereographic"

public class MGRS {
    private static final double MGRS_REPEAT_CYCLE = 2_000_000.0;
    private static final double MGRS_SQUARE_SIZE  = 100_000.0;
    private static final double UTM_FALSE_NORTHING = 10_000_000.0;
    private static final double LAT_METERS_PER_DEGREE = 111_132.0;

    private static char mgrsNumToAlpha(int num) {
        int code = num + 'A';
        if (code >= 'I') code++;
        if (code >= 'O') code++;
        return (char) code;
    }

    private static int mgrsAlphaToNum(char c) {
        char upper = Character.toUpperCase(c);
        int res = upper - 'A';
        if (upper > 'O') return res - 2;
        if (upper > 'I') return res - 1;
        return res;
    }

    public static String fromUTM(UTM utm) {
        if (utm == null) return "OUTSIDE UTM RANGE";

        int zone = utm.getZone();
        double e = utm.getEast();
        double n = utm.getNorth();

        // Identify the 100km Square Column (East-West)
        // MGRS Column sets repeat every 3 zones: Set 1 (A-H), Set 2 (J-R), Set 3 (S-Z)
        int set = (zone - 1) % 3;
        int e100k = (int) Math.floor(e / MGRS_SQUARE_SIZE);

        int colBase = 0;
        if (set == 0) colBase = mgrsAlphaToNum('A');      // Zones 1, 4, 7...
        else if (set == 1) colBase = mgrsAlphaToNum('J'); // Zones 2, 5, 8...
        else if (set == 2) colBase = mgrsAlphaToNum('S'); // Zones 3, 6, 9...

        // e100k is usually between 1 and 8 (100k to 800k).
        // We subtract 1 because the letters start at 100,000m.
        char columnId = mgrsNumToAlpha(colBase + e100k - 1);

        // Identify the 100km Square Row (North-South)
        // Odd zones start at 'A', Even zones start at 'F'
        int rowBase = (zone % 2 != 0) ? mgrsAlphaToNum('A') : mgrsAlphaToNum('F');

        // n100k is the number of 100km steps north.
        // The alphabet repeats every 20 steps (2,000,000 meters).
        int n100k = (int) Math.floor(n / MGRS_SQUARE_SIZE) % 20;
        char rowId = mgrsNumToAlpha((rowBase + n100k) % 20);

        // Calculate final numerical values (Floor, do not round!)
        int finalE = (int) Math.floor(e % MGRS_SQUARE_SIZE);
        int finalN = (int) Math.floor(n % MGRS_SQUARE_SIZE);

        return String.format(Locale.US, "%s %c%c %05d %05d", utm.getGZD(), columnId, rowId, finalE, finalN);
    }

    public static UTM toUTM(String mgrsStr) {
        String cleanStr = mgrsStr.replaceAll("\\s+", "").toUpperCase();
        if (cleanStr.length() < 5) throw new IllegalArgumentException("Invalid MGRS string");

        // Extract GZD components
        int firstLetterIdx = Character.isLetter(cleanStr.charAt(1)) ? 1 : 2;
        int zone = Integer.parseInt(cleanStr.substring(0, firstLetterIdx));
        char latBand = cleanStr.charAt(firstLetterIdx);

        if (latBand < 'C' || latBand > 'X') throw new IllegalArgumentException("Unsupported UTM Band");

        char colLetter = cleanStr.charAt(firstLetterIdx + 1);
        char rowLetter = cleanStr.charAt(firstLetterIdx + 2);

        // Extract precision numbers
        String numPart = cleanStr.substring(firstLetterIdx + 3);
        if (numPart.length() % 2 != 0) throw new IllegalArgumentException("Invalid precision length");
        int precisionLength = numPart.length() / 2;

        double eMeters = Double.parseDouble(numPart.substring(0, precisionLength)) * Math.pow(10, 5 - precisionLength);
        double nMeters = Double.parseDouble(numPart.substring(precisionLength)) * Math.pow(10, 5 - precisionLength);

        // Calculate Easting
        int setCol = (zone - 1) % 3;
        int e100kBase = (setCol == 0) ? mgrsAlphaToNum('A') :
                (setCol == 1) ? mgrsAlphaToNum('J') : mgrsAlphaToNum('S');

        int e100kSteps = mgrsAlphaToNum(colLetter) - e100kBase;
        if (e100kSteps < 0) e100kSteps += 8; // Wrap around for widened zones

        double utmEasting = (e100kSteps + 1) * MGRS_SQUARE_SIZE + eMeters;

        // Calculate Northing (The 2,000km ambiguity)
        int rowBase = (zone % 2 != 0) ? mgrsAlphaToNum('A') : mgrsAlphaToNum('F');
        int n100kSteps = mgrsAlphaToNum(rowLetter) - rowBase;
        if (n100kSteps < 0) n100kSteps += 20;

        double utmNorthing = n100kSteps * MGRS_SQUARE_SIZE + nMeters;

        // Find the minimum possible WGS84 northing for this Latitude Band
        int bandIndex = latBand - 'C';
        if (latBand > 'I') bandIndex--;
        if (latBand > 'O') bandIndex--;
        double minLat = -80.0 + (bandIndex * 8.0);

        // 1 deg latitude is approx 111,132 meters.
        double minNorthingEstimate = (latBand < 'N') ?
                UTM_FALSE_NORTHING + (minLat * LAT_METERS_PER_DEGREE) : // South (starts near 1,100,000 and goes up)
                (minLat * LAT_METERS_PER_DEGREE);               // North (starts at 0 and goes up)

        // Shift by 2,000,000m blocks until we are inside the correct Latitude Band
        while (utmNorthing < minNorthingEstimate) {
            utmNorthing += MGRS_REPEAT_CYCLE;
        }

        // Safety check: MGRS bands are ~890km tall. If we overshot by a full cycle, bring it back.
        // This handles edge cases where the rough estimate is slightly misaligned at the band borders.
        if (utmNorthing > minNorthingEstimate + MGRS_REPEAT_CYCLE) {
            utmNorthing -= MGRS_REPEAT_CYCLE;
        }

        boolean isNorthern = (latBand >= 'N');
        return new UTM(zone, isNorthern, utmEasting, utmNorthing);
    }

    // would be good with a strict and lax variant
    public static boolean isValid(String mgrsStr) {
        if (mgrsStr == null || mgrsStr.isEmpty()) return false;

        // 1. Basic Formatting and Cleanup
        String clean = mgrsStr.replaceAll("\\s+", "").toUpperCase();

        // MGRS must be GZD (2-3 chars) + SquareID (2 chars) + Digits (even number, 0-10)
        // Min length: 1VUC (4) or 01VUC (5). Max: 60XABCDE1234567890 (15)
        if (clean.length() < 5 || clean.length() > 15) return false;

        try {
            // Validate GZD (Grid Zone Designation)
            int firstLetterIdx = Character.isLetter(clean.charAt(1)) ? 1 : 2;
            int zone = Integer.parseInt(clean.substring(0, firstLetterIdx));
            char latBand = clean.charAt(firstLetterIdx);

            if (zone < 1 || zone > 60) return false;
            if (latBand < 'C' || latBand > 'X' || latBand == 'I' || latBand == 'O') return false;

            // Validate 100km Square ID Letters
            char colLetter = clean.charAt(firstLetterIdx + 1);
            char rowLetter = clean.charAt(firstLetterIdx + 2);
            if (colLetter < 'A' || colLetter > 'Z' || colLetter == 'I' || colLetter == 'O') return false;
            if (rowLetter < 'A' || rowLetter > 'V' || rowLetter == 'I' || rowLetter == 'O') return false;

            // Validate Numerical Part (must be all digits and even length)
            String numPart = clean.substring(firstLetterIdx + 3);
            if (!numPart.isEmpty()) {
                if (numPart.length() % 2 != 0) return false;
                if (!Pattern.matches("^[0-9]+$", numPart)) return false;
            }
            // The "Overlap" Check
            // We calculate the UTM boundaries for this specific 100km Square Letter
            return doesSquareExistInGZD(zone, latBand, colLetter, rowLetter);

        } catch (Exception e) {
            return false;
        }
    }

    private static boolean doesSquareExistInGZD(int zone, char latBand, char colLetter, char rowLetter) {
        // Determine the range of Easting for this column letter in this zone
        int setCol = (zone - 1) % 3;
        int e100kBase = (setCol == 0) ? mgrsAlphaToNum('A') :
                (setCol == 1) ? mgrsAlphaToNum('J') : mgrsAlphaToNum('S');

        int e100kSteps = mgrsAlphaToNum(colLetter) - e100kBase;
        if (e100kSteps < 0) e100kSteps += 8;

        double squareMinE = (e100kSteps + 1) * MGRS_SQUARE_SIZE;
        double squareMaxE = squareMinE + MGRS_SQUARE_SIZE;

        // Standard UTM zone boundaries are roughly 160k to 840k.
        // If a square is entirely outside the 0-1,000,000m range, it's impossible.
        if (squareMaxE < 0 || squareMinE > 1000000) return false;

        // Check North-South: MGRS rows repeat every 2,000,000m.
        // We check if ANY of the occurrences of this row letter fall within the Latitude Band.
        int bandIdx = latBand - 'C';
        if (latBand > 'I') bandIdx--;
        if (latBand > 'O') bandIdx--;
        double bandMinLat = -80.0 + (bandIdx * 8.0);
        double bandMaxLat = (latBand == 'X') ? 84.0 : bandMinLat + 8.0;

        // Roughly convert band Lat to Northing (approximation is fine for "existence")
        double bandMinN = (latBand < 'N') ? UTM_FALSE_NORTHING + (bandMinLat * LAT_METERS_PER_DEGREE) : (bandMinLat * LAT_METERS_PER_DEGREE);
        double bandMaxN = (latBand < 'N') ? UTM_FALSE_NORTHING+ (bandMaxLat * LAT_METERS_PER_DEGREE) : (bandMaxLat * LAT_METERS_PER_DEGREE);

        int rowBase = (zone % 2 != 0) ? mgrsAlphaToNum('A') : mgrsAlphaToNum('F');
        int n100kSteps = mgrsAlphaToNum(rowLetter) - rowBase;
        if (n100kSteps < 0) n100kSteps += 20;

        double firstOccurrenceN = n100kSteps * MGRS_SQUARE_SIZE;

        // Check all occurrences (every 2,000,000m) to see if one hits the latitude band
        for (double n = firstOccurrenceN; n < UTM_FALSE_NORTHING; n += MGRS_REPEAT_CYCLE) {
            double squareMaxN = n + MGRS_SQUARE_SIZE;
            // If the square overlaps with the latitude band's Northing range
            if (squareMaxN > bandMinN && n < bandMaxN) {
                return true;
            }
        }

        return false;
    }
}