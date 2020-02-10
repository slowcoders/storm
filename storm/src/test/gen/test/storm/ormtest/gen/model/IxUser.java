package test.storm.ormtest.gen.model;

import test.storm.ormtest.gen.model.storm.*;

public abstract class IxUser extends User_Reference {

	protected IxUser(long id) { super(id); }

	public static abstract class Snapshot extends User_Snapshot {
		protected Snapshot(IxUser ref) { super(ref); }
	}

	public interface UpdateForm extends IxUser_.UpdateForm {}

	public static class Editor extends User_Reference.Editor {
		protected Editor(User_Table table, User_Snapshot origin) { super(table, origin); }
	}
}

