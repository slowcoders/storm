package org.slowcoders.sample.orm.gen;

import com.google.common.collect.ImmutableList;

import java.sql.SQLException;

import org.joda.time.DateTime;
import org.slowcoders.storm.orm.*;

import org.slowcoders.sample.orm.def.*;

import org.slowcoders.sample.orm.gen.*;

import org.slowcoders.sample.orm.gen.storm.*;

public abstract class IxPost extends Post_Reference {

	protected IxPost(long id) { super(id); }

	public static abstract class Snapshot extends Post_Snapshot {
		protected Snapshot(IxPost ref) { super(ref); }

		public IxLike.Snapshot findLike(User_ORM user) {
			ImmutableList<IxLike.Snapshot> likes = this.peekLikes();
			IxLike.Snapshot likeFound = null;
			for (IxLike.Snapshot like : likes) {
				if (like.getUser() == user.getEntityReference()) {
					likeFound = like;
					break;
				}
			}
			return likeFound;
		}
	}

	public interface UpdateForm extends IxPost_.UpdateForm {}

	public static abstract class Editor extends Post_Reference.Editor {
		protected Editor(Post_Table table, Post_Snapshot origin) { super(table, origin); }

		@Override
		protected void onSave_inTR() throws SQLException, RuntimeException {
			if (getOriginalData() == null && getCreatedTime() == null) {
				setCreatedTime(DateTime.now());
			}
			setLastModified(DateTime.now());

			super.onSave_inTR();
		}
	}
}

