
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
import org.joda.time.DateTime;
import java.lang.String;

/**
This file is generated by Storm Generator.
Do not modify this file.
*/

public interface IxSubComment_ extends SubComment_ORM {

	public interface UpdateForm extends ORMEntity.UpdateForm {

		IxSubComment.Snapshot getOriginalData();

		IxComment getComment();

		DateTime getCreatedTime();

		String getText();

		IxUser getUser();

		default boolean equalsTo(IxSubComment.UpdateForm data) {
			return
			StormUtils.equals(this.getComment(), data.getComment()) &&
			StormUtils.equals(this.getCreatedTime(), data.getCreatedTime()) &&
			StormUtils.equals(this.getText(), data.getText()) &&
			StormUtils.equals(this.getUser(), data.getUser()) ;
		}

	}


}
