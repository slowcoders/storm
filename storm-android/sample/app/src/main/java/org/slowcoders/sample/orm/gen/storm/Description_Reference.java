
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

public abstract class Description_Reference extends EntityReference implements Description_ORM {

	private static final _TableBase.Initializer unsafeTools = _TableBase.initializer;

	IxUser _user;

	public static abstract class Editor extends Description_Editor {
		protected Editor(Description_Table table, Description_Snapshot origin) {
			super(table, origin);
		}

	}
	protected Description_Reference(long id) {
		super(id);
	}

	public final Description_Table getTable() {
		return _TableBase.tDescription;
	}

	public final IxDescription.Snapshot tryLoadSnapshot() {
		return (IxDescription.Snapshot)super.doTryLoadSnapshot();
	}

	public final IxDescription.Snapshot loadSnapshot() {
		return (IxDescription.Snapshot)super.doLoadSnapshot();
	}

	public final IxUser getUser() {
		doLoadForeignKeys();
		return this._user;
	}

	public void deleteEntity() throws SQLException {
		super.doDelete();
	}

	/****************************************/
	/*  Internal methods                    */
	/*--------------------------------------*/

	protected void onDelete_inTR() throws SQLException {
		super.onDelete_inTR();
	}

	protected final void validateForeignKey_RT(EntitySnapshot entity) {
		IxDescription.Snapshot d = (IxDescription.Snapshot)entity;
		if (DebugUtil.DEBUG) DebugUtil.assertNullOrMatch(this._user, d.getUser());
		this._user = d.getUser();
		if (DebugUtil.DEBUG) DebugUtil.assertDeletedOrMatch(((User_Reference)_user)._description, (IxDescription)this);
		((User_Reference)_user)._description = (IxDescription)this;

	}

	protected void invalidateForeignEntityCache_RT(ChangeType reason) {
		super.invalidateForeignEntityCache_RT(reason);
		this._user = getUser();
		if (_user != null) ((User_Reference)_user)._description = null;
	}

}
