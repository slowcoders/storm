package test.storm.ormtest;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slowcoders.storm.ConflictedUpdateException;
import org.slowcoders.storm.EditableEntities;
import test.storm.ormtest.gen.model.IxComment;
import test.storm.ormtest.gen.model.IxPost;
import test.storm.ormtest.gen.model.IxUser;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static test.storm.ormtest.gen.model.storm._TableBase.*;

public class MultiSnapshotJoinTest extends ORMTestBase {

    private static final String userEmail = "slowcoder@ggg.com";
    private static final String postSubject = "Post about algorithm";

    private static final String[] commentTexts = {
            "Alogrithms-1.4", "Alogrithms-2.3", "Alogrithms-3.2", "Alogrithms-4.1"
    };

    @Before
    public void setUp() throws SQLException {
        for (int i = 0; i < 4; i++) {
            String subject = postSubject + "_" + i;
            preparePost(subject, userEmail);
            for (String commentText : commentTexts) {
                addComment(subject, commentText);
            }
        }

    }

    @After
    public void tearDown(){
        clearTestDB(userEmail);
    }

    /**
     *  get joined post entities and
     *  compare data
     */
    @Test
    public void testLoadSubEntities() throws Exception {
        // load post entities and compare data
        ImmutableList<IxPost> postRefs = tPost.selectEntities();

        for (int i = 0, count = postRefs.size(); i < count; i ++){
            IxPost.Snapshot post = postRefs.get(i).loadSnapshot();
            IxComment.Snapshot comment = post.getComments().get(i);
            String text = comment.getText();
            assertEquals(commentTexts[count - i - 1], text);
        }
    }

    /**
     *  update post entity and check
     *  if data has been updated properly
     *
     *  when trying to edit entity with snapshot of old version
     *  error has to be thrown
     */
    @Test (expected = ConflictedUpdateException.class)
    public void testUpdateSubEntities() throws Exception {
        IxUser userRef = getUser(userEmail);

        IxPost postRef = userRef.getPosts().selectEntities().get(0);

        IxPost.Editor editPost_1 = postRef.loadSnapshot().editEntity();
        IxPost.Editor editPost_2 = postRef.loadSnapshot().editEntity();

        editPost_1.setTag("Quick");
        editPost_2.setTag("Slow");

        editPost_1.save();

        // check update result
        assertEquals("Quick", postRef.loadSnapshot().getTag());

        // error has to be thrown here
        editPost_2.save();

    }

    /**
     *  edit sub entities and
     *  check results
     */
    @Test
    public void testAddAndLoadSubEntities() throws Exception {
        String textStr = "comment !!";

        IxPost postRef = createPost("New Post", userEmail);

        IxPost.Editor editPost = postRef.loadSnapshot().editEntity();
        EditableEntities<IxComment.UpdateForm, IxComment.Editor> comments = editPost.editComments();
        for (int i = 0; i < 4; i++){
            IxComment.Editor editComment = tComment.newEntity();
            editComment.setPost(postRef);
            editComment.setUser(tUser.selectFirst());
            editComment.setText(textStr);
            comments.add(editComment);
        }
        editPost.save();

        // we inserted four comments
        assertEquals(4, postRef.getComments().selectEntities().size());

        ImmutableList<IxComment> commentRefs = postRef.getComments().selectEntities();
        for (int i = 4; i < commentRefs.size(); i--){
            assertEquals(textStr, commentRefs.get(i).loadSnapshot().getText());
        }
    }

    /**
     *  delete one of snapshot joined attachments
     *  and check if it is deleted
     */
    @Test
    public void testDeleteSubEntities() throws Exception {

        ImmutableList<IxPost> postRefs = tPost.selectEntities();
        IxPost postRef = postRefs.get(0);

        IxPost.Editor editPost = postRef.loadSnapshot().editEntity();
        EditableEntities<IxComment.UpdateForm, IxComment.Editor> editComments = editPost.editComments();

        // here we insert 4 comments
        for (int i = 0; i < postRefs.size(); i++){
            IxComment.Editor editComment = tComment.newEntity();
            editComment.setPost(postRef);
            editComment.setUser(tUser.selectFirst());
            editComment.setText("comment!!");
            editComments.add(editComment);
        }
        editPost.save();

        editPost = postRef.loadSnapshot().editEntity();
        editComments = editPost.editComments();

        int sizeBeforeDelete = editComments.size();

        editComments.remove(1);
        editPost.save();

        // because we deleted one of comments
        // updated snapshot has to hold 3 comments
        assertEquals(sizeBeforeDelete - 1, postRef.loadSnapshot().getComments().size());

    }

    private IxPost createPost(String subject, String email) throws SQLException {
        IxUser.Editor editUser = tUser.edit_withEmailAddress(email);
        IxPost.Editor editPost = tPost.newEntity();
        editPost.setSubject(subject);
        editPost.setUser(editUser);
        editPost.editBody().setBody("Body Body");
        editPost.setCreatedTime(DateTime.now());
        return editPost.save();
    }

}
