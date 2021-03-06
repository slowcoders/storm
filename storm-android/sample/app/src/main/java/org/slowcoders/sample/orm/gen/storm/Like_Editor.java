
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

/**
This file is generated by Storm Generator.
Do not modify this file.
*/

public abstract class Like_Editor extends EntityEditor implements Like_ORM, IxLike.UpdateForm {

	private static final _TableBase.Initializer unsafeTools = _TableBase.initializer;

	static final IxLike ghost = _TableBase.tLike.createGhostRef();

	protected Like_Editor(Like_Table table, Like_Snapshot origin) {
		super(table, origin);
	}

	public final Like_Table getTable() {
		return (Like_Table)super.getTable();
	}

	public final IxLike getEntityReference() {
		return (IxLike)super.getEntityReference_internal();
	}

	public final IxLike.Snapshot getOriginalData() {
		return (IxLike.Snapshot)super.getOriginalData_internal();
	}

	public final IxPost getPost() {
		Post_ORM v = (Post_ORM) super.getObjectValue(Like_ORM.Post);
		return v == null ? null : (IxPost)v.getEntityReference();
	}

	public final void setPost(Post_ORM v) {
		super.setFieldValue(Like_ORM.Post, v);
	}

	public final IxUser getUser() {
		User_ORM v = (User_ORM) super.getObjectValue(Like_ORM.User);
		return v == null ? null : (IxUser)v.getEntityReference();
	}

	public final void setUser(User_ORM v) {
		super.setFieldValue(Like_ORM.User, v);
	}

	public IxLike save() throws SQLException, RuntimeException {
		return (IxLike)super.doSave(false);
	}

	public IxLike saveAndContinueEdit() throws SQLException, RuntimeException {
		return (IxLike)super.doSave(true);
	}

	protected void onSave_inTR() throws SQLException, RuntimeException {
		super.__saveForeignEntityInternal((Post_ORM) super.getObjectValue(Like_ORM.Post));

		if (this.isChanged()) {
			super.__saveJoinedEntityInternal((User_ORM) super.getObjectValue(Like_ORM.User));

			super.onSave_inTR();
		}

	}

	protected void validate_inTR() throws RuntimeException {
		super.validate_inTR();
		ensureNotNull(Like_ORM.Post);
		ensureNotNull(Like_ORM.User);
	}

	public void _set(IxLike_.UpdateForm data) {
		setUser((IxUser)data.getUser());
	}

}

