package test.storm.ormtest;

import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slowcoders.storm.ColumnAndValue;
import test.storm.ormtest.gen.model.IxPost;
import test.storm.ormtest.schema.Post_ORM;

import static org.junit.Assert.assertEquals;
import static test.storm.ormtest.gen.model.storm._TableBase.tPost;

@FixMethodOrder(MethodSorters.JVM)
public class BatchOperationTest extends ORMTestBase {

	private static final String postSubject = "software maestro";
	private static final String emailAddress = "slowcoder@ssslllloooooowwww.com";

	@After
	public void tearDown(){
		clearTestDB(emailAddress);
	}

	/**
	 * 	update and delete
	 * 	multiple entities in batch
	 */
	@Test
	public void testBatchOperation() throws Exception {
		prepareUser(emailAddress);

		for (int i = 0; i < 500; i++) {
			preparePost(postSubject + '_' + i, emailAddress);
		}

		ImmutableList<IxPost> books = tPost.selectEntities();

		// update subjects of 500 posts
		// and check result
		String subject = "Author\t'\nchanged";
		ColumnAndValue[] cvs = new ColumnAndValue[] {
				new ColumnAndValue(Post_ORM.Subject, subject, true)
		};
		tPost.updateEntities(cvs, books);
		for (IxPost ref : books) {
			String author = ref.loadSnapshot().getSubject();
			assertEquals(author, subject);
		}

		// delete 500 posts in batch
		// and check results
		tPost.deleteEntities(books);

		assertEquals(0, tPost.selectEntities().size());
	}

}
