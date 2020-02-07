package test.storm.ormtest.schema;

import org.slowcoders.storm.ORMColumn;
import org.slowcoders.storm.ORMEntity;
import org.slowcoders.storm.orm.ModelDefinition;
import org.slowcoders.storm.orm.TableDefinition;

import static org.slowcoders.storm.orm.ORMFieldFactory._Column;
import static org.slowcoders.storm.orm.ORMFieldFactory._ForeignKey;
import static org.slowcoders.storm.orm.ORMFlags.Volatile;

@TableDefinition(
        tableName = "tLike"
)
@ModelDefinition(
        uniques = {"_post, _user"}
)
public interface Like_ORM extends ORMEntity {

    ORMColumn Post = _ForeignKey("_post", 0,
            Post_ORM.class);

    ORMColumn User = _Column("_user", 0,
            User_ORM.class);

}
