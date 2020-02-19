package org.slowcoders.storm.orm;

import java.lang.annotation.RetentionPolicy;

/**
 * Created by zeedh on 02/02/2018.
 */

@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)
@java.lang.annotation.Retention(RetentionPolicy.RUNTIME)
public @interface UniqueConstraint {
	String[] value();
}
