import java.sql.ResultSet;
import java.sql.SQLException;

public class Specimen {
    // Database IDs
    private int id;
    private int localityId;

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
    private String localityName;
    private String district;
    private String province;

    // Geographic / Grid Data
    private String rubin;
    private String riketsN;
    private String riketsO;

    // DMS Coordinates
    private String latDir, latDeg, latMin, latSec;
    private String longDir, longDeg, longMin, longSec;

    // Bridge Overrides
    private int distance;
    private String direction;
    private String oDistrict;
    private String oProvince;

    public Specimen() {}

    /**
     * Static factory method to map from a result set.
     * Note: You can use this OR the individual setters in the service.
     */
    public static Specimen fromResultSet(ResultSet rs) throws SQLException {
        Specimen s = new Specimen();
        s.setAccessionNo(rs.getString("AccessionNo"));
        s.setYear(rs.getInt("Year"));
        s.setMonth(rs.getInt("Month"));
        s.setDay(rs.getInt("Day"));
        s.setOriginalText(rs.getString("original_text"));
        s.setGenus(rs.getString("Genus"));
        s.setSpecies(rs.getString("Species"));
        s.setCollector(rs.getString(8));
        s.setInstitutionCode(rs.getString("InstitutionCode"));
        s.setLocalityId(rs.getInt("locality_ID"));
        s.setLocalityName(rs.getString("locality"));
        s.setProvince(rs.getString("province"));
        s.setId(rs.getInt("specimens_ID"));
        s.setRubin(rs.getString("RUBIN"));
        s.setRiketsN(rs.getString("RiketsN"));
        s.setRiketsO(rs.getString("RiketsO"));
        s.setLatDir(rs.getString("Lat_dir"));
        s.setLatDeg(rs.getString("Lat_deg"));
        s.setLatMin(rs.getString("Lat_min"));
        s.setLatSec(rs.getString("Lat_sec"));
        s.setLongDir(rs.getString("Long_dir"));
        s.setLongDeg(rs.getString("Long_deg"));
        s.setLongMin(rs.getString("Long_min"));
        s.setLongSec(rs.getString("Long_sec"));
        s.setCollectionCode(rs.getString("CollectionCode"));
        s.setDistance(rs.getInt("distance"));
        s.setDirection(rs.getString("direction"));
        s.setODistrict(rs.getString("oDistrict"));
        s.setOProvince(rs.getString("oProvince"));
        return s;
    }

    // --- Setters ---
    public void setId(int id) { this.id = id; }
    public void setLocalityId(int localityId) { this.localityId = localityId; }
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
    public void setLocalityName(String localityName) { this.localityName = localityName; }
    public void setDistrict(String district) { this.district = district; }
    public void setProvince(String province) { this.province = province; }
    public void setRubin(String rubin) { this.rubin = rubin; }
    public void setRiketsN(String riketsN) { this.riketsN = riketsN; }
    public void setRiketsO(String riketsO) { this.riketsO = riketsO; }
    public void setLatDir(String latDir) { this.latDir = latDir; }
    public void setLatDeg(String latDeg) { this.latDeg = latDeg; }
    public void setLatMin(String latMin) { this.latMin = latMin; }
    public void setLatSec(String latSec) { this.latSec = latSec; }
    public void setLongDir(String longDir) { this.longDir = longDir; }
    public void setLongDeg(String longDeg) { this.longDeg = longDeg; }
    public void setLongMin(String longMin) { this.longMin = longMin; }
    public void setLongSec(String longSec) { this.longSec = longSec; }
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
    public String getLocalityName() { return localityName; }
    public String getDistrict() { return district; }
    public String getProvince()  { return province; }
    public String getRubin() { return rubin; }
    public String getRiketsN() { return riketsN; }
    public String getRiketsO() { return riketsO; }
    public String getLatDeg() { return latDeg; }
    public String getLatMin() { return latMin; }
    public String getLatSec() { return latSec; }
    public String getLongDeg() { return longDeg; }
    public String getLongMin() { return longMin; }
    public String getLongSec() { return longSec; }
    public String getODistrict() { return oDistrict; }
    public String getOProvince() { return oProvince; }
    public int getDistance() { return distance; }
    public String getDirection() { return direction; }
    public int getLocalityId() { return localityId; }
}