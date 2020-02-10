package test.storm.ormtest.schema;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.slowcoders.storm.ORMColumn;
import org.slowcoders.storm.ORMEntity;
import org.slowcoders.storm.StormRowSet;
import org.slowcoders.storm.orm.OuterLink;
import org.slowcoders.storm.orm.TableDefinition;
import org.slowcoders.storm.orm.Where;

import static org.slowcoders.storm.orm.ORMFieldFactory.*;
import static org.slowcoders.storm.orm.ORMFlags.*;


@TableDefinition(
        tableName = "tPost"
)
public interface Post_ORM extends ORMEntity {

    ORMColumn User = _ForeignKey("_user", 0,
            User_ORM.class);

    ORMColumn Subject = _Column("_subject", 0,
            String.class);

    ORMColumn Tag = _Column("_tag", 0,
            String.class);

    OuterLink Body = _SnapshotJoin("_body", 0,
            Body_ORM.class);

    ORMColumn ImageUrls = _List("_imageUrls", 0,
            String.class);

    ORMColumn CreatedTime = _Column("_createdTime", 0,
            DateTime.class);

    ORMColumn LocalCreatedTime = _Column("_localCreatedTime", Nullable,
            LocalDateTime.class);

    OuterLink Comments = _SnapshotJoin("_comments", Nullable,
            Comment_ORM.class);

    OuterLink Likes = _VolatileJoin("_likes", 0,
            Like_ORM.class);

    interface Queries {
        @Where("_subject like {subject}")
        StormRowSet findBySubject(String subject);

        @Where("true GROUP BY _tag")
        StormRowSet groupByTag();

        @Where("_subject like {subject} GROUP BY _tag HAVING MAX(rowid)")
        StormRowSet findBySubject_groupByTag(String subject);


    }
}

