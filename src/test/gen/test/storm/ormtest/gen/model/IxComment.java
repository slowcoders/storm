package test.storm.ormtest.gen.model;

import test.storm.ormtest.gen.model.storm.*;

public abstract class IxComment extends Comment_Reference {

	protected IxComment(long id) { super(id); }

	public static abstract class Snapshot extends Comment_Snapshot {
		protected Snapshot(IxComment ref) { super(ref); }
	}

	public interface UpdateForm extends IxComment_.UpdateForm {}

	public static class Editor extends Comment_Reference.Editor {
		protected Editor(Comment_Table table, Comment_Snapshot origin) { super(table, origin); }
	}
}

