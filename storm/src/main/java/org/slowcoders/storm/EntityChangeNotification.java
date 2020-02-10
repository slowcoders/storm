package org.slowcoders.storm;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.slowcoders.observable.ChangeType;
import org.slowcoders.util.Debug;
import org.slowcoders.storm.StormTable.Observer;
import org.slowcoders.storm.orm.ORMField;
import org.slowcoders.util.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EntityChangeNotification<REF extends EntityReference> implements WeakRefQ.Visitor<Observer<REF>> {
//	public enum Type {
//		  Create,
//		  Update,
//		  Delete,
//		  OuterLinkChanged,
//	}
//

	private ChangeType changeType;
	private REF ref;
	private long modifyFlags;
	private EntityEditor editor;

	private EntityChangeNotification(REF replacedEntityRef, long modifyFlags) {
		this.ref = replacedEntityRef;
		this.modifyFlags = modifyFlags;
	}

	static <T extends EntityReference> EntityChangeNotification<T> inserted(T ref, EntityEditor editor) {
		EntityChangeNotification<T> noti = new EntityChangeNotification<>(ref, 0);
		noti.editor = editor;
		noti.changeType = ChangeType.Create;
		return noti;
	}
	
	static <T extends EntityReference> EntityChangeNotification<T> updated(T ref, EntityEditor editor) {
		EntityChangeNotification<T> noti = updated(ref, editor.getUpdateFlags());
		noti.editor = editor;
		return noti;
	}
	static <T extends EntityReference> EntityChangeNotification<T> updated(T ref, long modifyFlags) {
		EntityChangeNotification<T> noti = new EntityChangeNotification<>(ref, modifyFlags);
		noti.changeType = ChangeType.Update;
		return noti;
	}
	
	public static <T extends EntityReference> EntityChangeNotification<T> deleted(T ref) {
		EntityChangeNotification<T> noti = new EntityChangeNotification<>(ref, -1);
		noti.changeType = ChangeType.Delete;
		return noti;
	}
	
	final void sendNotification() {
//		switch (changeType) {
//			case Create:
//				EntitySnapshot data = ref.tryLoadSnapshot();
//				if (data != null) {
//					ref.onCreated_RT();
//				}
//				else {
//					NPDebug.trap();
//					// 그 사이에 삭제되었다??
//				}
//				break;
//			case Delete:
//				ref.onDeleted_RT();
//				break;
//			case Update:
//				this.ref.onUpdated_RT(this.modifyFlags);
//				break;
//			default:
//		}
		ref.notifyObserver(changeType);
		ref.getTable().delegate.postNotification(this);
	}

	public final ChangeType getChangeType() {
		return this.changeType;
	}
	
	public long getModifyFlags() {
		return this.modifyFlags;
	}

	public REF getEntityReference() {
		return ref;
	}

	final void rollback() {
		switch (changeType) {
		case Create:
			this.ref.getTable().removeDeletedReference(ref);
//			EntitySnapshot data = this.mutableSnapshot;
//			if (data != null) {
//				data.setMutableReference_unsafe(null);
//			}
			/**
			 * before entity is created,
			 * it is not possible to get access to ref.
			 * so we do not need additional invalidation process
			 */
			break;
		default:
		}
	}


	protected final void updateVolatileColumns_RT() {

		EntitySnapshot snapshot = ref.getAttachedSnapshot();
		if (snapshot == null) return;

		for (Map.Entry<ORMField, Object> entry : editor.getEditMap().entrySet()) {
			ORMField column = entry.getKey();
			if (!column.isDBColumn()) continue;
			if (column.isVolatileData()) {
				try {
					Field f = column.getReflectionField();
					Object v = entry.getValue();
					if (v != null) {
						if (column.isList()) {
							v = ImmutableList.copyOf((List)v);
						} else if (column.isSet()) {
							v = ImmutableSet.copyOf((Set)v);
						} else if (column.isMap()) {
							v = ImmutableMap.copyOf((Map)v);
						}
					}
					f.set(snapshot, v);
				} catch (Exception e) {
					Debug.wtf(e);
				}
			}
		}
	}

	@Override
	public boolean visit(StormTable.Observer<REF> listener) {
		listener.onEntityChanged(this);
		return true;
	}

	void clearEditMap() {
		if (editor != null) {
			editor.clearEditMap();
		}
	}
}