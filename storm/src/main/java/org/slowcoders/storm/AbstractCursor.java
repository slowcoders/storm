package org.slowcoders.storm;

import java.sql.SQLException;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public interface AbstractCursor {

	interface Visitor {
		/**
		 * @return false to terminate iteration
		 */
		boolean onNext(AbstractCursor cursor) throws SQLException;
	}

	Object get(AbstractColumn column) throws SQLException;

	String getString(AbstractColumn column) throws SQLException;
	
	byte[] getBLOB(AbstractColumn column) throws SQLException;

	boolean getBoolean(AbstractColumn column) throws SQLException;
	
	int getInt(AbstractColumn column) throws SQLException;

	long getLong(AbstractColumn column) throws SQLException;

	float getFloat(AbstractColumn column) throws SQLException;

	double getDouble(AbstractColumn column) throws SQLException;

	DateTime getDate(AbstractColumn column) throws SQLException;
	
	DateTime getDate(AbstractColumn column, DateTimeZone timezone) throws SQLException;

	EntityReference getReference(StormTable table, AbstractColumn column) throws SQLException;
	
	
}