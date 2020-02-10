package test.storm.ormtest;

import com.google.common.collect.ImmutableList;
import org.junit.*;
import org.junit.runners.MethodSorters;
import org.slowcoders.storm.ConflictedUpdateException;
import org.slowcoders.storm.EditableEntities;
import org.slowcoders.storm.ObservableCachedEntities;
import org.slowcoders.storm.jdbc.JDBCStatement;
import test.storm.ormtest.gen.model.*;
import test.storm.ormtest.schema.TestDatabase;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static test.storm.ormtest.gen.model.storm._TableBase.*;

@FixMethodOrder(MethodSorters.JVM)
public class EntityCacheTest extends ORMTestBase {

	private static final String postTitle = "Clean Code";
	private static final String userEmail = "slowcoder@ccc.com";

	@Before
	public void setUp() {
		preparePost(postTitle, userEmail);
	}

	@After
	public void tearDown(){
		clearTestDB(userEmail);
	}


	/**
	 *  when there is a change to entity
	 *  which has foreignKey,
	 *  snapshot cache of foreignKey reference has to be invalidated
	 *  so that next time it loads snapshot,
	 *  it gets latest version of snapshot
	 */
	@Test
	public void testUpdateSnapshot() throws Exception {
		IxPost postRef = getFirstPost(userEmail);
		IxPost.Snapshot post = postRef.tryLoadSnapshot();
		IxPost.Editor editPost = post.editEntity();

		IxBody.Editor edit = editPost.editBody();
		edit.setBody(System.currentTimeMillis() + "");
		editPost.save();

		assertTrue(postRef.tryLoadSnapshot() != post);
	}

	/**
	 *  if Joined OuterLink entity is nullable
	 *  (there is no flag "NotNull" in column definition),
	 *
	 *  that entity can be deleted
	 *  but at the same time, snapshot cache of foreignKey reference
	 *  has to be invalidated
	 */
	@Test
	public void testDeleteOuterLinkedNullableEntity() throws Exception {
		IxUser userRef = getUser(userEmail);
		IxUser.Snapshot oldSnapshot = userRef.tryLoadSnapshot();
		IxUser.Editor editOrder = oldSnapshot.editEntity();

		editOrder.removePhoto();
		editOrder.save();

		assertTrue(userRef.tryLoadSnapshot() != oldSnapshot);
	}

	/**
	 *  when trying to edit older version of entity
	 *  than that of entity stored in database
	 *  error has to be thrown when saving
	 */
	@Test (expected = ConflictedUpdateException.class)
	public void testUpdateAlreadyUpdatedEntity() throws Exception {
		IxPost postRef = getFirstPost(userEmail);
		IxPost.Snapshot oldSnapshot = postRef.tryLoadSnapshot();
		IxPost.Editor editOrder = oldSnapshot.editEntity();
		IxPost.Editor editOrder2 = oldSnapshot.editEntity();

		editOrder.editBody().setBody("updated body");
		editOrder.save();

		editOrder2.editBody().setBody("Rewrited");
		editOrder2.save();
	}



	/**
	 *  when deleting entity,
	 *  all joined sub entities have to be deleted automatically
	 */
	@Test
	public void testDeleteTrigger() throws Exception {
		IxUser userRef = getUser(userEmail);

		IxPhoto photo = userRef.getPhoto();
		ImmutableList<IxPost> posts = userRef.getPosts().selectEntities();

		userRef.deleteEntity();

		assertTrue(userRef.isDeleted());
		assertTrue(photo.isDeleted());
		for (IxPost post : posts) {
			assertTrue(post.isDeleted());
		}
	}

	/**
	 *  when entity which is included in
	 *  ObservableCachedEntities list is delete,
	 *  the list has to be updated automatically
	 */
	@Test
	public void testDeleteVolatileEntityCache() throws Exception {
		IxUser user = tUser.findByEmailAddress(userEmail);
		ObservableCachedEntities.ReferenceList<IxPost> orders = user.getPosts().makeAsyncEntities();

		int cntBefore = orders.size();
		IxPost post = orders.get(0);

		post.deleteEntity();
		orders.processPendingNotifications();

		assertEquals(orders.size(), cntBefore - 1);
	}

	/**
	 *  when joined sub entity is created,
	 *  its reference has to be attached
	 *  to master entity automatically
	 */
	@Test
	public void testValidateJoinedSubEntity() throws SQLException {
	    IxUser.Editor editUser = tUser.edit_withEmailAddress("test_user@nnn.com");
        IxUser userRef = editUser.save();

        editUser = userRef.loadSnapshot().editEntity();
        IxDescription.Editor editDescription = editUser.editDescription();
        editDescription.setText("i am a programmer");
        editUser.save();

		Assert.assertEquals(userRef.getDescription(), editDescription.getEntityReference());
	}

	/**
	 *  when joined sub entity id deleted and recreated,
	 *  reference of recreated has to be attached
	 *  to master entity automatically
	 */
	@Test
	public void testUpdateJoinedSubEntityCache() throws SQLException {
		IxUser userRef = tUser.selectFirst();
		IxPhoto photoRef = userRef.getPhoto();
		photoRef.deleteEntity();

		IxUser.Editor editUser = userRef.loadSnapshot().editEntity();
		IxPhoto.Editor editPhoto = editUser.editPhoto();
		editPhoto.setPhotoName("new photo!");
		editUser.save();

		IxPhoto changed = userRef.getPhoto();

		Assert.assertFalse(changed.isDeleted());
		Assert.assertNotEquals(photoRef, changed);
	}

	/**
	 *  snapshot fields which are not volatile
	 *  are not managed to be changed by update
	 *  so if they are updated
	 *  already loaded snapshot holds old data
	 *
	 *  also, updating non-volatile fields
	 *  invalidate snapshot cache
	 *
	 */
	@Test
	public void testUpdateNonVolatileField() throws SQLException {
		IxUser.Snapshot user = tUser.loadFirst();

        IxUser.Editor editUser = user.editEntity();
		editUser.setName("New Name"); // update Author which is not volatile
		editUser.save();

		Assert.assertNotEquals(user.getName(), "New Author");
		Assert.assertNotEquals(user, user.getEntityReference().loadSnapshot());
	}

	/**
	 *  volatile snapshot fields are always up-to-date
	 *  so if they are updated
	 *  already loaded snapshot holds updated data
	 *
	 *  also, updating volatile fields do not
	 *  invalidate snapshot cache
	 */
	@Test
	public void testUpdateVolatileField() throws SQLException {
        IxPost.Snapshot post = tPost.loadFirst();

        IxPost.Editor editPost = post.editEntity();
        editPost.setSubject("New Subject!!");
        editPost.save();

		Assert.assertEquals("New Subject!!", post.getSubject());
		Assert.assertEquals(post, post.getEntityReference().loadSnapshot());
	}

	/**
	 * 	snapshot-joined master entity always returns
	 * 	snapshot of sub entity whose version is
	 * 	that of the time master entity snapshot was loaded
	 */
	@Test
	public void testUpdateSnapshotJoinedEntity() throws SQLException {
        IxUser.Snapshot user = tUser.loadFirst();
		IxPhoto.Snapshot photo = user.getPhoto();

		IxPhoto.Editor editPhoto = photo.editEntity();
		editPhoto.setPhoto(new byte[] { 123 });
		editPhoto.save();

		IxPhoto.Snapshot updatedPhoto = photo.getEntityReference().loadSnapshot();

		Assert.assertNotEquals(photo.getPhoto(), updatedPhoto.getPhoto());
		Assert.assertNotEquals(photo, updatedPhoto);
	}

	/**
	 * 	master entity has to hold
     * 	old version of sub entity snapshot,
	 * 	when there is a change to sub entity
	 */
	@Test
	public void testUpdateSnapshotJoinedEntity2() throws SQLException {
        IxUser.Snapshot user = tUser.loadFirst();
		IxPhoto photoRef = user.getEntityReference().getPhoto();

		IxPhoto.Editor commentEditor = photoRef.loadSnapshot().editEntity();
		commentEditor.setPhoto(new byte[] { 111 });
		commentEditor.save();

		IxPhoto.Snapshot updatedPhoto = photoRef.loadSnapshot();

		Assert.assertNotEquals("New Text", user.getPhoto().getPhoto());
		Assert.assertNotEquals(user.getPhoto(), updatedPhoto);
	}

	@Test
	public void testDeleteSnapshotJoinedEntity() throws SQLException {
        IxUser userRef = getUser(userEmail);
        IxUser.Snapshot user = userRef.loadSnapshot();

		IxPhoto photoRef = tPhoto.findByUser(userRef);
		photoRef.deleteEntity();

		IxPhoto.Snapshot photo = user.getPhoto();

		Assert.assertNotNull(photo);
	}

	/**
	 *  when one of snapshot-joined sub entities is changed,
	 *  before change is written to database,
	 *  master snapshot has to be notified to load sub entities
	 *  so that snapshot has old versions of snapshots of sub entities
	 */
	@Test
	public void testDeleteSnapshotJoinedMultipleEntities() throws SQLException {
        IxPost.Snapshot post = tPost.loadFirst();
		createComment(post);

        IxComment comment = tComment.findByPost(post).selectFirst();
		comment.deleteEntity();

		ImmutableList<IxComment.Snapshot> entities = post.getComments();
		Assert.assertTrue(entities.size() > 0);
	}

	/**
	 *  in the test case below,
	 *  we first load snapshot of comment which does not have
	 *  big text sub entity, then we add sub entity.
	 *
	 *  because they are snapshot-joined,
	 *  preloaded snapshot cache should not be affected by creating sub entity,
	 *  which still holds no sub entity.
	 */
	@Test
	public void testCreateSnapshotJoinedEntity() throws SQLException {
        IxUser userRef = prepareUser("New User!!");
        IxUser.Snapshot user = userRef.loadSnapshot();

		IxPhoto.Editor editPhoto = user.editEntity().editPhoto();
        editPhoto.setPhoto(new byte[0]);
		editPhoto.save();

		Assert.assertNull(user.getPhoto());
	}

	/**
	 *	when one of sub entities is changed and if there is snapshot
	 *	of master entity which has not yet loaded sub entities,
	 *	master entity has to be notified to load its sub entities
	 *	before update
	 */
	@Test
	public void testUpdateSnapshotJoinedMultipleEntities() throws SQLException {
	    IxPost.Snapshot post = tPost.loadFirst();
		createComment(post);

		IxComment.Snapshot editComment = tComment.findByPost(post).loadEntities().get(0);
        IxComment.Editor editor = editComment.editEntity();
		editor.setText("This post is as bad as your ass");
		editor.save();

		ImmutableList<IxComment.Snapshot> entities = post.getComments();
		for (IxComment.Snapshot entity : entities) {
			// the change was made after snapshot has been loaded
            // so text should not be changed
			Assert.assertNotEquals("This post is as bad as your ass", entity.getText());
		}
	}

	/**
	 *	because volatile fields can be changed
	 *	by other editor while editing,
	 *	we need to late-check whether values are changed
	 *	when saving
	 */
	@Test
	public void testUpdateVolatileField2() throws SQLException {
        IxPost.Snapshot post = tPost.loadFirst();
		IxPost.Editor editPost = post.editEntity();
		IxPost.Editor editPost2 = post.editEntity();

        editPost2.setSubject("Updated Subject");
        editPost.setSubject("Updated Subject");
        editPost.save(); // volatile subject field has been changed by another editor

		int queryCnt = JDBCStatement.DebugUtil.execStatementCount;

		editPost.save(); // because values are not changed, db write should not be occurred

		Assert.assertEquals(queryCnt, JDBCStatement.DebugUtil.execStatementCount);

	}

	private void createComment(IxPost.Snapshot post) throws SQLException {
        IxPost.Editor editPost = post.editEntity();

        IxComment.Editor editComment = tComment.newEntity();
		editComment.setText("I like this post");
		editComment.setUser(post.getUser());
        EditableEntities<IxComment.UpdateForm, IxComment.Editor> comments = editPost.editComments();
		comments.add(editComment);
		editPost.save();

		TestDatabase.clearAllCache();

	}

//	/*
//	 * Immutable value 자동 로딩.
//	 */
//	@Deprecated
//	@Test
//	public void testAutoLoadImmutable() throws Exception {
//		IxOrder refOrder = getFirstOrder();
//		IxDelivery refDeleivery = refOrder.getDelivery();
//		IxComment refComment = refDeleivery.getComment();
//		assertNotNull(refComment);
//	}


	// 하위 Entity 변경 시, 상위 entity cache 도 자동 변경되어야 한다.
//	@Deprecated("중복된 테스트")
//	@Test
//	public void testHigherEntityCacheChangeOnUpdatingLowerEntity() throws Exception {
//		IxOrder orderRef = getFirstOrder();
//		IxOrder.Snapshot oldSnapshot = orderRef.loadSnapshot();
//		Order_Editor editOrder = oldSnapshot.editEntity();
//
//		Delivery_Editor delivery_edit = editOrder.editDelivery();
//		delivery_edit.setAddress("Rewrited");
//		editOrder.save();
//
//		assertTrue(orderRef.tryLoadSnapshot() != oldSnapshot);
//	}
}
