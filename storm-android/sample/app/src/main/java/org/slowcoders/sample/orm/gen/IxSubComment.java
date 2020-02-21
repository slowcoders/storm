package org.slowcoders.sample.orm.gen;

import java.sql.SQLException;

import org.slowcoders.storm.orm.*;

import org.slowcoders.sample.orm.def.*;

import org.slowcoders.sample.orm.gen.*;

import org.slowcoders.sample.orm.gen.storm.*;

public abstract class IxSubComment extends SubComment_Reference {

	protected IxSubComment(long id) { super(id); }

	public static abstract class Snapshot extends SubComment_Snapshot {
		protected Snapshot(IxSubComment ref) { super(ref); }
	}

	public interface UpdateForm extends IxSubComment_.UpdateForm {}

	public static class Editor extends SubComment_Reference.Editor {
		protected Editor(SubComment_Table table, SubComment_Snapshot origin) { super(table, origin); }

		@Override
		protected void onSave_inTR() throws SQLException, RuntimeException {
			super.onSave_inTR();
		}
	}
}

