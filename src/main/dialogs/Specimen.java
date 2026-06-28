package main.dialogs;

public class Specimen {
    // Database IDs
    private int id;

    // Taxonomic Info
    private String accessionNo;
    private String genus;
    private String species;
    private String institutionCode;
    private String collectionCode;

    // Collection Metadata
    private int year;
    private int month;
    private int day;
    private String collector;
    private String originalText;
    private String specimenLocality;
    private String district;
    private String province;

    // Geographic / Grid Data
    private String rubin;
    private String riketsN;
    private String riketsO;
    private int swerefN;
    private int swerefE;

    // DMS Coordinates
    private String latDir, latDeg, latMin, latSec;
    private String longDir, longDeg, longMin, longSec;

    // Bridge Overrides
    private int localityID;
    private int distance;
    private String direction;
    private String oDistrict;
    private String oProvince;

    public Specimen() {}

    // --- Setters ---
    public void setId(int id) { this.id = id; }
    public void setAccessionNo(String accessionNo) { this.accessionNo = accessionNo; }
    public void setGenus(String genus) { this.genus = genus; }
    public void setSpecies(String species) { this.species = species; }
    public void setInstitutionCode(String institutionCode) { this.institutionCode = institutionCode; }
    public void setCollectionCode(String collectionCode) { this.collectionCode = collectionCode; }
    public void setYear(int year) { this.year = year; }
    public void setMonth(int month) { this.month = month; }
    public void setDay(int day) { this.day = day; }
    public void setCollector(String collector) { this.collector = collector; }
    public void setOriginalText(String originalText) { this.originalText = originalText; }
    public void setSpecimenLocality(String specimenLocality) { this.specimenLocality = specimenLocality; }
    public void setDistrict(String district) { this.district = district; }
    public void setProvince(String province) { this.province = province; }
    // coordinate variables
    public void setRubin(String rubin) { this.rubin = rubin; }
    public void setRiketsN(String riketsN) { this.riketsN = riketsN; }
    public void setRiketsO(String riketsO) { this.riketsO = riketsO; }
    public void setSwerefN(int swerefN) { this.swerefN = swerefN; }
    public void setSwerefE(int swerefE) { this.swerefE = swerefE; }
    public void setLatDir(String latDir) { this.latDir = latDir; }
    public void setLatDeg(String latDeg) { this.latDeg = latDeg; }
    public void setLatMin(String latMin) { this.latMin = latMin; }
    public void setLatSec(String latSec) { this.latSec = latSec; }
    public void setLongDir(String longDir) { this.longDir = longDir; }
    public void setLongDeg(String longDeg) { this.longDeg = longDeg; }
    public void setLongMin(String longMin) { this.longMin = longMin; }
    public void setLongSec(String longSec) { this.longSec = longSec; }
    // bridge variables
    public void setLocalityId(int localityId) { this.localityID = localityId; }
    public void setDistance(int distance) { this.distance = distance; }
    public void setDirection(String direction) { this.direction = direction; }
    public void setODistrict(String oDistrict) { this.oDistrict = oDistrict; }
    public void setOProvince(String oProvince) { this.oProvince = oProvince; }

    // --- Getters ---
    public int getId() { return id; }
    public String getAccessionNo() { return accessionNo; }
    public String getInstitutionCode() { return institutionCode; }
    public String getGenus() { return genus; }
    public String getSpecies() { return species; }
    public String getOriginalText() { return originalText; }
    public String getCollector() { return collector; }
    public String getCollectionCode() { return collectionCode; }
    public int getYear() { return year; }
    public int getMonth() { return month; }
    public int getDay() { return day; }
    public String getSpecimenLocality() { return specimenLocality; }
    public String getDistrict() { return district; }
    public String getProvince()  { return province; }
    // coordinate varibales
    public String getRubin() { return rubin; }
    public String getRiketsN() { return riketsN; }
    public String getRiketsO() { return riketsO; }
    public String getSweref() {
        return (swerefN > 0) ? swerefN + ", " + swerefE : "";
    }

    public int getSwerefN() { return  swerefN; }
    public int getSwerefE() { return swerefE; }
    public String getLatDeg() { return latDeg; }
    public String getLatMin() { return latMin; }
    public String getLatSec() { return latSec; }
    public String getLatDir() { return latDir; }
    public String getLongDeg() { return longDeg; }
    public String getLongMin() { return longMin; }
    public String getLongSec() { return longSec; }
    public String getLongDir() { return longDir; }
    // bridge variables
    public int getLocalityId() { return localityID; }
    public String getODistrict() { return oDistrict; }
    public String getOProvince() { return oProvince; }
    public int getDistance() { return distance; }
    public String getDirection() { return direction; }

}