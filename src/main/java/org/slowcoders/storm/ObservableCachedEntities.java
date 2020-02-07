package org.slowcoders.storm;

import com.google.common.collect.ImmutableList;
import org.slowcoders.io.util.NPAsyncScheduler;
import org.slowcoders.observable.ChangeType;
import org.slowcoders.util.Debug;
import org.slowcoders.storm.orm.ORMProxy;
import org.slowcoders.util.RefList;
import org.slowcoders.util.SoftValueMap;

import java.util.AbstractList;
import java.util.AbstractMap;

public abstract class ObservableCachedEntities<REF extends EntityReference, T extends ORMProxy> extends AbstractList<T> {

	private VolatileSelection<REF> selection;
	private final AbstractMap<REF, T> itemCache;
	private final RefList<Observer> observers = new RefList<>();
	private static final int BUCKET_SIZE = 256;
	private int currentBucket = -2;

	public interface Observer<REF extends EntityReference> {
		void onDataChanged(ChangeType type, int index, int movedIndex, REF ref);

		void onEntireChanged();
	}


	public ObservableCachedEntities() {
		this(null);
	}

	public ObservableCachedEntities(StormView filter) {
		this(filter, new SoftValueMap<>());
	}

	/*internal*/ ObservableCachedEntities(StormView filter, AbstractMap<REF, T> itemCache) {
		if (filter != null) {
			this.bindFilter(filter);
		}

		this.itemCache = itemCache;
	}

	/**
	 *
	 * @param filter
	 * @return false if the filter is already bound.
	 */
	public boolean bindFilter(StormView filter) {
		Debug.Assert(filter != null);
		synchronized (this) {
			VolatileSelection newSelection = filter.getFilteredSelection();
			if (newSelection == this.selection) {
				return false;
			}

			if (this.selection != null) {
				selection.unbind(this);
			}
			this.selection = newSelection;
			selection.bind(this);
		}
		this.clearCache();
		this.notifyEntireChanged();
		return true;
	}

	/*internal*/ final void notifyEntireChanged() {
		for (Observer observer : observers) {
			observer.onEntireChanged();
		}
	}

	@Override
	public final int size() {
		if (selection == null) return 0;
		return selection.size();
	}

	public synchronized T get(int idx) {
		if (selection == null) return null;
		if (this.size() > BUCKET_SIZE * 2) {
			loadSnapshotBucket(idx);
		}
		REF ref = selection.get(idx);
		T item = getCachedItem(ref);
		return item;
	}

	public boolean isPreFetchEnabled() {
		return false;
	}

	private void loadSnapshotBucket(int idx) {
		final int idxBucket = idx / BUCKET_SIZE;
		final int margin = BUCKET_SIZE / 4;
		if (idxBucket == currentBucket) {
			return;
		}
		if ((idx + margin) / BUCKET_SIZE == currentBucket
				|| (idx - margin) / BUCKET_SIZE == currentBucket) {
			return;
		}

		int oldBucket = this.currentBucket;
		this.currentBucket = idxBucket;
		int dir = (idxBucket - oldBucket > 0) ? +1 : -1;

		loadBucket(currentBucket, oldBucket, false);

		if (isPreFetchEnabled()) {
			new Thread() {
				@Override
				public void run() {
					loadBucket(currentBucket + dir, oldBucket, true);
					loadBucket(currentBucket - dir, oldBucket, true);
				}
			}.start();

		}
	}

	private void loadBucket(int bucket, int cachedBucket, boolean isBackground) {
		if (bucket < 0) {
			return;
		}
		if (bucket < cachedBucket - 1 || bucket > cachedBucket + 1) {
			int offset = bucket * BUCKET_SIZE;
			if (offset < size()) {
				EntitySnapshot[] snapshots = selection.loadSnapshots(offset, BUCKET_SIZE);
				if (snapshots != null) {
					for (EntitySnapshot snapshot : snapshots) {
						this.getCachedItem((REF) snapshot.getEntityReference());
					}
				}
			}
		}
	}

	public synchronized int indexOfEntity(long entityId) {
		if (selection == null) return -1;
		int idx = selection.indexOfEntity(entityId);
		return idx;
	}

	public synchronized REF getReference(int idx) {
		if (selection == null) return null;
		REF ref = selection.get(idx);
		return (REF)ref;
	}

	public Observer addAsyncObserver(Observer listener) {
		this.observers.addLast(listener);
		return listener;
	}

	public Observer addWeakAsyncObserver(Observer listener) {
		this.observers.addLast_asWeakRef(listener);
		return listener;
	}

	public boolean removeObserver(Observer observer) {
		return this.observers.remove(observer);
	}

	public int indexOf(Object item) {
		try {
			EntityReference ref = ((T)item).getStormEntity(selection.getTable()).getEntityReference();
			return selection.indexOfEntity((ref.getEntityId()));
		}
		catch (Exception e) {
			throw Debug.wtf(e);
		}
	}

	final void notifyDataChanged(REF ref, ChangeType type, int idx, int movedIndex) {
		if (type == ChangeType.WholeListChanged) {
			this.clearCache();
		} else {
			removeCache(ref);
		}

		for (Observer observer : observers) {
			observer.onDataChanged(type, idx, movedIndex, ref);
		}
	}

	protected void removeCache(REF ref) {
		synchronized (itemCache) {
			itemCache.remove(ref);
		}

	}

	protected T getCachedItem(REF ref) {
		T item = itemCache.get(ref);
		if (item != null) {
			return item;
		}
		T newItem = this.createItem(ref);
		synchronized (itemCache) {
			item = itemCache.get(ref);
			if (item == null) {
				item = newItem;
				itemCache.put(ref, item);
			}
			return item;
		}
	}

	protected abstract T createItem(REF ref);

	protected void clearCache() {
		synchronized (itemCache) {
			this.itemCache.clear();
		}
	}

	public void processPendingNotifications() {
		NPAsyncScheduler.executePendingTasks();
	}

	public final ImmutableList<REF> toImmutableList() {
		return selection.getReferences();
	}


	public static abstract class EntityList<T extends ORMEntity> extends ObservableCachedEntities<EntityReference, T> {

		public EntityList() {
			this(null);
		}

		public EntityList(StormView filter) {
			super(filter, null);
		}

		public final T createItem(EntityReference ref) { throw Debug.shouldNotBeHere(); }

		protected final void removeCache(EntityReference ref) { /* Do nothing */ }

		protected abstract T getCachedItem(EntityReference ref);

		protected final void clearCache() { /* Do nothing*/ }

	}

	public static class ReferenceList<T extends EntityReference> extends EntityList<T> {

		public ReferenceList() {
			this(null);
		}

		public ReferenceList(StormView filter) {
			super(filter);
		}

		protected T getCachedItem(EntityReference ref) {
			return (T)ref;
		}

	}

	public static class SnapshotList<T extends EntitySnapshot> extends EntityList<T> {

		public SnapshotList() {
			super();
		}

		public SnapshotList(StormView filter) {
			super(filter);
		}

		protected T getCachedItem(EntityReference ref) {
			return (T)ref.loadSnapshot();
		}


	}
}