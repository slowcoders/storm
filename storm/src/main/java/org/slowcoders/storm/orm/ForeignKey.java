package org.slowcoders.storm.orm;

import org.slowcoders.storm.ORMColumn;

/**
 * Created by zeedh on 02/02/2018.
 */

public class ForeignKey extends ORMColumn {

	public ForeignKey(String key, int flags, Class<?> type) {
		/**
		 * For now, it is not allowed to change ForeignKey.
		 * Join relationship can be broken only when item is deleted.
		 *
		 * But later, if it should be allowed, when foreign key is changed,
		 * old master entity needs to clear cached joined item and
		 * new master entity has to get notified to update its cache
		 */
		super(key, flags | ORMFlags.Immutable, type);
	}
	
	public ForeignKey ewsVer(double value) {
		super.ewsVer(value);
		return this;
	}

	public void bind(OuterLink outerLink) {
		int t = this.getAccessFlags();
		if (outerLink.isVolatileData()) {
			t |= ORMFlags.Volatile;
		}
		else {
			t &= ~ORMFlags.Volatile;
		}
		this.setAccessFlags(t);
		outerLink.setForeignKey(this);
	}

	public final boolean isForeignKey() {
		return true;
	}
	
	public final ForeignKey asForeignKey() {
		return this;
	}

}
