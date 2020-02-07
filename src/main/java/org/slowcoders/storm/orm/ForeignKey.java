package org.slowcoders.storm.orm;

import org.slowcoders.storm.ORMColumn;

/**
 * Created by zeedh on 02/02/2018.
 */

public class ForeignKey extends ORMColumn {

	public ForeignKey(String key, int flags, Class<?> type) {
		/**
		 * 일단, ForeignKey 변경을 불허한다 -> Join 상태가 깨지는 것은 삭제에 의해서만 발생.
		 * 추후 ForeignKey 변경을 허용해야 하는 경우, FK가 변경 시 기존 상위 entity에는 삭제, 
		 * 새로운 상위 Entity에는 추가 Notification을 전달하여 캐시 정보 무결성을 유지해야 한다.
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
