
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

/**
This file is generated by Storm Generator.
Do not modify this file.
*/

public interface IxLike_ extends Like_ORM {

	public interface UpdateForm extends ORMEntity.UpdateForm {

		IxLike.Snapshot getOriginalData();

		IxPost getPost();

		IxUser getUser();

		default boolean equalsTo(IxLike.UpdateForm data) {
			return
			StormUtils.equals(this.getPost(), data.getPost()) &&
			StormUtils.equals(this.getUser(), data.getUser()) ;
		}

	}


}