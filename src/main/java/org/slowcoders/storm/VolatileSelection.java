package org.slowcoders.storm;

import com.google.common.collect.ImmutableList;
import org.slowcoders.observable.ChangeType;
import org.slowcoders.util.Debug;
import org.slowcoders.util.RefList;

import java.sql.SQLException;
import java.util.Arrays;

/*internal*/ class VolatileSelection<T extends EntityReference> implements StormTable.Observer {

	private final StormView rowSet;
    private T refs[];
    private int mCount;
    private ImmutableList<T> immutableCopy = notShared;
    private RefList<ObservableCachedEntities> observers = new RefList<>();
    private static ImmutableList notShared = ImmutableList.of();

	VolatileSelection(StormView<?, T, ?, ?> rowSet) throws SQLException {
		super();
		T[] ids = rowSet.getEntityIDs();

		this.rowSet = rowSet;
		this.refs = ids;
		this.mCount = ids.length;
		getTable().addWeakAsyncObserver(this);
		if (!rowSet.canResolveNextEntity()) {
			getTable().addRealtimeObserver(new StormTable.Observer() {
				@Override
				public void onEntityChanged(EntityChangeNotification noti) {
					rowSet.invalidateVolatileSelection(VolatileSelection.this);
				}
			});
		}
	}

	public synchronized final ImmutableList<T> getReferences() {
		if (this.immutableCopy == notShared) {
			this.immutableCopy = new ImmutableReferenceList<>(refs, mCount);
		}
		return this.immutableCopy;
	}

	private boolean unbindSharedCopy() {
		if (this.immutableCopy != notShared) {
			this.immutableCopy = notShared;
			return true;
		}
		return false;
	}


	public final StormTable getTable() {
    	return this.rowSet.getTable();
	}

//	final T getEntityReference(long id) {
//		return (T)rowSet.getTable().makeEntityReference(id);
//	}

	public synchronized T get(int idx) {
    	rangeCheck(idx);
        T ref = refs[idx];
        return ref;
    }

    protected final void rangeCheck(int index) {
        if (index >= mCount)
            throw new IndexOutOfBoundsException("Index: "+index+", Size: "+ mCount);
    }

	public int indexOfEntity(long entityId) {
		for (int index = 0; index < mCount; index++) {
			T ref = refs[index];
			if (ref.getEntityId() == entityId) {
				return index;
			}
		}
		return -1;
	}

	public int indexOfEntity(T entityRef) {
        for (int index = 0; index < mCount; index++) {
        	T ref = refs[index];
        	if (ref == entityRef) {
                return index;
            }
    	}
        return -1;
	}

	final void bind(ObservableCachedEntities entities) {
		observers.addLast_asWeakRef(entities);
	}

	public void unbind(ObservableCachedEntities ts) {
		observers.remove(ts);
	}


	private synchronized int addEntity(T ref, int idx) {

		int count = this.size();
		ensureCapacity2(count+2);
		Debug.Assert(idx >= 0);
		if (idx < count) {
			System.arraycopy(refs, idx, refs, idx+1, count-idx);
		}
		refs[idx] = ref;
		this.mCount = (count+1);
		return idx;
	}

	private synchronized int moveEntity(T ref, int idx, int idx2) {
		if (idx == idx2) {
			return idx;
		}
		this.removeEntity(ref);

		int pos = idx > idx2 ? idx2 : idx2 - 1;
		this.addEntity(ref, pos);
		return pos;
	}

	private synchronized int removeEntity(T entity) {
		int index = indexOfEntity(entity);
		if (index < 0) {
			return -1;
		}

		Debug.Assert(this.mCount > 0);
		this.mCount --;
		int numMoved = this.mCount - index;
		if (numMoved > 0) {
			EntityReference target[];
			if (this.unbindSharedCopy()) {
				target = new EntityReference[this.mCount];
				System.arraycopy(refs, 0, target, 0, index);
			} else {
				target = refs;
			}
			System.arraycopy(refs, index + 1, target, index, numMoved);
			this.refs = (T[])target;
		}
		return index;
	}

	final void ensureCapacity2(int minCapacity) {
		if (this.unbindSharedCopy() || minCapacity - refs.length > 0) {
			if (minCapacity < 16) {
				minCapacity = 16;
			}
			int oldCapacity = refs.length;
			int newCapacity = minCapacity + (oldCapacity / 2);
			refs = Arrays.copyOf(refs, newCapacity);
		}
	}


	public int size() {
		return mCount;
	}

	private void invalidateCachedEntities() {
		try {
			this.unbindSharedCopy();
			this.refs = (T[]) rowSet.getEntityIDs();
			this.mCount = refs.length;
		} catch (SQLException e) {
			throw Debug.wtf(e);
		}
		for (ObservableCachedEntities observer : observers) {
			observer.notifyEntireChanged();
		}
	}

	public void onEntityChanged(EntityChangeNotification noti_) {
		T ref = (T)noti_.getEntityReference();
		if (!rowSet.maybeContains(ref)) {
			return;
		}

		/**
		 * @zee
		 * progress below cannot be done asynchronously.
		 * if getIndexOfNextEntity() is called asynchronously,
		 * id which is not yet added to list can be returned
		 */
		int idx, movedIndex = -1;
		ChangeType type = noti_.getChangeType();
		EntitySnapshot entity;
		switch (type) {
			case Create:
				if (!rowSet.canResolveNextEntity()) {
					invalidateCachedEntities();
					return;
				}
				entity = ref.tryLoadSnapshot();
				if (entity == null) {
					return;
				}
				idx = rowSet.getIndexOfNextEntity(entity, refs, this.mCount);
				if (idx >= 0) {
					this.addEntity(ref, idx);
				}
				break;

			case Update:
				if (!rowSet.canResolveNextEntity()) {
					invalidateCachedEntities();
				}
				idx = this.indexOfEntity(ref);
				if (idx >= 0 && rowSet.canResolveNextEntity()) {
					entity = ref.tryLoadSnapshot();
					movedIndex = rowSet.getIndexOfNextEntity(entity, refs, this.mCount);
					if (ref.isDeleted() || movedIndex < 0) {
						idx = this.removeEntity(ref);
						type = ChangeType.Delete;
					} else if (idx != movedIndex - 1){
						movedIndex = moveEntity(ref, idx, movedIndex);
						type = ChangeType.Move;
					}
				}
				break;

			case Delete:
				idx = this.removeEntity(ref);
				break;

			default:
				idx = 0;
				break;
		}

		if (idx >= 0) {
			for (ObservableCachedEntities observer : observers) {
				observer.notifyDataChanged(ref, type, idx, movedIndex);
			}
		}

	}

	protected final void deleteInternal() throws InvalidEntityReferenceException, SQLException {
		StormTable table = this.getTable();
		for (int index = 0; index < mCount; index++) {
			EntityReference ref = refs[index];
			ref.onDelete_inTR();
		}
	}


	public EntitySnapshot[] loadSnapshots(int offset, int count) {
		if (offset + count > this.mCount) {
			count = this.mCount - offset;
		}
		StormTable table = this.getTable();
		return table.loadSnapshots(this.refs, offset, count);
	}
}