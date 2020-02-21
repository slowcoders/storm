package org.slowcoders.sample.orm.gen;

import java.sql.SQLException;

import org.slowcoders.storm.orm.*;

import org.slowcoders.sample.orm.def.*;

import org.slowcoders.sample.orm.gen.*;

import org.slowcoders.sample.orm.gen.storm.*;

public abstract class IxPhoto extends Photo_Reference {

	protected IxPhoto(long id) { super(id); }

	public static abstract class Snapshot extends Photo_Snapshot {
		protected Snapshot(IxPhoto ref) { super(ref); }
	}

	public interface UpdateForm extends IxPhoto_.UpdateForm {}

	public static class Editor extends Photo_Reference.Editor {
		protected Editor(Photo_Table table, Photo_Snapshot origin) { super(table, origin); }
	}
}

