package org.slowcoders.storm;

import java.sql.SQLException;

public interface EntityVisitor<T extends EntityReference> {
	boolean visit(T ref) throws SQLException;
}