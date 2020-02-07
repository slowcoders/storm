
package test.storm.ormtest.gen.model.storm;

import org.slowcoders.storm.*;

import test.storm.ormtest.schema.*;
import test.storm.ormtest.gen.model.*;
import java.lang.String;

/**
This file is generated by Storm Generator.
Do not modify this file.
*/

public abstract class SubComment_Snapshot extends EntitySnapshot implements IxSubComment.UpdateForm, SubComment_ORM {

	private static final _TableBase.Initializer unsafeTools = _TableBase.initializer;

	/*internal*/ Comment_ORM _comment;

	/*internal*/ String _text;

	/*internal*/ String _user;

	protected SubComment_Snapshot(IxSubComment ref) {
		super(ref);
	}

	public final IxComment getComment() {
		return (IxComment)(this._comment == null ? null : this._comment.getEntityReference());
	}

	public final String getText() {
		return this._text;
	}

	public final String getUser() {
		return this._user;
	}

	/****************************************/
	/*  Internal methods                    */
	/*--------------------------------------*/

	public final IxSubComment.Snapshot getOriginalData() {
		return (IxSubComment.Snapshot)this;
	}

	public IxSubComment.Editor editEntity() {
		return getTable().edit(this);
	}

	public final SubComment_Table getTable() {
		return _TableBase.tSubComment;
	}

	public final IxSubComment getEntityReference() {
		return (IxSubComment)super.getReference_internal();
	}

	protected void ensureLoadSubSnapshot() {
		super.ensureLoadSubSnapshot();
	}

}

