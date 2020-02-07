package org.slowcoders.storm.orm;

import java.lang.annotation.RetentionPolicy;

/**
 * Created by zeedh on 02/02/2018.
 * 
 * 
 * TODO tableName -> tables 로 변경하고, Entity와 table의 관계를 동적으로 설정.
 */

@java.lang.annotation.Retention(RetentionPolicy.RUNTIME)
public @interface TableDefinition {

	enum CacheType {
		Weak,
		Soft,
		Strong
	}
	
	String tableName() default "";
	
	long rowIdStart() default 0;
	
	CacheType entityCacheType() default CacheType.Soft;

	boolean isFts() default false;

//	String hiddenColumns() default "";
//
//	String extendedModel() default "";
}
