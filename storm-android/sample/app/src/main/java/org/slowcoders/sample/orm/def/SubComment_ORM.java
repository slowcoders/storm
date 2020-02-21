package org.slowcoders.sample.orm.def;

import org.joda.time.DateTime;
import org.slowcoders.storm.ORMColumn;
import org.slowcoders.storm.ORMEntity;
import org.slowcoders.storm.orm.TableDefinition;

import static org.slowcoders.storm.orm.ORMFieldFactory._Column;
import static org.slowcoders.storm.orm.ORMFieldFactory._ForeignKey;
import static org.slowcoders.storm.orm.ORMFlags.Volatile;

@TableDefinition(
        tableName = "tSubComment"
)
public interface SubComment_ORM extends ORMEntity {

    ORMColumn Comment = _ForeignKey("_comment", 0,
            Comment_ORM.class);

    ORMColumn Text = _Column("_text", 0,
            String.class);

    ORMColumn User = _Column("_user", 0,
            User_ORM.class);

    ORMColumn CreatedTime = _Column("_createdTime", 0,
            DateTime.class);
}
