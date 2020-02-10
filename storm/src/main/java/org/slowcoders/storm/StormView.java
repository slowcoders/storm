package org.slowcoders.storm;

import com.google.common.collect.ImmutableList;
import org.slowcoders.util.Debug;

import java.lang.ref.WeakReference;
import java.sql.SQLException;

class StormView<SNAPSHOT extends EntitySnapshot, REF extends EntityReference, EDITOR extends EntityEditor, FORM extends ORMEntity.UpdateForm>
implements StormRowSet<SNAPSHOT, REF, EDITOR> {

    private StormQuery query;
	private Object[] searchParams;
	private WeakReference<VolatileSelection<REF>> selectedEntities_cache = uninitializedEntities;
	private WeakReference<ObservableCachedEntities.ReferenceList<REF>> asyncEntities_cache = uninitializedEntities;
	private final static WeakReference uninitializedEntities = new WeakReference(null);

	public StormView(StormQuery query, Object[] searchParams) {
		this.query = query;
		this.searchParams = searchParams;
	}

	protected void setBaseQuery_unsafe(StormQuery query) {
		Debug.Assert(this.query == null);
		this.query = query;
	}


	public final StormQuery getQuery() {
		return this.query;
	}

	public final StormFilter<SNAPSHOT, REF, EDITOR> orderBy(SortableColumn... orderBy) {
		return new StormFilter<>(this.query.sortBy(orderBy), this.searchParams);
	}

	public final REF[] getEntityIDs() throws SQLException {
		REF[] ids = this.query.search(searchParams);
		return ids;
	}
	
	protected final REF getEntityReference(long id) {
		return getTable().makeEntityReference(id);
	}
	
	protected final Object getSearchParam(int argIndex) {
		return searchParams[argIndex];
	}
	
	public final StormTable<SNAPSHOT, REF, EDITOR> getTable() {
		return (StormTable<SNAPSHOT, REF, EDITOR>) query.getTable();
	}
	
	public final int forEach(EntityVisitor<REF> visitor) throws SQLException {
		return query.forEach(visitor, searchParams);
	}
	
	
	@Override
	public final int getEntityCount() {
	    ImmutableList<REF> shared = selectEntities();
		return shared.size();
	}




	public final ImmutableList<SNAPSHOT> loadEntities() {
		try {
			ImmutableList result = query.loadEntities(searchParams);
			return result;
		} catch (SQLException e) {
			throw Debug.wtf(e);
		}
    }
	
	public final synchronized REF selectFirst() {
		VolatileSelection<REF> entities = selectedEntities_cache.get();
		if (entities != null) {
			return entities.get(0);
		}
		else {
			return getTable().findFirst(this.query, searchParams);
		}
	}

	public final synchronized SNAPSHOT loadFirst() {
		REF ref = selectFirst();
		return ref == null ? null : (SNAPSHOT)ref.tryLoadSnapshot();
	}

	public final synchronized ImmutableList<REF> selectEntities() {
		try {
			return query.selectEntities(searchParams);
		} catch (SQLException e) {
			throw Debug.wtf(e);
		}
	}

	static StormView asFilter(StormRowSet rowSet) {
		if (rowSet instanceof StormView) {
			return (StormView)rowSet;
		}
		return null;
	}

	public final Object[] getSearchParams() {
		return searchParams;
	}
	
	@Override
	public EDITOR newEntity() {
		return this.getTable().newEntity();
	}

	protected final void delete_inTR() throws InvalidEntityReferenceException, SQLException {
		getTable().doDeleteEntities(this.selectEntities());
	}

	public final int forEachColumns(AbstractCursor.Visitor visitor, AbstractColumn... columnns) throws SQLException {
		int cntVisit = query.forEach(columnns, this.searchParams, visitor);
		return cntVisit;
	}

	public boolean maybeContains(EntityReference ref) {
		return true;
	}

	final void invalidateVolatileSelection(VolatileSelection selection) {
		if (selectedEntities_cache.get() == selection) {
			selectedEntities_cache = uninitializedEntities;
		}
	}

	protected boolean canResolveNextEntity() {
		if (query.canResolveNextEntity()) return true;
		//this.selectedEntities_cache = uninitializedEntities;
		return false;
	}

	final VolatileSelection<REF> getFilteredSelection() {
		VolatileSelection<REF> entities = selectedEntities_cache.get();
		if (entities == null) {
			try {
				entities = new VolatileSelection<>(this);
				this.selectedEntities_cache = new WeakReference<>(entities);
			}
			catch (Exception e) {
				Debug.wtf(e);
			}
		}
		return entities;
	}


	public synchronized ObservableCachedEntities.ReferenceList<REF> makeAsyncEntities() {
		ObservableCachedEntities.ReferenceList<REF> asyncEntities = asyncEntities_cache.get();
		if (asyncEntities != null) {
			return asyncEntities;
		}
		try {
			asyncEntities = new ObservableCachedEntities.ReferenceList<>(this);
			asyncEntities_cache = new WeakReference<>(asyncEntities);
			return asyncEntities;
		}
		catch (Exception e) {
			throw Debug.wtf(e);
		}
	}

	@Override
	public final long getNextRowId(ORMEntity entity) {
		return query.getNextRowId(entity, searchParams);
	}


	public final int getIndexOfNextEntity(EntitySnapshot entity, REF[] entities, int cntEntity) {
		return query.getIndexOfNextEntity(entity, searchParams, entities, cntEntity);
	}


}