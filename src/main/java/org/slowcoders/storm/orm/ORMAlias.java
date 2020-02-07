package org.slowcoders.storm.orm;

import org.slowcoders.storm.ORMColumn;

public class ORMAlias extends ORMField {
    private final ORMColumn orgColumn;

    protected ORMAlias(ORMColumn column, int flags) {
        super(column, flags);
        this.orgColumn = column;
    }

    public boolean isDBColumn() {
        return orgColumn.isDBColumn();
    }

    public ORMColumn getOriginColumn() {
        return this.orgColumn;
    }

    public long getUpdateBit() {
        return orgColumn.getUpdateBit();
    }

    void init_internal() {
        super.setAdapter_unsafe(orgColumn.getAdapter());
    }
}
