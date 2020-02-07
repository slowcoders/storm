package test.storm.ormtest.schema;

import org.slowcoders.storm.ORMColumn;
import org.slowcoders.storm.ORMEntity;
import org.slowcoders.storm.orm.TableDefinition;

import static org.slowcoders.storm.orm.ORMFieldFactory.*;
import static org.slowcoders.storm.orm.ORMFlags.Nullable;
import static org.slowcoders.storm.orm.ORMFlags.Unique;

@TableDefinition(
        tableName = "tBody"
)
public interface Body_ORM extends ORMEntity {

    ORMColumn Post = _ForeignKey("_post", Unique,
            Post_ORM.class);

    ORMColumn Body = _Column("_body", 0,
            String.class);

}
