package org.slowcoders.storm.orm;

import org.slowcoders.util.Debug;
import org.slowcoders.storm.ORMColumn;

/**
 * Created by zeedh on 02/02/2018.
 */

public class OuterLink extends ORMField {
	
	private String function;
	private ORMColumn[] searchParams;
	private ForeignKey fk;

	OuterLink(String key, int flags, Class<?> type) {
		super(key, flags, type);
	}
	
	public OuterLink forNine(String columnName) {
		return this;
	}

	public long getUpdateBit() {
		return OUTER_LINK_UPDATE_BIT;
	}

	public boolean isDBColumn() {
		return false;
	}
	
	public final String getFunction() {
		return this.function;
	}
	
	final void setForeignKey(ForeignKey fk) {
		Debug.Assert(this.fk == null || fk == this.fk);
		Debug.Assert(fk.isVolatileData() == this.isVolatileData());
		this.fk = fk;
	}
	
	public OuterLink asOuterLink() {
		return this;
	}
	
	final ForeignKey getForeignKey() {
		return this.fk;
	}

	final void setSubQuery(String function, ORMColumn[] searchParams) {
		this.function = function;
		this.searchParams = searchParams != null && searchParams.length == 0 ? null : searchParams;
	}

	final ORMColumn[] getSearchParams() {
		return searchParams;
	}
}
