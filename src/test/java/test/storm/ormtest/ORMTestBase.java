package test.storm.ormtest;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.junit.Assert;
import org.slowcoders.storm.StormTable;
import org.slowcoders.util.Debug;
import test.storm.ormtest.gen.model.*;
import test.storm.ormtest.gen.model.storm.Post_Table;
import test.storm.ormtest.gen.model.storm._TableBase;
import test.storm.ormtest.schema.TestDatabase;
import test.storm.ormtest.schema.User_ORM;

import java.sql.SQLException;
import java.util.EnumSet;

import static test.storm.ormtest.gen.model.storm._TableBase.*;

public class ORMTestBase {

	public static final String testUserEmail = "tester@storm.org";

	static {
		try {
			Post_Table tPost = _TableBase.tPost; // for static initialization
			TestDatabase.TestUtil.initDatabase();
		} catch (Exception e) {
			throw Debug.wtf(e);
		}
	}

	public IxPost getFirstPost(String email) {
		IxUser user = tUser.findByEmailAddress(email);
		IxPost post = user.getPosts().selectEntities().get(0);
		return post;
	}

	public IxPost getPostBySubject(String postSubject) {
		IxPost post = tPost.findBySubject(postSubject).selectFirst();
		return post;
	}

	public static void assertEmpty(StormTable table) throws Exception {
		Assert.assertNull(table.selectFirst());
	}

	public static void preparePost(String subject, String email) {
		registerPost(email, subject, null);
	}

	public static void preparePost(String subject, String email, String tag) {
		registerPost(email, subject, tag);
	}

	public static void addComment(String postSubject, String comment) throws SQLException {
		IxPost postRef = tPost.findBySubject(postSubject).selectFirst();
		IxPost.Editor editPost = postRef.loadSnapshot().editEntity();

		IxComment.Editor editComment = tComment.newEntity();
		editComment.setText(comment);
		editComment.setUser(tUser.selectFirst());
		editPost.editComments().add(editComment);
		editPost.save();
	}

	public static void clearTestDB(String email) {
		IxUser refUser = tUser.findByEmailAddress(email);
		if (refUser != null) {
			try {
				refUser.deleteEntity();
			}
			catch (Exception e) {
				e.printStackTrace();
				//assertTrue(false);
			}
		}
	}

	protected IxUser getUser(String email) {
		return tUser.findByEmailAddress(email);
	}

	protected static IxUser prepareUser(String emailAddress) {
		try {
			IxUser.Editor user = tUser.edit_withEmailAddress(emailAddress);
			return user.save();
		} catch (Exception e) {
			throw Debug.wtf(e);
		}
	}

	protected static void registerPost(String email, String subject, String tag) {
		try {
			IxUser.Editor editUser = tUser.edit_withEmailAddress(email);
			editUser.setName("slowcoder");

			IxDescription.Editor editDesc = editUser.editDescription();
			editDesc.setText("hi, I like to code");

			IxPhoto.Editor editPhoto = editUser.editPhoto();
			editPhoto.setPhotoName("my photo");

			IxPost.Editor editPost = tPost.newEntity();
			editPost.setUser(editUser);
			editPost.setSubject(subject);
			editPost.setTag(tag);
			editPost.setCreatedTime(DateTime.now());
			editPost.setLocalCreatedTime(LocalDateTime.now());

			IxBody.Editor editBody = editPost.editBody();
			editBody.setBody(email);
			editUser.editPosts().add(editPost);

			IxComment.Editor editComment = tComment.newEntity();
			editComment.setUser(editUser);
			editComment.setText("Comment for " + subject);
			editComment.setPost(editPost);

			IxSubComment.Editor editSubComment = tSubComment.newEntity();
			editSubComment.setText("hi dude");
			editSubComment.setComment(editComment);
			editComment.setUser(editUser);

			editComment.editSubComments().add(editSubComment);
			editPost.editComments().add(editComment);

			IxLike.Editor likeEditor = tLike.edit_withPostAndUser(editPost, editUser);
			editPost.editLikes().add(likeEditor);

			editUser.save();

			TestDatabase.clearAllCache();
		} catch (Exception e) {
			throw Debug.wtf(e);
		}
	}

//	protected static void registerBookOrder(String bookTitle, String publisherName, String author, String email) {
//		try {
//			User_Editor customer = tUser.edit_withEmailAddress(email);
//			Publisher_Editor publisher = tPublisher.edit_withName(publisherName);
//			Book_Editor book = tBook.edit_withPublisherAndTitle(publisher, bookTitle);
//			book.setAuthor(author);
//
//			List<LocalDateTime> publishDates = book.getPublishDates();
//			publishDates.add(new LocalDateTime());
//
//			Introduction_Editor intro = tIntroduction.edit_withOwner(book);
//			if (intro.getEntityReference() == null) {
//				intro.setHtmlText("It is the best book about ...");
//			}
//			Order_Editor order = tOrder.edit_withCustomerAndBook(customer, book);
//			int amount = 1;
//			try {
//				amount = Integer.parseInt(bookTitle.substring(bookTitle.length() - 1));
//			}
//			catch (Exception e) {
//				// ignore;
//			}
//			order.setAmount(amount);
//			Delivery_Editor delivery = order.editDelivery();
//			delivery.setAddress("Address for " + bookTitle);
//			delivery.setExpectedLocalDeliveryTime(LocalDateTime.now().plusDays(1));
//			delivery.setExpectedDeliveryTime(DateTime.now().plusDays(1));
//			Comment_Editor comment = delivery.editComment();
//			comment.setText(email + " ordered a " + bookTitle + " of " + publisherName);
//			order.save();
//			intro.save();
//
//			TestDatabase.clearAllCache();
//		}
//		catch (Exception e) {
//			throw Debug.wtf(e);
//		}
//	}

}
