package test.tools;

public class H2Convert {
    /*
    public void saveConvert() {
        try {
            Connection conn = core.DBConnection.getH2Conn();
            conn.setAutoCommit(false); // Enable manual transaction for speed

            Statement st = conn.createStatement();
            st.execute("DROP TABLE IF EXISTS ortnamnSWTM");
            st.execute("CREATE TABLE ortnamnSWTM AS SELECT * FROM ortnamnsDB");

            String selectSql = "SELECT NORTH, EAST, ORT_ID FROM ortnamnSWTM";
            String updateSql = "UPDATE ortnamnSWTM SET NORTH = ?, EAST = ? WHERE ORT_ID = ?";

            try (PreparedStatement select = conn.prepareStatement(selectSql);
                 PreparedStatement update = conn.prepareStatement(updateSql);
                 ResultSet rs = select.executeQuery()) {

                int count = 0;
                while (rs.next()) {
                    Coordinates rt90 = new Coordinates(rs.getDouble(1), rs.getDouble(2));

                    if (rt90.isValid(CoordSystem.RT90)) {
                        Coordinates swtm = rt90.toWGS84(CoordSystem.RT90).toProjected(CoordSystem.SWEREF99TM);

                        update.setDouble(1, swtm.getNorth());
                        update.setDouble(2, swtm.getEast());
                        update.setInt(3, rs.getInt(3));
                        update.addBatch(); // Use batching!

                        if (++count % 1000 == 0) {
                            update.executeBatch();
                            System.out.println("Processed " + count + " records...");
                        }
                    }
                }
                update.executeBatch();
                conn.commit(); // Save everything
                System.out.println("Conversion Complete.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }*/
}
