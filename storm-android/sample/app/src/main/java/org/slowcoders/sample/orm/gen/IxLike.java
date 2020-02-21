package org.slowcoders.sample.orm.gen;

import java.sql.SQLException;

import org.slowcoders.storm.orm.*;

import org.slowcoders.sample.orm.def.*;

import org.slowcoders.sample.orm.gen.*;

import org.slowcoders.sample.orm.gen.storm.*;

public abstract class IxLike extends Like_Reference {

	protected IxLike(long id) { super(id); }

	public static abstract class Snapshot extends Like_Snapshot {
		protected Snapshot(IxLike ref) { super(ref); }
	}

	public interface UpdateForm extends IxLike_.UpdateForm {}

	public static class Editor extends Like_Reference.Editor {
		protected Editor(Like_Table table, Like_Snapshot origin) { super(table, origin); }
	}
}

