package org.slowcoders.storm;

import java.sql.SQLException;

import com.google.common.collect.ImmutableList;

public abstract class StormQuery {

	
	public abstract StormTable<? extends EntitySnapshot,?,?> getTable();

	public abstract long[] find(Object... serarchParams) throws SQLException;

	public abstract <T extends EntityReference> T[] search(Object... serarchParams) throws SQLException;

	public abstract int forEach(EntityVisitor visitor, Object... serarchParams) throws SQLException;
	
	public abstract int forEach(AbstractColumn[] columnns, Object[] searchParams, AbstractCursor.Visitor visitor) throws SQLException;

	public abstract <T extends EntitySnapshot> ImmutableList<T> loadEntities(Object... serarchParams) throws SQLException;

	public final <T extends EntityReference> ImmutableList<T> selectEntities(Object... values) throws SQLException {
		EntityReference[] refs = this.search(values);
		if (refs.length == 0) {
			return ImmutableList.of();
		}
		else {
			return new ImmutableReferenceList(refs, refs.length);
		}
	}

	/****************************************************************************
	 * internal methods.
	 *---------------------------------------------------------------------------*/
	
	protected abstract long getNextRowId(ORMEntity entity, Object[] values);
	
	protected abstract StormQuery sortBy(SortableColumn[] orderBy);

	protected abstract int getIndexOfNextEntity(EntitySnapshot entity, Object[] searchParams, EntityReference[] entities, int cntEntity);

	protected abstract boolean canResolveNextEntity();

}