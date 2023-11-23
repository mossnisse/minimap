package db;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.sql.Connection;
import org.h2.jdbcx.JdbcDataSource;

public class TableH2 extends JPanel implements Table  {
	private String name;
	private Connection conn;
	//private JTable jTable;
	//private JScrollPane scrollPane;
	
	TableH2(String tableName) {
		super(new GridLayout(1,0));
		this.name = tableName;
		createConnection();
		
		String[] fieldNames = {"First Name",
                "Last Name",
                "Sport",
                "# of Years",
                "Vegetarian"};
		Object[][] data = {
			    {"Kathy", "Smith",
			     "Snowboarding", new Integer(5), new Boolean(false)},
			    {"John", "Doe",
			     "Rowing", new Integer(3), new Boolean(true)},
			    {"Sue", "Black",
			     "Knitting", new Integer(2), new Boolean(false)},
			    {"Jane", "White",
			     "Speed reading", new Integer(20), new Boolean(true)},
			    {"Joe", "Brown",
			     "Pool", new Integer(10), new Boolean(false)}
			};
		
		final JTable jTable = new JTable(data, fieldNames);
		
		jTable.setPreferredScrollableViewportSize(new Dimension(500, 70));
		jTable.setFillsViewportHeight(true);
		JScrollPane scrollPane = new JScrollPane(jTable);
		add(scrollPane);
	}
	
	private void createConnection() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:˜/test");
        ds.setUser("sa");
        ds.setPassword("sa");
        try {
            conn = ds.getConnection();
        } catch (Exception e) {
            System.err.println("Caught IOException: " + e.getMessage());
        } finally {
        }
    }
	
	public void test() {
		
	}
	
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}
	

	@Override
	public void adField(String name) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void adPost(Object[] data) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String[] getFieldNames() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getFieldName(int i) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object[] getPost(int i) {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	 private static void createAndShowGUI() {
	        //Create and set up the window.
	        JFrame frame = new JFrame("SimpleTableDemo");
	        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

	        //Create and set up the content pane.
	        //SimpleTableDemo newContentPane = new SimpleTableDemo();
	        TableH2 h2 = new TableH2("testtable");
			h2.test();
			h2.setOpaque(true); //content panes must be opaque
	        frame.setContentPane(h2);

	        //Display the window.
	        frame.pack();
	        frame.setVisible(true);
	    }
	
	public static void main(String[] args) {
		
		 javax.swing.SwingUtilities.invokeLater(new Runnable() {
	            public void run() {
	                createAndShowGUI();
	            }
	        });
		
	}
}
