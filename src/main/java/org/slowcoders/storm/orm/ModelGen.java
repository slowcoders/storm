package org.slowcoders.storm.orm;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

import org.slowcoders.util.Debug;
import org.slowcoders.storm.*;
import org.slowcoders.util.SourceWriter;

final class ModelGen {

	private static final String REFERENCE_SUFFIX = "_Reference";
	private static final String SNAPSHOT_SUFFIX = "_Snapshot";
	private static final String EDITOR_SUFFIX = "_Editor";
	private static final String TABLE_SUFFIX = "_Table";

	static final String MODEL_REF_SUFFIX = "";
	static final String MODEL_DATA_SUFFIX = ".Snapshot";
	static final String MODEL_FORM_SUFFIX = ".UpdateForm";
	static final String MODEL_EDITOR_SUFFIX = ".Editor";
	static final String MODEL_MOCK_SUFFIX = ".Mock";

	private final String exRef;
	String modelName;
	private static ArrayList<ORMField> emptyConstratins = new ArrayList<>();

	static class ApiList extends ArrayList<ORMField> {
		ModelGen apiSuper;
	}

	static class Instance {
		final String name;
		final boolean isEditable;

		Instance(String name, boolean isEditable) {
			this.name = name;
			this.isEditable = isEditable;
		}

		public String toString() {
			return name;
		}
	}

	static boolean ALWAYS_LOAD_FOREIGN_KEY_REFERENCE = true;
	static boolean EDITOR_EXTENDS_FORM = true;

	static Instance SnapshotSelf = new Instance("this", false);
	static Instance ReferenceSelf = new Instance("d", false);
	static Instance EditorSelf = new Instance("d", true);

	static ORMGenerator _ormGen;
	

	private static final int COLUMN_TYPE = 2;
	private static final int OBJ_COLUMN_TYPE = 4;
	private static final int COLUMN_KEY = 8;
	private static final boolean EARLY_CHECK_NOT_NULL = false;
	private static final boolean ENABLE_EDIT_FOREIGN_KEY = false;
	private static final boolean HIDE_VOLTILE = false;

	final Class<?> klass;
	private final ORMField[] declaredColumns;
	private final ORMField[] allProperties;
	
	private ModelGen superORM;
	final TableDefinition tableDefinition;
	final String _ref;
	final String _data;
	final String _editor;
	//final String _snapshot;
	final String dbTableName;
//	final ORMFieldFactory table;
	boolean apiExported;

	private final String baseName;
	private SourceWriter out;
	
	final HashMap<ModelGen, ORMField> slaveKeys = new HashMap<>();
	private final ArrayList<ORMField> uniques = new ArrayList<>();
	private final ArrayList<ORMField[]> uniqueParts = new ArrayList<>();
	private final ArrayList<QueryMethod> sqldata = new ArrayList<>();
	private final ArrayList<QueryMethod> outerQueries = new ArrayList<>();
	private ApiList apiColumns;

	private boolean hideDeleteMethod;
	private boolean hideSaveMethod;

	ModelGen(ORMGenerator _ormGen, Class<?> c, boolean genModel) throws Exception {
		this.klass = c;

//		Class<?> columnDef = Class.forName(c.getName() + "$ColumnDefinition");
//		Constructor<?> constructor = columnDef.getDeclaredConstructor(StormTable.class);
//		constructor.setAccessible(true);
//		this.table = (ORMFieldFactory)constructor.newInstance((String)null);
		
		this.baseName =  _ormGen.getEntityBaseName(c);
		this.modelName = _ormGen.getEntityModelName(c);
		//this.apiExported = true;

		this.allProperties = ORMField.getORMProperties(c, true);
		ArrayList<ORMField> ownSlots = new ArrayList<>();

		boolean hasMasterForeignKey = false;
		for (ORMField slot : this.allProperties) {
			if (slot.getDeclaringClass() == c) {
				ownSlots.add(slot);
			}
			if (slot.isMasterForeignKey()) {
				if (hasMasterForeignKey) {
					throw new RuntimeException("Each table can only have one masterForeignKey - " + c);
				}
				hasMasterForeignKey = true;
			}
		}

		this.declaredColumns = ownSlots.toArray(new ORMField[ownSlots.size()]);// ORMSlot.getDeclaredProperties(c, true);


		this.apiExported = true;//tableDefinition != null;
		this._ref = modelName + MODEL_REF_SUFFIX;
		this._data = modelName + MODEL_DATA_SUFFIX;
		this._editor = modelName + MODEL_EDITOR_SUFFIX;

		this.tableDefinition = c.getAnnotation(TableDefinition.class);
		if (tableDefinition == null) {
			this.dbTableName = null;
		}
		else {
			this.dbTableName = tableDefinition.tableName();
		}

		ModelDefinition modelDef = c.getAnnotation(ModelDefinition.class);
		if (modelDef == null) {
			this.exRef = this._ref;
		}
		else {
			String ex = modelDef.extendedModel();
			this.exRef = ex.length() == 0 ? this._ref : ex;
		}
	}
	
	final Class<?> getEntityType() {
		return this.klass;
	}
	
	void init(ModelGen superM, SourceWriter out) throws Exception {
		this.superORM = superM;
		this.out = out;
	}

	private void getUniqueConstraints(ArrayList<ORMField[]> multiUniques) throws Exception {
//		Class<?> columnDef = Class.forName(klass.getName() + "$ColumnDefinition");
		ORMField.visitUniqueParts(klass, new Consumer<String[]>() {
			@Override
			public void accept(String[] uniques) {
				for (String unique : uniques) {
					String[] columns = unique.split(",");
					if (columns.length < 2) {
						throw new RuntimeException("Column count of UniqueConstraint must be greater than 1");
					}
					ORMField[] fields = new ORMField[columns.length];
					for (int i = 0; i < columns.length; i ++) {
						String s = columns[i].trim();
						ORMField f = ORMField.getFieldByKey(s, allProperties);
						if (f == null) {
							throw new RuntimeException(new NoSuchFieldException(s));
						}
						fields[i] = f;
					}
					multiUniques.add(fields);
				}
			}
		});
	}


	final void initQueries() throws Exception {
		//if (this.superORM != null) {
		this.getUniqueConstraints(this.uniqueParts);



		for (ModelGen td = this; td != null; td = td.getSuperORM()) {
			try {
				Class<?> tableClass = Class.forName(td.getClassName() + "$Queries");
				Method methods[] = tableClass.getDeclaredMethods();
				for (Method m : methods) {
					QueryMethod d = QueryMethod.makeQuery(this, m);
					if (d != null) {
						sqldata.add(d);
					}
				} 
			} catch (ClassNotFoundException e) {
				//e.printStackTrace();
			}
			if (this.isAbstract()) {
				break;
			}
		}
		sqldata.sort(new Comparator<QueryMethod>() {
			@Override
			public int compare(QueryMethod o1, QueryMethod o2) {
				int diff = o1.methodName.compareTo(o2.methodName);
				if (diff == 0) {
					int cntCommonParam = Math.min(o1.paramTN.size(), o2.paramTN.size());
					for (int i = 0; i < cntCommonParam; i += 2) {
						String n1 = o1.paramTN.get(i);
						String n2 = o2.paramTN.get(i);
						diff = n1.compareTo(n2);
						if (diff != 0) {
							break;
						}
					}
				}
				return diff;
			}
		});
	}

	static String makeKey(String n) {
		return "_" + Character.toLowerCase(n.charAt(0)) + n.substring(1);
	}
	
	final void initRelativeProperties() throws Exception {
		for (ORMField slot : this.getAllDBFields()) {
			if (slot.getKey().equals("_remoteParentFolder")) {
				Debug.trap();
			}
			OuterLink se = slot.asOuterLink();
			if (se == null) {
				continue;
			}

			if (se.getKey().equals("_attachments")) {
				int a = 3;
				a ++;
			}

			String subQuery = se.getFunction();
			ModelGen subCtx = getModelOfColumnType(se);
			ForeignKey fk = subCtx == null ? null : subCtx.findForeignKeyByColumnType(this.klass);
			if (fk == null) {
				if (subCtx == null) {
					throw new RuntimeException("ORM defintion of " + this.baseName + "." + se.getSlotName() + " not found");
				}
				throw new RuntimeException(subCtx.baseName + " has not foreign key for " + this.baseName);
			}
			
			fk.bind(se);
			boolean isUnique = fk.isUnique();
		    subCtx.hideSaveMethod |= !slot.isVolatileData() && !slot.isNullable();
			if (subCtx.hideSaveMethod || (isUnique && !slot.isNullable())) {
				subCtx.hideDeleteMethod = true;
			}
			if (subCtx.hideSaveMethod) {
				for (ModelGen m = subCtx.superORM; m != null; m = m.superORM) {
					m.hideSaveMethod = true;
				}
			}
			if (subCtx.hideDeleteMethod) {
				for (ModelGen m = subCtx.superORM; m != null; m = m.superORM) {
					m.hideDeleteMethod = true;
				}
			}
			if (subQuery != null) {
				boolean found = false;
				for (QueryMethod sql : subCtx.sqldata) {
					if ((found = sql.methodName.equals(subQuery))) {
						isUnique |= !sql.isRowSet;
						break;
					}
				}
				if (!found) {
					throw new RuntimeException(subQuery + "() not found " + subCtx.baseName + ".Queries");
				}
			}

			slot.setJoinType(isUnique);
		}

		/**
		 * after doing slot.setJoinType(),
		 * do work below
		 */
		for (ORMField f : this.getAllDBFields()) {
			if (!f.isDBColumn()) {
				continue;
			}
			
			if (f.isUnique()) {
				this.uniques.add(f);
			}
		}

	}




	String getClassName() {
		return klass.getName();
	}

	boolean isAbstract() {
		return tableDefinition == null;
	}

	ModelGen getSuperORM() {
		return this.superORM;  
	}

	String getSimpleName() {
		return klass.getSimpleName();
	}

	String getBaseName() {
		return this.baseName;
	}

	String getBaseReferenceName() {
		return this.baseName + REFERENCE_SUFFIX;
	}

	String getBaseEditorName() {
		return this.baseName + EDITOR_SUFFIX;
	}

	String getBaseSnapshotName() {
		return this.baseName + SNAPSHOT_SUFFIX;
	}

	String getBaseTableName() {
		return this.baseName + TABLE_SUFFIX;
	}

	String getModelName() {
		return this.modelName;
	}

	String getModelFormName() {
		return this.modelName + MODEL_FORM_SUFFIX;
	}

	String getModelEditorName() {
		return this.modelName + MODEL_EDITOR_SUFFIX;
	}

	ORMField[] getDeclaredDBFields() {
		return declaredColumns;
	}

	ORMField[] getAllDBFields() {
		return allProperties;
	}

	private String getBaseNameOfColumnType(ORMField p) {
		ModelGen ctx = getModelOfColumnType(p);
		Debug.Assert(ctx != null);
		return ctx.baseName;
	}
	
	void genRefClass() throws Exception {

		boolean isAbstract = this.isAbstract();
		String superName = this.superORM == null ? "EntityReference" : this.superORM.exRef;

		out.println("public abstract class #BASE_REF# extends ", superName, " implements #ENTITY_ORM# {\n");

		if (!isAbstract) {				
			out.println("private static final _TableBase.Initializer unsafeTools = _TableBase.initializer;\n");
		}

		boolean hasOuterLink = false;
		for (ORMField p : getDeclaredDBFields()) {
			/*
			 * ForeignKey, 모든 OuterLink, ImmutableColumn 에 대해서 정의.
			 */
			if (p.isOuterLink()) {
				hasOuterLink = true;
				out.print("/*internal*/ "); 
				if (p.isUnique()) {
					out.println(getModelOfColumnType(p)._ref, " ", p.getKey(), ";\n");
				}
				else {
					if (!getModelOfColumnType(p).isAbstract()) {
						out.print("final ");
					}
					out.println(p.getModelOfColumnType().getBaseTableName(), ".RowSet ", p.getKey(), ";\n");
				}
			}
			else if (p.isForeignKey()) {
				out.println(getModelOfColumnType(p)._ref, " ", p.getKey(), ";\n");
			}

		}


		out.println("public static abstract class Editor extends #BASE_EDITOR# {");

		out.println("protected Editor(#BASE_TABLE# table, #BASE_SNAPSHOT# origin) {");
		out.println("super(table, origin);");
		out.println("}\n");
		out.println("}");

		out.println("protected #BASE_REF#(long id) {");
		out.println("super(id);");
		for (ORMField p : getDeclaredDBFields()) {
			if (p.isOuterRowSet() && !getModelOfColumnType(p).isAbstract()) {
				String subTable$ = "_TableBase." + getModelOfColumnType(p).dbTableName;
				ORMField fk = getForeignKeyOfOuterEntity(p);
				out.println("this.", p.getKey(), " = ",
						subTable$, ".", fk.getFindMethodName(p), "(this);");
			}
		}
		out.println("}\n");

		if (isAbstract) {
			out.println("public abstract #SNAPSHOT# loadSnapshot();\n");// throws SQLException, InvalidEntityReferenceException;\n");
			out.println("public abstract #SNAPSHOT# tryLoadSnapshot();\n");
		}
		else {

			out.println("public final #BASE_TABLE# getTable() {");
			out.println("return #TABLE_INSTANCE#;");
			out.println("}\n");

			out.println("public final #SNAPSHOT# tryLoadSnapshot() {");
			out.println("return (#SNAPSHOT#)super.doTryLoadSnapshot();");
			out.println("}\n");
			
			out.println("public final #SNAPSHOT# loadSnapshot() {");// throws SQLException, InvalidEntityReferenceException {");
			out.println("return (#SNAPSHOT#)super.doLoadSnapshot();");
			out.println("}\n");
		}
		

		for (ORMField p : getDeclaredDBFields()) {
			if (p.isOuterLink()) {
				ModelGen colModel = getModelOfColumnType(p);
				String type = p.isUnique() ? colModel._ref : colModel.getBaseTableName();
				boolean isAbstractColumn = colModel.isAbstract();
				String genType= "";
				if (isAbstractColumn) {
					genType = "<" + colModel._data + ", " + colModel._ref + ", " + colModel._editor + ">";
					out.println("public abstract ", type, genType, " get", colModel.baseName, "Table();\n");
				}
				if (!p.isUnique()) {
					type += ".RowSet";
					if (isAbstractColumn) {
						type += genType;
					}
				}

				out.println("public final ", type, " ", p.getGetMethodName(), "() {");
				out.println(type, " res = ", p.getKey(), ";");
				if (p.isUnique() || isAbstractColumn) {
					out.println("if (res == null) {");
					ORMField fk = getForeignKeyOfOuterEntity(p);//.getSlotName();
					String subTable$;
					if (isAbstractColumn) {
						subTable$ = "get" + colModel.baseName + "Table()";
					}
					else {
						subTable$ = "_TableBase." + colModel.dbTableName;
					}

					if (fk.isMasterForeignKey()) {
						out.println("res = ", subTable$, ".findEntityReference(this.getEntityId()", !p.isNullable() ? "" : ", false", ");");
					} else {
						out.println("res = ", subTable$, ".", fk.getFindMethodName(p), "(this);");
					}
					if (p.isUnique() && p.isNullable()) {
						out.println("if (res == null) res = ", p.getModelOfColumnType().getBaseEditorName(), ".ghost;");
					}
					else if (!p.isOuterRowSet()) {
						out.println("if (DebugUtil.DEBUG) Debug.Assert(res != null);");
					}
					out.println(p.getKey(), " = res;");
					out.println("}");
				}
				
				if (p.isUnique() && p.isNullable()) {
					/**
					 * unique volatile sub-reference 삭제시 자동으로 지워지지 않는다.
					 */
					out.println("return res == ", p.getModelOfColumnType().getBaseEditorName(), ".ghost ? null : res;");
				}
				else {
					out.println("return res;");
				}
				out.println("}\n");
			}
			else if (p.isForeignKey()) {
				String type = p.isForeignKey() ? getModelOfColumnType(p)._ref : getReturnType(p, SnapshotSelf);
				out.println("public final ", type, " ", p.getGetMethodName(), "() {");
				if (p.isMasterForeignKey()) {
					out.println("if (", p.getKey(), " == null) {");
					out.println(p.getKey(), " = (", type, ") loadMasterForeignKey();");
					out.println("}");
					out.println("return this.", p.getKey(), ";");
				} else {
					out.println("doLoadForeignKeys();");
					out.println("return ", "this.", p.getKey(), ";");
				}
				out.println("}\n");
			}
			else {
				// genGetRefField(p);
			}
		}

		genTableQuery(outerQueries, true);

		if (!hideDeleteMethod) {
			if (this.isAbstract()) {
				out.println("public abstract void deleteEntity() throws SQLException;\n");
			}
			else {
				out.println("public void deleteEntity() throws SQLException {");
				out.println("super.doDelete();");
				out.println("}\n");
			}
		}		
		out.println("/****************************************/");
		out.println("/*  Internal methods                    */");
		out.println("/*--------------------------------------*/\n");

		if (!isAbstract) {
			out.println("protected void onDelete_inTR() throws SQLException {");
			for (ORMField p : this.getAllDBFields()) {
				if (p.isOuterLink()) {
					out.print("onDelete_inTR(").printIf(getModelOfColumnType(p).isAbstract(), "(StormFilter)")
						.println("this.", p.getGetMethodName(), "());");
				}
				else if (p.isEmbedded()) {
					ModelGen colModel = getModelOfColumnType(p);
					out.print("onDelete_inTR(").printIf(colModel.isAbstract(), "(StormFilter)")
					.println("this.loadSnapshot().", p.getGetMethodName(), "_Ref());");
				}
			}
			out.println("super.onDelete_inTR();");

			out.println("}\n");
		}
		
		if (!this.isAbstract()) {

			out.println("protected final void validateForeignKey_RT(EntitySnapshot entity) {");
			out.println("#SNAPSHOT# d = (#SNAPSHOT#)entity;");
			for (ORMField p : this.getAllDBFields()) {
				if (p.isForeignKey()) {
					if (p.isForeignKey()) {
						out.println("if (DebugUtil.DEBUG) DebugUtil.assertNullOrMatch(", getCastedReferenceField$(p, "this"),
						", d.", p.getGetMethodName(), "());");
					}

					String refField = getCastedReferenceField$(p, "this");
					out.println(refField, " = d.", p.getGetMethodName(), "();");

					if (p.isForeignKey() && p.isUnique()) {
						OuterLink fse = this.getOuterLinkOfForeignKey(p);
						String field_ = getCastedReferenceField$(fse, p.getKey());
						out.print("if (DebugUtil.DEBUG").printIf(p.isNullable(), " && " + refField + " != null").println(") DebugUtil.assertDeletedOrMatch(",
								field_, ", (#REF#)this);");
						if (p.isNullable()) {
							out.print("if (", refField, " != null) ");
						}
						out.println(field_, " = (#REF#)this;");
					}
					out.println();
				}
			}

			out.println("}\n");			
		}


		boolean hasForeignKey = false;
		for (ORMField p : this.getDeclaredDBFields()) {
			if (p.isForeignKey()) {
				OuterLink outerLink = this.getOuterLinkOfForeignKey(p);
				String field_ = getCastedReferenceField$(outerLink, p.getKey());
				if (!p.isVolatileData()) {
					if (!hasForeignKey) {
						hasForeignKey = true;
						out.println("protected void invalidateForeignEntityCache_RT(ChangeType reason) {");
						out.println("super.invalidateForeignEntityCache_RT(reason);");
					}
					if (p.isNullable()) {
						out.print("if (", p.getKey(), " != null) ");
					}
					out.println("((", p.getModelOfColumnType().getBaseReferenceName(), ")", p.getGetMethodName(),
							"()).__invalidate", this.getOuterLinkOfForeignKey(p).getSlotName(), "();");
					if (outerLink.isNullable() && p.isUnique()) {
						out.print("if (", p.getKey(), " != null) ");
						out.println(field_, " = null;");
					}
				}
				else if (p.isUnique()) {
					if (outerLink.isNullable()) {
						if (!hasForeignKey) {
							hasForeignKey = true;
							out.println("protected void invalidateForeignEntityCache_RT(ChangeType reason) {");
							out.println("super.invalidateForeignEntityCache_RT(reason);");
						}
						out.println("this.", p.getKey(), " = ", p.getGetMethodName(), "();");
						out.print("if (", p.getKey(), " != null) ");
						out.println(field_, " = null;");
						//String key = p.isMasterForeignKey() ? p.getGetMethodName() + "()" : p.getKey();
					}
				}
			}
		}
		if (hasForeignKey) {
			out.println("}\n");
		}

		for (ORMField p : this.getDeclaredDBFields()) {
			if (p.isOuterLink() && !p.isVolatileData()) {
				out.println("final boolean __invalidate", p.getSlotName(), "() {");
				if (p.isNullable() && p.isUnique()) {
					out.println("if (", p.getKey(), " == ", p.getModelOfColumnType().getBaseEditorName(), ".ghost) ", p.getKey(), " = null;"); 
				}
				out.println("EntitySnapshot data = super.invalidateEntityCache_RT(ChangeType.OuterLinkChanged);");
				out.println("if (data != null) {");
				out.println("((", this._data, ")data).get", p.getSlotName(), "();");
				out.println("return true;");
				out.println("}");
				out.println("return false;");
				out.println("}\n");
			}
		}


		out.println("}");
	}


	String getDeclaredInternalSnapshot$(ORMField f) {
		ModelGen ed = getDeclaringModel(f.getOriginColumn());
		String fd = ed.getBaseSnapshotName();
		return fd;
	}


	String getCastedReferenceField$(ORMField f, String instance) {
		ModelGen ed = getDeclaringModel(f);
		if (ed == this && instance == "this") {
			return instance + "." + f.getKey();
		}
		String fd = ed.getBaseReferenceName();
		return "((" + fd + ")" + instance + ")." + f.getKey();
	}
	
	String getCastedSnapshotField$(ORMField f, String instance) {
		ModelGen ed = getDeclaringModel(f.getOriginColumn());
		if (ed == this) {
			return instance + "." + f.getKey();
		}
		String fd = getDeclaredInternalSnapshot$(f);
		return "((" + fd + ")" + instance + ")." + f.getKey();
	}

	String getCastedInternalSnapshotField$(ORMField f, String instance) {
		ModelGen ed = getDeclaringModel(f);
		if (ed == this) {
			return instance + "." + f.getKey();
		}
		String fd = getDeclaredInternalSnapshot$(f);
		return "((" + fd + ")" + instance + ")." + f.getKey();
	}
	
	String getCastedSnapshotInstance(ORMField f, String instance) {
		ModelGen ed = getDeclaringModel(f);
		if (ed == this) {
			return instance;
		}
		String fd = getDeclaredInternalSnapshot$(f);
		return "(" + fd + ")" + instance;
	}

	void genTableClass() throws IOException {

		ModelGen superC = this.superORM;
		String super_;
		if (superC == null) {
			super_ = hideDeleteMethod ? "StormRowSet" : "AbstractTable";
		} else {
			super_ = superC.getBaseTableName();
		}
		if (this.isAbstract()) {
			out.println("public interface #BASE_TABLE#<SNAPSHOT extends #SNAPSHOT#, REF extends #REF#, EDITOR extends #EDITOR#> extends ", super_, "<SNAPSHOT, REF, EDITOR> {\n");
		}
		else {
			out.println("public final class #BASE_TABLE# extends ", _ormGen.getStorageConfig(), ".Table<#SNAPSHOT#, #REF#, #EDITOR#> implements ",
					super_, "<#SNAPSHOT#, #REF#, #EDITOR#> {\n");
		}

		boolean hasQuery = false;
		if (!isAbstract()) {
            for (QueryMethod data : sqldata) {
                out.println("private StormQuery ", data.methodName, ";\n");
            }

            for (ORMField f : this.getAllDBFields()) {
                if (f.isForeignKey() || (f.isUnique() && f.isDBColumn())) {
                    hasQuery = true;
                    out.println("private StormQuery ", f.getFindMethodName(f), ";\n");
                    if (f.isCached()) {
                        out.println("private AbstractMap<", f.getBaseType().getSimpleName(), ", #REF#> ",
                                f.getFindMethodName(f), "_cache;\n");
                    }
                }
            }
			for (ORMField[] uc : this.uniqueParts) {
                out.println("private StormQuery ", getFindStatementName(uc), ";\n");
            }

			out.println("private static class _Ref extends ", this.exRef, " {");
			out.println("_Ref(long id) { super(id); }\n");
			out.println("protected static class Editor extends ", this.exRef, ".Editor {");
			out.println("protected Editor(#BASE_TABLE# table, #BASE_SNAPSHOT# origin) {");
			out.println("super(table, origin);");
			out.println("}");
//			if (!this.isAbstract()) {
//				out.println("protected Editor(#BASE_SNAPSHOT# origin, ORMColumn... uniques) {");
//				out.println("super(origin, uniques);");
//				out.println("}\n");
//			}
			out.println("}");
			out.println("}\n");

			out.println("private static class _Snapshot extends ", this._data, " {");
			out.println("_Snapshot(#REF# ref) { super(ref); }");
			out.println("}\n");

            out.println("protected #BASE_TABLE#(String tableName) {");
            out.println("super(tableName);");
            out.println("}\n");

			out.println("protected #REF# createEntityReference(long entityId) { return new _Ref(entityId); }\n");

			out.println("protected #SNAPSHOT# createEntitySnapshot(EntityReference ref) { return new _Snapshot((#REF#)ref); }\n");

			out.println("protected void init() throws Exception {");
            out.println("super.init();");
            if ((hasQuery || sqldata.size() > 0)) {
                out.println("try {");
                for (QueryMethod data : sqldata) {
                    String create$ = data.isJoinedStatement ? "createJoinedQuery" : "createQuery";
                    out.println(data.methodName, " = super.", create$, "(\"", data.sql, "\", null);");
                }
                for (ORMField f : this.getAllDBFields()) {
                    if (!f.isDBColumn()) continue;
                    if (!f.isForeignKey() && !f.isUnique()) continue;
                    String sql = f.getKey() + " = ?";
                    out.println(f.getFindMethodName(f), " = super.createQuery(\"WHERE ", sql, "\", null);");
                    if (f.isCached()) {
                        String type;
                        switch (tableDefinition.entityCacheType()) {
                            case Weak:
                                type = "WeakEntityMap";
                                break;
                            case Soft:
                                type = "SoftEntityMap";
                                break;
                            case Strong:
                                type = "HashMap";
                                break;
                            default:
                                throw Debug.shouldNotBeHere();
                        }
                        out.println(f.getFindMethodName(f), "_cache = new ", type, "<>();\n");
                    }
                }
				for (ORMField[] uc : this.uniqueParts) {
                    out.print(getFindStatementName(uc), " = super.createQuery(\"WHERE ");
                    for (ORMField f : uc) {
                        out.print(f.getKey(), " = ? AND ");
                    }
                    out.removeTail(5);
                    out.println("\", null);");
                }
                out.println("}\ncatch (Exception e) {\nthrow Debug.wtf(e);\n}");
            }
            out.println("}\n");

            genTableConfiguration();

            out.println("protected #REF# createGhostRef() {");
            out.println("return super.createGhostRef();");
            out.println("}\n");

        }

		if (!hideDeleteMethod) {
			if (!this.isAbstract()) {
				out.println("public void deleteEntities(Collection<#REF#> entities) throws SQLException {");
				out.println("super.doDeleteEntities(entities);");
				out.println("}\n");

				out.println("public final void updateEntities(ColumnAndValue[] updateValues, Collection<#REF#> entities) throws SQLException, RuntimeException {");
				out.println("super.doUpdateEntities(updateValues, entities);");
				out.println("}\n");
			}

		}

		if (this.isAbstract()) {
//			out.println("#ENTITY_ORM#.ColumnDefinition getColumnDefinition();\n");

			out.println("Class<? extends #ENTITY_ORM#> getORMDefinition();\n");
		}
		else {
//			out.println("public #ENTITY_ORM#.ColumnDefinition getColumnDefinition() {");
//			out.println("return this.columns;");
//			out.println("}\n");

			out.println("public final Class<#ENTITY_ORM#> getORMDefinition() {");
			out.println("return #ENTITY_ORM#.class;");
			out.println("}\n");
			
//			out.println("protected #REF# attachReferenceIntoMutableSnapshot_unsafe(long entityId, EntitySnapshot data) {");
//			out.println("#REF# ref = super.attachReferenceIntoMutableSnapshot_unsafe(entityId, data);");
//			out.println("#SNAPSHOT# entity = (#SNAPSHOT#)data;");
//			for (ORMField f : this.getAllDBFields()) {
//				if (f.isForeignKey()) {
//					String field = getCastedReferenceField$(f, "ref");
//					out.println(field, " = entity.", f.getGetMethodName(), "();");
//				}
//			}
//			out.println("return ref;");
//			out.println("}\n");
		}

		genTableQuery(sqldata, false);


		for (ORMField f : this.getAllDBFields()) {
			if (!f.isDBColumn()) {
				continue;
			}
			if (!f.isUnique() && !f.isForeignKey()) {
				continue;
			}

			String ret_t = "RowSet";
			out.printIf(!this.isAbstract(), "public ");
			out.print(f.isUnique() ? "#REF#" : ret_t, " ", f.getFindMethodName(f),
					" (", f.getBaseType().getSimpleName(), " ", f.getKey());
			if (this.isAbstract()) {
				out.println(");\n");
				continue;
			}
			out.println(") {");

			String stmt = f.getFindMethodName(f);
			if (f.isUnique()) {
				out.println("#REF# found;");
				if (!f.isCached()) {
					out.println("found = super.findUnique(", stmt, ", ", f.getKey(), ");");
				}
				else {
					out.println("found = ", stmt, "_cache.get(", f.getKey(), ");");
					out.println("if (found == null) {");
					out.println("found = super.findUnique(", stmt, ", ", f.getKey(), ");");
					out.println("if (found != null) {");
					out.println(stmt, "_cache.put(", f.getKey(), ", found);");
					out.println("}\n}");
				}
				out.println("return found;");
			}
			else {
				out.println("return new ", ret_t, "(", stmt, ", #ENTITY_ORM#.", f.getSlotName(), ", ", f.getKey(), ");");
			}
			out.println("}\n");
		}

		for (ORMField[] uc : this.uniqueParts) {
			String stmt = getFindStatementName(uc);
			out.print("public ").printIf(this.isAbstract(), "abstract ");
			out.print("#REF# ", stmt, "(");
			dumpParams(uc, COLUMN_TYPE | COLUMN_KEY, ", ");
			if (this.isAbstract()) {
				out.println(");\n");
			}
			else {
				out.println(") {");

				out.print("#REF# found = super.findUnique(", stmt, ", ");
				dumpParams(uc, COLUMN_KEY, ", ");
				out.println(");");
				out.println("return found;");
				out.println("}\n");
			}
		}

		genTableRowSet();

		if (!this.isAbstract()) {
			out.println("protected void clearAllCache_UNSAFE_DEBUG() {");
			out.println("super.clearAllCache_UNSAFE_DEBUG();");
			for (ORMField f : this.getAllDBFields()) {
				if (!f.isDBColumn()) continue;
				if (!f.isForeignKey() && !f.isUnique())  continue;
				if (f.isCached()) {
					out.println(f.getFindMethodName(f), "_cache.clear();");
				}
			}
			out.println("}");
		}

		out.println("}\n\n");
	}


	private void genTableRowSet() throws IOException {
		ModelGen superC = this.superORM;
		String super_ = superC == null ? "StormRowSet" : superC.getBaseTableName() + ".RowSet";
		String snapshot = "#SNAPSHOT#";
		if (this.isAbstract()) {
			out.println("interface RowSet<SNAPSHOT extends ", snapshot, ", REF extends #REF#, EDITOR extends #EDITOR#> extends ", super_, "<SNAPSHOT, REF, EDITOR> {\n");
		}
		else {
			out.println("public static final class RowSet extends StormFilter<", snapshot, ", #REF#, #EDITOR#> implements ", super_, "<", snapshot, ", #REF#, #EDITOR#> {");
			out.println("public RowSet(StormQuery query, ORMColumn foreignKey, Object... values) {");
			out.println("super(query, foreignKey, values);");
			out.println("}\n");
		}

		out.println("}");
	}

	private void genTableQuery(ArrayList<QueryMethod> sqls, boolean genSubQuery) throws IOException {
		for (QueryMethod data : sqls) {
			String ret_t = data.returnType$; 
			
			if (!genSubQuery) {
				out.println("// ", data.sql);
			}
			
			out.printIf(!this.isAbstract(), "public ")
				.print(ret_t, " ",  data.methodName, "(");
			
			data.printParams(out, true);
			if (genSubQuery) {
				out.println(") {");
				ModelGen fd = getDeclaringModel(data.joinedColumn);
				out.print("return _TableBase.", fd.dbTableName, ".", data.methodName, "(this, ");
				data.printParams(out, false);
				out.println(");");
				out.println("}\n");
				continue;
			}
			
			if (this.isAbstract()) {
				out.println(");\n");
				continue;
			}
			out.println(") {");

			if (!data.isRowSet) {
				out.print("return super.", data.isVolatile ? "find" : "load", "First(", data.methodName, ", ");
			}
			else {
				out.print("return new ", ret_t, "(", data.methodName, ", ");
				if (!genSubQuery && data.joinedColumn != null) {
					out.print("#ENTITY_ORM#", data.joinedColumn.getSlotName(), ", ");
				}				
				else {
					out.print("null, ");
				}
			}

			if (data.joinedColumn != null) {
				out.print(data.joinedColumn.getKey(), ", ");
			}				
			for (String param : data.searchParams) {
				out.print(param, ", ");
			}
			out.removeTail(2);
			out.println(");");
			out.println("}\n");
		} 
	}

	
	private void dumpParams(ORMField[] parts, int flags, String delimter) throws IOException {
		for (ORMField p : parts) {
			dumpParam(p, flags);
			out.print(delimter);
		}
		out.removeTail(delimter.length());
	}

	private void dumpParam(ORMField p, int flags) throws IOException {
		if ((flags & COLUMN_TYPE) != 0) {
			out.print(getSimpleGenericDeclation(p));
		}
		else if ((flags & OBJ_COLUMN_TYPE) != 0) {
			String s = ORMGenerator.getObjectType(getSimpleGenericDeclation(p));
			out.print(s);
		}

		if ((flags & COLUMN_KEY) != 0) {
			out.print(" ", p.getKey());
		}
		
	}
	
	TableDefinition getTaleDefinition() {
		return this.tableDefinition;
	}

	private void genTableConfiguration() throws IOException {

		boolean canCreateEmpty = true;
		for (ORMField slot : this.allProperties) {
			if (slot.isForeignKey() && slot.isUnique() && !slot.isNullable()) {
				canCreateEmpty = false; 
				break;
			}
		}

		if (canCreateEmpty) {
			out.println("public #EDITOR# newEntity() {");
			out.println("return edit((#BASE_SNAPSHOT#)null);");
			out.println("}\n");
		}
		
		out.println("public #EDITOR# edit(#BASE_SNAPSHOT# entity) {");
		out.println("return new _Ref.Editor(this, entity);");
		out.println("}\n");

        out.println("public #EDITOR# edit(#REF# ref) throws InvalidEntityReferenceException {");//, SQLException  {");
        out.println("return edit(ref == null ? null : ref.loadSnapshot());");
        out.println("}\n");

        out.println("public #EDITOR# edit(#BASE_FORM# form) throws InvalidEntityReferenceException, SQLException {");
        out.println("#EDITOR# edit = edit(form.getOriginalData());");
        out.println("edit._set(form);");
        out.println("return edit;");
        out.println("}\n");

        out.println("protected #EDITOR# edit(EntitySnapshot entity) throws InvalidEntityReferenceException {");
        out.println("return edit((#SNAPSHOT#)entity);");
        out.println("}\n");

        for (ORMField p : this.uniques) {
			out.print("public #EDITOR# edit_with", p.getSlotName(), "(");
			dumpParam(p, COLUMN_TYPE | COLUMN_KEY);				
			out.println(") {");
			if (!p.getBaseType().isPrimitive()) {
				out.println("assert(", p.getKey(), " != null);");
			}
			out.print("#REF# orgRef = findBy", p.getSlotName(), "(");
			dumpParam(p, COLUMN_KEY);
			out.println(");");
			out.println("#BASE_EDITOR# edit = edit(orgRef == null ? null : orgRef.tryLoadSnapshot());");
			out.println("if (edit.getOriginalData() == null) {");
			String prefix = p.isForeignKey() ? "edit.__set" : "edit.set"; 
			out.println(prefix, p.getSlotName(), "(", p.getKey(), ");");
			//out.println("long updateBits = ", p.getSlotName(), ".getUpdateBit();");
			out.println("}");
			out.println("return (#EDITOR#) edit;");
			out.println("}\n");
		}

		for (ORMField[] uc : this.uniqueParts) {
			boolean hasRestrictedSetMethod = false;
			for (ORMField f : uc) {
				if (f.isInternal() || f.isReadOnly()) {
					hasRestrictedSetMethod = true;
					break;
				}
			}
			out.print("public #EDITOR# ", getEditMethodName(uc), "(");
			dumpParams(uc, COLUMN_TYPE | COLUMN_KEY, ", ");
			out.println(")", hasRestrictedSetMethod ? " throws RestrictedOperation" : ""," {");

			out.print("#REF# orgRef = ", getFindStatementName(uc), "(");
			dumpParams(uc, COLUMN_KEY, ", ");
			out.println(");");
			
			out.println("#EDITOR# edit = edit(orgRef == null ? null : orgRef.tryLoadSnapshot());");
			out.println("if (edit.getOriginalData() == null) {");
			for (ORMField si : uc) {
				String prefix = /*si.isForeignKey() ? "edit.__set" : */ "edit.set"; 
				out.println(prefix, si.getSlotName(), "(", si.getKey(), ");");
			}
			out.println("}");
			out.println("return edit;");
			out.println("}\n");
		}

		TableDefinition td = getTaleDefinition();
		out.println("public void getTableConfiguration(StormTable.Config config) {");
		out.println("config.rowIdStart = ", td.rowIdStart(), "L;");
		//out.println("config.isImmutable = ", this.isImmutable, ";");
		out.println("config.helper = _Ref.createORMHelper();");
		out.println("config.snapshotType = _Snapshot.class;");
		out.println("config.referenceType = _Ref.class;");
		out.println("config.entityCache = TableDefinition.CacheType.", td.entityCacheType(), ";");
		out.println("config.isFts = ", td.isFts(), ";");

//		ArrayList<String> uniques = new ArrayList<>();
//		ArrayList<String> indexes = new ArrayList<>();
//		boolean isFirst = true;
//		for (ORMField f : this.getAllDBFields()) {
//			if (!f.isDBColumn()) {
//				continue;
//			}
//			String columnType = f.getDBColumnType();
//			String key = f.getKey();
//			if (isFirst) {
//				out.print("\t\"", key, " ", columnType);
//				isFirst = false;
//			}
//			else {
//				out.print("\t+ \"", key, " ", columnType);
//			}
//
//			if ((f.getAccessFlags() & ORMFlags.Indexed) != 0) {
//				indexes.add(key);
//			}
//
//			if ((f.getAccessFlags() & ORMFlags.Unique) != 0) {
//				uniques.add(key);
//			}
//			out.println(",\"");
//		}
//
//		String s = td.hiddenColumns().trim();
//		if (s.length() > 0) {
//			out.println("\t+ \"", s, ",\""); // @TODO 문자열 Escape 필요.
//		}
//
//		for (String key : uniques) {
//			out.println("\t+ \"UNIQUE (", key, "),\"");
//		}
//
//		for (ORMField[] uc : this.uniqueParts) {
//			out.print("config.sqlCreate = ");
//			out.print("\t\"UNIQUE (");
//			for (ORMField f : uc) {
//				out.print(f.getKey());
//				out.print(", ");
//			}
//			out.removeTail(2);
//			out.println(")\";");
//		}

		out.println("}\n");
	}

	private boolean hasSnapshotField(ORMField slot) {
		return slot.isDBColumn() || !slot.isVolatileData();
	}

	private boolean hasAlternateEditorField(ORMField slot) {
		// OuterLink 를 반환.
		 return slot.isOuterLink() // && (!slot.isSingleEntity() || slot.isVolatileData())
				 && !slot.getModelOfColumnType().isAbstract();
	}

	private void genFields(Instance instance, ORMField[] fields) throws Exception {
		for (ORMField f : fields) {
			if (f.getKey() == "_exceptions") {
				int a = 3;
				a ++;
			}
			if (f.isAlias()) {
				continue;
			}
			if (instance == SnapshotSelf ? !hasSnapshotField(f) : !hasAlternateEditorField(f)) {
				continue;
			}

			String name = f.getKey();
			String type = null;
			if (instance.isEditable) {
				if (f.isSingleEntity()) {
					if (f.isOuterLink() && !f.isVolatileData()) {
						type = f.getModelOfColumnType().getModelFormName();
					} else {
						type = f.getModelOfColumnType().getSimpleName();
					}
				} else {
					type = getEditableEntityType$(f);
				}
			}
			if (type == null) {
				type = getFieldType(f, instance);
			}
			out.println("/*internal*/ ", type, " ", name, ";\n");
		}
	}


	void genSnapshotClass() throws Exception {
		boolean isAbstract = this.isAbstract();

		String superName = this.superORM == null ? "EntitySnapshot" : this.superORM._data;

		out.println("public abstract class #BASE_SNAPSHOT# extends ",
				superName, " implements #MODEL_NAME#", MODEL_FORM_SUFFIX, ", #ENTITY_ORM# {\n");
		
		out.println("private static final _TableBase.Initializer unsafeTools = _TableBase.initializer;\n");

		genFields(SnapshotSelf, this.getDeclaredDBFields());

		out.println("protected #BASE_SNAPSHOT#(#REF# ref) {");
		out.println("super(ref);");
		out.println("}\n");


		for (ORMField f : this.getDeclaredDBFields()) {
			if (HIDE_VOLTILE && f.isOuterLink() && f.isVolatileData()) continue;
						
			genGetField(f, false);
		}

		out.println("/****************************************/");
		out.println("/*  Internal methods                    */");
		out.println("/*--------------------------------------*/\n");


		if (isAbstract) {
			out.println("public abstract #SNAPSHOT# getOriginalData();\n");
			out.print(this.hideSaveMethod ? "protected " : "public ").println("abstract #EDITOR# editEntity();\n");
		}
		else {
			out.println("public final #SNAPSHOT# getOriginalData() {");
			out.println("return (#SNAPSHOT#)this;");
			out.println("}\n");
			out.print(this.hideSaveMethod ? "protected " : "public ").println("#EDITOR# editEntity() {");
			out.println("return getTable().edit(this);");
			out.println("}\n");
		}
		
		if (!isAbstract) {
			out.println("public final #BASE_TABLE# getTable() {");
			out.println("return #TABLE_INSTANCE#;");
			out.println("}\n");
		}

		out.print("public ").printIf(!isAbstract, "final ").println("#REF# getEntityReference() {");
		out.println("return (#REF#)super.getReference_internal();");
		out.println("}\n");
		
//		out.println("protected void onLoadSnapshot() throws SQLException {");
//		out.println("super.onLoadSnapshot();");
//		for (ORMField p : this.getDeclaredDBFields()) {
//			if (p.isOuterLink() && this.hasSnapshotField(p)) {
//				String fieldRef = "this." + p.getKey();
//				if (p.isOuterRowSet()) {
//					out.print(fieldRef, " = ((#REF#)this.getEntityReference()).", p.getGetMethodName(), "()");
//					if (!p.isVolatileData()) {
//						out.print(".loadEntities()");
//					}
//					out.println(";");
//				}
//				else if (!p.isVolatileData()) {
//					out.println("StormTable.UnsafeTools.callOnLoad((EntitySnapshot)this.", p.getKey(), ");");
//				}
//			}
//			else if (p.isForeignKey()) {
//				if (!ALWAYS_LOAD_FOREIGN_KEY_REFERENCE && !p.isVolatileData()) {
//					String type = getReturnType(p, SnapshotSelf);
//					out.println(p.getKey(), " = (", type, ")", p.getKey(), ".getEntityReference().loadSnapshot();");
//				}
//			}
//		}
//		out.println("}\n");

		out.println("protected void ensureLoadSubSnapshot() {");
		out.println("super.ensureLoadSubSnapshot();");
		for (ORMField p : this.getDeclaredDBFields()) {
			/**
			 * 반드시 편집 전에 전체 Snapshot 을 로딩하여아만 한다.
			 * 한 컬럼이라도 변경되면 새로운 Entity가 생성되는데,
			 * 새로 생성된 Entity를 다시 변경한 경우, 최초의 원본 엔터티의 Snapshot 을 로딩할 수 없게 되기 때문이다.
			 */
			if (p.isOuterLink() && this.hasSnapshotField(p)) {
				out.println("this.", p.getGetMethodName(), "();");
			}
		}
		out.println("}\n");		
		out.println("}\n");
	}

	private ApiList getApiColumns() {
		if (this.apiColumns != null) {
			return this.apiColumns;
		}
		this.apiColumns = new ApiList();

		for (ModelGen model = this; ;) {
			for (ORMField f : model.declaredColumns) {
				if (!f.isInternal()) {
					this.apiColumns.add(f);
				}
			}

			model = model.superORM;
			if (model == null || model.apiExported) {
				apiColumns.apiSuper = model;
				break;
			}
		}
		this.apiColumns.sort((o1, o2) -> {return o1.getSlotName().compareTo(o2.getSlotName());});
		return apiColumns;
	}


	String getModelAccessType(ORMField f, boolean isEditable) {

		ModelGen model = f.getModelOfColumnType();
		boolean isForm = true;
		if (model != null) {
			if (f.isSingleEntity()) {
//				if (!isEditable) {
//					return model.modelName + MODEL_REF_SUFFIX;
//				}
				if (shouldLoadSnapshot(f, false)) {
					return model.modelName + (isForm ? MODEL_FORM_SUFFIX : MODEL_DATA_SUFFIX);
				}
				return model.modelName + MODEL_REF_SUFFIX;
			} else if (f.isOuterRowSet()) {
				String itemType = model.modelName + MODEL_FORM_SUFFIX;
//				String itemType = model.modelName + MODEL_FORM_SUFFIX;

				if (model.modelName == null) {
					itemType = model.getSimpleName();
				}
				String listType = (isForm || f.isVolatileData()) ? "List" : "ImmutableList";
				return listType + "<? extends " + itemType + ">";
			}
		}
		String type;
		if (!isEditable && EnumSet.class.isAssignableFrom(f.getBaseType())){
			// EnumSet 에 대한 임시 처리. 2019.11.27.
			type = "Set<" + getShortClassName(f.getGenericParameters()[0]) + ">";
		} else if (isForm && isEditableEntityType(f)) {
			type = getEditableEntityType$(f);
		}
		else {
			type = getReturnType(f, isForm ? EditorSelf : SnapshotSelf);
		}
		return type;
	}


	String genModelAPI() throws Exception {
		String thisName = this.modelName + "_";
		ApiList list = this.getApiColumns();
		out.println("public interface ", thisName, " extends #ENTITY_ORM# {\n");

		genModelFormAPI("UpdateForm");
//		genModelMockAPI("Mock");
		out.println();
		out.println("}");
		return thisName;
	}


//	String genModelDataAPI_obsolete(String thisName) throws Exception {
//		ApiList list = this.getApiColumns();
//		String _extends = " extends " + this.modelName + MODEL_FORM_SUFFIX;
//		if (list.apiSuper != null) {
//			_extends += ", " + list.apiSuper.modelName + MODEL_DATA_SUFFIX;
//		}
//		// _extends += ", #ENTITY_ORM#";
//
//		out.println("interface ", thisName, _extends, ", #ENTITY_ORM#.Data, ", this.modelName, " {\n");
//
//		for (ORMField f : list) {
//			//if (!hasSnapshotField(f))  continue;
//
//			String name = f.getSlotName();
//			String type = getModelAccessType(f, false);;
//			out.println(type, " ", f.getGetMethodName(), "();\n");
//			genEnumAccessHelper(f, "default", false);
//
//			if (f.isOuterLink() && !f.isVolatileData() && f.isUnique() && !f.isNullable()) {
//				String prefix = f.getGetMethodPrefix() + name + '_';
//				for (ORMField f2 : f.getModelOfColumnType().allProperties) {
//					if (f2.isForeignKey() && f2.getModelOfColumnType() == this) continue;
//
//					String m2 = f2.makeMethodName(prefix);
//					type = getModelAccessType(f2, SnapshotSelf);;
//					out.println(type, " ", m2, "();\n");
//				}
//			}
//		}
//
//		out.println("}\n");
//		return thisName;
//	}


	void genModelFormAPI(String thisName) throws Exception {
		ApiList list = this.getApiColumns();
		String _extends = " extends ";
		if (list.apiSuper == null) {
			_extends += "ORMEntity" + MODEL_FORM_SUFFIX;
		}
		else {
			_extends += list.apiSuper.modelName + MODEL_FORM_SUFFIX;
		}
		
		out.println("public interface ", thisName, _extends, " {\n");

		out.println("#SNAPSHOT# getOriginalData();\n");

		//out.println("interface ", thisName, _extends, " {\n");
		for (ORMField f : list) {
			String type = getModelAccessType(f, false);
			if (HIDE_VOLTILE && f.isOuterLink() && f.isVolatileData()) continue;
			String method = f.getGetMethodName();
			if (f.isOuterRowSet() && f.isVolatileData()) {
//					type = type.replace(MODEL_FORM_SUFFIX + ">", ">");
				method = "peek" + f.getFieldName();
			}

			if (f.isReadOnly()) {// (f.getAccessFlags() & ORMFlags.CanFind) != 0) {
				out.println("// ", type, " ", method, "();\n");
			}
			else {
				out.println(type, " ", method, "();\n");
				genEnumAccessHelper(f, "default", false);
			}
		}

		out.println("default boolean equalsTo(", this.modelName + MODEL_FORM_SUFFIX, " data) {");
		out.println("return");
		int buff_pos = out.getBufferdLength();
		if (list.apiSuper != null) {
			out.println("equalsTo((", list.apiSuper.modelName + MODEL_FORM_SUFFIX, ")data) &&");
		}
		for (ORMField f : list) {
			if (HIDE_VOLTILE && f.isOuterLink() && f.isVolatileData()) continue;
			if (f.isReadOnly() || f.isAlias()) {
				continue;
			}
			String methodName = f.getGetMethodName();
			if (f.isOuterRowSet()) {
				if (f.isVolatileData()) {
					methodName = "peek" + f.getFieldName();
				}
				out.println("StormUtils.contentEquals(this.", methodName, "(), data.", methodName, "()) &&");
			}
			else {
				out.println("StormUtils.equals(this.", methodName, "(), data.", methodName, "()) &&");
			}
		}
		if (out.getBufferdLength() > buff_pos) {
			out.removeTail(3);
		}
		else {
			out.removeTail(1);
			out.print(" true");
		}
		out.println(";\n}\n");

//		if (!this.isAbstract()) {
//            out.println("default ORMEntity getStormEntity(StormTable table) throws SQLException, InvalidEntityReferenceException {");
//            out.println("return ((#BASE_TABLE#)table).edit(this);");
//            out.println("}\n");
//        }

        out.println("}\n");
	}

//	String genModelEditorAPI_obsolete(String thisName) throws Exception {
//		ApiList list = this.getApiColumns();
//		String _extends = " extends " + this.modelName + MODEL_FORM_SUFFIX;
//		if (list.apiSuper != null) {
//			_extends += ", " + list.apiSuper.modelName + MODEL_EDITOR_SUFFIX;
//		}
//
//		out.println("interface ", thisName, _extends, " {\n");
//		if (!this.isAbstract()) {
//			out.println("void setReference(", this.modelName,  MODEL_REF_SUFFIX, " ref);\n");
//		}
//		for (ORMField f : list) {
//			String name = f.getSlotName();
//			String type = getModelAccessType(f, EditorSelf);
//			String prefix = f.isReadOnly() ? "// " : "";
//			out.println(prefix, "void set", name, "(", type, " ", f.getVariantName(), ");\n");
//		}
//
//		out.println("}\n");
//		return thisName;
//	}

	String genModelMockAPI_obsolete(String thisName) throws Exception {
		ApiList list = this.getApiColumns();
		String ifcName = this.modelName + MODEL_FORM_SUFFIX;
		String _extends = (list.apiSuper == null) ? "" : " extends " + list.apiSuper.modelName + MODEL_MOCK_SUFFIX;
		_extends = _extends.length() == 0 ? " extends EntityEditor" : _extends;

		out.print("abstract class ", thisName, _extends, " implements ", ifcName, " {\n");

//		if (!this.isAbstract()) {
//			out.println("private #SNAPSHOT# _origin;");
//		}
//		for (ORMField f : list) {
//			if (HIDE_VOLTILE && f.isOuterLink() && f.isVolatileData()) continue;
//			if (f.isInternal()) {
//				continue;
//			}
//			String prefix = f.isReadOnly() || (f.isAlias() && !f.getOriginColumn().isReadOnly()) ? "// " : "";
//			String type = getModelAccessType(f, true);
//			if (!f.isCollection() && !f.isMap()) {
//				out.println(prefix, "protected ", type, " ", f.getKey(), ";");
//			}
//			else {
//				out.print(prefix, "protected final ", type, " ", f.getKey(), " = ");
//				if (f.isMap()) out.println("new HashMap<>();");
//				else if (f.isList()) out.println("new ArrayList<>();");
//				else if (EnumSet.class.isAssignableFrom(f.getBaseType())) out.println("EnumSet.noneOf(", f.getFirstGenericParameterType().getSimpleName(),".class);");
//				else if (f.isSet()) out.println("new ArraySet<>();");
//				else throw new RuntimeException("Something wrong!!"  + f.getBaseType());
//			}
//		}
//		out.println("\n");
		out.println();

		out.println("public #REF# getEntityReference() { return (#REF#) super.getEntityReference_internal(); }\n");

		out.println("public #SNAPSHOT# getOriginalData() { return (#SNAPSHOT#) super.getOriginalData_internal(); }");
		if (!this.isAbstract()) {
			out.println("public void setOriginalData(#SNAPSHOT# data) { super.setOriginalData(data); }\n");
		} else {
			out.println();
		}



		for (ORMField f : list) {
			if (HIDE_VOLTILE && f.isOuterLink() && f.isVolatileData()) continue;
			if (f.isInternal()) {
				continue;
			}
			String prefix = f.isReadOnly() ? "// " : "";
			String name = f.getSlotName();
			String type = getModelAccessType(f, true);
			String param = f.getVariantName();

			String ormField = "#ENTITY_ORM#." + f.getSlotName();
			String getFieldValue = getGetFieldValueString(f, type);

			out.println(prefix, "public ", type, " ", f.getGetMethodName(), "() { return ", getFieldValue + "; }");

			if (f.isReadOnly()) continue;

			out.print(prefix, "public ", "void set", name, "(", getModelAccessType(f, false), " ", param, ") {");
			if (!f.isCollection() && !f.isMap()) {
				out.println(" super.setFieldValue(", ormField, ", " , param, "); }\n");
			}
			else {
				out.println();
				out.println(type, " v = ", getFieldValue, ";");
				out.println("if (v  == ", param, ") return;");
				if (f.isCollection()) {
					out.println("v.clear();");
					out.println("if (", param, " != null) v.addAll(", param, ");");
				}
				else if (f.isMap()) {
					out.println("v.clear();");
					out.println("if (", param, " != null) v.putAll(", param, ");");
				}
				else {
					throw new RuntimeException("something wrong");
				}
				out.println("} \n");
			}
		}

		out.println("public Mock _set(", this.modelName + MODEL_FORM_SUFFIX, " data) {");
		if (!this.isAbstract()) {
			out.println("setOriginalData(data.getOriginalData());");
		}
		if (list.apiSuper != null) {
			out.println("super._set(data);\n");
		}
		for (ORMField f : list) {
			if (HIDE_VOLTILE && f.isOuterLink() && f.isVolatileData()) continue;
			if (f.isReadOnly()) {
				continue;
			}
			String name = f.getGetMethodName();
			out.println("set" + f.getSlotName(), "(data.", name, "());");
		}
		out.println("return this;");
		out.println("}\n");

		out.println("}\n");
		return thisName;
	}

//	String genModelReferenceAPI(String thisName) throws Exception {
//		ApiList list = this.getApiColumns();
//		String _extends;
//		if (false) {
//			_extends = " extends #ENTITY_ORM#";
//			if (list.apiSuper != null) {
//				_extends += ", " + list.apiSuper.modelName;
//			}
//		}
//		else {
//			if (list.apiSuper != null) {
//				_extends = " extends " + list.apiSuper.modelName + MODEL_REF_SUFFIX;
//			}
//			else {
//				_extends = " extends ORMEntity";
//			}
//		}
//
//		out.println("interface ", thisName, _extends, ", #ENTITY_ORM#.Ref, ", this.modelName, " {\n");
//		out.println("long getEntityId();\n");
//		if (true) {
//			out.println("" + this.modelName, MODEL_DATA_SUFFIX, " loadSnapshot() throws SQLException, InvalidEntityReferenceException;\n");
//			out.println("" + this.modelName, MODEL_DATA_SUFFIX, " tryLoadSnapshot();\n");
//		}
////		else {
////			out.println("default " + this.modelApi, DATA_API_SUFFIX, " loadSnapshot() throws SQLException, InvalidEntityReferenceException { throw Debug.notImplemented(); }\n");
////			out.println("default " + this.modelApi, DATA_API_SUFFIX, " tryLoadSnapshot() { throw Debug.notImplemented(); }\n");
////		}
//
//		for (ORMField p : getDeclaredDBFields()) {
//			if ((p.isOuterLink() && p.isUnique() || p.isForeignKey() || p.isImmutable())) {
//				String type = this.getModelAccessType(p, ReferenceSelf);
//				if (false) {
//					out.println(type, " ", p.getGetMethodName(), "();\n");
//				}
//				else {
//					out.println("default ", type, " ", p.getGetMethodName(), "() { throw Debug.notImplemented(); }\n");
//				}
//			}
//		}
//		out.println("}\n");
//		return thisName;
//	}

	String genModelUserAPI() throws Exception {
		ApiList list = this.getApiColumns();
		String _extends;

		String thisName = this.modelName;

		/*
		if (list.apiSuper == null) {
			_extends = "ORMEntity.Holder";
		}
		else {
			if (this.isAbstract()) {
			_extends = list.apiSuper.modelName;
			}
			else {
				_extends = list.apiSuper.modelName;
			}
		}
		*/
		_extends = this.getSimpleName();// + ", ORMEntity.ReferenceHolder";

		String thisType = thisName;

		out.println("public abstract class ", thisType, " extends #BASE_REF# {\n");

//		out.println("abstract class Ref  extends #BASE_REF# {");
		out.println("protected ", thisType, "(long id) { super(id); }\n");
//		out.println("}\n");

		out.println("public static abstract class Snapshot extends #BASE_SNAPSHOT# {");
		out.println("protected Snapshot(#REF# ref) { super(ref); }");
		out.println("}\n");
	    
		out.println("public interface UpdateForm extends #BASE_FORM# {}\n");

        out.print("public static ").printIf(this.isAbstract(), "abstract ").println("class Editor extends ", getBaseReferenceName(), ".Editor {");
        out.println("protected Editor(", getBaseTableName(), " table, ", getBaseSnapshotName(), " origin) { super(table, origin); }");
        out.println("}");
		
		out.println("}\n");
		return thisName;
	}


	void genEditorClass() throws Exception {
		boolean isAbstract = this.isAbstract();

		String superName = this.superORM == null ? "EntityEditor" : this.superORM.exRef + ".Editor";
		String implements_ = " implements #ENTITY_ORM#";
		if (EDITOR_EXTENDS_FORM && this.modelName != null) {
			implements_ += ", " + this.modelName + MODEL_FORM_SUFFIX;
		}

		out.println("public abstract class #BASE_EDITOR# extends ", superName, implements_, " {\n");

		if (isAbstract) {

			out.println("protected #BASE_EDITOR#(#BASE_TABLE# table, #BASE_SNAPSHOT# origin) {");
			out.println("super(table, origin);");
			out.println("}\n");

			out.println("public abstract #REF# getEntityReference();\n");

			out.println("public #BASE_TABLE# getAbstractTable() { return (#BASE_TABLE#)this.getTable(); }\n");

			
			this.genEditFunctions(isAbstract, true);
			this.genEditFunctions(isAbstract, false);

			out.println();
			if (!this.hideSaveMethod) {
				out.println("public abstract #REF# save() throws SQLException, RuntimeException;");
			}
		}
		else {

			out.println("private static final _TableBase.Initializer unsafeTools = _TableBase.initializer;\n");
			out.println("static final #REF# ghost = _TableBase.", dbTableName, ".createGhostRef();\n");

			this.genFields(EditorSelf, this.getAllDBFields());

			out.println("protected #BASE_EDITOR#(#BASE_TABLE# table, #BASE_SNAPSHOT# origin) {");
			out.println("super(table, origin);");

			if (!HIDE_VOLTILE) {
				boolean hasNullableUniqueOuterLink = false;
				for (ORMField f : this.allProperties) {
					/**
					 * 2019.0830
					 * VolatileOuterLink 칼럼은 Snapshot에 field 가 생성되지 않는다. (Reference 에 생성)
					 * UniqueNullable 인 경우, removeXXX() 를 구현하기 위해 초기값을 설정한다.
					 */
//					boolean isNullableUniqueOuterLink = f.isOuterLink() && f.isUnique() && f.isNullable();
////					if (isNullableUniqueOuterLink && f.isVolatileData()) {
////						if (!hasNullableUniqueOuterLink) {
////							hasNullableUniqueOuterLink = true;
////							out.println("if (origin != null) {");
////						}
////						out.println("this.", f.getKey(), " = origin.", f.getGetMethodName(), "();");
////					}

					/**
					 *  Snapshot Join 관계에 있는 column 들의 removeMethod 를 구현하기 위해
					 *  초기값을 설정한다.
					 */
					if (f.isOuterLink() && f.isUnique() && !f.isVolatileData()) {
						if (!hasNullableUniqueOuterLink) {
							hasNullableUniqueOuterLink = true;
							out.println("if (origin != null) {");
						}
						out.println("this.", f.getKey(), " = origin.", f.getGetMethodName(), "();");
					}
				}
				if (hasNullableUniqueOuterLink) {
					out.println("}");
				}
			}
			out.println("}\n");

			out.println("public final #BASE_TABLE# getTable() {");
			out.println("return (#BASE_TABLE#)super.getTable();");
			out.println("}\n");

//			out.println("protected #BASE_SNAPSHOT# getEditingEntity_unsafe() {");
//			out.println("return this.edit;");
//			out.println("}\n");

			out.println("public final #REF# getEntityReference() {");
			out.println("return (#REF#)super.getEntityReference_internal();");
			out.println("}\n");

			out.println("public final #SNAPSHOT# getOriginalData() {");
			out.println("return (#SNAPSHOT#)super.getOriginalData_internal();");
			out.println("}\n");
			
			this.genEditFunctions(isAbstract, true);
			this.genEditFunctions(isAbstract, false);

			genSaveFunction();

			genValidate(allProperties);


		}


		if (this.modelName != null) {
//				@msg.Jonghoon.To_Daehoon("public 함수로 변경")
			out.println("public void _set(#BASE_FORM# data) {");
			if (this.superORM != null) {
				out.println("super._set(data);\n");
			}

			for (ORMField f : this.declaredColumns) {
				if (HIDE_VOLTILE && f.isOuterLink() && f.isVolatileData()) continue;
				if (f.isForeignKey() || f.isInternal() || f.isReadOnly()) {
					continue;
				}
				String name = f.getSlotName();
				boolean isEditableEntity = isEditableEntityType(f);
				if (!isEditableEntity) {
					if (f.isEntity()) {
						out.println("set", name, "((", f.getModelOfColumnType()._ref, ")data.",
								f.getGetMethodName(), "());");
					}
					else {
						out.println("set", name, "(data.", f.getGetMethodName(), "());");
					}
				}
				else if (f.isOuterRowSet()) {
					String method = f.getGetMethodName();
					if (f.isVolatileData()) {
						method = "peek" + f.getFieldName();
					}
					out.println("if (data.", method, "() != null) {");
					out.println("edit", name, "().replaceAll(data.", method, "());");
					out.println("}");
				}
				else if (f.isUniqueVolatileLink()) {
					out.println("this.", f.getKey(), " = ((", f.getModelOfColumnType()._data, ")data.", f.getGetMethodName(), "().loadSnapshot()).editEntity();");
				}
				else if (f.isOuterLink() || f.isEmbedded()) {
					out.println("if (data.", f.getGetMethodName(), "() != null) {");
					out.println("edit", name, "()._set(data.", f.getGetMethodName(), "());");

					if (f.isNullable()) {
						out.println("} else {");
						boolean isNullableUniqueOutterLink = f.isOuterLink() && f.isUnique() && f.isNullable();

						if (isNullableUniqueOutterLink) {
							out.println("this.remove", name, "();");
						} else {
							out.println("this.set", name, "(null);");
						}
					}

					out.println("}");
				}
				else {
					out.println("set", name, "((", f.getModelOfColumnType()._ref, ")data.", f.getGetMethodName(), "());");
				}
			}
			out.println("}\n");
		}

		out.println("}\n");

	}

	private void genEditFunctions(boolean isAbstract, boolean columns) throws Exception {
		ModelGen super_ = this.getSuperORM();
		if (super_ != null) {
			super_.genEditFunctions(isAbstract, columns);
		}
		genDeclaredEditFunctions(isAbstract, columns);
	}

	private void genDeclaredEditFunctions(boolean isAbstract, boolean bProcessColumn) throws Exception {
		ORMField[] fields = this.getDeclaredDBFields();
		for (ORMField f : fields) {
			if (bProcessColumn == f.isDBColumn()) {
				//if (HIDE_VOLTILE && f.isOuterLink() && f.isVolatileData()) continue;
				
				genGetOrEditFieldFunction(EditorSelf, f, isAbstract);
				genEditorSetField(EditorSelf, f, isAbstract);
			}
		}
	}

	private void genSaveFunction() throws Exception {
		if (!hideSaveMethod) {
			out.println("public #REF# save() throws SQLException, RuntimeException {");
			out.println("return (#REF#)super.doSave(false);");
			out.println("}\n");

			out.println("public #REF# saveAndContinueEdit() throws SQLException, RuntimeException {");
			out.println("return (#REF#)super.doSave(true);");
			out.println("}\n");
		}		
		
		out.println("protected void onSave_inTR() throws SQLException, RuntimeException {");
		ORMField[] fields = this.getAllDBFields();
		boolean shouldSkipLine = false;

		for (ORMField f : fields) {
			if (f.isForeignKey()) {
				/**
				 * 상위 엔터티를 먼저 저장. (ID가 필요하므로)
				 */
				String type = f.getModelOfColumnType().getSimpleName();
				String getFieldValue = getGetFieldValueString(f, type);
				out.println("super.__saveForeignEntityInternal(", getFieldValue, ");");
				shouldSkipLine = true;
			}
		}
		if (shouldSkipLine) {
			out.println();
			shouldSkipLine = false;
		}

		out.println("if (this.isChanged()) {");
		boolean hasUniqueLinkedField = false;
		for (ORMField f : fields) {
			hasUniqueLinkedField |= (f.isOuterLink() && !f.isOuterRowSet() && f.isNullable() && !f.isVolatileData());

			if (f.isDBColumn() && f.isEntity() && !f.isForeignKey()) {
				String type = f.getModelOfColumnType().getSimpleName();
				String getFieldValue = getGetFieldValueString(f, type);
				/**
				 * 직접 참조하는 외부 엔터티 먼저 저장. (ID가 필요하므로)
				 */
				out.println("super.__saveJoinedEntityInternal(", getFieldValue, ");");
				shouldSkipLine = true;
			}
		}

		if (shouldSkipLine) {
			out.println();
		}

		out.println("super.onSave_inTR();");
		out.println("}\n");

		if (hasUniqueLinkedField) {
			out.println("#BASE_SNAPSHOT# origin = (#BASE_SNAPSHOT#)this.getOriginalData();");
		}
		for (ORMField f : fields) {
			if (f.isOuterLink() && (!HIDE_VOLTILE && !f.getModelOfColumnType().isAbstract())) {
				String field = "this." + f.getKey();
				if (f.isOuterRowSet()) {
					out.println("super.__saveJoinedEntitiesInternal(", field, ", _TableBase.", getModelOfColumnType(f).dbTableName, ".",
							getForeignKeyOfOuterEntity(f).getFindMethodName(f), "(this));");
				} else if (!f.isNullable() || f.isVolatileData()) {
					out.println("super.__saveJoinedEntityInternal(", field, ");");
				} else {
					String getOriginValueMethod = "origin." + f.getGetMethodName() + "()";
					out.println("super.__saveOrDeleteJoinedEntityInternal(origin == null ? null : ",
							getOriginValueMethod, ", ", field, ");");
				}
			}
		}
		out.println("}\n");

	}

	private void genValidate(ORMField[] fields) throws IOException {
		out.println("protected void validate_inTR() throws RuntimeException {");
		//out.println(this._ref, ".validateBeforeSaveEntity_inTR(this);");
		out.println("super.validate_inTR();");
		for (ORMField f : fields) {
			if (!f.isNullable()) {
				Class<?> type = f.getBaseType();
				if (EntityEditor.resolveDefaultValue(type) != null) {
					continue;
				}
				if (f.isOuterLink()) {
					String field = "this." + f.getKey();
					out.println("ensureNotNull(#ENTITY_ORM#.", f.getSlotName(), ", ",
							field, ");");
				} else if (!type.isPrimitive() && !EnumSet.class.isAssignableFrom(type)) {
					out.println("ensureNotNull(#ENTITY_ORM#.", f.getSlotName(), ");");
				}
			}
		}

		out.println("}\n");
	}



	private String getGetFieldValueString(ORMField f, String type) {
		String ormField = "#ENTITY_ORM#." + f.getSlotName();
		String getFieldValue;
		Class<?> t = f.getBaseType();
		if (t.isPrimitive()) {
			String tName = t.getSimpleName();
			getFieldValue = "super.get" + (Character.toUpperCase(tName.charAt(0))) + tName.substring(1) + "Value";
		} else {
			getFieldValue = "super.getObjectValue";
			if (type != null) {
				getFieldValue = "(" + type + ") " + getFieldValue;
			}
		}
		getFieldValue += "(" + ormField + ")";
		return getFieldValue;
	}

	static ModelGen getModelOfColumnType(ORMField slot) {
	 return _ormGen.getModel(slot.getBaseType());
	}

	static  ModelGen getDeclaringModel(ORMField slot) {
		return _ormGen.getModel(slot.getDeclaringClass());
	}
	
	private boolean isEditableEntityType(ORMField f) {
		if (!f.isEntity() || (!ENABLE_EDIT_FOREIGN_KEY && f.isForeignKey())) {
			return false;
		}

		if (f.isSingleEntity() && !f.isOuterLink() && !shouldLoadSnapshot(f, true)) {
			return false;
		}
		
		ModelGen td = getModelOfColumnType(f);
		if (td.isAbstract() || td == this) {
			return false;
		}
		return true;
	}
	
	private String getEditableEntityType$(ORMField f) {
		String res;
		ModelGen td = getModelOfColumnType(f);
		if (f.isSingleEntity()) {
			res = td.getModelEditorName();
		}
		else {
			String prefix = td.isAbstract() ? "? extends " : "";
			res = "EditableEntities<" + td.getModelFormName() + ", " + prefix + td.getModelEditorName() + ">";
		}
		return res;
	}
	
	private void genGetOrEditFieldFunction(Instance self, ORMField f, boolean isAbstract) throws IOException {
		if ("Introduction".equals(f.getSlotName())) {
			Debug.trap();
		}

		boolean isEditableEntity = isEditableEntityType(f);
		if (HIDE_VOLTILE && !isEditableEntity && f.isOuterLink() && f.isVolatileData()) return;

		String type = isEditableEntity ? getEditableEntityType$(f) : getReturnType(f, self);
		ModelGen model = f.getModelOfColumnType();

		if (model != null && model.isAbstract() && f.isOuterRowSet()) {
			type = model.getBaseTableName() + ".RowSet";
		}

		String method = isEditableEntity ? "edit" + f.getSlotName() : f.getGetMethodName();

		String throws_ex = isEditableEntity && (f.isVolatileData() || (ENABLE_EDIT_FOREIGN_KEY && f.isForeignKey()))
				? " throws InvalidEntityReferenceException" : "";

		out.print("public ", isAbstract ? "abstract " : "final ", type, " ", method, "()",
				throws_ex);

		if (isAbstract) {
			out.print(";\n");
			if (isEditableEntity && f.isSingleEntity()) {
				if (!f.isOuterLink() || f.isVolatileData()) {
					out.print("public abstract ", f.getModelOfColumnType().getSimpleName(), " ", f.getGetMethodName(), "();\n");
				}

				boolean isNullableUniqueOutterLink = f.isOuterLink() && f.isUnique() && f.isNullable();
				if (isNullableUniqueOutterLink) {
					out.println("public abstract void remove", f.getSlotName(), "();\n");
				}
			}
			return;
		}

		out.println(" {");
		String snapshot_t = getDeclaredInternalSnapshot$(f);

		String fieldRef = "v";
		String getFieldValue;

		if (isEditableEntity) {
			genEditMethod(f, type, snapshot_t);
		}
		else {
			if (model != null && model.isAbstract() && f.isOuterRowSet()) {
				out.println(getBaseSnapshotName(), " org = this.getOriginalData();");
				out.println(model.getBaseTableName(), ".RowSet v = org == null ? null : org.getEntityReference().", f.getGetMethodName(), "();");
			} else if (f.isSingleEntity()) {
				String ormType = f.getModelOfColumnType().getSimpleName();
				getFieldValue = getGetFieldValueString(f, ormType);
				out.println(ormType, " v = ", getFieldValue, ";");
			} else if (EnumSet.class.isAssignableFrom(f.getBaseType())) {
				String t = type.substring(type.indexOf("Set<")); // 땜빵
				getFieldValue = getGetFieldValueString(f, t);
				out.println(t, " v = ", getFieldValue, ";");
			} else {
				getFieldValue = getGetFieldValueString(f, type);
				out.println(type, " v = ", getFieldValue, ";");
			}

			String result = "v";
			if (EnumSet.class.isAssignableFrom(f.getBaseType())) {
				Class<?> enumType = f.getFirstGenericParameterType();
				out.println("if (", fieldRef, " == null || ", fieldRef, " instanceof ImmutableCollection) {");
				out.println(fieldRef, " = StormUtils.toMutableEnumSet(", fieldRef, ", ", enumType.getCanonicalName(), ".class);");
				out.println("super.setFieldValue(#ENTITY_ORM#.", f.getSlotName(), ", v);");
				out.println("}");
				result = "(" + type + ")" + fieldRef;
			}
			else if (f.isCollection()) {
				out.println("if (", fieldRef, " == null || ", fieldRef, " instanceof ImmutableCollection) {");
				out.println(fieldRef, " = StormUtils.toMutable" , f.isSet() ? "Set" : "List", "(", fieldRef, ");");
				out.println("super.setFieldValue(#ENTITY_ORM#.", f.getSlotName(), ", v);");
				out.println("}");
				result = "(" + type + ")" + fieldRef;
			}
			else if (f.isMap()) {
				out.println("if (", fieldRef, " == null || ", fieldRef, " instanceof ImmutableMap) {");
				out.println(fieldRef, " = StormUtils.toMutableMap(", fieldRef, ");");
				out.println("super.setFieldValue(#ENTITY_ORM#.", f.getSlotName(), ", v);");
				out.println("}");
				result = "(" + type + ")" + fieldRef;
			} else if (f.isSingleEntity()) {
				result = "v == null ? null : (" + type + ")" + fieldRef + ".getEntityReference()";
			}

			out.println("return ", result, ";\n}\n");

			if (!f.isForeignKey()) {
				genEnumAccessHelper(f, "public final", true);
				return;
			}
			type = getEditableEntityType$(f);
		}


//		if (!f.isOuterLink() && !f.isForeignKey()) {
//			if (EDITOR_EXTENDS_FORM) {
//				out.println("public ", f.getModelOfColumnType()._ref, " ", f.getGetMethodName(), "() {");
//				out.println(snapshot_t, " d = this.edit;");
//				out.println("return d.", f.getGetMethodName(), "();");
//				out.println("}\n");
//			}
//			return;
//		}
//
//		if (EDITOR_EXTENDS_FORM && f.isUnique()) {
//
//		}

		
	}

	private void genEditorSetField(Instance instance, ORMField f, boolean isAbstract) throws IOException {
		String exception = f.isInternal() || f.isReadOnly() ? " throws RestrictedOperation" : "";

		if (f.isOuterLink() && (f.isOuterRowSet() || !f.isVolatileData())) {
			boolean isNullableUniqueOuterLink = f.isOuterLink() && f.isUnique() && f.isNullable();
			if (isNullableUniqueOuterLink && !f.isVolatileData() && !isAbstract) {
				String removeMethod = f.makeMethodName("remove");
				out.println("public final void ", removeMethod, "()", exception, " {");
//				if (!this.hasAlternateEditorField(f)) {
					out.println("this.", f.getKey(), " = null;");
					out.println("super.setFieldValue(#ENTITY_ORM#.", f.getSlotName(), ", ", f.getKey(), ");");
//				}
				out.println("}\n");
			}

			return;
		}


		if (f.isUnique() && (f.isForeignKey() || ENABLE_EDIT_FOREIGN_KEY)) {
			String type = f.getModelOfColumnType().getSimpleName();
			out.println("final void __set", f.getSlotName(), "(", type, " subEdit) {");
//			String self_;
//			if (hasAlternateEditorField(f)) {
//				self_ = "this.";
//			}
//			else {
//				self_ = "d.";
////				out.println(snapshot_t, " d = (", snapshot_t, ")this.edit;");
//			}
			String getFieldValue = getGetFieldValueString(f, type);
			out.println(type, " v = ", getFieldValue, ";");
			out.println("Debug.Assert(v == null || v.getEntityReference() == subEdit.getEntityReference());");
			out.println("super.setFieldValue(#ENTITY_ORM#.", f.getSlotName(), ", subEdit);");
			out.println("}\n");
			return;
		}

		String type = getFieldType(f, instance);
		String method = f.makeMethodName("set");

		if (f.isOuterLink() && !f.isVolatileData()) {
			type = f.getModelOfColumnType().getModelFormName();
		}

		if (isAbstract) {
			out.println("public abstract void ", method, "(", type, " v)", exception, ";\n");
			return;
		}
		out.println("public final void ", method, "(", type, " v)", exception, " {");

		if (EARLY_CHECK_NOT_NULL && !f.isNullable() && !f.getBaseType().isPrimitive()) {
			out.println("ensureNotNull(", f.getSlotName(), ", v);");
		}

		String ormField = "#ENTITY_ORM#." + f.getSlotName();
		if (this.hasAlternateEditorField(f)) {
			out.println("this.", f.getKey(), " = v;");
			out.println("super.setFieldValue(", ormField, ", v);");
		}
		else {
			out.println("super.setFieldValue(", ormField, ", v);");
		}
		out.println("}\n");
	}

	private void genEditMethod(ORMField f, String type, String snapshot_t) throws IOException {
		String fieldRef;
		String getFieldValue;
		String getOriginalEntity;

		if (f.isVolatileData() && f.isOuterLink()) {
			getOriginalEntity = "org.getEntityReference()." + f.getGetMethodName() + "()";
			if (!f.isUnique()) {
				getOriginalEntity += ".selectEntities()";
			}
		}
		else {
			getOriginalEntity = "org." + f.getGetMethodName() + "()";
		}


		/**
		 * OuterLink RowSet 의 경우에, editFoo() 함수를 생성.
		 * Unique + SnapshotJoin + Not-Null 이면, editFoo() 를 생성.
		 * Unique + SnapshotJoin + Nullable 이면, editFoo() 와 removeFoo 를 생성.
		 * Unique + VolatileJoin + Nullable 이면, editFoo() 와 get/setFoo 를 생성.
		 * ForeignKey 는 get/set 만 허용.
		 */

		fieldRef = genEditEntityField_internal(f, type, snapshot_t, getOriginalEntity);

//		out.println("checkUpdate(#ENTITY_ORM#.", f.getSlotName(), ", true);");
		out.println("}");

		out.println("return ", fieldRef, ";\n}\n");

		if (f.isEmbedded() || !f.isOuterLink() || !HIDE_VOLTILE) {
			String getMethod = f.getGetMethodName();
			String ret_t;
			ModelGen model = f.getModelOfColumnType();
			if (f.isOuterRowSet() && f.isVolatileData()) {
				ret_t = f.getModelOfColumnType().getBaseTableName() + ".RowSet";
			} else if (f.isOuterRowSet()) {
				ret_t = "List<" + f.getModelOfColumnType().getModelFormName() + ">";
			} else if (f.isEmbedded()) {
				ret_t = model.getSimpleName();
			} else if (f.isVolatileData()){
				ret_t = model.getModelName();
			} else {
				ret_t = model.getModelFormName();
			}

			out.println("public final ", ret_t, " ", getMethod, "() {");
			boolean isEditorField = this.hasAlternateEditorField(f);
			if (f.isVolatileData()) {
				out.println(getBaseSnapshotName(), " org = this.getOriginalData();");
				out.println(ret_t, " v = org != null ? org.getEntityReference().", f.getGetMethodName(), "()" +
								"\n\t\t : _TableBase.", getModelOfColumnType(f).dbTableName,
						".", getForeignKeyOfOuterEntity(f).getFindMethodName(f), "(this);");
				out.println("return v;");
			} else if (!f.isVolatileData() && f.isOuterRowSet()) {
				out.println("return this.edit", f.getSlotName(), "();");
			} else if (isEditorField) {
				out.println("return this.", f.getKey(), ";");
			} else  {
				getFieldValue = getGetFieldValueString(f, type);
				out.println(ret_t, " v = ", getFieldValue, ";");
				out.println("return v;");
			}
			out.println("}\n");

			if (f.isOuterRowSet() && f.isVolatileData()) {
				out.println("public final List<? extends ", f.getModelOfColumnType().getModelFormName(), "> peek", f.getFieldName(), "() {");
				out.println("return get", f.getFieldName(), "().loadEntities();");
				out.println("}\n");
			}
		}

	}

	private String genEditEntityField_internal(ORMField f, String type, String snapshot_t, String getOriginalEntity) throws IOException {
		String fieldRef = null;
		String getFieldValue;
		if (f.isOuterRowSet()) {
			fieldRef = "this." + f.getKey();
			out.println("if (", fieldRef, " == null) {");
			if (f.isVolatileData()) {
				out.println(fieldRef, " = new EditableEntities<>(this.get", f.getFieldName(), "());");
			} else {
				out.println(snapshot_t, " org = this.getOriginalData();");
				out.println("List<? extends ", getModelOfColumnType(f).getSimpleName(), "> items = org == null ? null : ", getOriginalEntity, ";");

				out.println(fieldRef, " = new EditableEntities<>(items);");

			}
			out.println("super.setFieldValue(#ENTITY_ORM#.", f.getSlotName(), ", ", fieldRef, ");");
			return fieldRef;
		}


		boolean isOuterLink = this.hasAlternateEditorField(f);

		String type_;
		if (f.isVolatileData()) {
			type_ = f.getModelOfColumnType().getSimpleName();
		} else {
			type_ = f.getModelOfColumnType().getModelFormName();
		}
		getFieldValue = getGetFieldValueString(f, type_);

		if (isOuterLink) {
			fieldRef = "this." + f.getKey();
			out.println(type_, " v = ", fieldRef, ";");
		}
		else {
			out.println(type_, " v = ", getFieldValue, ";");
		}

		out.println("if (EntityEditor.asEditor(v) == null) {");
		String baseEditor = f.getModelOfColumnType().getBaseEditorName();
		ORMField fk = getModelOfColumnType(f).findForeignKeyByColumnType(this.klass);
		String t = getReturnType(f, EditorSelf);

		if (f.isVolatileData()) {
			out.println(t, " sub = ", f.getGetMethodName(), "();");
		} else {
			out.println(snapshot_t, " org = this.getOriginalData();\n");
			out.println(t, " sub = org == null ? null : ", getOriginalEntity, ";");
		}

		out.println(baseEditor, " edit = _TableBase.", f.getModelOfColumnType().dbTableName,
				".edit(sub == null || sub.getEntityReference().isDeleted() ? null : sub);");

		if (f.isOuterLink()) {
			/**
			 * ForeignKey 설정.
			 */
			out.println("edit.__set", fk.getSlotName(), "(this);");
		}

		if (isOuterLink) {
			out.println(fieldRef + " = edit;");
			out.println("super.setFieldValue(#ENTITY_ORM#.", f.getSlotName(), ", edit);");
			fieldRef = "(" + type + ")" + fieldRef;
		} else {
			if (f.isEmbedded()) {
				out.println("v = edit;");
			}
			out.println("super.setFieldValue(#ENTITY_ORM#.", f.getSlotName(), ", v);");
			fieldRef = "(" + type + ") v";
		}
		return fieldRef;
	}

	private void genGetMethodOfEditor(ORMField f) {
		// TODO Auto-generated method stub
		
	}

	private void genEnumAccessHelper(ORMField f, String methodType, boolean isEditable) throws IOException {
		if (!EnumSet.class.isAssignableFrom(f.getBaseType())) return;

		String method = f.getGetMethodName();
		Class<?> enumType = f.getFirstGenericParameterType();
		Enum<?>[] enums= (Enum<?>[])enumType.getEnumConstants();
		for (Enum<?> e : enums) {
			String enumName = getShortClassName(enumType) + "." + e.name();
			out.println(methodType, " boolean ", method, "_", e.name(), "() {");
			out.println("return ", method, "().contains(", enumName, ");");
			out.println("}\n");
			if (!isEditable) continue;

			out.println(methodType, " void set", f.getFieldName(), "_", e.name(), "(boolean on) {");
			String type = getReturnType(f, isEditable ? EditorSelf : SnapshotSelf);

			out.println(type, " v = ", method, "();");
			out.println("if (on) {");
			out.println("v.add(", enumName, ");");
			out.println("} else {");
			out.println("v.remove(", enumName, ");");
			out.println("}");

			String fd = getDeclaredInternalSnapshot$(f);
//			out.println(fd, " d = this.getOriginalData();");
//			out.println("if (d != null) checkUpdate(#ENTITY_ORM#.", f.getSlotName(), ", v, d);");
					//, f.getBaseType().isPrimitive() ? " != " : ", d.", method, "());");
			out.println("}\n");
		}
	}

	static HashMap<Class, Class> fieldTypes = new HashMap<>();

	private void genGetRefField(ORMField f) throws IOException {
		String type = getReturnType(f, SnapshotSelf);

		String method = f.getGetMethodName();
		out.println("public final ", type, " ", method, "() {");

		out.println("return loadSnapshot().", method, "();");

		out.println("}\n");

		if (EnumSet.class.isAssignableFrom(f.getBaseType())) {
			this.genEnumAccessHelper(f, "public final", false);
		}

	}

	private void genGetField(ORMField f, boolean isAbstract) throws IOException {

		String type = getReturnType(f, SnapshotSelf);

		String method = f.getGetMethodName();
		out.print("public ", isAbstract ? "abstract " : (f.canOverride() ? "" : "final "), type, " ", method, "()");
		if (isAbstract) {
			out.println(";");
			return;
		}

		out.println(" {");

		String fieldRef = this.getCastedSnapshotField$(f, SnapshotSelf.toString());// + "." + f.getKey();
		if (f.isDBColumn()) { 
			if (f.isCollection()) {
				if (!type.startsWith("Immutable")) {
				 	Debug.trap();
				}
				fieldRef = "(" + type + ")" + fieldRef;
			}
			else if (f.isEntity()) {
				if (ALWAYS_LOAD_FOREIGN_KEY_REFERENCE || f.isVolatileData()) {
					fieldRef = "(" + type + ")(" + fieldRef + " == null ? null : " + fieldRef + ".getEntityReference()"
							+ (f.isEmbedded() ? ".tryLoadSnapshot()" : "") + ")";
				}
				else {
					fieldRef = "(" + type + ")" + fieldRef;
				}
			}
			else {
				Class<?> tt = f.getBaseType();
				if (!tt.isPrimitive() && !tt.isEnum() && tt != String.class) {
					if (fieldTypes.get(tt) == null) {
						fieldTypes.put(tt, tt);
						Debug.trap();
					}
				}
			}
			out.println("return ", fieldRef, ";");
		}
		else if (f.isOuterLink()) {
			if (f.isVolatileData()) {
				out.println("#REF# ref = this.getEntityReference();");
				out.print("return (ref == null) ? null : ref.", f.getGetMethodName(), "()");
				out.printIf(f.isOuterRowSet(), ".selectEntities()").println(";");
			}
			else {
				out.println("if (", fieldRef, " == null) /***/{");
				if (f.isOuterRowSet()) {
					out.println(fieldRef, " = getEntityReference().", f.getGetMethodName(), "().",
							f.isUnique() ? "loadSnapshot()" : "loadEntities()", ";");
					out.println("}");
				}
				else {
					String refType = f.getModelOfColumnType()._ref;
					out.println(refType, " ref = getEntityReference().", f.getGetMethodName(), "();");
					if (f.isNullable()) {
						out.println("if (ref != null) {");
					}
					out.println(fieldRef, " = ref.", 
							f.isUnique() ? "loadSnapshot()" : "loadEntities()", ";");
					out.println("}");
					if (f.isNullable()) {
						out.println("}");
					}				
				}
				out.print("return ").printIf(f.isUnique(), "(" + type + ")").println( 
						fieldRef, ";");
			}
		}
		else {
			out.println("return (", type, ")", fieldRef, ";");
		}
		out.println("}\n");

		if (f.isOuterRowSet() && f.isVolatileData()) {
			ModelGen model = f.getModelOfColumnType();
			out.println("public final ImmutableList<", model.modelName + MODEL_DATA_SUFFIX, "> peek", f.getFieldName(),
					"() {");
			out.println(modelName, " ref = this.getEntityReference();");
			out.println("return ref == null ? null : ref.", f.getGetMethodName(), "().loadEntities();");
			out.println("}\n");
		}

		if (f.isEmbedded()) {
			String field = SnapshotSelf + "." + f.getKey();
			out.println("public final EntityReference ", method, "_Ref() {");
			out.println("return ", field, " == null ? null : " + field + ".getEntityReference();");
			out.println("}\n");
		}
		
		if (EnumSet.class.isAssignableFrom(f.getBaseType())) {
			this.genEnumAccessHelper(f, "public final", isAbstract);
		}
		else if (f.isOuterLink() && !f.isVolatileData() && f.isUnique() && !f.isNullable()) {
			String prefix = method + '_';
			for (ORMField f2 : f.getModelOfColumnType().allProperties) {
				if (f2.isForeignKey() && f2.getModelOfColumnType() == this) continue;
				
				String m2 = f2.makeMethodName(prefix);
				type = getReturnType(f2, SnapshotSelf);
				out.print("public ", isAbstract ? "abstract " : "final ", type, " ", m2, "()");
				if (isAbstract) {
					out.println(";");
				}
				else {
					out.println(" {");
					out.println("return ", method, "().", f2.getGetMethodName(), "();");
					out.println("}\n");
				}
			}
		}
		
		return;
	}

	
	private ModelGen findModel(Class<?> c) {
		if (c == StormRowSet.class || c == ORMEntity.class) {
			return this;
		}
		ModelGen td = _ormGen.getModel(c);
		return td;
	}

	private OuterLink getOuterLinkOfForeignKey(ORMField c) {
		Class<?> type = c.getBaseType();
		ModelGen td = findModel(type);
		if (td == null) {
			throw new RuntimeException("Invalid join: " + this.getSimpleName() + "." + c.getSlotName()
			+ " with " + type.getSimpleName());
		}
		ORMField subRef = null;
		for (ORMField p : td.allProperties) {
			if (p.isOuterLink() && p.getBaseType().isAssignableFrom(this.klass)) {
				subRef = p;
				break;
			}
		}
		if (subRef == null) {
			throw new RuntimeException("Invalid join: The OuterLink of " + this.getSimpleName() + "." + c.getSlotName() + " is not found in " + type.getSimpleName());
		}
		return (OuterLink)subRef;
	}

	final ORMField getForeignKeyOfOuterEntity(ORMField c) {
		Class<?> type = c.getBaseType();
		ModelGen td = findModel(type);
		if (td == null) {
			throw new RuntimeException("Invalid join: " + this.getSimpleName() + "." + c.getSlotName()
			+ " with " + type.getSimpleName());
		}
		ORMField foreignRef = null;
		for (ORMField p : td.allProperties) {
			if (p.isForeignKey() && p.getBaseType().isAssignableFrom(this.klass)) {
				foreignRef = p;
				break;
			}
		}
		if (foreignRef == null) {
			throw new RuntimeException("Invalid join: " + this.getSimpleName() + "." + c.getSlotName()
			+ " with " + type.getSimpleName());
		}
		return (ForeignKey)foreignRef;
	}

	String getSimpleGenericDeclation(ORMField f) {
		return getSimpleGenericDeclation(f.getBaseType(), f.getGenericParameters(), false);
	}

	String getSimpleGenericDeclation(Class<?> type, Class<?>[] paramTypes, boolean isEditable) {
		String name;
		// TODO Enumset 도 Collection 처리.
		if (type.isArray() || (Iterable.class.isAssignableFrom(type) && (!isEditable || !EnumSet.class.isAssignableFrom(type)))) {
			boolean isSet = Set.class.isAssignableFrom(type);
			type = type.isArray() ? type.getComponentType() : paramTypes[0];
			name = getShortClassName(type);
			if (type.isPrimitive()) {
				name = type + "[]";
			}
			else if (isEditable) {
				name = (isSet ? "Set<" : "List<") + name + ">";
			}
			else {
				name = (isSet ? "ImmutableSet<" : "ImmutableList<") + name + ">";
			}
		}
		else {
			name = getShortClassName(type);
			if (paramTypes != null) {
				StringBuilder sb = new StringBuilder(name);
				sb.append('<');

				for (Class<?> paramType : paramTypes) {
					String p = getShortClassName(paramType);
					sb.append(p);

					if (ORMEntity.class.isAssignableFrom(paramType)) {
						if (!EnumSet.class.isAssignableFrom(type)){
							sb.append("? extends ");
						}
						sb.append(p);
					}
					sb.append(", ");
				}
				sb.setLength(sb.length() - 2);
				sb.append('>');
				name = sb.toString();
			}
		}
		return name;
	}

	private String getReturnType(ORMField f, Instance instance) {
		return getSimpleGenericType$(f.getBaseType(), f.getGenericParameters(), instance.isEditable, f, false);
	}

	private String getFieldType(ORMField f, Instance instance) {
		if (f.isCollection()) {
			Class<?> type = f.getBaseType();
			Class<?> ct = type.isArray() ? type.getComponentType() : f.getFirstGenericParameterType();
			if (EnumSet.class.isAssignableFrom(type)){
				return "Set<" + getShortClassName(ct) + ">";
			} else if (ct.getComponentType() != null && ct.getComponentType().isPrimitive()) {
				return "Collection<" + getShortClassName(ct) + ">";
			}
			return "Collection<? extends " + getShortClassName(ct) + ">";
		}
		return getSimpleGenericType$(f.getBaseType(), f.getGenericParameters(), instance.isEditable, f, true);
	}

	final String getSimpleGenericType$(Class<?> fieldType, Class<?>[] genericParams, boolean isEditable, ORMField f, boolean isField) {
		boolean isEntity = f != null ? f.isSingleEntity() : ORMEntity.class.isAssignableFrom(fieldType);
		boolean isEntities = f != null ? f.isOuterRowSet() : StormRowSet.class.isAssignableFrom(fieldType);

		if (!isEntity && !isEntities) {
			return getSimpleGenericDeclation(fieldType, genericParams, isEditable);
		}

		Class<?> t = genericParams == null ? fieldType : genericParams[0];

		ModelGen td = findModel(t);
		if (td == null) {
			return t.getSimpleName();
		}
		String type;
		if (isEntity) {
			if (!isField) {
				if (!EntityReference.class.isAssignableFrom(fieldType) && (f == null || shouldLoadSnapshot(f, false))) {
					type = td._data;
				}
				else { 
					type = td._ref;
				}
			}
			else {
				type = td.getSimpleName();
			}
		}
		else {
			if (f != null) {// && shouldLoadSnapshot(f, false)) {
				String prefix = td.isAbstract() ? "? extends " : "";
				if (shouldLoadSnapshot(f, false)) {
					type = "ImmutableList<" + prefix + td._data + ">";
				}
				else {
					type = "ImmutableList<" + prefix + td._ref + ">";
				}
			}
			else {
				type = td.getBaseTableName() + ".RowSet";
			}
		}
		return type;
	}


	private String getSetMethodName(String type, String name) {
		if ("boolean".equals(type) || "Boolean".equals(type)) {
			if (name.startsWith("is") && !Character.isLowerCase(name.charAt(2))) {
				name = name.substring(2);
			}
		}
		return "set" + captalizeName(name);
	}

	private String captalizeName(String name) {
		while (name.charAt(0) == '_') {
			name = name.substring(1);
		}
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private void genEWSSerializer() throws Exception {

		ORMField[] fields = this.getDeclaredDBFields();
		out.println("/*\npublic static void importEWS(ewsObj) {");
		out.println("#BASE_EDITOR# dm = #BASE_EDITOR#.editEntity();");
		for (ORMField f : fields) {
			String name = captalizeName(f.getSlotName());
			out.println("dm.set", name, "(ewsObj.get", name, "());");
		}
		out.println("}*/\n");

		out.println("/*npublic void export(ewsObj) {");
		for (ORMField f : fields) {
			String name = captalizeName(f.getSlotName());
			out.println("ewsObj.set", name, "(this.", f.getSlotName(), ");");
		}
		out.println("}\n*/\n");
	}	

	void getImports(HashMap<String, String> imports, boolean isStorm) throws InstantiationException, IllegalAccessException {
		ModelGen super_ = this.getSuperORM();
		if (super_ != null) {
			super_.getImports(imports, isStorm);
		}
		ORMField[] fields = this.getDeclaredDBFields();
		for (ORMField f : fields) {
			
			if (f.isCollection() || f.isOuterRowSet()) {
				imports.put(List.class.getCanonicalName(), List.class.getCanonicalName());
			}
			
			if (f.isForeignKey() || f.isUnique()) {
				//imports.put("java.util.*", "java.util.*");
				//imports.put("java.lang.ref.*", "java.lang.ref.*");
			}

			if (f.isEntity()) {
				if (f.isOuterLink() && !f.isVolatileData() && f.isUnique() && !f.isNullable()) {
					f.getModelOfColumnType().getImports(imports, isStorm);
				}
				continue;
			}


			Class<?> type = f.getBaseType();
			while (type.isArray()) {
				type = type.getComponentType();
			}

			String cp = getEnclosingClassName(type);
			if (cp.indexOf('.') > 0) {
				if (!"java.util".equals(cp.substring(0, cp.lastIndexOf('.')))) {
					imports.put(cp, cp);
				}
			}
			Class<?>[] paramTypes = f.getGenericParameters();
			if (paramTypes != null) {
				for (Class cc : paramTypes) {
					cp = getEnclosingClassName(cc);
					if (cp.indexOf('.') > 0) {
						if (!"java.util".equals(cp.substring(0, cp.lastIndexOf('.')))) {
							imports.put(cc.getCanonicalName(), cp);
						}
					}
				}
			}
		}
	}


	static String getEnclosingClassName(Class<?> type) {
		while (true) {
			Class<?> c = type.getEnclosingClass();
			if (c == null) break;
			type = c;
		}
		return type.getCanonicalName();
	}

	static String getShortClassName(Class<?> type) {
		String name = type.getCanonicalName();
		while (true) {
			Class<?> c = type.getEnclosingClass();
			if (c == null) break;
			type = c;
		}
		String name2 = type.getCanonicalName();
		name = name.substring(name2.lastIndexOf('.') + 1);
		return name;
	}

	
	private ForeignKey findForeignKeyByColumnType(Class<?> joinPrimary) {
		for (ORMField p : this.allProperties) {
			if (p.isForeignKey()) {
				if (p.getBaseType().isAssignableFrom(joinPrimary)) {
					return (ForeignKey)p;
				}
			}
		}
		return null;
	}

	static class OuterLinkResolver implements QueryMethod.EntityResolver {
		public boolean isJoiendQuery;
		static ORMColumn _id = new ORMColumn("rowid", 0, long.class);
		
		@Override
		public ORMField[] getORMProperties(String subTable) {
			ModelGen ctx = _ormGen.getModelByTableName(subTable);
			return ctx.allProperties;
		}

		@Override
		public SortableColumn[] getDefaultOrderBy() {
			return new SortableColumn[]{_id.createSortableColumn(null, false)};
		}
	}
	
//	void dumpDefinition() throws IOException {
//		out.println("////  ", this.getSimpleName());
//		out.println();
//
//		StringBuilder sb = new StringBuilder();			
//		for (ORMSlot p : this.declaredColumns) {
//			sb.setLength(0);
//			p.getConfig(sb);
//			out.print("ORMSlot ", p.getSlotName(), " = new ", sb);
//			if (p.getEWSVersion() != 0) {
//				out.print(".addAttribute(\"ewsVer\", ", p.getEWSVersion(), ")");
//			}
//			out.println(";\n");
//		}
//
//	}


	String getFindStatementName(ORMField[] parts) {
		return makeMethodNameByParamTypes("findBy", parts);
	}

	String getEditMethodName(ORMField[] parts) {
		return makeMethodNameByParamTypes("edit_with", parts);
	}

	String makeMethodNameByParamTypes(String prefix, ORMField[] parts) {
		StringBuilder sb = new StringBuilder();
		sb.append(prefix);
		for (ORMField p : parts) {
			sb.append(p.getSlotName());
			sb.append("And");
		}
		sb.setLength(sb.length() - 3);
		return sb.toString();
	}

	final boolean shouldLoadSnapshot(ORMField pr, boolean isEditable) {
		if (!pr.isEntity()) {
			return false;
		}
		
		if (pr.isForeignKey()) {
			return !ALWAYS_LOAD_FOREIGN_KEY_REFERENCE && !pr.isVolatileData();
		}

		return (pr.isOuterLink() && !pr.isVolatileData()) || pr.isEmbedded();
	}

	public String toString() {
		return this.baseName;
	}

}