package org.slowcoders.storm.jdbc;

import org.slowcoders.util.Debug;

import java.sql.SQLException;

public abstract class JDBCUpdateTransaction extends JDBCTransaction {

	protected final JDBCDatabase.LocalConnection dbLocal;
	protected JDBCUpdateTransaction subTransaction;

	protected JDBCUpdateTransaction(JDBCDatabase.LocalConnection conn) throws SQLException {
		super(conn);
		this.dbLocal = conn;
	}

	protected JDBCUpdateTransaction(JDBCUpdateTransaction parent) throws SQLException {
		super(parent);
		this.dbLocal = parent.dbLocal;
	}

	
	public JDBCUpdateTransaction beginSubTransaction() throws SQLException {
		Debug.Assert(super.isStarted());
		if (this.subTransaction == null) {
			this.subTransaction = new JDBCSavePoint(this);
		}
		this.subTransaction.doBegin();
		return this.subTransaction;
	}

	
	protected final JDBCTransaction getOuterTransaction() {
		return super.getOuterTransaction();
	}
	
	public void close() {
		dbLocal.onCurrentTransactionClosed(this);
		super.close();
	}

}