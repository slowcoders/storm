
package test.storm.ormtest.gen.model.storm;

import org.slowcoders.storm.*;

import test.storm.ormtest.schema.*;
import test.storm.ormtest.gen.model.*;
import java.lang.String;

/**
This file is generated by Storm Generator.
Do not modify this file.
*/

public abstract class Body_Snapshot extends EntitySnapshot implements IxBody.UpdateForm, Body_ORM {

	private static final _TableBase.Initializer unsafeTools = _TableBase.initializer;

	/*internal*/ Post_ORM _post;

	/*internal*/ String _body;

	protected Body_Snapshot(IxBody ref) {
		super(ref);
	}

	public final IxPost getPost() {
		return (IxPost)(this._post == null ? null : this._post.getEntityReference());
	}

	public final String getBody() {
		return this._body;
	}

	/****************************************/
	/*  Internal methods                    */
	/*--------------------------------------*/

	public final IxBody.Snapshot getOriginalData() {
		return (IxBody.Snapshot)this;
	}

	protected IxBody.Editor editEntity() {
		return getTable().edit(this);
	}

	public final Body_Table getTable() {
		return _TableBase.tBody;
	}

	public final IxBody getEntityReference() {
		return (IxBody)super.getReference_internal();
	}

	protected void ensureLoadSubSnapshot() {
		super.ensureLoadSubSnapshot();
	}

}

