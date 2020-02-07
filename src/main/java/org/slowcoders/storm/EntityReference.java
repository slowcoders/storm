package org.slowcoders.storm;

import java.lang.ref.SoftReference;
import java.sql.SQLException;

import org.slowcoders.io.util.NPAsyncScheduler;
import org.slowcoders.io.util.UITask;
import org.joda.time.ReadableInstant;
import org.slowcoders.observable.ChangeType;
import org.slowcoders.util.Debug;


public abstract class EntityReference implements ORMEntity {

	private long entityId;
	private SoftReference<EntitySnapshot> cache;
	private Object[] ghostData;

	private long lastModifiedTime;

	private Observer observer;

	public EntityReference(long entityId) {
		this.entityId = entityId;
	}

	public EntityReference(long entityId, EntitySnapshot snapshot) {
		this(entityId);
		snapshot.setReference_unsafe(this);
	}

	public abstract StormTable getTable();

	public abstract EntitySnapshot loadSnapshot();

	public abstract EntitySnapshot tryLoadSnapshot();

	public final long getEntityId() {
		return this.entityId;
	}

	public final EntityReference getEntityReference() {
		return this;
	}

	public final long lastModifiedTime() {
		return this.lastModifiedTime;
	}
	
	public final EntitySnapshot getAttachedSnapshot() {
		if (this.cache == null) {
			return null;
		}
		EntitySnapshot snapshot = this.cache.get();
		if (snapshot == null || snapshot.getReference_internal() == null) {
			return null;
		}
		return snapshot;
	}


	final Object[] getGhostData_unsafe() {
		return this.ghostData;
	}

	public final boolean isDeleted() {
		return this.entityId <= 0;
	}

	public static ORMHelper createORMHelper() {
		return ORMHelper.defaultInstance;
	}

	public boolean equals(Object other) {
		return this == other;
	}

	public String toString() {
		long id = -this.getEntityId();
		return this.getTable().getTableName() + id;
	}

	public ORMEntity getStormEntity(StormTable table) {
		Debug.Assert(this.getTable() == table);
		return this;
	}


	/**
	 * @param ghostData**************************************/
	/*  Internal methods                    */
	/*--------------------------------------*/
	final void setGhostData(Object[] ghostData) {
		// called before delete
		//
		// we store uniques values as ghostData in memory
		// and delete them from database
		// so that another entity can be created
		// with same uniques values
		this.ghostData = ghostData;
	}

	synchronized final void markDeleted() {
		// called after delete
		Debug.Assert(this.entityId > 0);
		this.entityId = -this.entityId;
	}

	final void setLastModifiedTime(long time)  {
		this.lastModifiedTime = time;
	}

	protected void deleteEntity() throws SQLException, InvalidEntityReferenceException {}

	private static TransactionalOperation<EntityReference> opDelete = new TransactionalOperation<EntityReference>() {
		@Override
		protected Object execute_inTR(EntityReference ref, long transactionId) throws SQLException {
			ref.onDelete_inTR();
			return null;
		}
	};

	protected final void doDelete() throws SQLException, InvalidEntityReferenceException {
		StormDatabase db = this.getTable().getDatabase();
		db.executeInLocalTransaction(opDelete, this);
	}

	public final boolean isForeignKeyLoaded() {
		return this.lastModifiedTime > 0;
	}

	protected final void doLoadForeignKeys() {
		if (!this.isForeignKeyLoaded()) {
			try {
				this.getTable().loadForeignKeys(this);
			} catch (SQLException e) {
				Debug.ignoreException(e);
			}
		}
	}


	protected void onDelete_inTR() throws SQLException, InvalidEntityReferenceException {
		StormTable table = getTable();
		table.delete_inTR(this);
	}

	protected static void onDelete_inTR(EntityReference ref) throws SQLException, InvalidEntityReferenceException {
		if (ref != null && !ref.isDeleted()) {
			ref.onDelete_inTR();
		}
	}

	protected static void onDelete_inTR(StormFilter rowSet) throws SQLException, InvalidEntityReferenceException {
		rowSet.delete_inTR();
	}

	final void markForeignKeyLoaded() {
		this.lastModifiedTime = 1;
	}

	protected final EntityReference loadMasterForeignKey() {
		return getTable().loadMasterForeignKey(this);
	}


	public static class ORMHelper {
        static ORMHelper defaultInstance = new ORMHelper();

		protected long getNewEntityId(EntityEditor editor) {
			throw Debug.notImplemented();
		}

		protected void onTableCreated(StormTable table) throws SQLException {}

		protected  void validateBeforeSaveEntity_inTR(EntityEditor edit) throws RuntimeException { edit.validate_inTR(); }
	}

	public static class DebugUtil {

		public static final boolean DEBUG = Debug.DEBUG;

		public static void assertNullOrMatch(Object a, Object b) {
			Debug.Assert(a == null || a.equals(b));
		}

		public static void assertDeletedOrMatch(ORMEntity a, Object b) {
			Debug.Assert(a == null || a.getEntityReference().isDeleted() || a.equals(b));
		}

		public static void assertNullOrMatch(ReadableInstant a, ReadableInstant b) {
			Debug.Assert(a == null || (a.getMillis() / 1000) == (b.getMillis() / 1000));
		}

		public static void assertNullOrMatch(boolean a, boolean b) {
			Debug.Assert(!a || a == b);
		}

		public static void assertNullOrMatch(Number a, Number b) {
			Debug.Assert(a.doubleValue() == 0 || a.equals(b));
		}

		public static void clearSnapshot(EntityReference ref) {
			ref.cache = null;
		}

	}



	synchronized final void attachEntity_RT(EntitySnapshot entity) {
		if (Debug.DEBUG) {
			EntitySnapshot old = this.getAttachedSnapshot();
			Debug.Assert(old == null || (entity == old && entity.getReference_internal() == this));
		}
		if (!isForeignKeyLoaded() || DebugUtil.DEBUG) {
			if (this.lastModifiedTime <= 0) {
				this.lastModifiedTime = 1;
			}
			this.validateForeignKey_RT(entity);
			Debug.Assert(isForeignKeyLoaded());
		}
		this.cache = new SoftReference<>(entity);
		this.notifyAll();
	}

	public synchronized boolean waitUpdate(long until) {
		SoftReference<EntitySnapshot> old = this.cache;
		while (old == this.cache) {
			try {
				if (until < 0) {
					this.wait();
				}
				else {
					long timeLimit = until - System.currentTimeMillis();
					if (timeLimit <= 0) {
						return false;
					}
					this.wait(timeLimit);
				}
			} catch (InterruptedException e) {
				return false;
			}
		}
		return true;
	}

	protected synchronized final EntitySnapshot invalidateEntityCache_RT(ChangeType reason) {
		/**
		 * this function is called before committing changes to database.
		 * we have to inform snapshot-joined foreign keys to load snapshot
		 * to make sure foreign keys hold snapshot before update
		 *
		 * we must first invalidate foreign key entity first, then delete cache
		 *
		 */
		this.doLoadForeignKeys();
		EntitySnapshot old = this.getAttachedSnapshot();
		this.invalidateForeignEntityCache_RT(reason);
		if (old != null) {
			this.cache = null;
		}
		return old;
	}

	protected void invalidateForeignEntityCache_RT(ChangeType reason) {}

	protected abstract void validateForeignKey_RT(EntitySnapshot entity);

	protected final EntitySnapshot doTryLoadSnapshot() {
		EntitySnapshot snapshot = getAttachedSnapshot();
		if (snapshot == null && !this.isDeleted()) {
			try {
				snapshot = this.doLoadSnapshot();
			}
			catch (InvalidEntityReferenceException e) {
				// ignore
			}
			catch (Exception e) {
				Debug.ignoreException(e);
			}
		}
		return snapshot;
	}

	protected final EntitySnapshot doLoadSnapshot() {
		EntitySnapshot snapshot = this.getAttachedSnapshot();
		if (snapshot != null) {
			return snapshot;
		}
		StormTable<?,?,?> table = this.getTable();
		snapshot = table.doLoadSnapshot(this, true);
		return snapshot;
	}

	public interface Observer {
		void onEntityChanged(EntityReference ref, ChangeType type);
	}

	public final Observer getAsyncObserver(){
		return this.observer;
	}

	public final void setAsyncObserver(Observer observer){
		this.observer = observer;
	}

	final void notifyObserver(ChangeType type){
		if (this.observer == null){
			return;
		}
		NPAsyncScheduler.executeLater(new UITask() {
			@Override
			public void executeTask() throws Exception {
				Observer ob = EntityReference.this.observer;
				if (ob != null){
					ob.onEntityChanged(EntityReference.this, type);
				}
			}
		});

	}

	public final int hashCode() {
		return (int)(this.entityId >= 0 ? this.entityId : -this.entityId);
	}

	protected void finalize(){
		if (isDeleted()) {
			getTable().removeGhost(entityId);
		}
	}

}