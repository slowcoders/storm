package org.slowcoders.storm;

import org.slowcoders.storm.orm.ForeignKey;
import org.slowcoders.storm.orm.OuterLink;
import org.slowcoders.util.Debug;

public interface SortableColumn extends AbstractColumn {
	
	boolean isDescentOrder();

	StormTable<?, ?, ?> getDeclaredTable();

	default boolean isJoined() {
		return getJoinedForeignKey() != null;
	}

	default OuterLink getJoinedOuterLink() {
		return null;
	}

	default ForeignKey getJoinedForeignKey() {
		return null;
	}

	default SortableColumn evaluate(String function) {
		return new ColumnFunc(function, this);
	}

	class ColumnFunc implements SortableColumn {
		
		protected final String function;
		private final SortableColumn column;
		
		ColumnFunc(String function, SortableColumn column) {
			Debug.Assert(column != null);
			Debug.Assert(function.indexOf('?') > 0);
			
			this.function = function;
			this.column = column;
		}

		@Override
		public StormTable<?,?,?> getDeclaredTable() {
			return column.getDeclaredTable();
		}

		public ORMColumn getColumn() {
			return column.getColumn();
		}
		
		public String toString() {
			return this.getColumnName();
		}

		@Override
		public String getColumnName() {
			return function.replace("?", column.getColumnName());
		}

		@Override
		public boolean isDescentOrder() {
			return column.isDescentOrder();
		}
	}

	class Descent implements SortableColumn {
		private final ORMColumn column;
		private final StormTable table;
		private final ForeignKey foreignKey;
		private final OuterLink outerLink;

		Descent(OuterLink outerLink, ForeignKey foreignKey, StormTable table, ORMColumn tableColumn) {
			this.table = table;
			this.column = tableColumn;
			this.foreignKey = foreignKey;
			this.outerLink = outerLink;
		}

		Descent(StormTable table, ORMColumn tableColumn) {
			this(null, null, table, tableColumn);
		}

		public SortableColumn descentOrder() {
			return this;
		}

		@Override
		public boolean isDescentOrder() {
			return true;
		}

		@Override
		public StormTable<?, ?, ?> getDeclaredTable() {
			return table;
		}

		@Override
		public OuterLink getJoinedOuterLink() {
			return this.outerLink;
		}

		@Override
		public ForeignKey getJoinedForeignKey() {
			return this.foreignKey;
		}

		@Override
		public String getColumnName() {
			String key = column.getKey();
			if (table != null) {
				key = table.getTableName() + "." + key;
			}
			return key;
		}

		@Override
		public ORMColumn getColumn() {
			return column;
		}
	}

	class Ascent implements SortableColumn {
		private final ORMColumn column;
		private final StormTable table;
		private final ForeignKey foreignKey;
		private final OuterLink outerLink;

		Ascent(StormTable table, ORMColumn tableColumn) {
			this(null, null, table, tableColumn);
		}

		Ascent(OuterLink outerLink, ForeignKey foreignKey, StormTable table, ORMColumn tableColumn) {
			this.column = tableColumn;
			this.table = table;
			this.foreignKey = foreignKey;
			this.outerLink = outerLink;
		}

		public SortableColumn descentOrder() {
			return this;
		}

		@Override
		public boolean isDescentOrder() {
			return false;
		}

		@Override
		public ForeignKey getJoinedForeignKey() {
			return this.foreignKey;
		}

		@Override
		public StormTable<?, ?, ?> getDeclaredTable() {
			return table;
		}

		@Override
		public OuterLink getJoinedOuterLink() {
			return this.outerLink;
		}

		@Override
		public String getColumnName() {
			String key = column.getKey();
			if (table != null) {
				key = table.getTableName() + "." + key;
			}
			return key;
		}

		@Override
		public ORMColumn getColumn() {
			return column;
		}
	}
}