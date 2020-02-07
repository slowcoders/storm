package test.storm.ormtest.schema;

import org.slowcoders.storm.ORMColumn;
import org.slowcoders.storm.ORMEntity;
import org.slowcoders.storm.orm.TableDefinition;

import static org.slowcoders.storm.orm.ORMFieldFactory._Column;
import static org.slowcoders.storm.orm.ORMFieldFactory._ForeignKey;
import static org.slowcoders.storm.orm.ORMFlags.Unique;

@TableDefinition(
        tableName = "tDescription"
)
public interface Description_ORM extends ORMEntity {

    ORMColumn User = _ForeignKey("_user", Unique,
            User_ORM.class);

    ORMColumn Text = _Column("_text", 0,
            String.class);
}
