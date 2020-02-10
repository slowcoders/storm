package org.slowcoders.storm;

public interface EntityFilter<T extends ORMEntity> {
	boolean matches(T ref);
}