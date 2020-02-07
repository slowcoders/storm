
package test.storm.ormtest.gen.model.storm;

import java.sql.SQLException;

import org.slowcoders.storm.*;
import org.slowcoders.observable.*;

import test.storm.ormtest.schema.*;
import test.storm.ormtest.gen.model.*;

/**
This file is generated by Storm Generator.
Do not modify this file.
*/

public abstract class Body_Reference extends EntityReference implements Body_ORM {

	private static final _TableBase.Initializer unsafeTools = _TableBase.initializer;

	IxPost _post;

	public static abstract class Editor extends Body_Editor {
		protected Editor(Body_Table table, Body_Snapshot origin) {
			super(table, origin);
		}

	}
	protected Body_Reference(long id) {
		super(id);
	}

	public final Body_Table getTable() {
		return _TableBase.tBody;
	}

	public final IxBody.Snapshot tryLoadSnapshot() {
		return (IxBody.Snapshot)super.doTryLoadSnapshot();
	}

	public final IxBody.Snapshot loadSnapshot() {
		return (IxBody.Snapshot)super.doLoadSnapshot();
	}

	public final IxPost getPost() {
		doLoadForeignKeys();
		return this._post;
	}

	/****************************************/
	/*  Internal methods                    */
	/*--------------------------------------*/

	protected void onDelete_inTR() throws SQLException {
		super.onDelete_inTR();
	}

	protected final void validateForeignKey_RT(EntitySnapshot entity) {
		IxBody.Snapshot d = (IxBody.Snapshot)entity;
		if (DebugUtil.DEBUG) DebugUtil.assertNullOrMatch(this._post, d.getPost());
		this._post = d.getPost();
		if (DebugUtil.DEBUG) DebugUtil.assertDeletedOrMatch(((Post_Reference)_post)._body, (IxBody)this);
		((Post_Reference)_post)._body = (IxBody)this;

	}

	protected void invalidateForeignEntityCache_RT(ChangeType reason) {
		super.invalidateForeignEntityCache_RT(reason);
		((Post_Reference)getPost()).__invalidateBody();
	}

}
