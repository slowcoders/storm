package test.storm.ormtest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slowcoders.util.Debug;
import test.storm.ormtest.gen.model.IxBody;
import test.storm.ormtest.gen.model.IxPost;
import test.storm.ormtest.gen.model.IxUser;
import test.storm.ormtest.gen.model.storm._TableBase;

import java.sql.SQLException;

public class MultiThreadTest extends ORMTestBase {

    private static final String userEmail = "slowcoder@ggg.com";

    @Before
    public void setUp() {
        IxUser ref = _TableBase.tUser.findByEmailAddress(userEmail);
        if (ref != null) {
            try {
                ref.deleteEntity();
            } catch (Exception e) {
                e.printStackTrace();
                //assertTrue(false);
            }
        }
        // prepare items for test
        preparePost("TEST POST", userEmail);
    }

    @Test
    public void test() {
        IxPost postRef = _TableBase.tPost.selectFirst();
        IxPost.Snapshot book = postRef.loadSnapshot();

        Runnable r = () -> {
            try {
                long time = System.currentTimeMillis();

                IxPost.Editor editPost = postRef.loadSnapshot().editEntity();
                editPost.setSubject("Changed Subject : " + time);
                IxBody.Editor editBody = editPost.editBody();
                editBody.setBody("Changed Body : " + time);
                editPost.save();
            } catch (SQLException e) {
                throw Debug.wtf(e);
            }
        };

        new Thread(r).start();
        new Thread(r).start();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw Debug.wtf(e);
        }

        Assert.assertNotEquals(book, book.getEntityReference().loadSnapshot());
    }
}
