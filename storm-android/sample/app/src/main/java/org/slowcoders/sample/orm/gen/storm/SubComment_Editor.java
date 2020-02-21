
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

public abstract class SubComment_Editor extends EntityEditor implements SubComment_ORM, IxSubComment.UpdateForm {

	private static final _TableBase.Initializer unsafeTools = _TableBase.initializer;

	static final IxSubComment ghost = _TableBase.tSubComment.createGhostRef();

	protected SubComment_Editor(SubComment_Table table, SubComment_Snapshot origin) {
		super(table, origin);
	}

	public final SubComment_Table getTable() {
		return (SubComment_Table)super.getTable();
	}

	public final IxSubComment getEntityReference() {
		return (IxSubComment)super.getEntityReference_internal();
	}

	public final IxSubComment.Snapshot getOriginalData() {
		return (IxSubComment.Snapshot)super.getOriginalData_internal();
	}

	public final IxComment getComment() {
		Comment_ORM v = (Comment_ORM) super.getObjectValue(SubComment_ORM.Comment);
		return v == null ? null : (IxComment)v.getEntityReference();
	}

	public final void setComment(Comment_ORM v) {
		super.setFieldValue(SubComment_ORM.Comment, v);
	}

	public final String getText() {
		String v = (String) super.getObjectValue(SubComment_ORM.Text);
		return v;
	}

	public final void setText(String v) {
		super.setFieldValue(SubComment_ORM.Text, v);
	}

	public final IxUser getUser() {
		User_ORM v = (User_ORM) super.getObjectValue(SubComment_ORM.User);
		return v == null ? null : (IxUser)v.getEntityReference();
	}

	public final void setUser(User_ORM v) {
		super.setFieldValue(SubComment_ORM.User, v);
	}

	public final DateTime getCreatedTime() {
		DateTime v = (DateTime) super.getObjectValue(SubComment_ORM.CreatedTime);
		return v;
	}

	public final void setCreatedTime(DateTime v) {
		super.setFieldValue(SubComment_ORM.CreatedTime, v);
	}

	public IxSubComment save() throws SQLException, RuntimeException {
		return (IxSubComment)super.doSave(false);
	}

	public IxSubComment saveAndContinueEdit() throws SQLException, RuntimeException {
		return (IxSubComment)super.doSave(true);
	}

	protected void onSave_inTR() throws SQLException, RuntimeException {
		super.__saveForeignEntityInternal((Comment_ORM) super.getObjectValue(SubComment_ORM.Comment));

		if (this.isChanged()) {
			super.__saveJoinedEntityInternal((User_ORM) super.getObjectValue(SubComment_ORM.User));

			super.onSave_inTR();
		}

	}

	protected void validate_inTR() throws RuntimeException {
		super.validate_inTR();
		ensureNotNull(SubComment_ORM.Comment);
		ensureNotNull(SubComment_ORM.User);
	}

	public void _set(IxSubComment_.UpdateForm data) {
		setText(data.getText());
		setUser((IxUser)data.getUser());
		setCreatedTime(data.getCreatedTime());
	}

}
