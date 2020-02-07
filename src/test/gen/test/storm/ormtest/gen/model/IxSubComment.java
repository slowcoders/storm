package test.storm.ormtest.gen.model;

import test.storm.ormtest.gen.model.storm.*;

public abstract class IxSubComment extends SubComment_Reference {

	protected IxSubComment(long id) { super(id); }

	public static abstract class Snapshot extends SubComment_Snapshot {
		protected Snapshot(IxSubComment ref) { super(ref); }
	}

	public interface UpdateForm extends IxSubComment_.UpdateForm {}

	public static class Editor extends SubComment_Reference.Editor {
		protected Editor(SubComment_Table table, SubComment_Snapshot origin) { super(table, origin); }
	}
}

