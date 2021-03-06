
package org.slowcoders.sample.orm.gen.storm;

import static org.slowcoders.storm.orm.ORMFlags.*;
import java.sql.SQLException;
import java.util.*;
import com.google.common.collect.*;
import org.slowcoders.util.*;
import org.slowcoders.storm.*;
import org.slowcoders.storm.orm.*;
import org.slowcoders.storm.util.*;
import org.slowcoders.io.serialize.*;
import org.slowcoders.observable.*;

import org.slowcoders.sample.orm.def.*;
import org.slowcoders.sample.orm.gen.*;
import java.util.List;
import org.slowcoders.sample.orm.def.User_ORM;
import org.slowcoders.sample.orm.def.User_ORM;
import java.lang.String;

/**
This file is generated by Storm Generator.
Do not modify this file.
*/

public interface IxUser_ extends User_ORM {

	public interface UpdateForm extends ORMEntity.UpdateForm {

		IxUser.Snapshot getOriginalData();

		IxDescription getDescription();

		String getEmailAddress();

		User_ORM.UserGender getGender();

		Set<User_ORM.Interest> getInterests();

		default boolean getInterests_Food() {
			return getInterests().contains(User_ORM.Interest.Food);
		}

		default boolean getInterests_Sports() {
			return getInterests().contains(User_ORM.Interest.Sports);
		}

		default boolean getInterests_Travel() {
			return getInterests().contains(User_ORM.Interest.Travel);
		}

		default boolean getInterests_Book() {
			return getInterests().contains(User_ORM.Interest.Book);
		}

		String getName();

		IxPhoto.UpdateForm getPhoto();

		List<? extends IxPost.UpdateForm> peekPosts();

		default boolean equalsTo(IxUser.UpdateForm data) {
			return
			StormUtils.equals(this.getDescription(), data.getDescription()) &&
			StormUtils.equals(this.getEmailAddress(), data.getEmailAddress()) &&
			StormUtils.equals(this.getGender(), data.getGender()) &&
			StormUtils.equals(this.getInterests(), data.getInterests()) &&
			StormUtils.equals(this.getName(), data.getName()) &&
			StormUtils.equals(this.getPhoto(), data.getPhoto()) &&
			StormUtils.contentEquals(this.peekPosts(), data.peekPosts()) ;
		}

	}


}
