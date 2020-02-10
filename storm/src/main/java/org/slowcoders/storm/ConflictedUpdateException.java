package org.slowcoders.storm;

import java.sql.SQLException;

public class ConflictedUpdateException extends SQLException {

	private static final long serialVersionUID = 5511960297609077859L;
	
	
	public ConflictedUpdateException(EntitySnapshot orginalEntity) {
		this("the entity of " + orginalEntity.getClass().getSimpleName() + " is already changed.");
	}

	public ConflictedUpdateException(String msg) {
		super(msg);
	}
	
}
