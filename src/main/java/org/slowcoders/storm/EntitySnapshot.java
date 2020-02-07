package org.slowcoders.storm;

import java.sql.SQLException;

import org.slowcoders.io.serialize.DataReader;
import org.slowcoders.io.serialize.IOEntity;
import org.slowcoders.io.serialize.IOField;
import org.slowcoders.util.Debug;

public abstract class EntitySnapshot
		implements ORMEntity, /*IOEntity,*/ Cloneable {
	private EntityReference ref;

	protected EntitySnapshot(EntityReference ref) {
		if (ref != null) {
			this.setReference_unsafe(ref);
		}
	}

	public abstract StormTable getTable();

	public final boolean isDeleted() { return ref != null && ref.isDeleted(); }

	public final long getEntityId() {
		return ref == null ? 0 : ref.getEntityId();
	}

	/*internal*/ final void setReference_unsafe(EntityReference ref) {
		this.ref = ref;
	}

	public EntityReference getEntityReference() {
		return ref;
	}

	protected final EntityReference getReference_internal() {
		return ref;
	}

	protected abstract EntityEditor editEntity();

	public ORMEntity getStormEntity(StormTable table) {
		Debug.Assert(this.getTable() == table);
		return this;
	}
	
	public boolean equals(Object other) {
		return this == other;
	}
	
	public String toString() {
		return IOEntity.toString(this);
	}


	protected void onLoadSnapshot() throws SQLException {
	}

	protected void ensureLoadSubSnapshot() {
	}

    protected void setFieldValue(IOField field, Object value) {
		try {
			field.getReflectionField().set(this, value);
		} catch (IllegalAccessException e) {
			throw Debug.wtf(e);
		}
	}

	protected Object getFieldValue(IOField field) {
		try {
			return field.getReflectionField().get(this);
		} catch (IllegalAccessException e) {
			throw Debug.wtf(e);
		}
	}

	public void setProperty(IOField col, DataReader reader) throws Exception {
		IOEntity.setProperty_unsafe(col, this, reader);
	}
}