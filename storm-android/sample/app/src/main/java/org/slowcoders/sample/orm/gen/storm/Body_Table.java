
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

public final class Body_Table extends ORMDatabase.Table<IxBody.Snapshot, IxBody, IxBody.Editor> implements StormRowSet<IxBody.Snapshot, IxBody, IxBody.Editor> {

	private StormQuery findByPost;

	private AbstractMap<Post_ORM, IxBody> findByPost_cache;

	private static class _Ref extends IxBody {
		_Ref(long id) { super(id); }

		protected static class Editor extends IxBody.Editor {
			protected Editor(Body_Table table, Body_Snapshot origin) {
				super(table, origin);
			}
		}
	}

	private static class _Snapshot extends IxBody.Snapshot {
		_Snapshot(IxBody ref) { super(ref); }
	}

	protected Body_Table(String tableName) {
		super(tableName);
	}

	protected IxBody createEntityReference(long entityId) { return new _Ref(entityId); }

	protected IxBody.Snapshot createEntitySnapshot(EntityReference ref) { return new _Snapshot((IxBody)ref); }

	protected void init() throws Exception {
		super.init();
		try {
			findByPost = super.createQuery("WHERE _post = ?", null);
			findByPost_cache = new SoftEntityMap<>();

		}
		catch (Exception e) {
			throw Debug.wtf(e);
		}
	}

	public IxBody.Editor edit(Body_Snapshot entity) {
		return new _Ref.Editor(this, entity);
	}

	public IxBody.Editor edit(IxBody ref) throws InvalidEntityReferenceException {
		return edit(ref == null ? null : ref.loadSnapshot());
	}

	public IxBody.Editor edit(IxBody_.UpdateForm form) throws InvalidEntityReferenceException, SQLException {
		IxBody.Editor edit = edit(form.getOriginalData());
		edit._set(form);
		return edit;
	}

	protected IxBody.Editor edit(EntitySnapshot entity) throws InvalidEntityReferenceException {
		return edit((IxBody.Snapshot)entity);
	}

	public IxBody.Editor edit_withPost(Post_ORM _post) {
		assert(_post != null);
		IxBody orgRef = findByPost( _post);
		Body_Editor edit = edit(orgRef == null ? null : orgRef.tryLoadSnapshot());
		if (edit.getOriginalData() == null) {
			edit.__setPost(_post);
		}
		return (IxBody.Editor) edit;
	}

	public void getTableConfiguration(StormTable.Config config) {
		config.rowIdStart = 0L;
		config.helper = _Ref.createORMHelper();
		config.snapshotType = _Snapshot.class;
		config.referenceType = _Ref.class;
		config.entityCache = TableDefinition.CacheType.Soft;
		config.isFts = false;
	}

	protected IxBody createGhostRef() {
		return super.createGhostRef();
	}

	public final Class<Body_ORM> getORMDefinition() {
		return Body_ORM.class;
	}

	public IxBody findByPost (Post_ORM _post) {
		IxBody found;
		found = findByPost_cache.get(_post);
		if (found == null) {
			found = super.findUnique(findByPost, _post);
			if (found != null) {
				findByPost_cache.put(_post, found);
			}
		}
		return found;
	}

	public static final class RowSet extends StormFilter<IxBody.Snapshot, IxBody, IxBody.Editor> implements StormRowSet<IxBody.Snapshot, IxBody, IxBody.Editor> {
		public RowSet(StormQuery query, ORMColumn foreignKey, Object... values) {
			super(query, foreignKey, values);
		}

	}
	protected void clearAllCache_UNSAFE_DEBUG() {
		super.clearAllCache_UNSAFE_DEBUG();
		findByPost_cache.clear();
	}
}


