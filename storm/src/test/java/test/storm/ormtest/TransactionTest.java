package test.storm.ormtest;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slowcoders.storm.ConflictedUpdateException;
import org.slowcoders.storm.EntityReference;
import org.slowcoders.storm.StormTable;
import org.slowcoders.storm.TransactionalOperation;
import org.slowcoders.util.Debug;
import test.storm.ormtest.gen.model.IxComment;
import test.storm.ormtest.gen.model.IxPost;
import test.storm.ormtest.gen.model.IxSubComment;
import test.storm.ormtest.gen.model.IxUser;

import java.sql.SQLException;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static test.storm.ormtest.gen.model.storm._TableBase.*;

public class TransactionTest extends ORMTestBase {

	boolean editSameEntity;
	int delayTimeAfterStart_inTR1;
	int delayTimeAfterStart_inTR2;
	int delayTimeBeforeCommit_inTR1;
	int delayTimeBeforeCommit_inTR2;

	private static final String[] postSubjects = {
			"Alogrithms-1.4", "Alogrithms-2.3", "Alogrithms-3.2", "Alogrithms-4.1"
	};

	private static final String[] tags = {
			"Kelly", "Kelly", "Park", "Han"
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
	 * when two transactions
	 * edit same entity and try to save
	 *
	 * ConflictEntityException has to be thrown
	 *
	 */
	@Test (expected = ConflictedUpdateException.class)
	public void testConflictTransaction() throws Exception {
		System.out.println("testConflictTransaction started");
		this.editSameEntity = true;
		this.delayTimeAfterStart_inTR1 = 0;
		this.delayTimeAfterStart_inTR2 = 0;
		this.delayTimeBeforeCommit_inTR1 = 200;
		this.delayTimeBeforeCommit_inTR2 = 200;
		doUpdateTest();
	}

	/**
	 * in case when Tr_2 acquired DB Lock before Tr_1,
	 * if transaction is not completed in five seconds,
	 * Tr_1 encounters error below
	 *
	 * [SQLITE_BUSY]  The database file is locked.
	 */
	@Test
	public void testBusyTransaction() throws Exception {
		System.out.println("testBusyTransaction started");
		this.editSameEntity = false;
		this.delayTimeAfterStart_inTR1 = 10;   // tr2 가 먼저 시작되도록 보장.
		this.delayTimeAfterStart_inTR2 = 0;
		this.delayTimeBeforeCommit_inTR1 = 0;
		this.delayTimeBeforeCommit_inTR2 = 0;
		try {
			doUpdateTest();
		}
		catch (Exception e) {
			assertTrue(e.getMessage().startsWith("[SQLITE_BUSY]"));
		}
	}

	/**
	 * even though Tr_2 acquired DB Lock before Tr_1
	 * if transaction is completed within five second,
	 *
	 * [SQLITE_BUSY] error must not occur
	 */
	@Test
	public void testFastTransaction() throws Exception {
		System.out.println("testFastTransaction started");

		try {
//			 try {
//
//				DateTime orderTime = DateTime.now();
//				DateTime recvTime = orderTime.plusDays(2);
//				String testComment = "Test order : at " + orderTime;
//
//				{
//					Order_Editor order = getFirstOrder().loadSnapshot().edit();
//					Delivery_Editor delivery = order.editDelivery();
//					Comment_Editor comment = delivery.editComment();
//					delivery.setExpectedDeliveryTime(recvTime);
//					comment.setText(testComment);
//					comment.save();
//				}
//
//				{
//					IxOrder.Data order = this.getFirstOrder().tryLoadSnapshot();
//					IxDelivery.Data deliver = order.getDelivery();
//					IxComment.Data comment = deliver.getComment();
//
//					assertEquals(deliver.getExpectedDeliveryTime(), recvTime);
//					assertEquals(comment.getText(), testComment);
//				}
//			}
//			 catch (Exception e) {
//
//			 }
//
//
//			/*
//			 * Outer Join 을 이용하여 snapshot에 속한 모든 entity를 한 번의 SQL 실행으로 로딩한다.
//			 */
//			try {
//				// initialize test condition
//
//				IxOrder order = this.getFirstOrder();
//
//				int before = JDBCStatement.DebugUtil.execStatementCount;
//				{
//					IxDelivery.Data deliver = order.tryLoadSnapshot().getDelivery();
//					System.out.println(order.toString());
//				}
//				int after = JDBCStatement.DebugUtil.execStatementCount;
//				//assertEquals(before + 1, after);
//
//				/**
//				 * test cached access
//				 */
//				before = JDBCStatement.DebugUtil.execStatementCount;
//				{
//					IxDelivery.Data deliver = order.tryLoadSnapshot().getDelivery();
//					System.out.println(order.toString());
//				}
//				//assertEquals(before, JDBCStatement.DebugUtil.execStatementCount);
//			}
//			catch (Exception e) {
//
//			}


			/*
			 * entity 삭제시 foreignKey로 연결된 OuterEntity도 함께 삭제되어야만 한다.
			 * (delete trigger 검사)
			 */
			 try {
				IxPost postRef = this.getFirstPost(userEmail);
				IxComment.Snapshot comment = postRef.tryLoadSnapshot().getComments().get(0);
				IxSubComment.Snapshot subComment = comment.getSubComments().get(0);
				System.out.println(postRef.toString());

				int cntA = tComment.getEntityCount();
				int cntB = tSubComment.getEntityCount();
				postRef.deleteEntity();

				int cntA2 = tComment.getEntityCount();
				int cntB2 = tSubComment.getEntityCount();
				assertEquals(cntA - 1, cntA2);
				assertEquals(cntB - 1, cntB2);

				assertTrue(postRef.isDeleted());
				assertTrue(comment.getEntityReference().isDeleted());
				assertTrue(subComment.getEntityReference().isDeleted());
			}
			 catch (Exception e) {

			 }

//				 /*
//			 * OuterEntity 추가시 관련 Collection 들에 변경사항 반영 확인
//			 */
//			try {
//				IxPublisher publisher = tPublisher.findByName(userEmail);
//				EditableEntities<Book_Editor> books = publisher.getBooks().editEntities();
//				int cntBefore = books.size();
//
//				for (int i = 1; i <= 5; i ++) {
//					Book_Editor book = tBook.editEntity(publisher, null);
//					book.setTitle("Book " + i);
//					books.addOrReplace(book);
//				}
//				books.save(true);
//				int cntAfter = publisher.getBooks().editEntities().size();
//
//				assertEquals(cntBefore + 5, cntAfter);
//			}
//			catch (Exception e) {
//
//			}
//
//			/*
//			 * Foreign 키로 연결해야할 Entity 는 항상 NotNull 조건을 만족해야 한다.
//			 */
//
//			//@Test (expected = InvalidEntityValueException.class)
//			try {
//
//
//				Book_Editor book = tBook.newEntity();
//				book.setTitle("test-title");
//				book.setAuthor("test-author");
//				book.setPublisher(null);
//				book.save();
//			}
//			catch (Exception e) {
//
//			}
//
//



//			try {
//				IxPublisher refPublisher = tPublisher.findByName(userEmail);
//				VolatileEntities<IxBook> refBooks = refPublisher.getBooks().loadVolatileEntities();
//
//				for (int i = 0; i < refBooks.size(); i++){
//					IxBook refBook = refBooks.get(i);
//					assertEquals(refBook.loadSnapshot().getTitle(), booksTitles[i]);
//					assertEquals(refBook.loadSnapshot().getPublisher(), refPublisher);
//					assertEquals(refBook.loadSnapshot().getAuthor(), tags[i]);
//				}
//			}
//			catch (Exception e) {
//
//			}
//
//
//			try {
//				IxPublisher refPublisher = tPublisher.findByName(userEmail);
//				VolatileEntities<IxBook> refBooks = refPublisher.getBooks().loadVolatileEntities();
//
//				IxBook refBook = refBooks.get(0);
//
//				// 업데이트 전후의 modifyVersion 을 비교하기 위해 현재 modifyVersion 값 저장
//				long versionBeforeUpdate = refBook.loadSnapshot().getModifiyVersion();
//
//				Book_Editor editBook_1 = refBook.loadSnapshot().edit();
//				Book_Editor editBook_2 = refBook.loadSnapshot().edit();
//				editBook_1.setTitle("test-title");
//				editBook_2.setTitle("test-title2");
//
//				editBook_1.save();
//
//				// 업데이트 시 modifyVersion 의 값이 변해야 하므로 두 값은 같지 않다.
//				assertNotEquals(versionBeforeUpdate, refBook.loadSnapshot().getModifiyVersion());
//
//				// 업데이트한 데이터가 반영되었는지 확인.
//				assertEquals("test-title", refBook.loadSnapshot().getTitle());
//
//				// 이전 버전의 스냅샷으로 업데이트를 시도하려 하므로 오류 발생.
//				editBook_2.save();
//			}
//			catch (Exception e) {
//
//			}
//
//			try {
//
//				IxPublisher refPublisher = tPublisher.findByName(userEmail);
//				VolatileEntities<IxBook> refBooks = refPublisher.getBooks().loadVolatileEntities();
//
//				// 삭제 후 VolatileEntities 의 캐시를 체크하기 위해 현재 VolatileEntities 의 사이즈 저장.
//				int sizeBeforeDelete = refBooks.size();
//
//				IxBook refBook = refBooks.get(0);
//
//				Book_Editor editBook = refBook.loadSnapshot().edit();
//				refBook.delete();
//				editBook.save();
//
//				// 1개의 엔터티를 삭제했으므로 삭제 후 VolatileEntities 의 사이즈는
//				// sizeBeforeDelete - 1 이다.
//				assertEquals(sizeBeforeDelete - 1, refBooks.size());
//
//				// 엔터티가 삭제되었으므로 isDelete() 가 true 반환.
//				assertTrue(refBook.isDeleted());
//
//				// 삭제된 엔터티로 스냅샷을 불려오려 하므로 오류 발생.
//				refBook.loadSnapshot();
//			}
//			catch (Exception e) {
//
//			}

		}
		catch (Exception e){

		}

		this.editSameEntity = false;
		this.delayTimeAfterStart_inTR1 = 10;   // tr2 이 먼저 시작되도록 보장.
		this.delayTimeAfterStart_inTR2 = 0;
		this.delayTimeBeforeCommit_inTR1 = 0;
		this.delayTimeBeforeCommit_inTR2 = 0;
		doUpdateTest();
	}

	/**
	 * inside EntityEditor.save() which is executed in Tr_2
	 * if SQLITE_BUSY Exception is caught
	 * we retry save for certain count
	 */
	@Test
	public void testBusyTransactionHandler() throws Exception {
		System.out.println("testBusyTransactionHandler started");

		int retryCount1 = StormTable.UnsafeTools.getTotalRetryCount();

		this.editSameEntity = false;
		this.delayTimeAfterStart_inTR1 = 0;
		this.delayTimeAfterStart_inTR2 = 10; // tr1 이 먼저 시작되도록 보장.
		this.delayTimeBeforeCommit_inTR1 = 10_000; // tr2 내부에서 BusyStorageException 이 발생하도록 한다.
		this.delayTimeBeforeCommit_inTR2 = 0;

		doUpdateTest();

		int retryCount2 = StormTable.UnsafeTools.getTotalRetryCount();
		assertTrue(retryCount1 < retryCount2);
	}



	private void doUpdateTest() throws Exception {
		ImmutableList<IxPost> postRefs = tPost.selectEntities();
		final IxPost.Snapshot post =  postRefs.get(0).loadSnapshot();
		final IxPost.Snapshot post2 =  postRefs.get(1).loadSnapshot();

		Exception tr_exception[] = new Exception[1];
		Thread th = null;
		try {

			th = new Thread() {
				public void run() {
					try {
						if (delayTimeAfterStart_inTR2 > 0) {
							Thread.sleep(delayTimeAfterStart_inTR2);
						}
						System.out.println("tr2 started");

						if (editSameEntity) {
							IxPost.Editor editOrder = post.editEntity();

							editOrder.setSubject("Updated Subject 111");

							editOrder.save();
						}
						else {
							IxUser userRef = tUser.findByEmailAddress(userEmail);
							IxPost.Editor editPost = tPost.newEntity();
							editPost.editBody().setBody("Test Body");
							editPost.setCreatedTime(DateTime.now());
							editPost.setUser(userRef);
							editPost.setSubject("Test Post 222");
							editPost.save();
						}

						System.out.println("tr2 commit success");
					} catch (Exception e) {
						System.err.println("tr2 error " + e.getClass().getSimpleName() + " " + e.getMessage());
						Debug.ignoreException(e);
						tr_exception[0] = e;
					}
				}
			};
			th.start();

			tPost.getDatabase().executeIsolatedTransaction(new TransactionalOperation<Void>() {
				@Override
				protected Object execute_inTR(Void operationParam, long transactionId) throws SQLException {
					if (delayTimeAfterStart_inTR1 > 0) {
						try {
							Thread.sleep(delayTimeAfterStart_inTR1);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

                    IxPost.Editor editPost = post.editEntity();
					editPost.setSubject("Subject update 2223344");
					System.out.println("tr1 started");

					/**
					 * WriteLock을 획득한다.
					 */
					editPost.save();

					if (delayTimeBeforeCommit_inTR1 > 0) {
						try {
							Thread.sleep(delayTimeBeforeCommit_inTR1);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					return null;
				}
			}, null);

			System.out.println("tr1 commit success");
		}
		catch (Exception e) {
			System.err.println("tr1 error " + e.getClass().getSimpleName() + " " + e.getMessage());
			Debug.ignoreException(e);
			throw e;
		}
		finally {
			th.join();
		}
		if (tr_exception[0] != null) {
			throw tr_exception[0];
		}
	}

	@Test
	public void testReadTransaction() throws Exception {
		ImmutableList<IxPost> postRefs = tPost.selectEntities();

		tPost.getDatabase().executeIsolatedTransaction(new TransactionalOperation<Void>() {
			@Override
			protected Object execute_inTR(Void operationParam, long transactionId) throws SQLException {
				try {
					testReadTransaction_0(postRefs);
					return null;
				} catch (Exception e) {
					throw Debug.wtf(e);
				}
			}
		}, null);
		testReadTransaction_0(postRefs);
	}

	public void testReadTransaction_0(ImmutableList<IxPost> postRefs) throws Exception {
		this.editSameEntity = false;
		this.delayTimeAfterStart_inTR1 = 0;
		this.delayTimeAfterStart_inTR2 = 10; // tr1 이 먼저 시작되도록 보장.
		this.delayTimeBeforeCommit_inTR1 = 3_000; // tr2 가 tr1 종료 전에 시작하도록 한다.
		this.delayTimeBeforeCommit_inTR2 = 0;

		IxPost postRef1 = postRefs.get(0);
        IxPost postRef2 = postRefs.get(1);

		final long[] timeBeforeSave_TR2 = new long[1];
		final long[] timeBeforeCommit_TR2 = new long[1];
		final long[] timeFinished_TR2 = new long[1];
		long timeStarted_TR1 = System.currentTimeMillis();
		long timeFinished_TR1 = 0;

		Thread th = null;
		try {
			th = new Thread() {
				public void run() {
					try {
						tPost.getDatabase().executeIsolatedTransaction(new TransactionalOperation<Void>() {
							@Override
							protected Object execute_inTR(Void operationParam, long transactionId) throws SQLException {
								if (delayTimeAfterStart_inTR2 > 0) {
									try {
										Thread.sleep(delayTimeAfterStart_inTR2);
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
								}

								EntityReference.DebugUtil.clearSnapshot(postRef2);
								IxPost.Snapshot post2 = postRef2.loadSnapshot();
								IxPost.Editor editPost2 = post2.editEntity();

								editPost2.setSubject("subject -- 442");

								timeBeforeSave_TR2[0] = System.currentTimeMillis();
								editPost2.save();

								if (delayTimeBeforeCommit_inTR2 > 0) {
									try {
										Thread.sleep(delayTimeBeforeCommit_inTR2);
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
								}
								timeBeforeCommit_TR2[0] = System.currentTimeMillis();
								return null;
							}
						}, null);
						//System.out.println("tr2 commited");
					} catch (Exception e) {
						Debug.ignoreException(e);
					} finally {
						timeFinished_TR2[0] = System.currentTimeMillis();
					}
				}
			};
			th.start();

			if (delayTimeAfterStart_inTR1 > 0) {
				Thread.sleep(delayTimeAfterStart_inTR1);
			}

			EntityReference.DebugUtil.clearSnapshot(postRef1);
			IxPost.Snapshot comment1 = postRef1.loadSnapshot();

			if (delayTimeBeforeCommit_inTR1 > 0) {
				Thread.sleep(delayTimeBeforeCommit_inTR1);
			}

		}
		finally {
			timeFinished_TR1 = System.currentTimeMillis();
		}

		th.join();

		System.out.println("tr1 started: " + new Date(timeStarted_TR1));
		System.out.println("tr1 finished: " + new Date(timeFinished_TR1));
		System.out.println("tr2 beforeSave: " + new Date(timeBeforeSave_TR2[0]));
		System.out.println("tr2 beforeCommit: " + new Date(timeBeforeCommit_TR2[0]));
		System.out.println("tr2 finished: " + new Date(timeFinished_TR2[0]));

		/**
		 * TR2의 Save 동작이 TR1(=외부에서 진행 중인 ReadTransaction)과 관계없이(=Blocking 없이) 수행되는 가를 확인한다.
		 * (참고, TR2 Commit 동작은 Blocking 된다.)
		 */
		assertTrue(timeBeforeCommit_TR2[0] < timeFinished_TR1);
	}


}
