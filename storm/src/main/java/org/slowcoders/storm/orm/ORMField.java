package org.slowcoders.storm.orm;

import org.slowcoders.io.serialize.IOEntity;
import org.slowcoders.io.serialize.IOField;
import org.slowcoders.storm.EntityReference;
import org.slowcoders.util.Debug;
import org.slowcoders.storm.ORMColumn;
import org.slowcoders.storm.ORMEntity;
import org.slowcoders.storm.StormRowSet;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;

import static org.slowcoders.storm.orm.ORMFlags.*;

/**
 * Created by zeedh on 02/02/2018.
 */

public abstract class ORMField extends IOField {//, IOConverter<Object> {

	public static final boolean NOTNULL_IN_DEFAULT = true;

	private static final int TYPE_UNKNOWN = 0;

	private static final int TYPE_NUMBER = 0x70000;

	private static final int TYPE_ENUM = 0x80000;

	private static final int TYPE_SET = 0x90000;

	private static final int TYPE_LIST = 0xA0000;

	private static final int TYPE_MAP = 0xB0000;

	private static final int TYPE_SingleEntity = 0xC0000;

	private static final int TYPE_Entities = 0xD0000;

	/**
	 * Update Bit for Immutable column like ForeignKey
	 */
	public static final long IMMUTABLE_COLUMN_UPDATE_BIT = 1 << 0;

	/**
	 * Update Bit for Joined OuterLink
	 */
	public static final long OUTER_LINK_UPDATE_BIT = 1 << 1;
	
	protected Class<?> columnType;

//	private StormTable<?, ?, ?> table;

	private static final int PartOfUnique = Reserved_1;

	protected ORMField(String key, int flags, Class<?> columnType) {
		super(key, makeAccessFlags(columnType, flags));
		this.columnType = columnType;

//		if (table != null) {
//			this.table = table;
//			StormTable.UnsafeTools.registerField(table, this);
//		}
	}

	protected ORMField(ORMField orgColumn, int flags) {
		super(orgColumn.getKey(), flags);
//		this.table = orgColumn.getDeclaredTable();
		this.columnType = orgColumn.getBaseType();
	}

	private static int makeAccessFlags(Class<?> columnType, int flags) {
		int flags0 = getValueType(columnType);
		return flags | flags0;
	}

	public static void markUniqueParts(Class<? extends ORMEntity> c, ORMField[] ormFields) {
		visitUniqueParts(c, new Consumer<String[]>() {
			@Override
			public void accept(String[] uniques) {
				for (String unique : uniques) {
					String[] columns = unique.split(",");
					for (int i = 0; i < columns.length; i ++) {
						String column = columns[i].trim();
						ORMField f = ORMField.getFieldByKey(column, ormFields);
						if (f == null) {
							throw new RuntimeException(c + " does not have column - " + column);
						}
						int bit = (columns.length == 1) ? Unique : PartOfUnique;
						f.setAccessFlags(f.getAccessFlags() | bit);
					}
				}

			}
		});
	}

	public static void visitUniqueParts(Class<?> c, Consumer<String[]> handler) {
		Class<?>[] interfaces = c.getInterfaces();
		for (Class inf : interfaces) {
			if (inf != ORMEntity.class && ORMEntity.class.isAssignableFrom(inf)) {
				visitUniqueParts(inf, handler);
			}
		}
		ModelDefinition modelDef = c.getAnnotation(ModelDefinition.class);
		if (modelDef != null && modelDef.uniques().length > 0) {
			handler.accept(modelDef.uniques());
		}
	}

    public static void visitIndexParts(Class<?> c, Consumer<String[]> handler) {
		Class<?>[] interfaces = c.getInterfaces();
		for (Class inf : interfaces) {
			if (inf != ORMEntity.class && ORMEntity.class.isAssignableFrom(inf)) {
				visitIndexParts(inf, handler);
			}
		}
        ModelDefinition modelDef = c.getAnnotation(ModelDefinition.class);
        if (modelDef != null && modelDef.indexes().length > 0) {
            handler.accept(modelDef.indexes());
        }
    }

	public ORMField forNine(String columnName) {
		return this;
	}
	
	final String getFlags$() {
		int flags = this.getAccessFlags();
		StringBuilder sb = new StringBuilder();
		
		if ((flags & Unique) != 0) {
			sb.append("Unique | ");
		}

		if ((flags & Nullable) != 0) {
			sb.append("Nullable | ");
		}
		
		if ((flags & (ReadOnly)) != 0) {
			// ews 검색 가능 프로퍼티. (일부 지원하지 않는 Column이 있음)
			sb.append("CanFind | ");
		}

//		if ((flags & CanFind) != 0) {
//			sb.append("CanFind | ");
//		}
//
/*		
		if ((flags & MustBeExplicitlyLoaded) != 0) {
			/**
			 * firstClass property가 아니다. 명시적으로 로딩해야만 한다.
			 * sb.append("MustBeExplicitlyLoaded | ");
		}

		if ((flags & UpdateCollectionItems) != 0) {
			/**
			 * Collection을 다시 생성하지 않고, 기존 아이템을 Update 한다.
			 * sb.append("UpdateCollectionItems | ");
		}

		if ((flags & AutoInstantiateOnRead) != 0) {
			/**
			 * Complex Property 를 자동 생성.
			 * sb.append("AutoInstantiateOnRead | ");
		}

		if ((flags & ReuseInstance) != 0) {
			/**
			 * 기존 Instance를 재사용.
			 * sb.append("ReuseInstance | ");
		}
			 */

		
		if (sb.length() > 0) {
			sb.setLength(sb.length() - 3);
		}
		else {
			return "None";
		}
		return sb.toString();
	}


	public Class<?> getBaseType() {
		return this.columnType;
	}

	public final boolean isAlias() {
		return this.getOriginColumn() != this;
	}

	public ORMField getOriginColumn() {
		return this;
	}


//	public final StormTable<?,?,?> getDeclaredTable() {
//		return table;
//	}
	
	/*final*/ public final String getDBColumnType() {
		switch (this.getAdapter().getPreferredTransferType()) {
			case Integer:
			case Enum:
			case EnumSet:
				return "INTEGER";
			case Numbers:
			case BitStream:
				return "BLOB";
			case PrintableText:
				return "TEXT";
			default:
				throw Debug.shouldNotBeHere();
		}
	}

	protected static int getValueType(Class<?> field_t) {
		if (field_t.isEnum()) {
			return TYPE_ENUM;
		}
		if (ORMEntity.class.isAssignableFrom(field_t)) {
			return TYPE_SingleEntity;
		}
		if (StormRowSet.class.isAssignableFrom(field_t)) {
			return TYPE_Entities;
		}
		if (Map.class.isAssignableFrom(field_t)) {
			return TYPE_MAP;
		}
		if (Set.class.isAssignableFrom(field_t)) {
			return TYPE_SET;
		}
		if (Iterable.class.isAssignableFrom(field_t)) {
			return TYPE_LIST;
		}
		return TYPE_UNKNOWN;
	}
	
	final boolean isEntity() {
		long t = (this.getAccessFlags() & 0xFF0000);
		return t == TYPE_SingleEntity || t == TYPE_Entities;
	}

	public Class<?> getFirstGenericParameterType() {
		return null;
	}

	public Class<?>[] getGenericParameters() {
		return null;
	}

	public static void initAliases(ArrayList<ORMAlias> aliases) {
		for (ORMAlias slot : aliases) {
			slot.init_internal();
			ORMColumn orgColumn = slot.getOriginColumn();
			Debug.Assert(orgColumn.getReflectionField() != null);
			slot.setReflectionField(orgColumn.getReflectionField());
		}
	}


	public static synchronized void registerORMProperties(Class<?> dmClass, ORMField[] slots) throws InstantiationException, IllegalAccessException, NoSuchFieldException, SecurityException {
		for (ORMField slot : slots) {
			if (slot.isOuterLink() && slot.isVolatileData()) continue;

			Field f = getReflectionField(slot, dmClass);
			slot.setReflectionField(f);
		}
		IOEntity.registerSerializableFields_unsafe(dmClass, slots);
	}
	
	public static Field getReflectionField(ORMField slot, Class<?> c) {
		NoSuchFieldException err = null;
		while (true) {
			Debug.Assert(c != null && slot != null);
			try {
				Field f = c.getDeclaredField(slot.getKey());
				return f;
			} catch (NoSuchFieldException e) {
				if (err == null) {
					err = e;
				}
				c = c.getSuperclass();
			}
		}
	}


	public static synchronized ORMField[] getORMProperties(Class<?> dmClass, boolean initColumnInfo) throws InstantiationException, IllegalAccessException, NoSuchFieldException, SecurityException {
		ORMField[] fields;
		ArrayList<ORMField> dbfList = new ArrayList<>();
		HashMap<Class<?>, Class<?>> processed = new HashMap<>();
		getDBFields(dmClass, dbfList, initColumnInfo, processed);
		fields = dbfList.toArray(new ORMField[dbfList.size()]);
		return fields;
	}

	public Class<?> getComponentType() {
		if (this.columnType.isArray()) {
			return this.columnType.getComponentType();
		}
		else {
			return this.getFirstGenericParameterType();
		}
	}

	private static void getDBFields(Class<?> dmClass, ArrayList<ORMField> dbfList, boolean initColumnInfo, HashMap<Class<?>, Class<?>> processed) throws InstantiationException, IllegalAccessException, NoSuchFieldException, SecurityException {
		if (!dmClass.isInterface()) {
			Class<?> super_ = dmClass.getSuperclass();
			if (ORMEntity.class.isAssignableFrom(super_)) {
				getDBFields(super_, dbfList, initColumnInfo, processed);
			}
		}
		
		Class<?>[] ifcs = dmClass.getInterfaces();
		for (Class<?> ifc : ifcs) {
			if (ORMEntity.class.isAssignableFrom(ifc)) {
				if (processed.get(ifc) == null) {
					processed.put(ifc, ifc);
					getDBFields(ifc, dbfList, initColumnInfo, processed);
				}
			}
		}
		
		getDeclaredDBFields(dmClass, dbfList, initColumnInfo);
	}
	
	static ORMField[] getDeclaredProperties(Class<?> c, boolean initColumnInfo, Object entityDifinition) throws ClassNotFoundException  {
		ArrayList<ORMField> fs = new ArrayList<ORMField>();
		Class<?> orm = Class.forName(c.getName() + "$Table");
		getDeclaredDBFields(orm, fs, true);
		return fs.toArray(new ORMField[fs.size()]);
	}

	private static void getDeclaredDBFields(Class<?> c, ArrayList<ORMField> fs, boolean initColumnInfo)  {
		//NPDebug.Assert(ORMEntity.class.isAssignableFrom(c));
		for (Field f : c.getDeclaredFields()) {
			int modifier = f.getModifiers();
		    if (!Modifier.isPublic(modifier)) {// || !Modifier.isStatic(modifier)) {
		    	continue;
		    }
			try {
				Object v = f.get(null);
				if (!(v instanceof ORMField)) {
					continue;
				}
				
				ORMField slot = (ORMField)v;
				if (initColumnInfo) {
					if (slot instanceof ORMAlias) {
						((ORMAlias)slot).init_internal();
					}
					slot.setReflectionField(f);
				}
				fs.add(slot);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				Debug.wtf(e);
			}
		}
	}

	public final boolean isChanged(long modifyFlags) {
		return (this.getUpdateBit() & modifyFlags) != 0;
	}

	public static ORMField getFieldByKey(String key, ORMField[] fields) {
		key = key.intern();
		for (ORMField f : fields) {
			if (f.getKey() == key) {
				return f;
			}
		}
		return null;
	}

	public static ORMField getFieldByName(String key, ORMField[] fields) {
		key = key.intern();
		for (ORMField f : fields) {
			if (key.equals(f.getFieldName())) {
				return f;
			}
		}
		return null;
	}

	
	public boolean isDBColumn() {
		return true;
	}

	protected final boolean isSingleEntity() {
		long t = (this.getAccessFlags() & 0xFF0000);
		return t == TYPE_SingleEntity;
	}

	final boolean isOuterRowSet() {
		long t = (this.getAccessFlags() & 0xFF0000);
		if (t == TYPE_Entities) {
			Debug.Assert(this.isOuterLink());
			return true;
		}
		return false;
	}
	
	public boolean isForeignKey() {
		return false;
	}

	public boolean isMasterForeignKey() {
		return false;
	}

	public ForeignKey asForeignKey() {
		return null;
	}
	
	public final boolean isUnique() {
		return (this.getAccessFlags() & Unique) != 0;
	}

	public final boolean isImmutable() {
		return (this.getAccessFlags() & Immutable) != 0;
	}

	public final boolean isVolatileData() {
		if (this.asOuterLink() == null && !this.isForeignKey()) {
			Debug.trap();
		}
		return (this.getAccessFlags() & Volatile) != 0;
	}
	
	public final boolean isGhostColumn() {
		return (this.isDBColumn() && (getAccessFlags() & (Unique | PartOfUnique)) != 0);
	}

    public abstract long getUpdateBit();
    
	
    final boolean isCached() {
		return (this.getAccessFlags() & Unique_Cache) == Unique_Cache;
	}

	final String getSlotName() {
		return this.getFieldName();
	}

	final String getGetMethodPrefix() {
//		boolean mayLoad = this.isOuterRowSet();// : this.isOuterLink();
//		String prefix = (mayLoad && this.isVolatileData()) ? "find" : "get";

		String prefix = "get";
//		if (!isForeignKey() && !isOuterLink() && isVolatileData()) {
//			prefix = "peek";
//		} else {
//			prefix = "get";
//		}
//		String prefix;
//		if (isOuterRowSet() && isVolatileData()) {
//			prefix = "get";
//		} else if (!isForeignKey() && isVolatileData()) {
//			prefix = "peek";
//		} else {
//			prefix = "get";
//		}
//		String prefix;
//		if ("ParentFolder".equals(this.getSlotName())) {
//			int a = 3;
//			a ++;
//		}
//		if (!this.isOuterLink() || !this.isVolatileData() || this.isForeignKey()) {
//			prefix = "get";
//		}
//		else if (this.isUnique()) {
//			prefix = "find";
//		}
//		else {
//			prefix = "find";
//		}
		return prefix;
	}

	final String getGetMethodName() {
		String prefix = this.getGetMethodPrefix();
		return prefix + this.getFieldName();
	}

	final String getFindMethodName(ORMField column) {
		String prefix;
		if (this.isUnique()) {
			prefix = "findBy";
		}
		else {
			prefix = "findBy";
		}
//		if (column.isOuterRowSet()) {
//			prefix = "filterBy";
//		}
//		else if (column.isVolatileData()) {
//			prefix = "findBy";
//		}
//		else {
//			prefix = "loadBy";
//		}
		return prefix + this.getFieldName();
	}

	final void setJoinType(boolean unique) {
		int t = this.getAccessFlags() & ~(TYPE_SingleEntity | TYPE_Entities);
		if (unique) {
			t |= TYPE_SingleEntity | Unique;
		}
		else {
			t |= TYPE_Entities;
		}
		this.setAccessFlags(t);
		// TODO Auto-generated method stub
	}


	final void getConfig(StringBuilder sb) {
		sb.append(this.getClass().getSimpleName()).append("(\"").append(this.getKey()).append("\", ");
		sb.append(getFlags$()).append(",");
		sb.append("\n\t");
		sb.append(this.getBaseType().getSimpleName()).append(".class");
		Class<?> param = this.getFirstGenericParameterType();
		if (param != null) {
			sb.append(", ").append(param.getSimpleName()).append(".class");
		}
		sb.append(")");
	}

	public final boolean isCollection() {
		long t = (this.getAccessFlags() & 0xFF0000);
		return t == TYPE_LIST || t == TYPE_SET;
	}

	public boolean isList() {
		long t = (this.getAccessFlags() & 0xFF0000);
		return t == TYPE_LIST;
	}

	public boolean isSet() {
		long t = (this.getAccessFlags() & 0xFF0000);
		return t == TYPE_SET;
	}

	public boolean isMap() {
		long t = (this.getAccessFlags() & 0xFF0000);
		return t == TYPE_MAP;
	}

	public boolean isUnknownType() {
		long t = (this.getAccessFlags() & 0xFF0000);
		return t == TYPE_UNKNOWN;
	}


	public static ForeignKey attachForeignKey(OuterLink subSlot, ORMColumn[] dbFields, Class<?> columnType) {
		for (ORMColumn fkCol : dbFields) {
			ForeignKey fk = fkCol.asForeignKey();
			if (fk.getBaseType().isAssignableFrom(columnType)) {
				fk.bind(subSlot);
				return fk;
			}
		}
		throw Debug.shouldNotBeHere();
	}

	public final boolean isOuterLink() {
		return this.asOuterLink() != null;
	}
	
	final boolean isUniqueVolatileLink() {
		return this.isOuterLink() && this.isVolatileData() && this.isUnique();	
	}
	
	protected OuterLink asOuterLink() {
		return null;
	}

	public final Object getImmutableProperty(EntityReference ref) {
		Debug.Assert(this.isImmutable());
		try {
			Field f = this.getRefField();
			Debug.Assert(f.getDeclaringClass().isAssignableFrom(ref.getClass()));
			Object value = f.get(ref);
			return value;
		} catch (Exception e) {
			throw Debug.wtf(e);
		}
	}
	
	protected Field getRefField() {
		return null;
	}

	protected final ModelGen getModelOfColumnType() {
		 return ModelGen._ormGen.getModel(this.getBaseType());
	}

	protected final ModelGen getDeclaringModel() {
		return ModelGen._ormGen.getModel(this.getDeclaringClass());
	}

	final String makeMethodName(String prefix) {
		return prefix + this.getSlotName();
	}

	public final boolean isNullable() {
		return (this.getAccessFlags() & Nullable) != 0;
	}


	final String getVariantName() {
		String s = this.getSlotName();
		return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

	final boolean isInternal() {
		// TODO Auto-generated method stub
		return (super.getAccessFlags() & ORMFlags.Hidden) != 0;
	}

	final boolean isReadOnly() {
		return (getAccessFlags() & ORMFlags.ReadOnly) != 0;
	}

	final boolean isEmbedded() {
		return (getAccessFlags() & ORMFlags.Embedded) != 0;
	}

	final boolean canOverride() {
		return (getAccessFlags() & ORMFlags.CanOverride) != 0;
	}

}
