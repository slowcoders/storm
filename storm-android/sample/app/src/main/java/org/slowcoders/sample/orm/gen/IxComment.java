package org.slowcoders.sample.orm.gen;

import java.sql.SQLException;

import org.joda.time.DateTime;
import org.slowcoders.storm.orm.*;

import org.slowcoders.sample.orm.def.*;

import org.slowcoders.sample.orm.gen.*;

import org.slowcoders.sample.orm.gen.storm.*;

public abstract class IxComment extends Comment_Reference {

	protected IxComment(long id) { super(id); }

	public static abstract class Snapshot extends Comment_Snapshot {
		protected Snapshot(IxComment ref) { super(ref); }
	}

	public interface UpdateForm extends IxComment_.UpdateForm {}

	public static class Editor extends Comment_Reference.Editor {
		protected Editor(Comment_Table table, Comment_Snapshot origin) { super(table, origin); }

		@Override
		protected void onSave_inTR() throws SQLException, RuntimeException {
			if (getOriginalData() == null) {
				setCreatedTime(DateTime.now());
			}
			setLastModified(DateTime.now());

			super.onSave_inTR();
		}
	}
}

