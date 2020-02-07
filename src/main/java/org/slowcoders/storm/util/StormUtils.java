package org.slowcoders.storm.util;

import java.util.*;

public interface StormUtils {

    static boolean equals(Object a, Object b) {
        return Objects.equals(a, b);
    }

    static boolean contentEquals(Collection<?> a, Collection<?> b) {
        int a_size = a == null ? 0 : a.size();
        int b_size = b == null ? 0 : b.size();
        if (a_size != b_size) {
            return false;
        }
        return a_size == 0 || a.equals(b);
    }

    static <T> ArrayList<T> toMutableList(Collection<T> items) {
        ArrayList<T> list = new ArrayList<>();
        if (items != null) {
            list.addAll(items);
        }
        return list;
    }

    static <T> HashSet<T> toMutableSet(Collection<T> items) {
        HashSet<T> list = new HashSet<>();
        if (items != null) {
            list.addAll(items);
        }
        return list;
    }

    static <K,V> HashMap<K,V> toMutableMap(Map<K,V> items) {
        HashMap<K,V> list = new HashMap<>();
        if (items != null) {
            list.putAll(items);
        }
        return list;
    }

    static <T extends Enum<T>> EnumSet<T> toMutableEnumSet(Iterable<T> items, Class<T> enumClass) {
        EnumSet<T> list = EnumSet.noneOf(enumClass);
        if (items != null) {
            for (Iterator<T> it = items.iterator(); it.hasNext();) {
                list.add(it.next());
            }
        }
        return list;
    }
}
