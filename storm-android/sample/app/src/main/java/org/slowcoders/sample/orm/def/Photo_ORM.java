package org.slowcoders.sample.orm.def;

import org.slowcoders.storm.ORMColumn;
import org.slowcoders.storm.ORMEntity;
import org.slowcoders.storm.orm.TableDefinition;

import static org.slowcoders.storm.orm.ORMFieldFactory.*;
import static org.slowcoders.storm.orm.ORMFlags.*;

@TableDefinition(
        tableName = "tPhoto"
)
public interface Photo_ORM extends ORMEntity {

    ORMColumn User = _MasterForeignKey(0,
            User_ORM.class);

    ORMColumn Photo = _Column("_photo", UnsafeMutable | Nullable,
            byte[].class);

    ORMColumn PhotoName = _Column("_photoName", 0,
            String.class);
}
