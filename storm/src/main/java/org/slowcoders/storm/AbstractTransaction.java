package org.slowcoders.storm;

import org.slowcoders.observable.ChangeType;
import org.slowcoders.util.Debug;

import java.sql.SQLException;
import java.util.ArrayList;

public abstract class AbstractTransaction implements AutoCloseable {

    private ConcurrentUpdatePolicy updatePolicy;
    private ArrayList<EntityChangeNotification> changeList = new ArrayList<>();
    private int state;

    private static final int COMMIT_FAIL = -1;
    private static final int TR_CLOSED = 0;
    private static final int TR_STARTED = 1;
    private static final int COMMIT_SUCCESS = 2;

    protected AbstractTransaction(ConcurrentUpdatePolicy policy) {
        this.updatePolicy = policy;
    }

    public final boolean canOverwriteConflictEntity() {
        return updatePolicy != ConcurrentUpdatePolicy.ErrorOnUpdateConflict;
    }

    public final void setUpdatePolicy(ConcurrentUpdatePolicy policy) {
        this.updatePolicy = policy;
    }

    public final ConcurrentUpdatePolicy getUpdatePolicy() {
        return this.updatePolicy;
    }

    protected void doBegin() throws SQLException {
        Debug.Assert(this.state == TR_CLOSED);
        this.state = TR_STARTED;
    }

    protected void doCommit() throws SQLException {
        throw Debug.shouldNotBeHere();
    }

    protected void doRollback() throws SQLException {
        throw Debug.shouldNotBeHere();
    }

    protected void invalidateCaches() throws SQLException {

        /**
         * before executing commit,
         * if there are snapshot-joined entities which loaded only part of their snapshot
         * we first need to notify them to load entire snapshot
         * (because after commit, it is not possible to load data of previous version)
         */

        for (EntityChangeNotification noti : changeList) {
            EntityReference ref = noti.getEntityReference();
            ChangeType changeType = noti.getChangeType();
            StormTable table;
            switch (changeType) {
                case Update:
                    table = ref.getTable();
                    if (table.shouldInvalidCache(noti.getModifyFlags())) {
                        StormTable.UnsafeTools.invalidateEntityCache_RT(ref, ChangeType.Update);
                    }
                    break;
                case Delete:
                    ref.invalidateForeignEntityCache_RT(changeType);
                    break;
                default:
                    break;
            }
        }
    }

    public void commit() throws SQLException {
        Debug.Assert(state == TR_STARTED);

        this.state = COMMIT_FAIL;
        if (changeList == null) {
            this.doCommit();
            this.state = COMMIT_SUCCESS;
            return;
        }

        for (EntityChangeNotification noti : changeList) {
            EntityReference ref = noti.getEntityReference();
            ChangeType changeType = noti.getChangeType();
            StormTable table;
            switch (changeType) {
                case Create:
                    ref.invalidateForeignEntityCache_RT(changeType);
                    table = ref.getTable();
                    if (table.hasMasterForeignKey()) {
                        // we do this because when it has masterForeignKey
                        // there is a chance that entity with duplicate rowid is created,
                        // which is not allowed
                        //
                        // if there is a way that can make ghost id always unique,
                        // we do not need this process
                        table.purgeGhost(-ref.getEntityId());
                    }
                    break;
            }
        }

        doCommit();

        long changedTime = System.currentTimeMillis();
        for (EntityChangeNotification noti : changeList) {
            EntityReference ref = noti.getEntityReference();
            ChangeType changeType = noti.getChangeType();
            switch (changeType) {
                case Update:
                    noti.updateVolatileColumns_RT();
                    noti.clearEditMap();
                    break;
                case Delete:
                    /**
                     *  in Ghost Mode, we do not have to invalidate cache
                     *  so it is commented
                        StormTable.UnsafeTools.invalidateEntityCache_RT(ref, ChangeType.Delete);
                     */
                    ref.getTable().removeDeletedReference(ref);
                    break;
            }
            ref.setLastModifiedTime(changedTime);
        }

        this.state = COMMIT_SUCCESS;
    }

    protected void commitWithoutNotification(AbstractTransaction outerTransaction) {
        if (outerTransaction.changeList == null) {
            outerTransaction.changeList = this.changeList;
            this.changeList = null;
        }
        else {
            outerTransaction.changeList.addAll(this.changeList);
            this.changeList.clear();
        }
        this.state = COMMIT_SUCCESS;
    }

    protected final boolean isStarted() {
        return (state == TR_STARTED);
    }

    public void close() {
        ArrayList<EntityChangeNotification> notiList = this.changeList;
        boolean commitSuccess = this.state == COMMIT_SUCCESS;

        this.changeList = null;
        this.state = TR_CLOSED;
        if (notiList == null) return;

        try {
            if (commitSuccess) {
                // @TODO Notification 순서 보장. (현재, Insert 보다 Delete 가 먼저 전달될 수 있다.)
                for (EntityChangeNotification noti : notiList) {
                    noti.sendNotification();
                }
            } else {
                for (EntityChangeNotification noti : notiList) {
                    noti.rollback();
                }
                this.doRollback();
            }
        } catch (SQLException e) {
            Debug.wtf(e);
        } finally {
            if (this.changeList == null) {
                notiList.clear();
                this.changeList = notiList;
            }
        }

    }


    protected final void addChangeNotification(EntityChangeNotification noti) {
        if (this.changeList == null) {
            this.changeList = new ArrayList<>();
        }
        this.changeList.add(noti);
    }

    public final void addDeletedEntity(EntityReference entity) {
        addChangeNotification(EntityChangeNotification.deleted(entity));
    }

    public final void addUpdatedEntity(EntityReference entity, EntityEditor editor) {
        addChangeNotification(EntityChangeNotification.updated(entity, editor));// TODO Auto-generated method stub
    }

    public final void addUpdatedEntity(EntityReference entity, long modifyFlags) {
        addChangeNotification(EntityChangeNotification.updated(entity, modifyFlags));// TODO Auto-generated method stub
    }

    public final void addInsertedEntity(EntityReference entity, EntityEditor editor) {
        addChangeNotification(EntityChangeNotification.inserted(entity, editor));// TODO Auto-generated method stub
    }

}
