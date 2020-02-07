package org.slowcoders.storm.orm;

import java.lang.annotation.RetentionPolicy;

@java.lang.annotation.Retention(RetentionPolicy.RUNTIME)
public @interface Where {
	
	String value();	
}