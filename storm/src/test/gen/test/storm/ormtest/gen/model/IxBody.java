package test.storm.ormtest.gen.model;

import test.storm.ormtest.gen.model.storm.*;

public abstract class IxBody extends Body_Reference {

	protected IxBody(long id) { super(id); }

	public static abstract class Snapshot extends Body_Snapshot {
		protected Snapshot(IxBody ref) { super(ref); }
	}

	public interface UpdateForm extends IxBody_.UpdateForm {}

	public static class Editor extends Body_Reference.Editor {
		protected Editor(Body_Table table, Body_Snapshot origin) { super(table, origin); }
	}
}

