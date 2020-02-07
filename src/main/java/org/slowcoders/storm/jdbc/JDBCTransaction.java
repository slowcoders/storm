package org.slowcoders.storm.jdbc;

import java.sql.SQLException;

import org.slowcoders.storm.ConcurrentUpdatePolicy;
import org.slowcoders.storm.AbstractTransaction;

public abstract class JDBCTransaction extends AbstractTransaction {

	private final JDBCTransaction outerTransaction;

	protected JDBCTransaction(ConcurrentUpdatePolicy policy) {
		super(policy);
		this.outerTransaction = null;
	}

	protected JDBCTransaction(JDBCTransaction parent) throws SQLException {
		super(parent.getUpdatePolicy());
		this.outerTransaction = parent;
	}

	public abstract JDBCTransaction beginSubTransaction() throws SQLException;

	public long getTransactionId() {
		return outerTransaction.getTransactionId();
	}

	protected JDBCTransaction getOuterTransaction() {
		return this.outerTransaction;
	}

	protected void invalidateCaches() throws SQLException {
		super.invalidateCaches();
	}
}