package org.slowcoders.storm;

import java.sql.SQLException;

public class IllegalOperationException extends SQLException {

	public IllegalOperationException(String msg) {
		super(msg);
	}
	
}
