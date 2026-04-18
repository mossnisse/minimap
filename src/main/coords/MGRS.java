package main.coords;

import java.util.Locale;

// Todo: write an isValid fucntion

public class MGRS {

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
        int e100k = (int) Math.floor(e / 100000.0);

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
        int n100k = (int) Math.floor(n / 100000.0) % 20;
        char rowId = mgrsNumToAlpha((rowBase + n100k) % 20);

        // Calculate final numerical values (Floor, do not round!)
        int finalE = (int) Math.floor(e % 100000.0);
        int finalN = (int) Math.floor(n % 100000.0);

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

        double utmEasting = (e100kSteps + 1) * 100000.0 + eMeters;

        // Calculate Northing (The 2,000km ambiguity)
        int rowBase = (zone % 2 != 0) ? mgrsAlphaToNum('A') : mgrsAlphaToNum('F');
        int n100kSteps = mgrsAlphaToNum(rowLetter) - rowBase;
        if (n100kSteps < 0) n100kSteps += 20;

        double utmNorthing = n100kSteps * 100000.0 + nMeters;

        // Find the minimum possible WGS84 northing for this Latitude Band
        int bandIndex = latBand - 'C';
        if (latBand > 'I') bandIndex--;
        if (latBand > 'O') bandIndex--;
        double minLat = -80.0 + (bandIndex * 8.0);

        // 1 deg latitude is approx 111,132 meters.
        double minNorthingEstimate = (latBand < 'N') ?
                10000000.0 + (minLat * 111132.0) : // South (starts near 1,100,000 and goes up)
                (minLat * 111132.0);               // North (starts at 0 and goes up)

        // Shift by 2,000,000m blocks until we are inside the correct Latitude Band
        while (utmNorthing < minNorthingEstimate) {
            utmNorthing += 2000000.0;
        }

        // Safety check: MGRS bands are ~890km tall. If we overshot by a full cycle, bring it back.
        // This handles edge cases where the rough estimate is slightly misaligned at the band borders.
        if (utmNorthing > minNorthingEstimate + 2000000.0) {
            utmNorthing -= 2000000.0;
        }

        boolean isNorthern = (latBand >= 'N');
        return new UTM(zone, isNorthern, utmEasting, utmNorthing);
    }
}