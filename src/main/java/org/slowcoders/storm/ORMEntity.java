package org.slowcoders.storm;

import org.slowcoders.storm.orm.ORMProxy;

public interface ORMEntity extends ORMProxy {

	EntityReference getEntityReference();

	ORMEntity getStormEntity(StormTable table);

	interface UpdateForm extends ORMProxy {
		EntitySnapshot getOriginalData();
	}
}
