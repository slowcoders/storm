package org.slowcoders.storm;


public abstract class ORMEntities<T extends ORMEntity> {

    protected final StormRowSet rowSet;
    private int count;

    /*internal*/ ORMEntities(StormRowSet rowSet) {
        this.rowSet = rowSet;
    }

    public final StormTable getTable() {
        return this.rowSet.getTable();
    }

    public final StormRowSet getRowSet() {
        return this.rowSet;
    }

    public final int size() {
        return count;
    }

    protected void setSize(int count) {
        this.count = count;
    }

    protected final void rangeCheck(int index) {
        if (index >= count)
            throw new IndexOutOfBoundsException("Index: "+index+", Size: "+count);
    }

    public int indexOfEntity(long entityId) {
        for (int index = 0; index < count; index++) {
            long id = getEntityId(index);
            if (id == entityId) {
                return index;
            }
        }
        return -1;
    }

    protected abstract long getEntityId(int index);

    public int indexOf(ORMEntity entity) {
        return indexOfEntity(entity.getEntityReference().getEntityId());
    }

}
