
public class SQL {
	/*public static String SQLF(String text) {
		text.replace( "\"", "\\\"");
		text.replace( ";", "\\;");
		return text;
	}*/
	
	//myLabel.setText("<html>" + myString.replaceAll("<","&lt;").replaceAll(">", "&gt;").replaceAll("\n", "<br/>") + "</html>")
	
	public static String HTMLF(String text) {
		return text.replaceAll("<","&lt;").replaceAll(">", "&gt;").replaceAll("\n", "<br/>").replaceAll("","<br/>");
	}
}
