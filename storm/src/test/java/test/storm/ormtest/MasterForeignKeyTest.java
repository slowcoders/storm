package test.storm.ormtest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import test.storm.ormtest.gen.model.IxPhoto;
import test.storm.ormtest.gen.model.IxUser;

import java.sql.SQLException;

import static test.storm.ormtest.gen.model.storm._TableBase.tPhoto;

public class MasterForeignKeyTest extends ORMTestBase {

    private static final String postSubject = "Merry Christmas";
    private static final String userEmail = "test_email@email.com";
    private static final String author = "John Doe";

    @Before
    public void setup() throws SQLException {
        preparePost(postSubject, userEmail);
    }

    /**
     *  check if master foreign key entity loads
     *  sub entity right
     */
    @Test
    public void testLoadOuterLink() throws SQLException {
        IxUser userRef = getUser(userEmail);
        IxPhoto photoRef = userRef.getPhoto();

        IxPhoto.Snapshot photo = photoRef.loadSnapshot();

        IxPhoto.Editor editPhoto = photo.editEntity();
        editPhoto.setPhotoName("Photo name");
        editPhoto.save();

        photo = userRef.getPhoto().loadSnapshot();

        Assert.assertEquals("Photo name", photo.getPhotoName());
    }

    /**
     *  when master foreign key is deleted,
     *  its sub entity has to be also deleted
     */
    @Test
    public void testDeleteMaster() throws SQLException {
        IxUser userRef = getUser(userEmail);

        IxPhoto photoRef = userRef.getPhoto();

        userRef.deleteEntity();

        Assert.assertTrue(photoRef.isDeleted());
    }

    /**
     *  when sub entity is deleted and recreated,
     *  master foreignKey has to be able to load
     *  recreated sub entity properly
     */
    @Test
    public void testDeleteAndCreateSubEntity() throws SQLException {
        IxUser userRef = getUser(userEmail);

        IxPhoto photoRef = userRef.getPhoto();
        photoRef.deleteEntity();

        Assert.assertTrue(photoRef.isDeleted());

        IxUser.Editor editUser = userRef.loadSnapshot().editEntity();
        IxPhoto.Editor editPhoto = editUser.editPhoto();
        editPhoto.setPhotoName("My Photo");
        userRef = editUser.save();

        Assert.assertEquals("My Photo", userRef.getPhoto().loadSnapshot().getPhotoName());
        Assert.assertNotEquals(photoRef, userRef.getPhoto());
    }

    /**
     *  when deleting sub entity twice
     *  error should not be thrown
     */
    @Test
    public void testDeleteSubEntityTwice() throws Exception {
        IxUser userRef = getUser(userEmail);

        userRef.getPhoto().deleteEntity();

        IxUser.Editor editUser = userRef.loadSnapshot().editEntity();

        IxPhoto.Editor editPhoto = editUser.editPhoto();
        editPhoto.setPhotoName("updated photo name");
        userRef = editUser.save();

        userRef.getPhoto().deleteEntity();
    }

    /**
     *  test if sub entity which has master foreign key
     *  works fine as ghost after it is deleted
     */
    @Test
    public void testDeleteAndLoadOuterLink() throws Exception {
        IxUser userRef = getUser(userEmail);
        IxPhoto photoRef = userRef.getPhoto();
        IxPhoto.Snapshot origin = photoRef.loadSnapshot();

        photoRef.deleteEntity();

        IxPhoto.Snapshot data = photoRef.loadSnapshot();

        Assert.assertEquals(origin.getPhoto(), data.getPhoto());
        Assert.assertTrue(data.isDeleted());
    }

    /**
     *  test if sub entity can find its
     *  master foreign key
     */
    @Test
    public void testLoadMaster() {
        IxPhoto photoRef = tPhoto.selectFirst();
        IxUser userRef = photoRef.getUser();

        Assert.assertNotNull(userRef);
    }
}
