import java.util.Iterator;

public class TableIterator implements Iterator<Table.Row>{
	private int curRow;
	Table.Field[] fields;
	Table.Row[] rows;

	public TableIterator(Table.Field[] fields, Table.Row[] rows) {
		curRow =-1;
		this.rows = rows;
		this.fields= fields;
	}
	
	@Override
	public boolean hasNext() {
		// TODO Auto-generated method stub
		return fields.length>curRow;
	}

	@Override
	public Table.Row next() {
		// TODO Auto-generated method stub
		curRow++;
		return rows[curRow];
		
	}

	@Override
	public void remove() {
		// TODO Auto-generated method stub
		
	}

}
