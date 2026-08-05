package app.plugin.herbarium;

/**
 * One row of the herbarium specimen view, including the bridge overrides that
 * link it to a locality. Built positionally in {@code SpecimenService.mapRow};
 * keep the component order below in step with that call.
 */
public record Specimen(
        int id,
        // Taxonomic info
        String accessionNo, String genus, String species,
        String institutionCode, String collectionCode,
        // Collection metadata
        int year, int month, int day,
        String collector, String originalText, String specimenLocality,
        String district, String province,
        // Geographic / grid data
        String rubin, String riketsN, String riketsO, int swerefN, int swerefE,
        // DMS coordinates
        String latDir, String latDeg, String latMin, String latSec,
        String longDir, String longDeg, String longMin, String longSec,
        // Bridge overrides
        int localityId, int distance, String direction,
        String oDistrict, String oProvince) {

    public String sweref() {
        return (swerefN > 0) ? swerefN + ", " + swerefE : "";
    }

    /** Copy with the bridge overrides replaced, for keeping the UI in sync after a save. */
    public Specimen withBridge(int localityId, int distance, String direction,
                               String oDistrict, String oProvince) {
        return new Specimen(id, accessionNo, genus, species, institutionCode, collectionCode,
                year, month, day, collector, originalText, specimenLocality, district, province,
                rubin, riketsN, riketsO, swerefN, swerefE,
                latDir, latDeg, latMin, latSec, longDir, longDeg, longMin, longSec,
                localityId, distance, direction, oDistrict, oProvince);
    }
}
