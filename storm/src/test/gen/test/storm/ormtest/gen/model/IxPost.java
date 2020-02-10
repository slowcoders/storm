package test.storm.ormtest.gen.model;

import org.joda.time.DateTime;
import test.storm.ormtest.gen.model.storm.*;

import java.sql.SQLException;

public abstract class IxPost extends Post_Reference {

	protected IxPost(long id) { super(id); }

	public static abstract class Snapshot extends Post_Snapshot {
		protected Snapshot(IxPost ref) { super(ref); }
	}

	public interface UpdateForm extends IxPost_.UpdateForm {}

	public static class Editor extends Post_Reference.Editor {
		protected Editor(Post_Table table, Post_Snapshot origin) { super(table, origin); }

		@Override
		protected void onSave_inTR() throws SQLException, RuntimeException {
			if (getOriginalData() == null) {
				setCreatedTime(DateTime.now());
			}
			super.onSave_inTR();
		}
	}
}

