package test.storm.ormtest;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.slowcoders.observable.ChangeType;
import org.slowcoders.storm.*;
import test.storm.ormtest.gen.model.*;
import test.storm.ormtest.gen.model.storm.Comment_Table;
import test.storm.ormtest.gen.model.storm.User_Table;
import test.storm.ormtest.gen.model.storm._TableBase;
import test.storm.ormtest.schema.Post_ORM;
import test.storm.ormtest.schema.User_ORM;

import java.sql.SQLException;

import static test.storm.ormtest.gen.model.storm._TableBase.*;

public class StormSample {

    public void loadSnapshot() {

        IxUser userRef = null;
        IxUser.Snapshot userSnapshot = userRef.loadSnapshot();

    }

    public void getJoinedEntity() {

        IxPost postRef = tPost.selectFirst();

        IxUser userRef = postRef.getUser();
        IxUser.Snapshot userSnapshot = userRef.loadSnapshot();

        Comment_Table.RowSet commentRowSet = postRef.getComments();
        ImmutableList<IxComment.Snapshot> commentSnapshots = commentRowSet.loadEntities();

    }

    public void getJoinedEntityWithSnapshot() {

        IxPost postRef = tPost.selectFirst();


        IxPost.Snapshot postSnapshot = postRef.loadSnapshot();

        IxUser userRef = postSnapshot.getUser();

        ImmutableList<IxComment.Snapshot> commentSnapshots = postSnapshot.getComments();

        ImmutableList<IxLike> likeRefs = postSnapshot.getLikes();
        ImmutableList<IxLike.Snapshot> likeSnapshots = postSnapshot.peekLikes();

    }

    public void loadFromTable() {

        ImmutableList<IxUser> userRefs = tUser.selectEntities();

        ImmutableList<IxUser.Snapshot> userSnapshots = tUser.loadEntities();

    }

    public void loadFromCustomQuery() {

        User_Table.RowSet rowSet = tUser.findByPhotoNameLike("%photo");

        ImmutableList<IxUser> userRefs = rowSet.selectEntities();

        ImmutableList<IxUser.Snapshot> userSnapshots = rowSet.loadEntities();

    }

    public void createDynamicQuery() throws SQLException {

        String sql = "SELECT * FROM tUser WHERE _name IS NULL";

        StormQuery query = tUser.createQuery(sql,
            new SortableColumn[] {
                    User_ORM.EmailAddress.createSortableColumn(tUser, false)
            }
        );

        ImmutableList<EntityReference> userRefs = query.selectEntities();
        ImmutableList<EntitySnapshot> userSnapshots = query.loadEntities();
    }

    public void observableCachedEntities() {

        ObservableCachedEntities.SnapshotList<IxPost.Snapshot> entities = new ObservableCachedEntities.SnapshotList<>();
        entities.bindFilter(tPost.orderBy(
                Post_ORM.CreatedTime.createSortableColumn(tPost, false)
        ));
        entities.addAsyncObserver(new ObservableCachedEntities.Observer() {
            @Override
            public void onDataChanged(ChangeType type, int index, int movedIndex, EntityReference entityReference) {

            }

            @Override
            public void onEntireChanged() {

            }
        });

        IxPost.Snapshot snapshot = entities.get(0);
    }

    public void observableCachedEntities2() {

        ObservableCachedEntities.SnapshotList<IxPost.Snapshot> entities = new ObservableCachedEntities.SnapshotList<>();

        String str = "WHERE _subject LIKE '%post%'";
        StormQuery query = tPost.createQuery(str, null);
        StormFilter filter = new StormFilter(query);

        entities.bindFilter(filter.orderBy(
                Post_ORM.CreatedTime.createSortableColumn(tPost, false)
        ));
        entities.addAsyncObserver(new ObservableCachedEntities.Observer() {
            @Override
            public void onDataChanged(ChangeType type, int index, int movedIndex, EntityReference entityReference) {

            }

            @Override
            public void onEntireChanged() {

            }
        });

        IxPost.Snapshot snapshot = entities.get(0);
    }


    public void makeAsyncEntities() {

        ObservableCachedEntities.ReferenceList<IxPost> entities = tPost.makeAsyncEntities();

    }

    public void deleteEntity() throws SQLException {

        IxUser userRef = tUser.selectFirst();
        userRef.deleteEntity();

    }

    public void executeInTransaction() throws SQLException {

        tUser.getDatabase().executeInLocalTransaction(new TransactionalOperation<Void>() {
            @Override
            protected Object execute_inTR(Void operationParam, long transactionId) throws SQLException {
                IxUser userRef = tUser.selectFirst();
                ImmutableList<IxUser.Snapshot> userSnapshots = tUser.loadEntities();
                return null;
            }
        }, null);

    }

    public void createEntity() {

        IxPost.Editor editPost = tPost.newEntity();
        editPost.setSubject("New Post");
        editPost.setCreatedTime(DateTime.now());

    }

    public void updateEntity() {

        IxPost.Snapshot postSnapshot = tPost.loadFirst();

        IxPost.Editor editPost = postSnapshot.editEntity();
        editPost.setSubject("Updated Subject");

    }

    public void editJoinedEntity() throws SQLException {

        IxPost.Editor editPost = tPost.newEntity();

        IxBody.Editor editBody = editPost.editBody();
        editBody.setBody("New Body");
        editPost.save();

        editPost.editComments();
    }

    public void editJoinedEntity2() throws SQLException {

        IxUser userRef = tUser.selectFirst();
        IxPost.Editor editPost = tPost.newEntity();

        EditableEntities<IxComment.UpdateForm, IxComment.Editor> editComments = editPost.editComments();

        IxComment.Editor editComment = tComment.newEntity();
        editComment.setPost(editPost);
        editComment.setUser(userRef);
        editComment.setText("New Comment");

        editComments.add(editComment);

        editPost.save();

        editComment = editComments.edit(0);

    }

    public void editWithUniqueColumns() {

        IxPost postRef = tPost.selectFirst();
        IxUser userRef = tUser.selectFirst();

        IxLike.Editor editLike = tLike.edit_withPostAndUser(postRef, userRef);

    }

    public void batchUpdate() throws SQLException {

        ImmutableList<IxPost> postRefs = tPost.selectEntities();

        String updatedSubject = "updated subject";
        ColumnAndValue[] cvs = new ColumnAndValue[] {
                new ColumnAndValue(Post_ORM.Subject, updatedSubject, true)
        };
        tPost.updateEntities(cvs, postRefs);

    }

    public void addTableObserver() {

        StormTable.Observer<IxPost> observer = noti -> {
            ChangeType type = noti.getChangeType();
            IxPost ref = noti.getEntityReference();
            long modifyFlag = noti.getModifyFlags();

            if ((modifyFlag & Post_ORM.Subject.getUpdateBit()) != 0) {
                //...
            }
        };

        _TableBase.tPost.addAsyncObserver(observer);

        _TableBase.tPost.removeObserver(observer);

    }

    public void setReferenceObserver() {

        IxPost postRef = tPost.selectFirst();
        postRef.setAsyncObserver((ref, type) -> {

        });

    }

    public void findByUniques() {

        IxUser userRef = tUser.findByEmailAddress("slowcoder@ggg.com");

    }
}
