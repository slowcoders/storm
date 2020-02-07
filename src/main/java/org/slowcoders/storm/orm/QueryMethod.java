package org.slowcoders.storm.orm;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;


import org.slowcoders.storm.*;
import org.slowcoders.storm.jdbc.JDBCStatement;
import org.slowcoders.util.ClassUtils;
import org.slowcoders.util.SourceWriter;

public class QueryMethod {
	//Method m;
	ORMField joinedColumn;
	String sql;
	String searchParams[];
	String methodName;
	String returnType$;
	boolean isRowSet;
	
	boolean isJoinedStatement;
	ArrayList<String> paramTN = new ArrayList<>();
	boolean isVolatile;

	public interface EntityResolver {

		ORMField[] getORMProperties(String subTable);

		SortableColumn[] getDefaultOrderBy();
	}

	public static QueryMethod makeQuery(ModelGen model, Method m) {
		Where where = m.getAnnotation(Where.class);
		if (where == null) {
			return null;
		}
		String sql = where.value().trim();
		if (sql.length() == 0) {
			return null;
		}

		try {
			QueryMethod query = new QueryMethod(sql, model);

			ModelGen.OuterLinkResolver er = new ModelGen.OuterLinkResolver();
			JDBCStatement.DebugUtil.validateQuery(query.sql, model.getAllDBFields(), er);

			query.setMethod(m, model);
			return query;
		}
		catch (Exception e) {
			throw new RuntimeException("wrong sql " +model.getSimpleName()+ ": " + sql, e);
		}

	}

	private QueryMethod(String query, ModelGen model) {
		int lb, rb = 0;
		StringBuilder sb = new StringBuilder("WHERE ");
		sb.append(query);
		ArrayList<String> params = new ArrayList<>();
		for (; ; ) {
			lb = sb.indexOf("{", rb);
			if (lb < 0) {
				break;
			}
			rb = sb.indexOf("}", lb);
			if (rb < 0) {
				break;
			}
			String param = sb.substring(lb + 1, rb);
			params.add(param);
			sb.replace(lb, rb + 1, "?");
			rb = lb + 1;
		}
		this.searchParams = params.toArray(new String[params.size()]);

		this.resolveJoin(sb, model);
		this.sql = sb.toString();
	}

	private void resolveJoin(StringBuilder sbWhere, ModelGen model) {

		StringBuilder sbJoin = new StringBuilder();
		HashMap<String, String> joinedTable = new HashMap<>();
		int lb = 0, rb = 0;
		for (;;) {
			lb = sbWhere.indexOf("@", rb);
			if (lb < 0) break;

			rb = sbWhere.indexOf(".", lb);
			if (rb < 0) break;

			String joinedColumnName = sbWhere.substring(lb+1, rb);
			ORMField subEntity = ORMField.getFieldByName(joinedColumnName, model.getAllDBFields());
			if (subEntity == null || !subEntity.isSingleEntity() || subEntity.isDBColumn()) {
				throw new RuntimeException(joinedColumnName + " is not joined column");
			}
			ModelGen ctx = model.getModelOfColumnType(subEntity);
			ORMField fk = model.getForeignKeyOfOuterEntity(subEntity);
			String tableName = ctx.dbTableName;

			sbWhere.replace(lb, rb, tableName);

			if (joinedTable.get(tableName) == null) {
				joinedTable.put(tableName,  tableName);

				sbJoin.append("LEFT OUTER JOIN ").append(tableName).append(" ON ")
						.append(model.dbTableName).append(".rowId = ")
						.append(tableName).append('.').append(fk.getKey()).append(' ');
			}
		}

		if (sbJoin.length() > 0) {
			sbWhere.insert(0, sbJoin);
		}

	}

	private void setMethod(Method m, ModelGen baseContext) {
		//this.m = m;
		Type grt = ClassUtils.getFirstGenericParameter(m.getGenericReturnType());
		Class<?> gt = (grt != null) ? ClassUtils.toClass(grt) : (Class)m.getGenericReturnType();

		this.isVolatile = (gt == EntityReference.class);
		if (this.isVolatile || gt == EntitySnapshot.class
		||  gt.isAssignableFrom(baseContext.klass)) {
			gt = baseContext.klass;
		}
		
		String ret_t = baseContext.getSimpleGenericType$(m.getReturnType(),
				new Class<?>[] { gt }, false, null, false);
		
		this.methodName = m.getName();
		this.returnType$ = ret_t;
		this.isRowSet = m.getReturnType().isAssignableFrom(StormRowSet.class);
	
		/**
		 * 	Caution!) only when compile option
		 * 	is set to store method parameters,
		 * 	we can appropriately
		 * 	retrieve parameter names through reflection
		 * 	(applicable only in Java8)
		 *
		 *  */
		for (Parameter p : m.getParameters()) {
			Class<?> type = p.getType();
			String type$;
			if (ORMEntity.class.isAssignableFrom(type)) {
				type$ = ModelGen._ormGen.getModel(type)._ref;
			}
			else {
				type$ = type.getCanonicalName();
			}
			paramTN.add(type$);
			paramTN.add(p.getName());
		}
		
	}

	public void printParams(SourceWriter out, boolean withType) throws IOException {
		if (paramTN.size() == 0) {
			return;
		}
		for (int i = 0; i < paramTN.size(); ) {
			 String type = this.paramTN.get(i++);
			 String name = this.paramTN.get(i++);
			 if (withType) {
				 out.print(type).print(" ");
			 }
			 out.print(name).print(", ");
		}
		out.removeTail(2);
	}

}