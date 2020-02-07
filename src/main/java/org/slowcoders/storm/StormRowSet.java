package org.slowcoders.storm;

import com.google.common.collect.ImmutableList;

import java.sql.SQLException;

public interface StormRowSet<SNAPSHOT extends EntitySnapshot,
		REF extends EntityReference,
		EDITOR extends EntityEditor> {

	int getEntityCount();

	int forEach(EntityVisitor<REF> visitor) throws Exception;

	int forEachColumns(AbstractCursor.Visitor visitor, AbstractColumn... columns) throws SQLException;

	REF selectFirst();

	SNAPSHOT loadFirst();

	ImmutableList<SNAPSHOT> loadEntities();
	
	ImmutableList<REF> selectEntities();
	
	StormRowSet orderBy(SortableColumn... orderBy);

	StormTable<?,?,?> getTable();

	EDITOR newEntity();
	
    long getNextRowId(ORMEntity entity);

	int getIndexOfNextEntity(EntitySnapshot entity, REF[] entities, int cntEntity);

	ObservableCachedEntities.ReferenceList<REF> makeAsyncEntities();
}