package org.slowcoders.storm;

import java.sql.SQLException;

public abstract class TransactionalOperation<P> {

    protected abstract <T> T execute_inTR(P operationParam, long transactionId) throws SQLException;

}
