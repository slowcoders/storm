package org.slowcoders.storm.orm;

import org.slowcoders.storm.ORMEntity;

public class MasterForeignKey extends ForeignKey {

    public MasterForeignKey(int flags, Class<? extends ORMEntity> columnType) {
        super("rowid", flags | ORMFlags.Unique, columnType);
    }

    public final boolean isMasterForeignKey() {
        return true;
    }
}
