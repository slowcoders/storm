package org.slowcoders.storm.orm;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slowcoders.io.serialize.IOAdapter;
import org.slowcoders.storm.ORMColumn;
import org.slowcoders.storm.ORMEntity;
import org.slowcoders.util.Debug;

/**
 * Created by zeedh on 02/02/2018.
 */

public interface ORMFieldFactory {

	static ORMColumn _Column(String key, int flags, Class<?> columnType) {
		if (ORMEntity.class.isAssignableFrom(columnType)) {
			flags |= ORMFlags.Volatile;
		}
		ORMColumn slot = new ORMColumn(key, flags, columnType);
		return slot;
	}

	static ORMAlias _Alias(ORMColumn column, int flags) {
		ORMAlias slot = new ORMAlias(column, flags);
		return slot;
	}

	static ORMColumn _List(String key, int flags, Class<?> columnType) {
		ORMColumn slot = new GenericColumn(key, flags, List.class, new Class<?>[] { columnType });
		return slot;
	}

	static ORMColumn _Set(String key, int flags, Class<?> itemType) {
		Class<?> columnType = itemType.isEnum() ? EnumSet.class : Set.class;
		ORMColumn slot = new GenericColumn(key, flags, columnType, new Class<?>[] { columnType });
		return slot;
	}

	static ORMColumn _EnumSet(String key, int flags, Class<? extends Enum<?>> columnType) {
		ORMColumn slot = new GenericColumn(key, flags, EnumSet.class, new Class<?>[] { columnType });
		return slot;
	}

	static ORMColumn _Map(String key, int flags, Class<?> keyType, Class<?> valueType) {
		ORMColumn slot = new GenericColumn(key, flags, Map.class, new Class<?>[] { keyType, valueType });
		return slot;
	}

	static ORMColumn _ForeignKey(String key, int flags, Class<?> columnType) {
		ORMColumn slot = new ForeignKey(key, flags, columnType);
		return slot;
	}

	static ORMColumn _MasterForeignKey(int flags, Class<? extends ORMEntity> columnType) {
		ORMColumn slot = new MasterForeignKey(flags, columnType);
		return slot;
	}

	static <T> ORMColumn _Generic(String key, int flags, Class<T> type, Class<?> genericParam, IOAdapter adapter) {
		GenericColumn slot = new GenericColumn(key, flags, type, new Class<?>[] { genericParam });
		slot.setAdapter_unsafe(adapter);
		return slot;
	}

	static ORMColumn _Embedded(String key, int flags, Class<? extends ORMEntity> columnType) {
		ORMColumn slot = new ORMColumn(key, flags | ORMFlags.Embedded, columnType);
		return slot;
	}

	static OuterLink _SnapshotJoin(String key, int flags, Class<? extends ORMEntity> columnType) {
		OuterLink slot = new OuterLink(key, flags & ~ORMFlags.Volatile, columnType);
		return slot;
	}

	static OuterLink _VolatileJoin(String key, int flags, Class<? extends ORMEntity> columnType) {
		OuterLink slot = new OuterLink(key, flags | ORMFlags.Volatile | ORMFlags.Nullable, columnType);
		return slot;
	}
}
