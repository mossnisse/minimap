package test.tools;

public class MSQLConvert {
    /*
    private void convCoord() {
        String sql1 = "SELECT lat, `long`, id FROM locality where country = 'Sweden' limit ?,1";
        String sql2 = "update locality set SWTMN = ?, SWTME = ? where id =?";
        try {
            Connection conn = core.DBConnection.getConn();
            PreparedStatement statmt1= conn.prepareStatement(sql1);
            PreparedStatement statmt2= conn.prepareStatement(sql2);

            for (int i=1; i< 46028; i++) {
                System.out.println("i: "+i);
                statmt1.setInt(1, i);
                ResultSet result = statmt1.executeQuery();
                result.next();
                double lat = result.getDouble(1);
                double longi = result.getDouble(2);
                int id = result.getInt(3);
                System.out.println("id: "+id);
                Coordinates c = new Coordinates(lat,longi);
                Coordinates swtm = c.toProjected(CoordSystem.SWEREF99TM);
                int swtmN = (int)Math.round(swtm.getNorth());
                int swtmE = (int)Math.round(swtm.getEast());
                statmt2.setInt(1,swtmN);
                statmt2.setInt(2,swtmE);

                statmt2.setInt(3,id);
                statmt2.execute();
            }
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    static void main(String[] args) {
        layers.MYSQLTableLayer MT = new layers.MYSQLTableLayer();
        MT.convCoord();
    }*/
}
