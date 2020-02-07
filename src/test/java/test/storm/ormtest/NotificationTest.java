package test.storm.ormtest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slowcoders.observable.ChangeType;
import org.slowcoders.storm.EntityReference;
import org.slowcoders.storm.StormTable;
import org.slowcoders.util.Debug;
import test.storm.ormtest.gen.model.IxBody;
import test.storm.ormtest.gen.model.IxPost;

import java.sql.SQLException;

import static test.storm.ormtest.gen.model.storm._TableBase.tPost;
import static test.storm.ormtest.gen.model.storm._TableBase.tUser;


public class NotificationTest extends ORMTestBase {

    private static final String userEmail = "slowcoder@ggg.com";

    @Before
    public void setUp() {
        preparePost("Test Post", userEmail);
    }

    @After
    public void tearDown(){
        clearTestDB(userEmail);
    }

    /**
     *  we can register observers for each storm table
     *  it notifies change when item is created, updated, deleted
     */
    @Test
    public void testAsyncCreate_TableNotification() {
        StormTable.Observer<IxPost> observer = (noti) -> {
            if (noti.getChangeType() == ChangeType.Create) {
                synchronized (NotificationTest.this) {
                    NotificationTest.this.notifyAll();
                }
            }
        };
        try {
            tPost.addAsyncObserver(observer);

            new Thread(() -> {
               try {
                   IxPost.Editor editPost = tPost.newEntity();
                   editPost.setUser(tUser.findByEmailAddress(userEmail));
                   IxBody.Editor editBody = editPost.editBody();
                   editBody.setBody("Test Body");
                   editPost.setSubject("Test Post");
                   editPost.save();
                } catch (SQLException e) {
                    throw Debug.wtf(e);
                }
            }).start();
            synchronized (this) {
                this.wait();
            }
        } catch (Exception e) {
            throw Debug.wtf(e);
        } finally {
            tPost.removeObserver(observer);
        }
    }

    @Test
    public void testAsyncUpdate_TableNotification() {
        IxPost.Snapshot post = tPost.loadFirst();

        StormTable.Observer<IxPost> observer = (noti) -> {
            if (noti.getChangeType() == ChangeType.Update
                        && post.getEntityReference() == noti.getEntityReference()) {
                synchronized (NotificationTest.this) {
                    NotificationTest.this.notifyAll();
                }
            }
        };
        try {
            tPost.addAsyncObserver(observer);

            new Thread(() -> {
                try {
                    IxPost.Editor bookEditor = post.editEntity();
                    bookEditor.setSubject("New Subject");
                    bookEditor.save();
                } catch (SQLException e) {
                    throw Debug.wtf(e);
                }
            }).start();
            synchronized (this) {
                this.wait();
            }
        } catch (Exception e) {
            throw Debug.wtf(e);
        } finally {
            tPost.removeObserver(observer);
        }
    }

    @Test
    public void testAsyncDelete_TableNotification() {
        IxPost book = tPost.selectFirst();

        StormTable.Observer<IxPost> observer = (noti) -> {
            if (noti.getChangeType() == ChangeType.Delete
                    && book == noti.getEntityReference()) {
                synchronized (NotificationTest.this) {
                    NotificationTest.this.notifyAll();
                }
            }
        };
        try {
            tPost.addAsyncObserver(observer);

            new Thread(() -> {
                try {
                    book.deleteEntity();
                } catch (SQLException e) {
                    throw Debug.wtf(e);
                }
            }).start();
            synchronized (this) {
                this.wait();
            }
        } catch (Exception e) {
            throw Debug.wtf(e);
        } finally {
            tPost.removeObserver(observer);
        }

    }

    /**
     *  we can register observer to storm reference
     *  and receive notifications when that reference
     *  has been updated or deleted
     */
    @Test
    public void testAsyncUpdate_ReferenceNotification() {
        IxPost book = tPost.selectFirst();
        EntityReference.Observer observer = (ref, type) -> {
            synchronized (NotificationTest.this) {
                NotificationTest.this.notifyAll();
            }
        };
        try {
            book.setAsyncObserver(observer);
            new Thread(() -> {
                try {
                    IxPost.Editor bookEditor = book.loadSnapshot().editEntity();
                    bookEditor.setSubject("New Subject!!");
                    bookEditor.save();
                } catch (SQLException e) {
                    throw Debug.wtf(e);
                }
            }).start();
            synchronized (this) {
                this.wait();
            }
        } catch (Exception e) {
            throw Debug.wtf(e);
        } finally {
            book.setAsyncObserver(null);
        }
    }

    @Test
    public void testAsyncDelete_ReferenceNotification() {
        IxPost book = tPost.selectFirst();
        EntityReference.Observer observer = (ref, type) -> {
            synchronized (NotificationTest.this) {
                NotificationTest.this.notifyAll();
            }
        };
        try {
            book.setAsyncObserver(observer);
            new Thread(() -> {
                try {
                    book.deleteEntity();
                } catch (SQLException e) {
                    throw Debug.wtf(e);
                }
            }).start();
            synchronized (this) {
                this.wait();
            }
        } catch (Exception e) {
            throw Debug.wtf(e);
        } finally {
            book.setAsyncObserver(null);
        }
    }
}
