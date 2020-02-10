package org.slowcoders.storm.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

public class JDBCSavePoint extends JDBCUpdateTransaction {
	private Savepoint savePoint;
	
	protected JDBCSavePoint(JDBCUpdateTransaction parent) throws SQLException {
		super(parent);
		
		Connection conn = dbLocal.getConnection();
		this.savePoint = conn.setSavepoint();
	}

	public void commit() throws SQLException {
		Connection conn = dbLocal.getConnection();
		
		conn.releaseSavepoint(savePoint);
		this.savePoint = null;
		super.commitWithoutNotification(super.getOuterTransaction());
	}

	protected void doRollback() throws SQLException {
		Connection conn = dbLocal.getConnection();
		
		conn.rollback(this.savePoint);
		this.savePoint = null;
	}


}