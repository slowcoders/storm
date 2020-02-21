
package org.slowcoders.sample.orm.gen.storm;

import static org.slowcoders.storm.orm.ORMFlags.*;
import java.sql.SQLException;
import java.util.*;
import com.google.common.collect.*;
import org.slowcoders.util.*;
import org.slowcoders.storm.*;
import org.slowcoders.storm.orm.*;
import org.slowcoders.storm.util.*;
import org.slowcoders.io.serialize.*;
import org.slowcoders.observable.*;

import org.slowcoders.sample.orm.def.*;
import org.slowcoders.sample.orm.gen.*;
import java.lang.String;

/**
This file is generated by Storm Generator.
Do not modify this file.
*/

public final class Photo_Table extends ORMDatabase.Table<IxPhoto.Snapshot, IxPhoto, IxPhoto.Editor> implements AbstractTable<IxPhoto.Snapshot, IxPhoto, IxPhoto.Editor> {

	private StormQuery findByUser;

	private AbstractMap<User_ORM, IxPhoto> findByUser_cache;

	private static class _Ref extends IxPhoto {
		_Ref(long id) { super(id); }

		protected static class Editor extends IxPhoto.Editor {
			protected Editor(Photo_Table table, Photo_Snapshot origin) {
				super(table, origin);
			}
		}
	}

	private static class _Snapshot extends IxPhoto.Snapshot {
		_Snapshot(IxPhoto ref) { super(ref); }
	}

	protected Photo_Table(String tableName) {
		super(tableName);
	}

	protected IxPhoto createEntityReference(long entityId) { return new _Ref(entityId); }

	protected IxPhoto.Snapshot createEntitySnapshot(EntityReference ref) { return new _Snapshot((IxPhoto)ref); }

	protected void init() throws Exception {
		super.init();
		try {
			findByUser = super.createQuery("WHERE rowid = ?", null);
			findByUser_cache = new SoftEntityMap<>();

		}
		catch (Exception e) {
			throw Debug.wtf(e);
		}
	}

	public IxPhoto.Editor edit(Photo_Snapshot entity) {
		return new _Ref.Editor(this, entity);
	}

	public IxPhoto.Editor edit(IxPhoto ref) throws InvalidEntityReferenceException {
		return edit(ref == null ? null : ref.loadSnapshot());
	}

	public IxPhoto.Editor edit(IxPhoto_.UpdateForm form) throws InvalidEntityReferenceException, SQLException {
		IxPhoto.Editor edit = edit(form.getOriginalData());
		edit._set(form);
		return edit;
	}

	protected IxPhoto.Editor edit(EntitySnapshot entity) throws InvalidEntityReferenceException {
		return edit((IxPhoto.Snapshot)entity);
	}

	public IxPhoto.Editor edit_withUser(User_ORM rowid) {
		assert(rowid != null);
		IxPhoto orgRef = findByUser( rowid);
		Photo_Editor edit = edit(orgRef == null ? null : orgRef.tryLoadSnapshot());
		if (edit.getOriginalData() == null) {
			edit.__setUser(rowid);
		}
		return (IxPhoto.Editor) edit;
	}

	public void getTableConfiguration(StormTable.Config config) {
		config.rowIdStart = 0L;
		config.helper = _Ref.createORMHelper();
		config.snapshotType = _Snapshot.class;
		config.referenceType = _Ref.class;
		config.entityCache = TableDefinition.CacheType.Soft;
		config.isFts = false;
	}

	protected IxPhoto createGhostRef() {
		return super.createGhostRef();
	}

	public void deleteEntities(Collection<IxPhoto> entities) throws SQLException {
		super.doDeleteEntities(entities);
	}

	public final void updateEntities(ColumnAndValue[] updateValues, Collection<IxPhoto> entities) throws SQLException, RuntimeException {
		super.doUpdateEntities(updateValues, entities);
	}

	public final Class<Photo_ORM> getORMDefinition() {
		return Photo_ORM.class;
	}

	public IxPhoto findByUser (User_ORM rowid) {
		IxPhoto found;
		found = findByUser_cache.get(rowid);
		if (found == null) {
			found = super.findUnique(findByUser, rowid);
			if (found != null) {
				findByUser_cache.put(rowid, found);
			}
		}
		return found;
	}

	public static final class RowSet extends StormFilter<IxPhoto.Snapshot, IxPhoto, IxPhoto.Editor> implements StormRowSet<IxPhoto.Snapshot, IxPhoto, IxPhoto.Editor> {
		public RowSet(StormQuery query, ORMColumn foreignKey, Object... values) {
			super(query, foreignKey, values);
		}

	}
	protected void clearAllCache_UNSAFE_DEBUG() {
		super.clearAllCache_UNSAFE_DEBUG();
		findByUser_cache.clear();
	}
}


