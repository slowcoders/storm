package test.storm.ormtest.gen.model;

import test.storm.ormtest.gen.model.storm.*;

public abstract class IxDescription extends Description_Reference {

	protected IxDescription(long id) { super(id); }

	public static abstract class Snapshot extends Description_Snapshot {
		protected Snapshot(IxDescription ref) { super(ref); }
	}

	public interface UpdateForm extends IxDescription_.UpdateForm {}

	public static class Editor extends Description_Reference.Editor {
		protected Editor(Description_Table table, Description_Snapshot origin) { super(table, origin); }
	}
}

