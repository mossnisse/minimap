package db;

public interface Table {
	
	public void  setName(String name);
	public String getName();
	
	public void adField(String name);
	public void adPost(Object[] data);
	
	public String[] getFieldNames();
	public String getFieldName(int i);
	public Object[] getPost(int i);
}
