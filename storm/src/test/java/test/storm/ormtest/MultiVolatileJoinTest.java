package test.storm.ormtest;

import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slowcoders.storm.ConflictedUpdateException;
import org.slowcoders.storm.ObservableCachedEntities;
import test.storm.ormtest.gen.model.IxPost;
import test.storm.ormtest.gen.model.IxUser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static test.storm.ormtest.gen.model.storm._TableBase.tUser;

public class MultiVolatileJoinTest extends ORMTestBase {


    private static final String[] postSubjects = {
            "Alogrithms-1.4", "Alogrithms-2.3", "Alogrithms-3.2", "Alogrithms-4.1"
    };

    private static final String userEmail = "slowcoder@ggg.com";

    @Before
    public void setUp() {
        for (int i = 0; i < postSubjects.length; i++){
            preparePost(postSubjects[i], userEmail);
        }
    }

    @After
    public void tearDown(){
        clearTestDB(userEmail);
    }

    /**
     *  load volatile entities
     *  and check data
     */
    @Test
    public void testLoadSubEntities() throws Exception {
        IxUser userRef = tUser.findByEmailAddress(userEmail);
        ImmutableList<IxPost> postRefs = userRef.getPosts().selectEntities();

        for (int i = 4; i < postRefs.size(); i--){
            IxPost refBook = postRefs.get(i);
            assertEquals(refBook.loadSnapshot().getSubject(), postSubjects[i]);
            assertEquals(refBook.loadSnapshot().getUser(), userRef);
        }
    }

    /**
     *  update book entity
     *  check if data is updated properly
     *
     *  when trying to edit entity with snapshot of old version
     *  error has to be thrown
     */
    @Test (expected = ConflictedUpdateException.class)
    public void testUpdateSubEntities() throws Exception {
        IxUser userRef = tUser.findByEmailAddress(userEmail);
        ImmutableList<IxPost> postRefs = userRef.getPosts().selectEntities();

        IxPost postRef = postRefs.get(0);

        IxPost.Editor editPost_1 = postRef.loadSnapshot().editEntity();
        IxPost.Editor editPost_2 = postRef.loadSnapshot().editEntity();
        editPost_1.setTag("test-tag");
        editPost_2.setTag("test-tag2");

        editPost_1.save();

        // check if data has been updated.
        assertEquals("test-tag", postRef.loadSnapshot().getTag());

        // because it tries to edit entity with old version of snapshot
        // error should be thrown here
        editPost_2.save();
    }

    /**
     *  delete entity and check if entity is deleted
     *  and entity is delete from reference list
     */
    @Test
    public void testDeleteSubEntities() throws Exception {
        IxUser userRef = tUser.findByEmailAddress(userEmail);
        ObservableCachedEntities.ReferenceList<IxPost> postRefs = new ObservableCachedEntities.ReferenceList<>(userRef.getPosts());

        // size of asyncEntities before delete
        int sizeBeforeDelete = postRefs.size();

        IxPost refBook = postRefs.get(0);

        refBook.deleteEntity();

        postRefs.processPendingNotifications();

        // because we deleted one of entities
        // size should be sizeBeforeDelete - 1
        assertEquals(sizeBeforeDelete - 1, postRefs.size());
        assertTrue(refBook.isDeleted());
    }
}
