package org.slowcoders.storm.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slowcoders.storm.EntityReference;
import org.slowcoders.storm.AbstractCursor;
import org.slowcoders.storm.AbstractColumn;
import org.slowcoders.util.Debug;
import org.slowcoders.storm.StormTable;

public class JDBCCursor implements AbstractCursor {
	
	private ResultSet results;
	private AbstractColumn[] selects;

	public JDBCCursor(ResultSet results, AbstractColumn[] selects) {
		this.results = results;
		this.selects = selects;
	}

	private int getColumnIndex(AbstractColumn column) {
		for (int i = 0; i < selects.length; i ++) {
			if (column == selects[i]) {
				return i + 1;
			}
		}
		throw Debug.wtf(column + " is not found in select statement");
	}

	
	@Override
	public Object get(AbstractColumn column) throws SQLException {
		int c = getColumnIndex(column);
		return results.getObject(c);
	}

	@Override
	public String getString(AbstractColumn column) throws SQLException {
		int c = getColumnIndex(column);
		return results.getString(c);
	}

	@Override
	public byte[] getBLOB(AbstractColumn column) throws SQLException {
		int c = getColumnIndex(column);
		return results.getBytes(c);
	}

	@Override
	public boolean getBoolean(AbstractColumn column) throws SQLException {
		int c = getColumnIndex(column);
		return results.getBoolean(c);
	}

	@Override
	public int getInt(AbstractColumn column) throws SQLException {
		int c = getColumnIndex(column);
		return results.getInt(c);
	}

	@Override
	public long getLong(AbstractColumn column) throws SQLException {
		int c = getColumnIndex(column);
		return results.getLong(c);
	}

	@Override
	public float getFloat(AbstractColumn column) throws SQLException {
		int c = getColumnIndex(column);
		return results.getFloat(c);
	}

	@Override
	public double getDouble(AbstractColumn column) throws SQLException {
		int c = getColumnIndex(column);
		return results.getDouble(c);
	}

	@Override
	public DateTime getDate(AbstractColumn column) throws SQLException {
		int c = getColumnIndex(column);
		long v = results.getLong(c);
		return new DateTime(v);
	}
	
	@Override
	public DateTime getDate(AbstractColumn column, DateTimeZone timeZone) throws SQLException {
		int c = getColumnIndex(column);
		long v = results.getLong(c);
		return new DateTime(v, timeZone);
	}

	@Override
	public EntityReference getReference(StormTable table, AbstractColumn column) throws SQLException {
		int c = getColumnIndex(column);
		long v = results.getLong(c);
		EntityReference ref = StormTable.UnsafeTools.getEntityReference(table, v);
		return ref;
	}
	

}
