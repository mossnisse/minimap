package db;

public interface Table {
	void  setName(String name);
	String getName();
	
	void adField(String name);
	void adPost(Object[] data);
	
	String[] getFieldNames();
	String getFieldName(int i);
	Object[] getPost(int i);
}
