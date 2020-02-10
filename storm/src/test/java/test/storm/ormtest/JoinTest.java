package test.storm.ormtest;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slowcoders.storm.EditableEntities;
import org.slowcoders.storm.InvalidEntityValueException;
import test.storm.ormtest.gen.model.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static test.storm.ormtest.gen.model.storm._TableBase.*;

@FixMethodOrder(MethodSorters.JVM)
public class JoinTest extends ORMTestBase {

	private static final String postSubject = "weather is good today";
	private static final String userEmail = "slowcoder@ggg.com";

	@Before
	public void setUp() {
		preparePost(postSubject, userEmail);
	}

	@After
	public void tearDown(){
		clearTestDB(userEmail);
	}

	/**
	 *  when editor saves itself
	 *  all editing joined entities are saved
	 *  in transaction automatically
	 */
	@Test
	public void testSaveJoinedEntities() throws Exception {
		IxPost.Editor editPost = getFirstPost(userEmail).loadSnapshot().editEntity();
		EditableEntities<IxComment.UpdateForm, IxComment.Editor> editComments = editPost.editComments();
		IxComment.Editor editComment = editComments.edit(0);
		editComment.setText("updated comment");
		IxSubComment.Editor editSubComment = editComment.editSubComments().edit(0);
		editSubComment.setText("updated sub comment");
		editPost.save();

		IxPost.Snapshot post = getFirstPost(userEmail).loadSnapshot();
		IxComment.Snapshot comment = post.getComments().get(0);
		IxSubComment.Snapshot subComment = comment.getSubComments().get(0);

		assertEquals("updated comment", comment.getText());
		assertEquals("updated sub comment", subComment.getText());
	}

	/**
	 *  all sub-entities which have foreignKey
	 *  must be deleted when their foreignKey is deleted
	 */
	@Test
	public void testDeleteEntityAndCheckTrigger() throws Exception {
		IxPost post = this.getFirstPost(userEmail);
		IxComment.Snapshot comment = post.tryLoadSnapshot().getComments().get(0);
		IxSubComment.Snapshot subComment = comment.getSubComments().get(0);

		int cntA = tComment.getEntityCount();
		int cntB = tSubComment.getEntityCount();
		post.deleteEntity();

		int cntA2 = tComment.getEntityCount();
		int cntB2 = tSubComment.getEntityCount();
		assertEquals(cntA - 1, cntA2);
		assertEquals(cntB - 1, cntB2);

		assertTrue(post.isDeleted());
		assertTrue(comment.getEntityReference().isDeleted());
		assertTrue(subComment.getEntityReference().isDeleted());
	}

	/**
	 *  when outer entity is added or deleted,
	 *  related collections must be updated
	 */
	@Test
	public void testAddSubEntities() throws Exception {
		IxUser userRef = getUser(userEmail);
		EditableEntities<IxPost.UpdateForm, IxPost.Editor> posts = new EditableEntities<>(userRef.getPosts());
		int cntBefore = posts.size();

		for (int i = 1; i <= 5; i ++) {
			IxPost.Editor editPost = tPost.newEntity();
			editPost.setSubject("New Post " + i);
			IxBody.Editor editBody = editPost.editBody();
			editBody.setBody("Body");
			editPost.setCreatedTime(DateTime.now());
			posts.add(editPost);
		}
		posts.save(userRef.getPosts());

		int cntAfter = new EditableEntities<>(userRef.getPosts()).size();

		assertEquals(cntBefore + 5, cntAfter);
	}

	/**
	 * 	when assigning null to foreign key
	 * 	which is not nullable
	 * 	error is thrown when save
	 */
	@Test (expected = InvalidEntityValueException.class)
	public void testSetNullToForeignKey() throws Exception {
		IxPost.Editor editPost = tPost.newEntity();
		editPost.setSubject("test-title");
		IxBody.Editor editBody = editPost.editBody();
		editBody.setBody("Body ~~!!!");
		// book has publisher as foreign key
		editPost.setUser(null);
		editPost.save(); // it throws error
	}

//	/*
//	 * Outer Join 을 이용하여 snapshot에 속한 모든 entity를 한 번의 SQL 실행으로 로딩한다.
//	 */
//
//	@Deprecated
//	@Test
//	public void testLoadSnapshotInSingleQuery() throws Exception
//	{
//		// initialize test condition
//
//		IxOrder order = this.getFirstOrder();
//
//        int before = JDBCStatement.DebugUtil.execStatementCount;
//        {
//			IxDelivery.Snapshot deliver = order.tryLoadSnapshot().getDelivery();
//	        System.out.println(order.toString());
//        }
//        int after = JDBCStatement.DebugUtil.execStatementCount;
//        assertEquals(before + 1, after);
//
//        /**
//         * test cached access
//         */
//        before = JDBCStatement.DebugUtil.execStatementCount;
//        {
//			IxDelivery.Snapshot deliver = order.tryLoadSnapshot().getDelivery();
//	        System.out.println(order.toString());
//        }
//        assertEquals(before, JDBCStatement.DebugUtil.execStatementCount);
//	}

}
