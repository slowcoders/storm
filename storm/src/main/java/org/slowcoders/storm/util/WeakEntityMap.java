package org.slowcoders.storm.util;

import org.slowcoders.storm.EntityReference;
import org.slowcoders.util.WeakValueMap;

public class WeakEntityMap<K, E extends EntityReference> extends WeakValueMap<K, E> {

    public synchronized E get(Object key) {
        E ref = super.get(key);
        if (ref != null && ref.isDeleted()) {
            super.remove(key);
            return null;
        }
        return ref;
    }
}