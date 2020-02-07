
package test.storm.ormtest.gen.model.storm;

import java.sql.SQLException;
import java.util.*;

import org.slowcoders.util.*;
import org.slowcoders.storm.*;
import org.slowcoders.storm.orm.*;
import org.slowcoders.storm.util.*;

import test.storm.ormtest.schema.*;
import test.storm.ormtest.gen.model.*;
import test.storm.ormtest.schema.User_ORM;
import java.lang.String;

/**
This file is generated by Storm Generator.
Do not modify this file.
*/

public final class User_Table extends TestDatabase.Table<IxUser.Snapshot, IxUser, IxUser.Editor> implements AbstractTable<IxUser.Snapshot, IxUser, IxUser.Editor> {

	private StormQuery findByPhotoNameLike;

	private StormQuery findByEmailAddress;

	private AbstractMap<String, IxUser> findByEmailAddress_cache;

	private static class _Ref extends IxUser {
		_Ref(long id) { super(id); }

		protected static class Editor extends IxUser.Editor {
			protected Editor(User_Table table, User_Snapshot origin) {
				super(table, origin);
			}
		}
	}

	private static class _Snapshot extends IxUser.Snapshot {
		_Snapshot(IxUser ref) { super(ref); }
	}

	protected User_Table(String tableName) {
		super(tableName);
	}

	protected IxUser createEntityReference(long entityId) { return new _Ref(entityId); }

	protected IxUser.Snapshot createEntitySnapshot(EntityReference ref) { return new _Snapshot((IxUser)ref); }

	protected void init() throws Exception {
		super.init();
		try {
			findByPhotoNameLike = super.createQuery("LEFT OUTER JOIN tPhoto ON tUser.rowId = tPhoto.rowid WHERE tPhoto._photoName LIKE ?", null);
			findByEmailAddress = super.createQuery("WHERE _emailAddress = ?", null);
			findByEmailAddress_cache = new SoftEntityMap<>();

		}
		catch (Exception e) {
			throw Debug.wtf(e);
		}
	}

	public IxUser.Editor newEntity() {
		return edit((User_Snapshot)null);
	}

	public IxUser.Editor edit(User_Snapshot entity) {
		return new _Ref.Editor(this, entity);
	}

	public IxUser.Editor edit(IxUser ref) throws InvalidEntityReferenceException {
		return edit(ref == null ? null : ref.loadSnapshot());
	}

	public IxUser.Editor edit(IxUser_.UpdateForm form) throws InvalidEntityReferenceException, SQLException {
		IxUser.Editor edit = edit(form.getOriginalData());
		edit._set(form);
		return edit;
	}

	protected IxUser.Editor edit(EntitySnapshot entity) throws InvalidEntityReferenceException {
		return edit((IxUser.Snapshot)entity);
	}

	public IxUser.Editor edit_withEmailAddress(String _emailAddress) {
		assert(_emailAddress != null);
		IxUser orgRef = findByEmailAddress( _emailAddress);
		User_Editor edit = edit(orgRef == null ? null : orgRef.tryLoadSnapshot());
		if (edit.getOriginalData() == null) {
			edit.setEmailAddress(_emailAddress);
		}
		return (IxUser.Editor) edit;
	}

	public void getTableConfiguration(StormTable.Config config) {
		config.rowIdStart = 200000000000000000L;
		config.helper = _Ref.createORMHelper();
		config.snapshotType = _Snapshot.class;
		config.referenceType = _Ref.class;
		config.entityCache = TableDefinition.CacheType.Soft;
		config.isFts = false;
	}

	protected IxUser createGhostRef() {
		return super.createGhostRef();
	}

	public void deleteEntities(Collection<IxUser> entities) throws SQLException {
		super.doDeleteEntities(entities);
	}

	public final void updateEntities(ColumnAndValue[] updateValues, Collection<IxUser> entities) throws SQLException, RuntimeException {
		super.doUpdateEntities(updateValues, entities);
	}

	public final Class<User_ORM> getORMDefinition() {
		return User_ORM.class;
	}

	// LEFT OUTER JOIN tPhoto ON tUser.rowId = tPhoto.rowid WHERE tPhoto._photoName LIKE ?
	public User_Table.RowSet findByPhotoNameLike(java.lang.String photoName) {
		return new User_Table.RowSet(findByPhotoNameLike, null, photoName);
	}

	public IxUser findByEmailAddress (String _emailAddress) {
		IxUser found;
		found = findByEmailAddress_cache.get(_emailAddress);
		if (found == null) {
			found = super.findUnique(findByEmailAddress, _emailAddress);
			if (found != null) {
				findByEmailAddress_cache.put(_emailAddress, found);
			}
		}
		return found;
	}

	public static final class RowSet extends StormFilter<IxUser.Snapshot, IxUser, IxUser.Editor> implements StormRowSet<IxUser.Snapshot, IxUser, IxUser.Editor> {
		public RowSet(StormQuery query, ORMColumn foreignKey, Object... values) {
			super(query, foreignKey, values);
		}

	}
	protected void clearAllCache_UNSAFE_DEBUG() {
		super.clearAllCache_UNSAFE_DEBUG();
		findByEmailAddress_cache.clear();
	}
}


