package org.slowcoders.storm;

import org.slowcoders.io.serialize.*;
import org.slowcoders.observable.ChangeType;
import org.slowcoders.util.SoftValueMap;
import org.slowcoders.util.WeakValueMap;
import org.slowcoders.io.util.NPAsyncScheduler;
import org.slowcoders.util.Debug;
import org.slowcoders.observable.AbstractObservable;
import org.slowcoders.observable.Observable;
import org.slowcoders.storm.orm.*;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.*;

public abstract class StormTable<SNAPSHOT extends EntitySnapshot,
							  REF extends EntityReference,
							  EDITOR extends EntityEditor>
		extends StormView<SNAPSHOT, REF, EDITOR, ORMEntity.UpdateForm>
		implements Observable<StormTable.Observer<REF>>, QueryMethod.EntityResolver {

	private ORMColumn[] dbColumns;
	private ORMField[] ormFields;
	private ORMColumn[] foreignKeys;
	private ORMColumn masterForeignKey;
	private ORMColumn[] ghostColumns;

	private AbstractMap<Object, REF> refCache;
	private final String tableName;
	private final Config config;
	private final SortableColumn[] defaultSort;
	private final Ref_Adapter entityAdapter;
	private long dirtyUpdateBits;

	private static final HashMap<Class<?>, String> defaultColumnTypes;

	protected final AbstractObservable<Observer<REF>> delegate = new AbstractObservable<Observer<REF>>() {
		@Override
		protected final void doNotify(Observer<REF> observer, Object noti) {
			observer.onEntityChanged((EntityChangeNotification<REF>)noti);
		}
	};



	public interface Observer<T extends EntityReference> {
		void onEntityChanged(EntityChangeNotification<T> noti);
	}

	public interface AbstractEntityResolver {
		StormTable<?,?,?> getTable(long id);
	}

	public static class Config {
		public long rowIdStart;
		public EntityReference.ORMHelper helper;
		public Class<?> snapshotType;
		public Class<?> referenceType;
		public TableDefinition.CacheType entityCache;
		public boolean isFts;
	}

	public StormTable(String tableName) {
		super(null, null);
		this.entityAdapter = new Ref_Adapter(this);
		this.config = new Config();
		this.getTableConfiguration(config);
		this.tableName = tableName;

		this.defaultSort = new SortableColumn[] { new IDSlot().createSortableColumn(this, false) };
	}

	@Override
	public Observer<REF> addAsyncObserver(Observer<REF> observer) {
		return delegate.addAsyncObserver(observer);
	}

	@Override
	public Observer<REF> addRealtimeObserver(Observer<REF> observer) {
		return delegate.addRealtimeObserver(observer);
	}

	@Override
	public Observer<REF> addWeakAsyncObserver(Observer<REF> observer) {
		return delegate.addWeakAsyncObserver(observer);
	}

	@Override
	public Observer<REF> addWeakRealtimeObserver(Observer<REF> observer) {
		return delegate.addWeakRealtimeObserver(observer);
	}

	@Override
	public boolean removeObserver(Observer<REF> observer) {
		return delegate.removeObserver(observer);
	}
	
	private static class IDSlot extends ORMColumn {
		
		protected IDSlot() {
			super("rowid", 0, long.class);
			try {
				super.setReflectionField(EntitySnapshot.class.getDeclaredField("ref"));
				this.setAdapter_unsafe(ID_Converter.instance);
			} catch (NoSuchFieldException | SecurityException e) {
				Debug.wtf(e);
			}
		}
	}

	final EntityReference.ORMHelper getORMHelper() {
		return this.config.helper;
	}

	protected void initTable() throws Exception {
		long dirtyUpdateBits = 0;
		for (ORMField f : this.ormFields) {
			if (f.isDBColumn() && !f.isVolatileData()) {
				dirtyUpdateBits |= f.getUpdateBit();
			}
		}
		this.dirtyUpdateBits = dirtyUpdateBits;
	}

	private ArrayList<Class> getORMClasses() {
		ArrayList<Class> ormClasses = new ArrayList<>();
		Class c = getORMDefinition();

		while (c != ORMEntity.class) {
			ormClasses.add(c);

			Class<?>[] interfaces = c.getInterfaces();
			for (Class inf : interfaces) {
				if (ORMEntity.class.isAssignableFrom(inf)) {
					c = inf;
				}
			}
		}
		return ormClasses;
	}
	
	public void initColumns() throws Exception {
		ArrayList<ORMField> ormFields = new ArrayList<>();
		ArrayList<ORMAlias> aliases = new ArrayList<>();
		ArrayList<ORMField> dbColumns = new ArrayList<>();
		ArrayList<ORMField> foreignKeys = new ArrayList<>();
		ArrayList<ORMField> ghostColumns = new ArrayList<>();

		/**
		 *  to keep indexes of ORMColumns constant,
		 *  we register fields from highest ORMEntity
		 *  hierarchy to lower
		 * */
		ArrayList<Class> list = getORMClasses();
		for (int i = list.size(); --i >= 0; ) {
			Class klass = list.get(i);
			Field[] fields = klass.getDeclaredFields();
			for (Field f : fields) {
				if (ORMField.class.isAssignableFrom(f.getType())) {
					ORMField ormField = (ORMField) f.get(null);
					if (ormField.isAlias()) {
						aliases.add((ORMAlias) ormField);
						continue;
					}

					ormFields.add(ormField);
					if (!ormField.isDBColumn()) {
						continue;
					}
					dbColumns.add(ormField);
					if (ormField.isMasterForeignKey()) {
						if (this.masterForeignKey != null) {
							throw new RuntimeException("Each table can only have one masterForeignKey - " + getTableName());
						}
						this.masterForeignKey = (ORMColumn)ormField;
					} else if (ormField.isForeignKey()) {
						foreignKeys.add(ormField);
					}
				}
			}
		}

		this.ormFields = ormFields.toArray(new ORMField[ormFields.size()]);
		ORMField.markUniqueParts(this.getORMDefinition(), this.ormFields);
		for (ORMField field : dbColumns) {
			if (field.isGhostColumn()) {
				ghostColumns.add(field);
			}
		}
		this.ghostColumns = ghostColumns.toArray(new ORMColumn[ghostColumns.size()]);
		this.dbColumns = dbColumns.toArray(new ORMColumn[dbColumns.size()]);

		this.foreignKeys = foreignKeys.toArray(new ORMColumn[foreignKeys.size()]);

		switch (config.entityCache) {
			case Weak:
				this.refCache = new WeakValueMap<>();
				break;
			case Soft:
				this.refCache = new SoftValueMap<>();
				break;
			case Strong:
				this.refCache = new HashMap<>();
				break;
			default:
				throw Debug.shouldNotBeHere();
		}

		Debug.Assert(EntitySnapshot.class.isAssignableFrom(config.snapshotType));
		ORMField.registerORMProperties(config.snapshotType, this.ormFields);
		this.initORMProperties(config.snapshotType, config.referenceType);
		IOEntity.registerSerializableFields_unsafe(config.snapshotType, this.ormFields);
		ORMField.initAliases(aliases);
	}

	protected boolean hasMasterForeignKey() {
		return this.masterForeignKey != null;
	}

	public abstract Class<? extends ORMEntity> getORMDefinition();

	protected final boolean shouldInvalidCache(long modifyFlags) {
		return (modifyFlags & dirtyUpdateBits) != 0;
	}

	public final SortableColumn[] getDefaultOrderBy() {
		return this.defaultSort;
	}

	public abstract StormDatabase getDatabase();
	
	public final Class<?> getEntityDefinition() {
		return config.snapshotType;
	}

	public final String getTableName() {
		return this.tableName;
	}

	public final boolean isAutoIncrementEnabled() {
		return config.rowIdStart != Long.MIN_VALUE;
	}
	
	public final long getAutoIncrementStart() {
		return config.rowIdStart;
	}

	public final boolean isFts(){
		return config.isFts;
	}

	public final ORMColumn[] getDBColumns() {
		return dbColumns;
	}

	public final ORMField[] getORMFields() {
		return ormFields;
	}

	
	public final ORMColumn[] getForeignKeys() {
		return foreignKeys;
	}

	protected final ORMColumn[] getGhostColumns() {
		return ghostColumns;
	}

	public void getTableConfiguration(Config config) {return;}

	private static class RefSearchKey {

		long entityId;

		public int hashCode() {
			return (int)(entityId ^ entityId >>> 32);
		}

		public boolean equals(Object other) {
			return ((Number)other).longValue() == this.entityId;
		}


	}

	private RefSearchKey searchKey = new RefSearchKey();

	public final REF findEntityReference(long entityId) throws InvalidEntityReferenceException {
		return findEntityReference(entityId, true);
	}

	public final REF findEntityReference(long entityId, boolean mustExist) throws InvalidEntityReferenceException {
		if (entityId < 0) {
			return null;
		}
		REF ref = peekEntityReference(entityId);
		if (ref != null) {
			return ref;
		}

		return findEntityReference_impl(entityId, mustExist);
	}

	protected abstract REF findEntityReference_impl(long entityId, boolean mustExist) throws InvalidEntityReferenceException ;

	protected final REF makeEntityReference(long entityId) {
		REF ref;
		synchronized (refCache) {
			searchKey.entityId = entityId;
			ref = refCache.get(searchKey);
			if (ref == null || ref.isDeleted()) {
	        	ref = (REF)createEntityReference(entityId);
	            refCache.put(entityId, ref);
	        }
		}
		
        return ref;
	}

	protected REF[] makeEntityReferenceList(long[] ids, int count) throws SQLException {
		EntityReference refs[] = new EntityReference[count];

		synchronized (refCache) {

			for (int i = 0; i < count; i ++) {
				long entityId = ids[i];
				searchKey.entityId = entityId;
				REF ref = refCache.get(searchKey);
				if (ref == null) {
					ref = (REF)createEntityReference(entityId);
					refCache.put(entityId, ref);
				}
				refs[i] = ref;
			}
		}

		return (REF[])refs;
	}

	private REF peekEntityReference(long entityId) {
		synchronized (refCache) {
			searchKey.entityId = entityId;
			REF ref = refCache.get(searchKey);
			return ref;
		}
	}

	protected abstract SNAPSHOT doLoadSnapshot(EntityReference ref, boolean mustExist);

	public abstract EntitySnapshot[] loadSnapshots(REF[] refs, int offset, int count);

	protected abstract REF createEntityReference(long entityId);

	protected abstract SNAPSHOT createEntitySnapshot(EntityReference ref);

	public abstract StormQuery createQuery(String string, SortableColumn[] orderBy) throws Exception;

	public abstract StormQuery createJoinedQuery(String string, SortableColumn[] orderBy) throws Exception;

	protected abstract EntityReference createEntityInternal(EntityEditor entity) throws SQLException, RuntimeException;

	protected abstract void updateEntityInternal(EntityEditor entity) throws SQLException, RuntimeException;

	protected abstract void doUpdateEntities(ColumnAndValue[] columnValues, Collection<? extends EntityReference> entities) throws SQLException;

	protected abstract void deleteEntities(Long[] ids) throws SQLException;

	protected void doDeleteEntities(Collection<? extends EntityReference> entities) throws SQLException {
		getDatabase().executeInLocalTransaction(new TransactionalOperation<Void>() {
			protected Object execute_inTR(Void param, long transactionId) throws SQLException {
				for (EntityReference ref : entities) {
					ref.onDelete_inTR();
				}
				return null;
			}
		}, null);
		return;
	}

	final EntityReference removeDeletedReference(EntityReference ref) {
		ref.markDeleted();
		return ref;
	}
	
//	protected final REF attachReferenceIntoMutableSnapshot_unsafe(long entityId, EntitySnapshot entity) {
//    	REF ref = makeEntityReference(entityId);
//    	entity.setMutableReference_unsafe(ref);
//    	ref.validateForeignKey_RT(entity);
//        return ref;
//	}

    protected final REF attachReferenceIntoEditor_unsafe(long entityId, EntityEditor editor) {
		/**
		 * after Entity_Editor saves its entity,
		 * create reference temporarily
		 */
		REF ref = makeEntityReference(entityId);
        editor.setReference_Unsafe(ref);
		return ref;
    }
	

	protected EntityEditor edit(EntitySnapshot snapshot) throws SQLException {
			throw Debug.notImplemented();
	}

//	protected void delete(EntityReference ref) throws SQLException {
//		int cntRetry = 0;
//		while (true) {
//			StormTransaction tr = null;
//			try {
//				tr = getStorage().beginUpdateTransaction(true);
//
//				if (!ref.isForienKeyLoaded()) {
//					/**
//					 * 최초 delete 실행 시, deleted-notification 을 전달할 FK-Ref 를 로딩한다.
//					 * (
//					 */
//					this.loadForeignKeys(ref);
//				}
//				ref.delete_inTR(tr);
//				tr.commit();
//				return;
//			}
//			catch (SQLException e) {
//				handleStorageBusyException(e, ++cntRetry, true);
//			}
//			finally {
//				if (tr != null) {
//					tr.close();
//				}
//			}
//		}
//	}

	protected abstract boolean delete_inTR(EntityReference ref) throws SQLException;

	protected abstract void loadForeignKeys(EntityReference ref) throws SQLException;
	
	public abstract REF findFirst(StormQuery statement, Object... values);
	
	protected abstract SNAPSHOT loadFirst(StormQuery statement, Object[] values) throws SQLException;

//	protected abstract EntitySnapshot reloadUniqueContent_unsafe(EntitySnapshot entity, ORMColumn[] uniques);
	
	protected abstract REF findUnique(StormQuery statment, Object... uniqueParts);
	
	public static String getColumnType(Class<?> type) {
		if (type.isEnum()) {
			return "INTEGER";
		}
		String dbType = defaultColumnTypes.get(type);
		if (dbType == null) {
			if (EnumSet.class.isAssignableFrom(type)) {
				dbType = "INTEGER";
			}
			else {
				dbType = "TEXT";
			}
		}
		return dbType;
	}
	
	static {
		defaultColumnTypes = new HashMap<>();
		defaultColumnTypes.put(String.class, "TEXT");
		defaultColumnTypes.put(char[].class, "TEXT");
		
		defaultColumnTypes.put(byte[].class, "BLOB");
		
		defaultColumnTypes.put(float.class, "REAL");
		defaultColumnTypes.put(double.class, "REAL");
		defaultColumnTypes.put(Float.class, "REAL");		
		defaultColumnTypes.put(Double.class, "REAL");
		
		defaultColumnTypes.put(int.class, "INTEGER");
		defaultColumnTypes.put(long.class, "INTEGER");
		defaultColumnTypes.put(byte.class, "INTEGER");
		defaultColumnTypes.put(char.class, "INTEGER");
		defaultColumnTypes.put(boolean.class, "INTEGER");
		defaultColumnTypes.put(Integer.class, "INTEGER");
		defaultColumnTypes.put(Long.class, "INTEGER");
		defaultColumnTypes.put(Byte.class, "INTEGER");
		defaultColumnTypes.put(Character.class, "INTEGER");
		defaultColumnTypes.put(Boolean.class, "INTEGER");
	}

	public static abstract class Ref_Converter extends IOAdapters._Long<ORMEntity> {

		@Override
		public ORMEntity decode(long id, boolean isImmutable) throws Exception {
			if (id == 0) {
				return null;
			}
			StormTable table = getTable(id);
			Debug.Assert(table != null);
			ORMEntity v = table.makeEntityReference(id);
			return v;
		}

		@Override
		public long encode(ORMEntity entity) throws Exception {
			EntityReference ref = entity.getEntityReference();
			long v;
			if (ref == null) {
				Debug.Assert(entity instanceof EntityEditor);
				v = -1;
			}
			else {
				v = ref.getEntityId();
			}
			return v;
		}

		public abstract StormTable<?,?,?> getTable(long id);
	}

	private static class Ref_Adapter extends StormTable.Ref_Converter {
		private StormTable<?, ?, ?> table;

		public Ref_Adapter(StormTable<?, ?, ?> f_table) {
			this.table = f_table;
		}

		@Override
		public StormTable<?,?,?> getTable(long id) {
			return table;
		}

	}

	public static class ID_Converter extends IOAdapter {
		public static final ID_Converter instance = new ID_Converter();
		
		private ID_Converter() {}



		public Long read(DataReader reader) throws Exception {
			long id = reader.readLong();
			return id;
		}

		public void write(Object value, DataWriter writer) throws Exception {
			if (value == null){
				writer.writeNull();
			}
			else if (value instanceof ORMEntity) {
				EntityReference ref = ((ORMEntity)value).getEntityReference();
				if (ref.isDeleted()) {
					// @TODO throws Exception explicitly 
//					throw NPDebug.wtf(ref + " is deleted");
					Debug.debugLogging("ghost ref");
				}
				long v = ref.getEntityId();
				writer.writeLong(v);
			}
			else {
				long v = ((Number)value).longValue();
				writer.writeLong(v);
			}
		}

		@Override
		public EncodingType getPreferredTransferType() {
			return EncodingType.Integer;
		}

		@Override
		public Object decode(Object encoded, boolean isImmutable) throws Exception {
			return (Long)encoded;
		}

	}


	public final boolean isImmutable() {
		return false;//this.config.isImmutable;
	}

	protected static void attachLoadedSnapshot(EntitySnapshot snapshot) throws SQLException {
		EntityReference ref = snapshot.getEntityReference();
		snapshot.onLoadSnapshot();
		ref.attachEntity_RT(snapshot);
	}

	protected static void attachGhostData(EntityReference ref, Object[] ghostData) throws SQLException {
		ref.setGhostData(ghostData);
	}

	protected static void markForeignKeyLoaded(EntityReference ref) {
		ref.markForeignKeyLoaded();
	}



	protected static Object[] getGhostData(EntityReference ref) {
		return ref.getGhostData_unsafe();
	}


	public static class UnsafeTools {
		public UnsafeTools() {}

		public static int gTotalRetryCount;

//		public static void setModifiedSyncFlags(EntitySnapshot snapshot, long modifiedSyncFlags){
//			snapshot.setModifiedSyncFlags_unsafe(modifiedSyncFlags);
//		}

//		public static Field getRefField(ORMColumn column) {
//			return column.getRefField();
//		}
//
		public static int getTotalRetryCount() {
			return gTotalRetryCount;
		}

//		public static EntitySnapshot getEditingEntity(EntityEditor editor) {
//			EntitySnapshot snapshot = editor.getEditingEntity_unsafe();
//			return snapshot;
//		}

		public static HashMap<ORMField, Object> getEditMap(EntityEditor editor) {
			return editor.getEditMap();
		}
		
		public static void callOnLoad(EntitySnapshot entity) throws SQLException {
			if (entity != null) {
				entity.onLoadSnapshot();
			}
		}

		public static <REF extends EntityReference, SNAPSHOT extends EntitySnapshot, EDITOR extends EntityEditor, FORM	extends ORMEntity.UpdateForm>
				REF getEntityReference(StormTable<SNAPSHOT, REF, EDITOR> table, long entityId) {
			return table.makeEntityReference(entityId);
		}

		public static EntitySnapshot invalidateEntityCache_RT(EntityReference ref, ChangeType reason) {
			return ref.invalidateEntityCache_RT(reason);
		}

		public static void deleteEntities(StormTable table, Long[] ids) throws SQLException {
			table.deleteEntities(ids);
		}
	}

	protected void clearAllCache_UNSAFE_DEBUG() {
		synchronized (refCache) {
			NPAsyncScheduler.removeAllTasks();
			NPAsyncScheduler.executePendingTasks(500);
			refCache.clear();
		}
	}

	private void initORMProperties(Class<?> dmClass, Class<?> refClass) throws Exception {
		IOAdapterLoader loader = IOAdapter.getLoader(true);

		for (ORMColumn p : this.getDBColumns()) {
			if (p.isOuterLink() && p.isVolatileData()) {
				continue;
			}
			
			if (p.isDBColumn() && p.getAdapter() != null) {
				continue;
			}

			Class<?> field_t = p.getReflectionField().getType();
			IOAdapter<?, ?> adapter;
			if (ORMEntity.class.isAssignableFrom(field_t)) {
				StormTable f_table = getDatabase().findDBTable(field_t);
				if (f_table != null) {
					adapter = f_table.entityAdapter;
				}
				else {
					adapter = getDatabase().getAbstractReferenceAdapter();
				}
			}
			else {
				adapter = loader.loadAdapter(p.getValueType());
			}

			if (p.isForeignKey()) {
				Field f = ORMField.getReflectionField(p, this.config.referenceType);
				p.setRefField(f);
			}

			p.setAdapter_unsafe(adapter);
		}
	}

	protected final EntityReference loadMasterForeignKey(EntityReference ref) {
		try {
			return (EntityReference) masterForeignKey.getAdapter().decode(ref.getEntityId(), true);
//			IOAdapter adapter = getDatabase().getAbstractReferenceAdapter();
//			return (EntityReference) adapter.decode(ref.getEntityId(), true);
		} catch (Exception e) {
			throw Debug.wtf(e);
		}

	}

	protected REF createGhostRef() {
		REF ref = (REF)createEntityReference(-1);
		return ref;
	}

	
	protected final long getNewEntityId(EntityEditor editor) {
		long id = this.config.helper.getNewEntityId(editor);
		return id;
	}

	public ORMField[] getORMProperties(String subTable) {
		return this.getDatabase().findDBTableByName(subTable).getDBColumns();
	}

	public abstract void purgeGhost(long entityId) throws SQLException;

	ArrayList<Long> ghostList = new ArrayList<>();
	final void removeGhost(long entityId) {
		synchronized(ghostList){
			if (ghostList.size() == 0){
				new Cleaner();
			}
			ghostList.add(entityId);
		}
	}

	private class Cleaner {
		@Override
		protected void finalize() throws Throwable {
			Long[] arr;
			synchronized(ghostList){
				arr = ghostList.toArray(new Long[ghostList.size()]);
				ghostList.clear();
			}
			deleteEntities(arr);
		}
	}

	public ORMField getORMFieldByKey(String key) {
		for (ORMField f : ormFields) {
			if (f.getKey().equals(key)) {
				return f;
			}
		}
		return null;
	}
}
