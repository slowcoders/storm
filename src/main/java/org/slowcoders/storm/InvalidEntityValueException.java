package org.slowcoders.storm;

import org.slowcoders.util.Debug;

public class InvalidEntityValueException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public InvalidEntityValueException(String msg) {
		super(msg);
		Debug.trap();
	}

}
