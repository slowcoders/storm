
package test.storm.ormtest.gen.model.storm;

import java.sql.SQLException;
import java.util.*;
import com.google.common.collect.*;
import org.slowcoders.storm.*;
import org.slowcoders.storm.util.*;

import test.storm.ormtest.schema.*;
import test.storm.ormtest.gen.model.*;
import java.util.List;
import org.joda.time.DateTime;
import java.lang.String;
import org.joda.time.LocalDateTime;

/**
This file is generated by Storm Generator.
Do not modify this file.
*/

public abstract class Post_Editor extends EntityEditor implements Post_ORM, IxPost.UpdateForm {

	private static final _TableBase.Initializer unsafeTools = _TableBase.initializer;

	static final IxPost ghost = _TableBase.tPost.createGhostRef();

	/*internal*/ IxBody.UpdateForm _body;

	/*internal*/ EditableEntities<IxComment.UpdateForm, IxComment.Editor> _comments;

	/*internal*/ EditableEntities<IxLike.UpdateForm, IxLike.Editor> _likes;

	protected Post_Editor(Post_Table table, Post_Snapshot origin) {
		super(table, origin);
		if (origin != null) {
			this._body = origin.getBody();
		}
	}

	public final Post_Table getTable() {
		return (Post_Table)super.getTable();
	}

	public final IxPost getEntityReference() {
		return (IxPost)super.getEntityReference_internal();
	}

	public final IxPost.Snapshot getOriginalData() {
		return (IxPost.Snapshot)super.getOriginalData_internal();
	}

	public final IxUser getUser() {
		User_ORM v = (User_ORM) super.getObjectValue(Post_ORM.User);
		return v == null ? null : (IxUser)v.getEntityReference();
	}

	public final void setUser(User_ORM v) {
		super.setFieldValue(Post_ORM.User, v);
	}

	public final String getSubject() {
		String v = (String) super.getObjectValue(Post_ORM.Subject);
		return v;
	}

	public final void setSubject(String v) {
		super.setFieldValue(Post_ORM.Subject, v);
	}

	public final String getTag() {
		String v = (String) super.getObjectValue(Post_ORM.Tag);
		return v;
	}

	public final void setTag(String v) {
		super.setFieldValue(Post_ORM.Tag, v);
	}

	public final List<String> getImageUrls() {
		List<String> v = (List<String>) super.getObjectValue(Post_ORM.ImageUrls);
		if (v == null || v instanceof ImmutableCollection) {
			v = StormUtils.toMutableList(v);
			super.setFieldValue(Post_ORM.ImageUrls, v);
		}
		return (List<String>)v;
	}

	public final void setImageUrls(Collection<? extends String> v) {
		super.setFieldValue(Post_ORM.ImageUrls, v);
	}

	public final DateTime getCreatedTime() {
		DateTime v = (DateTime) super.getObjectValue(Post_ORM.CreatedTime);
		return v;
	}

	public final void setCreatedTime(DateTime v) {
		super.setFieldValue(Post_ORM.CreatedTime, v);
	}

	public final LocalDateTime getLocalCreatedTime() {
		LocalDateTime v = (LocalDateTime) super.getObjectValue(Post_ORM.LocalCreatedTime);
		return v;
	}

	public final void setLocalCreatedTime(LocalDateTime v) {
		super.setFieldValue(Post_ORM.LocalCreatedTime, v);
	}

	public final IxBody.Editor editBody() {
		IxBody.UpdateForm v = this._body;
		if (EntityEditor.asEditor(v) == null) {
			Post_Snapshot org = this.getOriginalData();

			IxBody.Snapshot sub = org == null ? null : org.getBody();
			Body_Editor edit = _TableBase.tBody.edit(sub == null || sub.getEntityReference().isDeleted() ? null : sub);
			edit.__setPost(this);
			this._body = edit;
			super.setFieldValue(Post_ORM.Body, edit);
		}
		return (IxBody.Editor)this._body;
	}

	public final IxBody.UpdateForm getBody() {
		return this._body;
	}

	public final EditableEntities<IxComment.UpdateForm, IxComment.Editor> editComments() {
		if (this._comments == null) {
			Post_Snapshot org = this.getOriginalData();
			List<? extends Comment_ORM> items = org == null ? null : org.getComments();
			this._comments = new EditableEntities<>(items);
			super.setFieldValue(Post_ORM.Comments, this._comments);
		}
		return this._comments;
	}

	public final List<IxComment.UpdateForm> getComments() {
		return this.editComments();
	}

	public final EditableEntities<IxLike.UpdateForm, IxLike.Editor> editLikes() throws InvalidEntityReferenceException {
		if (this._likes == null) {
			this._likes = new EditableEntities<>(this.getLikes());
			super.setFieldValue(Post_ORM.Likes, this._likes);
		}
		return this._likes;
	}

	public final Like_Table.RowSet getLikes() {
		Post_Snapshot org = this.getOriginalData();
		Like_Table.RowSet v = org != null ? org.getEntityReference().getLikes()
				 : _TableBase.tLike.findByPost(this);
		return v;
	}

	public final List<? extends IxLike.UpdateForm> peekLikes() {
		return getLikes().loadEntities();
	}

	public IxPost save() throws SQLException, RuntimeException {
		return (IxPost)super.doSave(false);
	}

	public IxPost saveAndContinueEdit() throws SQLException, RuntimeException {
		return (IxPost)super.doSave(true);
	}

	protected void onSave_inTR() throws SQLException, RuntimeException {
		super.__saveForeignEntityInternal((User_ORM) super.getObjectValue(Post_ORM.User));

		if (this.isChanged()) {
			super.onSave_inTR();
		}

		super.__saveJoinedEntityInternal(this._body);
		super.__saveJoinedEntitiesInternal(this._comments, _TableBase.tComment.findByPost(this));
		super.__saveJoinedEntitiesInternal(this._likes, _TableBase.tLike.findByPost(this));
	}

	protected void validate_inTR() throws RuntimeException {
		super.validate_inTR();
		ensureNotNull(Post_ORM.User);
		ensureNotNull(Post_ORM.Body, this._body);
	}

	public void _set(IxPost_.UpdateForm data) {
		setSubject(data.getSubject());
		setTag(data.getTag());
		if (data.getBody() != null) {
			editBody()._set(data.getBody());
		}
		setImageUrls(data.getImageUrls());
		setCreatedTime(data.getCreatedTime());
		setLocalCreatedTime(data.getLocalCreatedTime());
		if (data.getComments() != null) {
			editComments().replaceAll(data.getComments());
		}
		if (data.peekLikes() != null) {
			editLikes().replaceAll(data.peekLikes());
		}
	}

}

