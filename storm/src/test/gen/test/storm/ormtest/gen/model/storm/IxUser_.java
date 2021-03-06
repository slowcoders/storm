
package test.storm.ormtest.gen.model.storm;

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

import test.storm.ormtest.schema.*;
import test.storm.ormtest.gen.model.*;
import java.util.List;
import test.storm.ormtest.schema.User_ORM;
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

		Set<User_ORM.UserGender> getGender();

		default boolean getGender_Male() {
			return getGender().contains(User_ORM.UserGender.Male);
		}

		default boolean getGender_Female() {
			return getGender().contains(User_ORM.UserGender.Female);
		}

		String getName();

		IxPhoto.UpdateForm getPhoto();

		List<? extends IxPost.UpdateForm> peekPosts();

		default boolean equalsTo(IxUser.UpdateForm data) {
			return
			StormUtils.equals(this.getDescription(), data.getDescription()) &&
			StormUtils.equals(this.getEmailAddress(), data.getEmailAddress()) &&
			StormUtils.equals(this.getGender(), data.getGender()) &&
			StormUtils.equals(this.getName(), data.getName()) &&
			StormUtils.equals(this.getPhoto(), data.getPhoto()) &&
			StormUtils.contentEquals(this.peekPosts(), data.peekPosts()) ;
		}

	}


}
