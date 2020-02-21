package org.slowcoders.sample.orm.def;

import org.joda.time.DateTime;
import org.slowcoders.storm.ORMColumn;
import org.slowcoders.storm.ORMEntity;
import org.slowcoders.storm.StormRowSet;
import org.slowcoders.storm.orm.OuterLink;
import org.slowcoders.storm.orm.TableDefinition;
import org.slowcoders.storm.orm.Where;

import static org.slowcoders.storm.orm.ORMFieldFactory.*;
import static org.slowcoders.storm.orm.ORMFlags.*;

@TableDefinition(
        tableName = "tUser"
)
public interface User_ORM extends ORMEntity {

    ORMColumn EmailAddress = _Column("_emailAddress", Unique,
            String.class);

    ORMColumn Name = _Column("_name", 0,
            String.class);

    OuterLink Posts = _VolatileJoin("_posts", 0,
            Post_ORM.class);

    ORMColumn Gender = _Column("_gender", 0,
            UserGender.class);

    ORMColumn Interests = _EnumSet("_interests", 0,
            Interest.class);

    OuterLink Photo = _SnapshotJoin("_photo", UnsafeMutable | Nullable,
            Photo_ORM.class);

    OuterLink Description = _VolatileJoin("_description", 0,
            Description_ORM.class);

    enum UserGender {
        Male,
        Female
    }

    enum Interest {
        Food,
        Sports,
        Travel,
        Book
    }
}

