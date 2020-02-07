package org.slowcoders.storm.jdbc;

import org.slowcoders.util.Debug;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

public class JDBCRootTransaction extends JDBCUpdateTransaction {

	private final PreparedStatement stmtBegin;
	private final PreparedStatement stmtCommit;
	private final PreparedStatement stmtRollback;
	private long trNo;
	private static AtomicInteger cntConnection = new AtomicInteger();

	protected JDBCRootTransaction(JDBCDatabase.LocalConnection dbLocal) throws SQLException {
		super(dbLocal);
		stmtBegin = dbLocal.conn.prepareStatement("BEGIN DEFERRED;");
		stmtCommit = dbLocal.conn.prepareStatement("COMMIT;");
		stmtRollback = dbLocal.conn.prepareStatement("ROLLBACK;");

		this.trNo = cntConnection.incrementAndGet() * 100_0000_0000L;
		dbLocal.getConnection().setAutoCommit(false);
		stmtCommit.execute();
	}

	public long getTransactionId() {
		return this.trNo;
	}
	
	public void doBegin() throws SQLException {
		this.trNo++;
		stmtBegin.execute();
		super.doBegin();
	}

	final void execBegin() {
		try {
			stmtBegin.execute();
		} catch (SQLException e) {
			Debug.wtf(e);
		}
	}

	final void execCommit() {
		try {
			stmtCommit.execute();
		} catch (SQLException e) {
			Debug.wtf(e);
		}
	}

	public void doCommit() throws SQLException {
		stmtCommit.execute();
	}

	public void doRollback() throws SQLException {
		stmtRollback.execute();
	}


}