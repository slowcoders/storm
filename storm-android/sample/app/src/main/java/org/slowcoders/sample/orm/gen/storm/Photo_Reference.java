
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

public abstract class Photo_Reference extends EntityReference implements Photo_ORM {

	private static final _TableBase.Initializer unsafeTools = _TableBase.initializer;

	IxUser rowid;

	public static abstract class Editor extends Photo_Editor {
		protected Editor(Photo_Table table, Photo_Snapshot origin) {
			super(table, origin);
		}

	}
	protected Photo_Reference(long id) {
		super(id);
	}

	public final Photo_Table getTable() {
		return _TableBase.tPhoto;
	}

	public final IxPhoto.Snapshot tryLoadSnapshot() {
		return (IxPhoto.Snapshot)super.doTryLoadSnapshot();
	}

	public final IxPhoto.Snapshot loadSnapshot() {
		return (IxPhoto.Snapshot)super.doLoadSnapshot();
	}

	public final IxUser getUser() {
		if (rowid == null) {
			rowid = (IxUser) loadMasterForeignKey();
		}
		return this.rowid;
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
		IxPhoto.Snapshot d = (IxPhoto.Snapshot)entity;
		if (DebugUtil.DEBUG) DebugUtil.assertNullOrMatch(this.rowid, d.getUser());
		this.rowid = d.getUser();
		if (DebugUtil.DEBUG) DebugUtil.assertDeletedOrMatch(((User_Reference)rowid)._photo, (IxPhoto)this);
		((User_Reference)rowid)._photo = (IxPhoto)this;

	}

	protected void invalidateForeignEntityCache_RT(ChangeType reason) {
		super.invalidateForeignEntityCache_RT(reason);
		((User_Reference)getUser()).__invalidatePhoto();
		if (rowid != null) ((User_Reference)rowid)._photo = null;
	}

}