package org.slowcoders.util;

public interface LinkedEntry<T> {

	T get();
	
	LinkedEntry<T> getNext();
	
}
