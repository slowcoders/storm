package org.slowcoders.storm;

import org.slowcoders.io.serialize.IOAdapter;

import java.sql.SQLException;

public abstract class StormDatabase {

	public abstract StormTable findDBTable(Class<?> entityType);

	public abstract StormTable findDBTableByName(String tableName);

    public final <T, P> T executeIsolatedTransaction(TransactionalOperation<P> op, P operationParam) throws SQLException {
        return executeIsolatedTransaction(op, operationParam, null);
    }

	public final <T, P> T executeInLocalTransaction(TransactionalOperation<P> op, P operationParam) throws SQLException {
		return executeInLocalTransaction(op, operationParam, null);
	}

	public abstract <T, P> T executeIsolatedTransaction(TransactionalOperation<P> op, P operationParam, ConcurrentUpdatePolicy updatePolicy) throws SQLException;

	public abstract <T, P> T executeInLocalTransaction(TransactionalOperation<P> op, P operationParam, ConcurrentUpdatePolicy updatePolicy) throws SQLException;


	/*********************************************/
	/* Internal Methods & Classes                */
	/*-------------------------------------------*/

	protected final <T, P> T doExecuteOperation(TransactionalOperation<P> op, P operationParam, long transactionId) throws SQLException {
		return op.execute_inTR(operationParam, transactionId);
	}

    protected void notifyTableCreated(StormTable<?,?,?> table) throws SQLException {
		table.getORMHelper().onTableCreated(table);
	}

    protected abstract IOAdapter<?, ?> getAbstractReferenceAdapter();
}
