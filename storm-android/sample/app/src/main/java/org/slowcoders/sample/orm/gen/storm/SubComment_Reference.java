
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
import org.joda.time.DateTime;
import java.lang.String;

/**
This file is generated by Storm Generator.
Do not modify this file.
*/

public abstract class SubComment_Reference extends EntityReference implements SubComment_ORM {

	private static final _TableBase.Initializer unsafeTools = _TableBase.initializer;

	IxComment _comment;

	public static abstract class Editor extends SubComment_Editor {
		protected Editor(SubComment_Table table, SubComment_Snapshot origin) {
			super(table, origin);
		}

	}
	protected SubComment_Reference(long id) {
		super(id);
	}

	public final SubComment_Table getTable() {
		return _TableBase.tSubComment;
	}

	public final IxSubComment.Snapshot tryLoadSnapshot() {
		return (IxSubComment.Snapshot)super.doTryLoadSnapshot();
	}

	public final IxSubComment.Snapshot loadSnapshot() {
		return (IxSubComment.Snapshot)super.doLoadSnapshot();
	}

	public final IxComment getComment() {
		doLoadForeignKeys();
		return this._comment;
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
		IxSubComment.Snapshot d = (IxSubComment.Snapshot)entity;
		if (DebugUtil.DEBUG) DebugUtil.assertNullOrMatch(this._comment, d.getComment());
		this._comment = d.getComment();

	}

	protected void invalidateForeignEntityCache_RT(ChangeType reason) {
		super.invalidateForeignEntityCache_RT(reason);
		((Comment_Reference)getComment()).__invalidateSubComments();
	}

}
