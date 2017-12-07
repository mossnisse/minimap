
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


public class Datum {
	Date date;
	
	public Datum(String UTC) throws ParseException {
		DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		date = formatter.parse(UTC);
	}
	
	public String getSEDate() {
		DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		formatter.setTimeZone(TimeZone.getTimeZone("Europe/Stockholm"));
		return formatter.format(date);
	}
	
	public String getSETime() {
		DateFormat formatter = new SimpleDateFormat("HH:mm:ss");
		formatter.setTimeZone(TimeZone.getTimeZone("Europe/Stockholm"));
		return formatter.format(date);
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		/*
	    String[] timeZones = TimeZone.getAvailableIDs();
	    for (String id: timeZones){
	    	System.out.println(id);
	    }*/
		
		try {
			DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'");
		
			String dateTimeUTC = "2013-04-25T09:54:59Z";
			formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
			Date currentDate = formatter.parse(dateTimeUTC);
		
			formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
			formatter.setTimeZone(TimeZone.getTimeZone("Europe/Stockholm"));
			System.out.println(formatter.format(currentDate));
			    
			// now for "UTC"
			formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
			System.out.println(formatter.format(currentDate));
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	  
	}

}
