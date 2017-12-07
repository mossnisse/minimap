import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;


public class Table implements Collection<Table.Row>, Iterable<Table.Row> {
	private String name;
	private Field[] fields;
	private ArrayList<Row> rows;

	public class Field {
		private String name;
		private String dataType;
		private Class dataClass;
	}
	
	public class Row {
		private Object[] data;
	}

	public Table(String name, int numFields) {
		this.name = name;
		fields = new Field[numFields];
	}
	
	public void setField(int num, String name, String dataType, Class dataClass) {
		fields[num].name = name;
		fields[num].dataType = dataType;
		fields[num].dataClass = dataClass;
	}
	
	public Object getData(int rowN, int fieldN) {
		Row row = rows.get(rowN);
		return row.data[fieldN];
	}
	
	public Class getClass(int fieldN) {
		return fields[fieldN].dataClass;
	}
	
	@Override
	public Iterator iterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean add(Row e) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return rows.size() == 0;
	}

	@Override
	public boolean remove(Object o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int size() {
		// TODO Auto-generated method stub
		return rows.size();
	}

	@Override
	public Row[] toArray() {
		// TODO Auto-generated method stub
		return null;
	}

	

	@Override
	public boolean addAll(Collection<? extends Row> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean contains(Object o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public <Rows> Rows[] toArray(Rows[] a) {
		// TODO Auto-generated method stub
		return null;
	}
}
