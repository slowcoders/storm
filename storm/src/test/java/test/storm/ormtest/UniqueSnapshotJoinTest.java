package test.storm.ormtest;

import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slowcoders.storm.InvalidEntityValueException;
import test.storm.ormtest.gen.model.IxComment;
import test.storm.ormtest.gen.model.IxDescription;
import test.storm.ormtest.gen.model.IxPost;
import test.storm.ormtest.gen.model.IxUser;

import static org.junit.Assert.*;
import static test.storm.ormtest.gen.model.storm._TableBase.*;

public class UniqueSnapshotJoinTest extends ORMTestBase {


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
     *  test if foreign key entity snapshot
     *  can load sub entity snapshot properly
     */
    @Test
    public void testDataLoadForSnapshotJoin() throws Exception {
        ImmutableList<IxPost> postRefs = tPost.selectEntities();

        for (int i = 0, count = postRefs.size(); i < count; i++){
            IxPost postRef = postRefs.get(i);
            ImmutableList<IxComment.Snapshot> comments = postRef.loadSnapshot().getComments();

            for (IxComment.Snapshot comment : comments) {
                assertEquals("Comment for " + postSubjects[count - i - 1], comment.getText());
            }
        }

    }

    /**
     *  - make new post entity and try to
     *      save without assigning body
     *  - since body entity is not nullable,
     *      error has to be thrown
     */
    @Test (expected = InvalidEntityValueException.class)
    public void testErrorOnMissingNotNullEntity () throws Exception {
        IxPost.Editor editPost = tPost.newEntity();
        editPost.setUser(tUser.findByEmailAddress(userEmail));
        editPost.setSubject("Test Post");

        // we did not assign body entity
        // which is not nullable
        editPost.save();
    }

    /**
     *  since photo is nullable,
     *  save succeeds without photo
     */
    @Test
    public void testInsertWithNoNullableEntity () throws Exception {
        IxUser.Editor editUser = tUser.newEntity();
        editUser.setEmailAddress("slowcoder2222@ggg.com");
        editUser.save();

        assertNull(editUser.getEntityReference().loadSnapshot().getPhoto());
    }

    /**
     *  since description is nullable,
     *  it is allowed to be deleted.
     */
    @Test
    public void testDeleteNullableEntity () throws Exception {
        IxUser userRef = tUser.selectFirst();
        IxDescription descriptionRef = userRef.getDescription();
        descriptionRef.deleteEntity();

        // entity has been deleted
        assertTrue(descriptionRef.isDeleted());
        assertNull(userRef.getDescription());
    }
}
