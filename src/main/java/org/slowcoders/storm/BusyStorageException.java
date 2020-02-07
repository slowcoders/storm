package org.slowcoders.storm;

import java.sql.SQLException;

public class BusyStorageException extends SQLException {

	public BusyStorageException(String msg) {
		super(msg);
	}

}
