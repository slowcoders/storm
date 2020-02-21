
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

public final class Description_Table extends ORMDatabase.Table<IxDescription.Snapshot, IxDescription, IxDescription.Editor> implements AbstractTable<IxDescription.Snapshot, IxDescription, IxDescription.Editor> {

	private StormQuery findByUser;

	private AbstractMap<User_ORM, IxDescription> findByUser_cache;

	private static class _Ref extends IxDescription {
		_Ref(long id) { super(id); }

		protected static class Editor extends IxDescription.Editor {
			protected Editor(Description_Table table, Description_Snapshot origin) {
				super(table, origin);
			}
		}
	}

	private static class _Snapshot extends IxDescription.Snapshot {
		_Snapshot(IxDescription ref) { super(ref); }
	}

	protected Description_Table(String tableName) {
		super(tableName);
	}

	protected IxDescription createEntityReference(long entityId) { return new _Ref(entityId); }

	protected IxDescription.Snapshot createEntitySnapshot(EntityReference ref) { return new _Snapshot((IxDescription)ref); }

	protected void init() throws Exception {
		super.init();
		try {
			findByUser = super.createQuery("WHERE _user = ?", null);
			findByUser_cache = new SoftEntityMap<>();

		}
		catch (Exception e) {
			throw Debug.wtf(e);
		}
	}

	public IxDescription.Editor edit(Description_Snapshot entity) {
		return new _Ref.Editor(this, entity);
	}

	public IxDescription.Editor edit(IxDescription ref) throws InvalidEntityReferenceException {
		return edit(ref == null ? null : ref.loadSnapshot());
	}

	public IxDescription.Editor edit(IxDescription_.UpdateForm form) throws InvalidEntityReferenceException, SQLException {
		IxDescription.Editor edit = edit(form.getOriginalData());
		edit._set(form);
		return edit;
	}

	protected IxDescription.Editor edit(EntitySnapshot entity) throws InvalidEntityReferenceException {
		return edit((IxDescription.Snapshot)entity);
	}

	public IxDescription.Editor edit_withUser(User_ORM _user) {
		assert(_user != null);
		IxDescription orgRef = findByUser( _user);
		Description_Editor edit = edit(orgRef == null ? null : orgRef.tryLoadSnapshot());
		if (edit.getOriginalData() == null) {
			edit.__setUser(_user);
		}
		return (IxDescription.Editor) edit;
	}

	public void getTableConfiguration(StormTable.Config config) {
		config.rowIdStart = 0L;
		config.helper = _Ref.createORMHelper();
		config.snapshotType = _Snapshot.class;
		config.referenceType = _Ref.class;
		config.entityCache = TableDefinition.CacheType.Soft;
		config.isFts = false;
	}

	protected IxDescription createGhostRef() {
		return super.createGhostRef();
	}

	public void deleteEntities(Collection<IxDescription> entities) throws SQLException {
		super.doDeleteEntities(entities);
	}

	public final void updateEntities(ColumnAndValue[] updateValues, Collection<IxDescription> entities) throws SQLException, RuntimeException {
		super.doUpdateEntities(updateValues, entities);
	}

	public final Class<Description_ORM> getORMDefinition() {
		return Description_ORM.class;
	}

	public IxDescription findByUser (User_ORM _user) {
		IxDescription found;
		found = findByUser_cache.get(_user);
		if (found == null) {
			found = super.findUnique(findByUser, _user);
			if (found != null) {
				findByUser_cache.put(_user, found);
			}
		}
		return found;
	}

	public static final class RowSet extends StormFilter<IxDescription.Snapshot, IxDescription, IxDescription.Editor> implements StormRowSet<IxDescription.Snapshot, IxDescription, IxDescription.Editor> {
		public RowSet(StormQuery query, ORMColumn foreignKey, Object... values) {
			super(query, foreignKey, values);
		}

	}
	protected void clearAllCache_UNSAFE_DEBUG() {
		super.clearAllCache_UNSAFE_DEBUG();
		findByUser_cache.clear();
	}
}

