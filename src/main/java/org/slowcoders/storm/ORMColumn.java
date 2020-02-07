package org.slowcoders.storm;

import java.lang.reflect.Field;

import org.slowcoders.io.serialize.IOAdapter;
import org.slowcoders.io.serialize.IOAdapterLoader;
import org.slowcoders.io.serialize.ImmutableEntity;
import org.slowcoders.storm.orm.ForeignKey;
import org.slowcoders.storm.orm.OuterLink;
import org.slowcoders.util.Debug;
import org.slowcoders.storm.orm.ORMField;
import org.slowcoders.storm.orm.ORMFlags;

/**
 * Created by zeedh on 02/02/2018.
 */

public class ORMColumn extends ORMField implements AbstractColumn {

	private int idxColumn;
	private long updateBit;
	private Field refField;

	public ORMColumn(String key, int flags, Class<?> columnType) {
		super(key, flags, columnType);

		if (super.isUnknownType() && (IOAdapterLoader.findDefaultAdapter(columnType, null) == null || columnType.isArray())) {
			if (!ImmutableEntity.class.isAssignableFrom(columnType) && (flags & ORMFlags.UnsafeMutable) == 0) {
				throw new RuntimeException("unsafe " + key + " : " + columnType);
			}
		}
	}

	public ORMColumn forNine(String columnName) {
		return this;
	}
	
	public ORMColumn ewsVer(double value) {
		return this;
	}
	
	public final boolean isDBColumn() {
		return true;
	}

	public final void setColumnIndex_unsafe(int idx, long bit) {
		if (idx == this.idxColumn) {
			Debug.Assert(this.updateBit == bit);
			return;
		}
		if (bit < OUTER_LINK_UPDATE_BIT) {
			int a = 3;
		}
		updateBit = bit;
		Debug.Assert(idxColumn == 0 && idx > 0);
		this.idxColumn = idx;
	}

	public int getColumnIndex() {
		Debug.Assert(idxColumn > 0);
		return this.idxColumn;
	}

	public long getUpdateBit() {
		return updateBit;
	}
	
	public final Field getRefField() {
		return refField;
	}

	protected void setAdapter_unsafe(IOAdapter adapter) {
		super.setAdapter_unsafe(adapter);
	}

	final void setRefField(Field refField) {
		this.refField = refField;
		refField.setAccessible(true);
	}

	public SortableColumn createSortableColumn(StormTable table, boolean isAscent) {
		if (isAscent) {
			return new SortableColumn.Ascent(table, this);
		}
		else {
			return new SortableColumn.Descent(table, this);
		}
	}

	public SortableColumn createJoinedSortableColumn(OuterLink outerLink, ORMColumn foreignKey, StormTable table, boolean isAscent) {
		ForeignKey fk = foreignKey.asForeignKey();
		Debug.Assert(fk != null);
		if (isAscent) {
			return new SortableColumn.Ascent(outerLink, fk, table, this);
		}
		else {
			return new SortableColumn.Descent(outerLink, fk, table, this);
		}
	}

	public String getColumnName() {
		return getKey();
	}

	@Override
	public ORMColumn getColumn() {
		return this;
	}
}
