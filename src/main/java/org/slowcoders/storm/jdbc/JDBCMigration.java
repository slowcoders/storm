package org.slowcoders.storm.jdbc;

import java.sql.SQLException;

public abstract class JDBCMigration {
	
	private int version;

	protected JDBCMigration(int version) {
		this.version = version;
	}
	
	public final int getTargetVersion() {
		return version;
	}
	
	protected abstract void migrate(JDBCDatabase db) throws SQLException;
	 
}
