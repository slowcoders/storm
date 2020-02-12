package test.storm.ormtest;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.junit.*;
import org.junit.runners.MethodSorters;
import org.slowcoders.observable.ChangeType;
import org.slowcoders.storm.*;
import org.slowcoders.storm.jdbc.JDBCStatement;
import test.storm.ormtest.gen.model.*;
import test.storm.ormtest.gen.model.storm.Post_Table;
import test.storm.ormtest.gen.model.storm.User_Table;
import test.storm.ormtest.gen.model.storm._TableBase;
import test.storm.ormtest.invalidORM.TestORMGen2;
import test.storm.ormtest.schema.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.*;
import static test.storm.ormtest.gen.model.storm._TableBase.*;


@FixMethodOrder(MethodSorters.JVM)
public class QueryTest extends ORMTestBase {

	private static final String postSubject1 = "Algorithms-1.4";
	private static final String postSubject2 = "Algorithms-2.3";
	private static final String postSubject3 = "Algorithms-3.2";
	private static final String postSubject4 = "Algorithms-4.1";

	private static final String userEmail = "slowcoder@ggg.com";

	@Before
	public void setUp() {
		IxUser ref = tUser.findByEmailAddress(userEmail);
		if (ref != null) {
			try {
				ref.deleteEntity();
			}
			catch (Exception e) {
				e.printStackTrace();
				//assertTrue(false);
			}
		}

		// prepare items for test
		preparePost(postSubject1, userEmail, "Programming");
		preparePost(postSubject2, userEmail, "Programming");
		preparePost(postSubject3, userEmail, "Food");
		preparePost(postSubject4, userEmail, "Travel");
	}

	@After
	public void tearDown(){
		clearTestDB(userEmail);
	}

	/**
	 *  QueryParser test
	 *
	 *  we inserted invalid custom query
	 *  in {@link test.storm.ormtest.invalidORM.TestEntity_withInvalidSubQuery}
	 *  so error message has to be returned
	 *  for {@link test.storm.ormtest.schema.GenTestORM2#generateDatabase(boolean)}
	 */
	@Test
	public void testQueryBuilder() throws Exception {
		String err = TestORMGen2.generateDatabase(true);
		assertNotNull(err);
	}

	/**
	 *  when entity is deleted, it turns into ghost
	 *  which has its rowid as negative number
	 *  it must not be included in results derived from table query
	 *  but still it can load snapshot
	 */
	@Test
	public void testLateDelete() throws Exception {
		Post_Table.RowSet res;
		int cntBook;

		res = tPost.findBySubject("%-4.1");
		cntBook = res.selectEntities().size();
		assertEquals(1, cntBook);

		IxPost ref = res.selectEntities().get(0);
		ref.deleteEntity();

		res = tPost.findBySubject("%-4.1");
		cntBook = res.selectEntities().size();
		assertEquals(0, cntBook);

		assertTrue(ref.isDeleted());
		assertTrue((ref.getEntityId() < 0));
		assertNotNull(ref.loadSnapshot());
	}

	/**
	 *	we test custom query
	 *	{@link Post_Table#findBySubject(String)} (String)} in this test
	 */
	@Test
	public void testQuery() {
		Post_Table.RowSet res;
		int cntBook;

		res = tPost.findBySubject("%-4.1");
		cntBook = res.selectEntities().size();
		assertEquals(cntBook, 1);

		res = tPost.findBySubject("A%");
		cntBook = res.selectEntities().size();
		assertEquals(cntBook, 4);
	}

	/**
	 *  we test custom query
	 *  {@link Post_Table#findBySubject_groupByTag(String)} (String)} (String)}
	 *  it groups data with tag
	 *  and out of four posts
	 *  there are two posts that have same subject
	 *  so size of query results has to be three
	 */
	@Test
	public void testGroupByHaving() throws Exception {
		Post_Table.RowSet res = tPost.findBySubject_groupByTag("A%");

		ImmutableList<IxPost.Snapshot> snapshots = res.orderBy(
				Post_ORM.Subject.createSortableColumn(tPost, false)
		).loadEntities();

		String[] assertValues = new String[] { postSubject4, postSubject3, postSubject2 };
		assertEquals(snapshots.size(), assertValues.length);

		for (int i = snapshots.size(); --i >= 0;) {
			IxPost.Snapshot book = snapshots.get(i);
			String subject = book.getSubject();
			assertEquals(subject, assertValues[i]);
		}
	}

	/**
	 *	test if sortOption
	 *	with query "CASE WHEN" works fine
	 */
	@Test
	public void testCustomSort() throws Exception {
		SortableColumn sortBy[] = new SortableColumn[] {
				Post_ORM.Tag.createSortableColumn(tPost, false).evaluate(
						"CASE SUBSTR(?, 1, 1) WHEN 'F' THEN 0 " +
								"WHEN 'P' THEN 1 " +
								"WHEN 'T' THEN 2 " +
								"ELSE 10 END"),
				Post_ORM.Subject.createSortableColumn(tPost, false)
		};
        
		ImmutableList<IxPost.Snapshot> snapshots = tPost.orderBy(sortBy).loadEntities();
		String[] assertValues = new String[] { postSubject4, postSubject2, postSubject1, postSubject3 };
		assertEquals(snapshots.size(), assertValues.length);

		for (int i = 0; i < snapshots.size(); i++) {
			IxPost.Snapshot post = snapshots.get(i);
			String subject = post.getSubject();
			assertEquals(subject, assertValues[i]);
		}
	}

	/**
	 *  test query with sort option works fine
	 */
	@Test
	public void testSort() throws Exception {
		IxUser userRef = tUser.findByEmailAddress(userEmail);
		ImmutableList<IxPost.Snapshot> rs = userRef.getPosts().orderBy(
				Post_ORM.Subject.createSortableColumn(tPost, false),
				Post_ORM.Tag.createSortableColumn(tPost, false)).loadEntities();

		String t0 = rs.get(0).getSubject();
		assertEquals(t0.substring(t0.length() - 3), "4.1");
	}

	/**
	 *  test custom sort with sql function
	 */
	@Test
	public void testSortByFunction() throws Exception {
		IxUser pub = tUser.findByEmailAddress(userEmail);
		SortableColumn[] sortBy = new SortableColumn[] {
				Post_ORM.Subject.createSortableColumn(tPost, false).evaluate("SUBSTR(?, 13)"),
				Post_ORM.Tag.createSortableColumn(tPost, false)
		};
		ImmutableList<IxPost.Snapshot> rs = pub.getPosts().orderBy(sortBy).loadEntities();

		String t0 = rs.get(0).getSubject();
		assertEquals(t0.substring(t0.length() - 3), "1.4");
	}

	/**
	 *  test custom query which contains
	 *  "JOIN" statement
	 */
	@Test
	public void testJoinedQuery() throws Exception {
		ImmutableList<? extends EntityReference> rs;

		// we pass string that does not match
		// with any photo so size of results has to be 0
		rs = tUser.findByPhotoNameLike("%eitidneli%").selectEntities();
		assertEquals(rs.size(), 0);

		rs = tUser.findByPhotoNameLike("%photo%").selectEntities();
		assertEquals(rs.size(), tUser.getEntityCount());
	}

	/**
	 *  ObservableCachedEntities list have to be updated
	 *  when new entity is created and index of created entity
	 *  in list has to be managed properly considering sort option
	 */
	@Test
	public void testInsertNotificationOfSortedQuery() throws Exception {

		IxUser pub = tUser.findByEmailAddress(userEmail);
		ObservableCachedEntities.ReferenceList<IxPost> rs = pub.getPosts()
				.orderBy(Post_ORM.Subject.createSortableColumn(tPost, false),
					Post_ORM.Tag.createSortableColumn(tPost, false)).makeAsyncEntities();
		int test_idx = 0;

		ObservableCachedEntities.Observer observer = null;
		try {
			observer = rs.addAsyncObserver(new ObservableCachedEntities.Observer() {

				@Override
				public void onDataChanged(ChangeType type, int index, int movedIndex, EntityReference ref) {
					assertEquals(type, ChangeType.Create);
					assertEquals(test_idx + 1, index);

					synchronized (QueryTest.this) {
						QueryTest.this.notifyAll();
					}
				}

				public void onEntireChanged() {
				}
			});

			IxPost.Editor editPost = tPost.newEntity();
			IxPost.Snapshot post = rs.get(test_idx).tryLoadSnapshot();
			editPost.editBody().setBody("Body");
			editPost.setCreatedTime(DateTime.now());
			editPost.setUser(post.getUser());
			editPost.setSubject("Algorithms-3.4");
			editPost.setTag(post.getTag());
			editPost.save();

			synchronized (this) {
				try {
					this.wait(1000);
				} catch (Exception e) {
					throw e;
				}
			}
		}
		finally {
			rs.removeObserver(observer);
		}
	}

	/**
	 *  same with test above
	 *  except it tests entity update
	 */
	@Test
	public void testChangeNotificationOfSortedQuery() throws Exception {

		IxUser pub = tUser.findByEmailAddress(userEmail);
		ObservableCachedEntities.ReferenceList<IxPost> rs = pub.getPosts().orderBy(
				Post_ORM.Subject.createSortableColumn(tPost, true),
				Post_ORM.Tag.createSortableColumn(tPost, false)).makeAsyncEntities();

		ObservableCachedEntities.Observer observer = rs.addAsyncObserver(new ObservableCachedEntities.Observer() {

			@Override
			public void onDataChanged(ChangeType type, int index, int movedIndex, EntityReference ref) {
				assertEquals(type, ChangeType.Move);
				assertEquals(rs.size() - 1, movedIndex);
				assertEquals(0, index);

				synchronized (QueryTest.this) {
					QueryTest.this.notifyAll();
				}
			}

			public void onEntireChanged() {
			}
		});

		IxPost.Snapshot firstBook = rs.get(0).tryLoadSnapshot();
		IxPost.Editor firstBookEditor = firstBook.editEntity();
		// change title so that entity should be last
		// in the list.
		firstBookEditor.setSubject("z" + firstBook.getSubject().substring(1));
		firstBookEditor.save();

		synchronized (this) {
			this.wait();
		}

		rs.removeObserver(observer);
	}

	/**
	 *  same with test above
	 *  except it tests entity delete
	 */
	@Test
	public void testDeleteNotificationOfSortedQuery() throws Exception {
		IxUser pub = tUser.findByEmailAddress(userEmail);
		ObservableCachedEntities.ReferenceList<IxPost> rs = pub.getPosts().orderBy(
				Post_ORM.Subject.createSortableColumn(tPost, true),
				Post_ORM.Tag.createSortableColumn(tPost, false)).makeAsyncEntities();

		ObservableCachedEntities.Observer observer = rs.addAsyncObserver(new ObservableCachedEntities.Observer() {
			@Override
			public void onDataChanged(ChangeType type, int idx, int movedIndex, EntityReference ref) {
				assertEquals(type, ChangeType.Delete);
				assertEquals(rs.get(1).tryLoadSnapshot().getSubject(), postSubject3);

				synchronized (QueryTest.this) {
					QueryTest.this.notifyAll();
				}

			}

			public void onEntireChanged() {
			}
		});

		rs.get(1).deleteEntity();

		synchronized (this) {
			this.wait(500);
		}

		rs.removeObserver(observer);
	}

	/**
	 *  check custom query data
	 *  using ORMCursor
	 */
	@Test
	public void testCustomCursorQuery() throws Exception {
        User_Table.RowSet view = tUser.findByPhotoNameLike("%photo%");
		int cntSelected = view.forEachColumns(new AbstractCursor.Visitor() {
			public boolean onNext(AbstractCursor cursor) throws SQLException {
				String addr = cursor.getString(Photo_ORM.PhotoName);
				assertTrue(addr.contains("photo"));
				return true;
			}
		}, Photo_ORM.PhotoName);
		assertTrue(cntSelected >= 1);
	}

	/**
	 *  test SQL global function
	 */
	@Test
	public void testFunctionSelect() throws Exception {
		AbstractColumn cnt = AbstractColumn.evaluate("count()");
		int cntSelected = tPost.forEachColumns(new AbstractCursor.Visitor() {
			public boolean onNext(AbstractCursor cursor) throws SQLException {
				int count = cursor.getInt(cnt);
				assertEquals(count, tPost.getEntityCount());
				return true;
			}
		},cnt);
		assertEquals(cntSelected, 1);
	}

	/**
	 *  global functions have to be cached
	 */
	@Test
	public void testGlobalFunctionCache() throws Exception {
		AbstractColumn cnt = AbstractColumn.evaluate("count()");
		AbstractColumn cnt2 = AbstractColumn.evaluate("count()");
		assertTrue(cnt == cnt2);
	}

	/**
	 *  when creating new entity
	 *  if there is already an entity with
	 *  unique column value
	 *  error has to be thrown
	 */
	@Test (expected = SQLException.class)
	public void testInsertDuplicateUniqueEntity() throws Exception {
		IxLike.Snapshot like = tLike.loadFirst();

		IxLike.Editor editLike = tLike.newEntity();
		editLike.setUser(like.getUser());
		editLike.setPost(like.getPost());
		editLike.save();
	}

	/**
	 *  same with test above
	 *  exception it updates entity
	 *  with same unique column value
	 */
	@Test (expected = SQLException.class)
	public void testUpdateDuplicateUniqueEntity() throws Exception {

		ImmutableList<IxLike> likeRefs = tLike.selectEntities();

		IxLike firstLike = likeRefs.get(0);
		IxLike secondLike = likeRefs.get(1);

		IxLike.Editor editLike = secondLike.loadSnapshot().editEntity();
		editLike.setPost(firstLike.getPost());
		editLike.setUser(firstLike.tryLoadSnapshot().getUser());
		editLike.save();
	}

	/**
	 *	when creating entity,
	 *	if field value is null which is not nullable
	 *	defined by column flag {@link org.slowcoders.storm.orm.ORMFlags#Nullable},
	 *	error has to be thrown
	 */
	@Test (expected = InvalidEntityValueException.class)
	public void testNotNullError() throws Exception {
		// we did not assign createdTime
		// which has to be  not-null
		IxPost.Editor editPost = tPost.newEntity();
		editPost.setSubject("test-subject");
		editPost.setTag("test-tag");
		editPost.save();
	}

	/**
	 *  same with test above
	 *  except it assigns null to
	 *  NotNull column
	 */
	@Test (expected = InvalidEntityValueException.class)
	public void testNotNullError_2() throws Exception {
		IxPost.Editor editor = tPost.newEntity();
		editor.setSubject("test-subject");
		editor.setTag("test-tag");
		editor.setCreatedTime(null);
		editor.save();
	}

	/**
	 *	check if foreign key entity snapshot can load
	 *	its joined sub entity snapshots
	 */
	@Test
	public void testLoadSnapshot() throws Exception {
		ImmutableList<IxPost.Snapshot> posts = tPost.findByUser(tUser.findByEmailAddress(userEmail)).loadEntities();

		assertEquals(4, posts.size());

		for (IxPost.Snapshot post : posts){

			assertNotNull(post.getUser());
			assertNotNull(post.getBody());
			assertNotNull(post.getCreatedTime());

			ImmutableList<IxComment.Snapshot> comments = post.getComments();
			for (IxComment.Snapshot comment : comments) {
				assertNotNull(comment.getPost());
				assertNotNull(comment.getText());

				ImmutableList<IxSubComment.Snapshot> subComments = comment.getSubComments();
				for (IxSubComment.Snapshot subComment : subComments) {
					assertNotNull(subComment.getComment());
					assertNotNull(subComment.getText());
				}
			}

			ImmutableList<IxLike.Snapshot> likes = post.peekLikes();
			for (IxLike.Snapshot like : likes) {
				assertNotNull(like.getPost());
				assertNotNull(like.getUser());
			}
		}

	}

	/**
	 * 	according to orm definition
	 * 	in Like_ORM {@link test.storm.ormtest.schema.Like_ORM},
	 *
	 * 	there can be multiple entities
	 * 	that have same _publisher or same _title
	 * 	with one another
	 *
	 * 	but there can not be entities of which
	 * 	_publisher and _title are both same
	 *
	 */
	@Test (expected = Exception.class)
	public void testPartOfUnique() throws Exception {
		preparePost("Test Post ~!!", userEmail);

		// it is allowed to create entity
		// with same _user
		IxLike.Snapshot like = tLike.loadFirst();

		IxLike.Editor editLike = tLike.newEntity();
		editLike.setUser(like.getUser());
		editLike.setPost(tPost.findBySubject("Test Post ~!!").selectFirst());
		editLike.save();


		// but if we try to create entity with
		// same _user and _post
		// error is thrown
		editLike = tLike.newEntity();
		editLike.setUser(like.getUser());
		editLike.setPost(like.getPost());
		editLike.save();
	}

	/**
	 *  custom queries can defined in
	 *  ORM definition class
	 *  see usage {@link Post_ORM.Queries}
	 */
	@Test
	public void testQueriesInterfaceCustomQuery () throws Exception {

		ImmutableList<IxPost> postRefs = tPost.selectEntities();

		String[] postSubjects = {postSubject1, postSubject2, postSubject3, postSubject4};

		// findByTitle 쿼리를 호출하고 쿼리의 결과값이 일치하는지 확인
		for (int i = 0; i < postRefs.size(); i++){
			IxPost postRef = tPost.findBySubject("%" + postSubjects[i]).selectEntities().get(0);
			assertEquals(postSubjects[i], postRef.loadSnapshot().getSubject());
		}
	}

	/**
	 * 	when column is defined to be hidden
	 * 	with orm flag {@link org.slowcoders.storm.orm.ORMFlags#Hidden}
	 *
	 * 	it is not accessible through storm entities
	 * 	but still column is added to table
	 *
	 * 	this column can be used through custom query
	 */
	@Test
	public void testHiddenColumns () throws Exception {

//		ImmutableList<IxPost> refBooks = tPost.selectEntities();
//		ImmutableList<IxPost> refBestSellers = tPost.findBestsellers().selectEntities();
//		for (int i = 0; i < refBooks.size(); i++){
//			assertEquals(refBooks.get(i), refBestSellers.get(i));
//		}
	}

	/**
	 * 	test LocalDateTime Converter
	 * 	if it serializes and deserializes data properly
	 */
	@Test
	public void testLocalDateTimeConverter() throws Exception {
		IxPost postRef = tPost.selectEntities().get(0);
		assertNotNull(postRef.loadSnapshot().getLocalCreatedTime());
	}

	/**
	 *  test list type column
	 */
	@Test
	public void testListGenericColumn() throws Exception {
		ImmutableList<IxPost> postRefs = tPost.selectEntities();

		IxPost postRef = postRefs.get(0);
		IxPost.Editor editPost = postRef.loadSnapshot().editEntity();

		ArrayList<String> urls = new ArrayList<>();
		urls.add("http://image.com/23");
		urls.add("http://image.com/12987444");
		urls.add("http://image.com/35");
		// LocalDateTime 을 담은 ArrayList 를 publishDates 로 저장
		editPost.setImageUrls(urls);
		editPost.save();

		ImmutableList<String> imageUrls = postRef.loadSnapshot().getImageUrls();

		assertEquals(urls.size(), imageUrls.size());

		for (int i = 0; i < imageUrls.size(); i++){
			assertEquals(urls.get(i), imageUrls.get(i));
		}
	}

	/**
	 *  test enumSet type column
	 */
	@Test
	public void testEnumSetGenericColumn() throws Exception {
		IxUser userRef = getUser(userEmail);

		IxUser.Snapshot user = userRef.loadSnapshot();
		IxUser.Editor editUser = user.editEntity();

		EnumSet<User_ORM.UserGender> gender = EnumSet.of(User_ORM.UserGender.Male);
		editUser.setGender(gender);
		editUser.save();

		assertEquals(gender, userRef.loadSnapshot().getGender());
	}

	/**
	 * 	when editing entity
	 * 	if there is no change in data including
	 * 	data structures (list, map, ...)
	 * 	db access should not be occurred
	 */
	@Test
	public void updateDirtyFieldWithSameValue() throws Exception {
		ImmutableList<IxPost> postRefs = tPost.selectEntities();

		IxPost postRef = postRefs.get(0);
		IxPost.Editor editPost = postRef.loadSnapshot().editEntity();
		List<String> dates = editPost.getImageUrls();
		editPost.save();

		int queryCnt = JDBCStatement.DebugUtil.execStatementCount;

		editPost.save();

		Assert.assertEquals(queryCnt, JDBCStatement.DebugUtil.execStatementCount);
	}

	@Test
	public void testCustomCursor2() throws Exception {
		AbstractColumn sectionName = Post_ORM.Tag;
		AbstractColumn rowCount = Post_ORM.Tag.createSortableColumn(tPost, false).evaluate("COUNT(?)");
		StormFilter rs = tPost.groupByTag().orderBy(Post_ORM.Tag.createSortableColumn(tPost, false));


		class StructAssert {
			public int rowCount;
			public String name;

			public StructAssert(int aRowCount, String aName) {
				rowCount = aRowCount;
				name = aName;
			}
		}
		StructAssert[] structAsserts = new StructAssert[] {
				new StructAssert(1, "Travel"),
				new StructAssert(2, "Programming"),
				new StructAssert(1, "Food"),
		};

		int countEntity = rs.forEachColumns(new AbstractCursor.Visitor() {
			int i = 0;
			public boolean onNext(AbstractCursor cursor) throws SQLException {
				// TODO Auto-generated method stub
				StructAssert structAssert = structAsserts[i++];

				String name = cursor.getString(sectionName);
				assertEquals(name, structAssert.name);

				int rCount = cursor.getInt(rowCount);
				assertEquals(rCount, structAssert.rowCount);

				//			System.out.println(name + " " + rCount);
				return true;
			}
		}, sectionName, rowCount);
	}

	@Test
	public void testSortedObservableEntities() throws SQLException, InterruptedException {
		ObservableCachedEntities.ReferenceList<IxPost> entities = new ObservableCachedEntities.ReferenceList<>();
		StormFilter filter = tPost.orderBy(
				Post_ORM.CreatedTime.createSortableColumn(tPost, true)
		);
		entities.bindFilter(filter);

		int size = entities.size();
		entities.addAsyncObserver(new ObservableCachedEntities.Observer() {
			@Override
			public void onDataChanged(ChangeType type, int index, int movedIndex, EntityReference entityReference) {
				synchronized (QueryTest.this) {
					QueryTest.this.notifyAll();
				}
			}

			@Override
			public void onEntireChanged() {
				synchronized (QueryTest.this) {
					QueryTest.this.notifyAll();
				}
			}
		});

		IxPost.Editor editPost = tPost.newEntity();
		editPost.setCreatedTime(DateTime.now());
		editPost.setLocalCreatedTime(LocalDateTime.now());
		editPost.editBody().setBody("Test Body ~!!");
		editPost.setUser(_TableBase.tUser.findByEmailAddress(userEmail));
		editPost.setTag(null);
		editPost.save();

		synchronized (this) {
			this.wait(1000);
		}

		Assert.assertEquals(size + 1, entities.size());
	}

	@Test
	public void testDefaultOrderBy() throws InterruptedException, SQLException {
		ObservableCachedEntities.ReferenceList<IxUser> users = new ObservableCachedEntities.ReferenceList<>();
		users.bindFilter(tUser);

		int size = users.size();
		users.addAsyncObserver(new ObservableCachedEntities.Observer() {
			@Override
			public void onDataChanged(ChangeType type, int index, int movedIndex, EntityReference entityReference) {
				synchronized (QueryTest.this) {
					QueryTest.this.notifyAll();
				}
			}

			@Override
			public void onEntireChanged() {
				synchronized (QueryTest.this) {
					QueryTest.this.notifyAll();
				}
			}
		});

		IxUser.Editor editUser = tUser.edit_withEmailAddress("new-email@sss.com");
		editUser.setName("name");
		editUser.setGender_Female(true);
		editUser.save();

		synchronized (this) {
			this.wait(1000);
		}

		Assert.assertEquals(size + 1, users.size());
	}

	@Test
	public void testSortOnChangeItem() throws SQLException, InterruptedException {
		ObservableCachedEntities.SnapshotList<IxPost.Snapshot> posts = new ObservableCachedEntities.SnapshotList<>();
		posts.bindFilter(tPost.orderBy(
				Post_ORM.Subject.createSortableColumn(tPost, true)
		));
		posts.addAsyncObserver(new ObservableCachedEntities.Observer() {
			@Override
			public void onDataChanged(ChangeType type, int index, int movedIndex, EntityReference entityReference) {
				synchronized (QueryTest.this) {
					QueryTest.this.notifyAll();
				}
			}

			@Override
			public void onEntireChanged() {

			}
		});

		IxPost.Snapshot post = posts.get(1);
		IxPost.Editor editPost = post.editEntity();
		editPost.setSubject("Algorithms-5.1");
		editPost.save();

		synchronized (QueryTest.this) {
			this.wait(1000);
		}

		Assert.assertEquals(posts.get(3).getEntityReference(), post.getEntityReference());
	}

	@Test
	public void testSortByJoinedColumn() throws SQLException, InterruptedException {
		ObservableCachedEntities.SnapshotList<IxPost.Snapshot> posts = new ObservableCachedEntities.SnapshotList<>();
		posts.bindFilter(tPost.orderBy(
				Body_ORM.Body.createJoinedSortableColumn(Post_ORM.Body, Body_ORM.Post, tBody, true)
		));
		posts.addAsyncObserver(new ObservableCachedEntities.Observer() {
			@Override
			public void onDataChanged(ChangeType type, int index, int movedIndex, EntityReference entityReference) {
				Assert.assertEquals(ChangeType.Create, type);
				Assert.assertEquals(0, index);
				synchronized (QueryTest.this) {
					QueryTest.this.notifyAll();
				}
			}

			@Override
			public void onEntireChanged() {

			}
		});

		IxPost.Editor editPost = tPost.newEntity();
		editPost.setUser(tUser.selectFirst());
		editPost.editBody().setBody("haha");
		editPost.save();

		synchronized (this) {
			this.wait();
		}
	}

	@Test
	public void testSortByCustomJoinedQuery() throws SQLException, InterruptedException {
		String sql = " LEFT OUTER JOIN tBody ON tPost.rowid == tBody._post WHERE tBody._body IS NOT NULL";
		StormQuery query = tPost.createQuery(sql, null);
		ObservableCachedEntities.SnapshotList<IxPost.Snapshot> posts = new ObservableCachedEntities.SnapshotList<>();
		posts.bindFilter(new StormFilter(query));
		posts.addAsyncObserver(new ObservableCachedEntities.Observer() {
			@Override
			public void onDataChanged(ChangeType type, int index, int movedIndex, EntityReference entityReference) {
				Assert.assertEquals(ChangeType.Create, type);
				Assert.assertEquals(0, index);
				synchronized (QueryTest.this) {
					QueryTest.this.notifyAll();
				}
			}

			@Override
			public void onEntireChanged() {

			}
		});

		IxPost.Editor editPost = tPost.newEntity();
		editPost.setUser(tUser.selectFirst());
		editPost.editBody().setBody("haha");
		editPost.save();

		synchronized (this) {
			this.wait();
		}
	}


//	/*
//	 * Sectioning Using ORMQuery.
//	 */
//	@Test
//	public void testCustomCursor() throws Exception {
//		QueryColumn sectionName = tPost.Author;
//		QueryColumn rowCount = ORMQuery.makeColumnFunction("COUNT(?)", tPost.Author);
//		ORMQuery view = tPost.createCustomView(null, sectionName, rowCount);
//
//		SortOption sortBy = new SortOption(
//				new ORMColumn[] { tPost.Author },
//				null,
//				new SortableColumn[] { tPost.Author });
//		QueryCursor cursor = view.search(null, sortBy);
//
//		class StructAssert {
//			public int rowCount;
//			public String name;
//
//			public StructAssert(int aRowCount, String aName) {
//				rowCount = aRowCount;
//				name = aName;
//			}
//		}
//		StructAssert[] structAsserts = new StructAssert[] {
//				new StructAssert(1, "Han"),
//				new StructAssert(2, "Kelly"),
//				new StructAssert(1, "Park"),
//		};
//
//		int i = 0;
//		while (cursor.moveToNext()) {
//		    StructAssert structAssert = structAsserts[i];
//
//			String name = cursor.getString(sectionName);
//			assertEquals(name, structAssert.name);
//
//			int rCount = cursor.getInt(rowCount);
//			assertEquals(rCount, structAssert.rowCount);
//
////			System.out.println(name + " " + rCount);
//			i++;
//		}
//		cursor.close();
//	}



}
