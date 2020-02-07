package test.storm.ormtest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slowcoders.util.Debug;
import test.storm.ormtest.gen.model.IxPhoto;
import test.storm.ormtest.gen.model.IxPost;
import test.storm.ormtest.gen.model.IxUser;
import test.storm.ormtest.gen.model.storm._TableBase;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class GhostReferenceTest extends ORMTestBase {

    private static final String postTitle = "Merry Christmas";
    private static final String userEmail = "Nine Folders";

    @Before
    public void setup() throws SQLException {
        preparePost(postTitle, userEmail);
    }

    /**
     *  delete entity and load snapshot
     *  with deleted reference
     *  even though it has been deleted,
     *  we still can load snapshot
     */
    @Test
    public void testLoadGhostSnapshot() throws SQLException {
        IxUser.Editor editor = _TableBase.tUser.edit_withEmailAddress("jonghoon@9folders.com");
        editor.setName("Jonghoon");
        IxUser ref = editor.save();

        IxUser.Snapshot origin = ref.loadSnapshot();

        ref.deleteEntity();

        IxUser.Snapshot ghost = ref.loadSnapshot();

        Assert.assertTrue(ghost.getEntityId() < 0);
        Assert.assertEquals(origin.getEmailAddress(), ghost.getEmailAddress());
        Assert.assertEquals(origin.getName(), ghost.getName());
    }

    /**
     *  ghost reference remains in DB
     *  after it is deleted with its id being negative
     *  but table query must not return
     *  ghost reference as result
     */
    @Test
    public void testGhostNotVisibleInSelect() throws SQLException {
        IxUser.Editor editor = _TableBase.tUser.edit_withEmailAddress("jonghoon@9folders.com");
        IxUser.Snapshot ghost = editor.save().loadSnapshot();
        ghost.getEntityReference().deleteEntity();

        IxUser result = _TableBase.tUser.findByEmailAddress("jonghoon@9folders.com");
        Assert.assertNull(result);
    }

    /**
     *  when entity turns into ghost,
     *  its unique fields must be updated to null
     *  to prevent another attempt to create item
     *  with same unique value from being failed
     */
    @Test
    public void testCreateDuplicateUnique() throws SQLException {
        IxUser.Editor editor = _TableBase.tUser.edit_withEmailAddress("jonghoon@9folders.com");
        editor.setName("Jonghoon");
        IxUser ref = editor.save();

        ref.deleteEntity();

        IxUser.Snapshot ghost = ref.loadSnapshot();

        editor = _TableBase.tUser.edit_withEmailAddress("jonghoon@9folders.com");

        IxUser.Snapshot created = editor.save().loadSnapshot();

        Assert.assertEquals(ghost.getEmailAddress(), created.getEmailAddress());
    }

    /**
     *  delete sub-entity and try to get
     *  snapshot of it from master entity
     */
    @Test
    public void testLoadSnapshotWithGhostSubEntity() throws SQLException {
        IxUser userRef = getUser(userEmail);

        IxPhoto photoRef = userRef.getPhoto();
        photoRef.deleteEntity();

        IxUser.Snapshot user = userRef.loadSnapshot();
        IxPhoto.Snapshot photo = user.getPhoto();

        Assert.assertTrue(photo.isDeleted());
    }

    /**
     *  when there is an attempt to edit
     *  snapshot of ghost, error must be thrown
     */
    @Test (expected = RuntimeException.class)
    public void testEditGhostEntity() throws SQLException {
        IxUser userRef = getUser(userEmail);

        IxPhoto photoRef = userRef.getPhoto();
        photoRef.deleteEntity();

        IxUser.Snapshot user = userRef.loadSnapshot();
        IxPhoto.Editor comment = user.getPhoto().editEntity();
        comment.setPhoto(new byte[1]);
        comment.save();
    }

    /**
     *  When master entity is changed to ghost
     *  subEntity must be changed to ghost too
     */
    @Test
    public void testCheckIfSubEntityChangesToGhost() throws SQLException {
        IxUser user = getUser(userEmail);
        IxPhoto photo = user.getPhoto();

        user.deleteEntity();

        Assert.assertTrue(photo.isDeleted());
    }

    /**
     *  ghost entities have to be deleted
     *  when their references are garbage collected
     */
    @Test
    public void testClearGhostCache() throws SQLException {
        {
            IxUser.Editor editor = _TableBase.tUser.edit_withEmailAddress("jonghoon@9folders.com");
            IxUser ref = editor.save();
            ref.deleteEntity();
        }

        System.gc();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw Debug.wtf(e);
        }

        Connection conn = _TableBase.tUser.getDatabase().getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM tUser_rw WHERE rowid < 0");

        Assert.assertFalse(rs.next());
    }

    private IxPost getTestPost() {
        IxPost post = getPostBySubject(postTitle);
        return post;
    }

}
