package test.storm.ormtest.schema;

import org.slowcoders.storm.ORMColumn;
import org.slowcoders.storm.ORMEntity;
import org.slowcoders.storm.orm.OuterLink;
import org.slowcoders.storm.orm.TableDefinition;

import static org.slowcoders.storm.orm.ORMFieldFactory.*;
import static org.slowcoders.storm.orm.ORMFlags.*;

@TableDefinition(
	tableName = "tComment"
)
public interface Comment_ORM extends ORMEntity {

	ORMColumn Post = _ForeignKey("_post", 0,
			Post_ORM.class);
	
	ORMColumn Text = _Column("_text", SyncUpdate,
			String.class);

	ORMColumn User = _Column("_user", 0,
			User_ORM.class);

	OuterLink SubComments = _SnapshotJoin("_subComments", Nullable,
			SubComment_ORM.class);
}

