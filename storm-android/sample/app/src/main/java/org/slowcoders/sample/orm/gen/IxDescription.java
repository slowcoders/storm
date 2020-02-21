package org.slowcoders.sample.orm.gen;

import java.sql.SQLException;

import org.slowcoders.storm.orm.*;

import org.slowcoders.sample.orm.def.*;

import org.slowcoders.sample.orm.gen.*;

import org.slowcoders.sample.orm.gen.storm.*;

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

