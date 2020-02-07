package org.slowcoders.storm.util;

import java.util.Set;

import org.slowcoders.storm.EntityReference;
import org.slowcoders.util.Debug;
import org.slowcoders.util.SoftValueMap;

public class SoftEntityMap<K, E extends EntityReference> extends SoftValueMap<K, E> {

	@Override
	public Set<Entry<K, E>> entrySet() {
		throw Debug.notImplemented();//return (Set<Entry<K, E>>)(Set)map.entrySet();
	}

	public synchronized E get(Object key) {
		E ref = super.get(key);
		if (ref != null && ref.isDeleted()) {
			super.remove(key);
			return null;
		}
		return ref;
	}

}