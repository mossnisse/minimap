package dialogs;

public class BridgeData {
    public int localityId;
    public String distance;
    public String direction;
    public String oDistrict;
    public String oProvince;

    // Empty constructor for "No Link"
    public BridgeData() {
        this.localityId = -1;
        this.distance = "";
        this.direction = "";
        this.oDistrict = "";
        this.oProvince = "";
    }

    // Constructor to capture current UI state
    public BridgeData(int locId, String dist, String dir, String oDist, String oProv) {
        this.localityId = locId;
        this.distance = dist;
        this.direction = dir;
        this.oDistrict = oDist;
        this.oProvince = oProv;
    }

    // Standard equals() method for easy "Dirty Checking"
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BridgeData)) return false;
        BridgeData other = (BridgeData) obj;
        return localityId == other.localityId &&
                distance.equals(other.distance) &&
                direction.equals(other.direction) &&
                oDistrict.equals(other.oDistrict) &&
                oProvince.equals(other.oProvince);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(localityId, distance, direction, oDistrict, oProvince);
    }
}