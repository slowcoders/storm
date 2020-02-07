package org.slowcoders.storm;

import com.google.common.collect.ImmutableList;

import org.slowcoders.storm.orm.ORMField;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public class StormFilter<SNAPSHOT extends EntitySnapshot, REF extends EntityReference, EDITOR extends EntityEditor>
extends StormView<SNAPSHOT, REF, EDITOR, ORMEntity.UpdateForm> {

    private static final WeakReference<?> noCached = new WeakReference<>(null);
    private transient Reference<ImmutableList<REF>> volatileRef = (Reference)noCached;
    
	private ORMField fkSlot;

	public StormFilter(StormQuery query, Object... searchParams) {
		super(query, searchParams);
	}

	protected StormFilter(StormQuery query, ORMField foreignKey, Object... searchParams) {
		this(query, searchParams);
		this.fkSlot = foreignKey;
	}


	static StormFilter asFilter(StormRowSet rowSet) {
		if (rowSet instanceof StormFilter) {
			return (StormFilter)rowSet;
		}
		return null;
	}

	final void validate(EntityEditor dm) {
		if (this.fkSlot != null) {
			dm.validateFixedForeignKey(fkSlot, this.getSearchParam(0));
		}
	}


	public boolean maybeContains(EntityReference ref) {
		if (this.fkSlot != null) {
			Object v = fkSlot.getImmutableProperty(ref);
			return v == super.getSearchParam(0);
		}
		return true;
	}


}