package org.slowcoders.storm.orm;

import org.slowcoders.storm.ORMEntity;
import org.slowcoders.storm.EntityReference;
import org.slowcoders.storm.StormTable;

public interface ORMProxy {

	ORMEntity getStormEntity(StormTable table);

	default EntityReference getEntityReference() { return null; }
}
