package org.slowcoders.storm.orm;

import org.slowcoders.io.serialize.IOAdapter;
import org.slowcoders.storm.ORMColumn;

/**
 * Created by zeedh on 02/02/2018.
 */

public class GenericColumn extends ORMColumn {
	private final Class<?>[] genericParams;
	
	public GenericColumn(String key, int flags, Class<?> type, Class<?>[] genericParams) {
		super(key, flags, type);
		this.genericParams = genericParams;
	}

	protected void setAdapter_unsafe(IOAdapter adapter) {
		super.setAdapter_unsafe(adapter);
	}
	
	public Class<?> getFirstGenericParameterType() {
		return genericParams[0];
	}

	public Class<?>[] getGenericParameters() {
		return genericParams;
	}

	public GenericColumn forNine(String columnName) {
		return this;
	}
	
	public GenericColumn ewsVer(double value) {
		super.ewsVer(value);
		return this;
	}
}
