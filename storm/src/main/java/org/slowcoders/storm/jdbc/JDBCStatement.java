package org.slowcoders.storm.jdbc;

import com.google.common.collect.ImmutableList;
import org.slowcoders.io.serialize.IOAdapter;
import org.slowcoders.io.serialize.IOAdapters;
import org.slowcoders.io.serialize.IOEntity;
import org.slowcoders.storm.orm.ForeignKey;
import org.slowcoders.util.Debug;
import org.slowcoders.storm.*;
import org.slowcoders.storm.orm.ORMField;
import org.slowcoders.storm.orm.QueryMethod;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class JDBCStatement extends StormQuery {

    private JDBCTable table;
    private String query$;
    private SortableColumn[] orderBy;
    private IOAdapter[] varConverters;
    private boolean canResolveNextEntity;


    private static class LocalStatement {
        ResultSet rs;
        PreparedStatement stmt;
        PreparedStatement searchNext;
    }

    private static ThreadLocal<long[]> localIdBuff = new ThreadLocal<long[]>() {
        protected long[] initialValue() {
            return new long[100_000];
        }
    };

    private static final IOAdapter stringAdapter = new IOAdapters._String<String>() {
        @Override
        public String decode(String v, boolean isImmutable) {
            return v;
        }

        @Override
        public String encode(String v) {
            return v;
        }
    };

    JDBCStatement(JDBCTable table, String sql, SortableColumn[] orderBy, boolean isJoinedQuery) {
        this(table, sql, getIOAdapters(table.getORMFields(), sql, table));
        if (orderBy == null || orderBy.length == 0) {
            this.orderBy = table.getDefaultOrderBy();
        } else {
            this.orderBy = orderBy;
        }
        this.query$ = getSortedQuery(this.orderBy);
    }

    JDBCStatement(JDBCTable table, String sql, IOAdapter[] adapters) {
        if (adapters != null && adapters.length == 0) {
            adapters = null;
        }
        this.table = table;
        this.varConverters = adapters;
        this.query$ = sql;
        String upperCase = sql.toUpperCase();
        this.canResolveNextEntity = upperCase.indexOf("GROUP BY") < 0 && upperCase.indexOf("ORDER BY") < 0;

        if (Debug.DEBUG) {
            try {
                // for Testing;
                this.prepareStatment(sql);
            } catch (Exception e) {
                throw Debug.wtf(e);
            }
        }
    }

    protected StormQuery sortBy(SortableColumn[] orderBy) {
        if (orderBy == null || orderBy.length == 0) {
            return this;
        }
        String sql = getBaseQuery();
        JDBCStatement query = new JDBCStatement(table, sql, orderBy, false);
        return query;
    }

    private String getSortedQuery(SortableColumn[] orderBy) {
        boolean isJoined = this.isJoinedQuery();
        boolean hasNullable = false;
        check_join:
        if (!isJoined) {
            for (SortableColumn order : orderBy) {
                isJoined = table != order.getDeclaredTable();
                if (order.getColumn().isNullable()) {
                    hasNullable = true;
                }
                if (isJoined) {
                    if (order.getJoinedOuterLink().isNullable()) {
                        hasNullable = true;
                    }
                    break check_join;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        String sql = this.getBaseQuery();
        sb.append(sql);

        if (hasNullable) {
            this.canResolveNextEntity = false;
        }

        if (isJoined) {
            int where_p = getWherePos(sql);
            String whereStr = sb.substring(where_p);
            sb.setLength(where_p);

            for (SortableColumn col : orderBy) {
                if (!col.isJoined()) {
                    continue;
                }
                StormTable<?, ?, ?> joinedTable = col.getDeclaredTable();
                if (sql.indexOf(joinedTable.getTableName()) > 0) {
                    continue;
                }
                sb.append(" LEFT OUTER JOIN ").append(joinedTable.getTableName()).append(" ON ");
                sb.append(table.getTableName()).append(".rowid");
                sb.append(" == ").append(joinedTable.getTableName()).append(".").append(col.getJoinedForeignKey().getKey());
            }
            sb.append(whereStr);
        }
        sb.append('\n');

        sb.append("ORDER BY ");
        for (SortableColumn c : orderBy) {
            sb.append(c.getColumnName());
            if (c.isDescentOrder()) {
                sb.append(" DESC");
            }
            sb.append(", ");
        }
        sb.setLength(sb.length() - 2);
        sb.append('\n');
        return sb.toString();
    }


    private String getBaseQuery() {
        String sql = this.query$;
        int order_by = sql.toLowerCase().indexOf("order by");
        if (order_by > 0) {
            sql = sql.substring(0, order_by - 1);
        }
        return sql;
    }

    private int getWherePos(String sql) {
        int i = sql.indexOf("\nWHERE ");
        if (i < 0) {
            i = sql.indexOf(" WHERE ");
            if (i < 0) {
                i = sql.length();
            }
        }
        return i;
    }

    public StormTable<? extends EntitySnapshot, ?, ?> getTable() {
        return this.table;
    }

    final PreparedStatement getStatement(Object... values) throws SQLException {
        Debug.Assert(!this.query$.toLowerCase().startsWith("select"));
        return this.getSearchStatement(values).stmt;
    }

    public int forEach(EntityVisitor visitor, Object... serarchParams) throws SQLException {
        JDBCDatabase db = this.table.getDatabase();
        db.beginReadTransaction();
        ResultSet rs = null;
        try {
            rs = this.executeQuery(serarchParams);
            int cnt = 0;
            while (rs.next()) {
                cnt++;
                long id = rs.getLong(JDBCTable.IDX_ID);
                if (!visitor.visit(StormTable.UnsafeTools.getEntityReference(this.table, id))) {
                    break;
                }
            }
            return cnt;
        } finally {
            JDBCUtils.close(rs);
            db.endReadTransaction();
        }
    }

    public final long[] find(Object... values) throws SQLException {
        int cntEntity = this.selectInternal(values);
        long[] idBuff = localIdBuff.get();

        long[] ids = new long[cntEntity];
        System.arraycopy(idBuff, 0, ids, 0, cntEntity);
        return ids;

    }

    public final EntityReference[] search(Object... values) throws SQLException {
        int cntEntity = this.selectInternal(values);
        long[] idBuff = localIdBuff.get();

        EntityReference[] refs = this.table.makeEntityReferenceList(idBuff, cntEntity);
        return refs;
    }


    private final int selectInternal(Object[] values) throws SQLException {
        JDBCDatabase db = this.table.getDatabase();
        db.beginReadTransaction();

        ResultSet rs = null;
        try {
            rs = this.executeQuery(values);
            long[] idBuff = localIdBuff.get();
            int buf_len = idBuff.length;
            int count = 0;
            while (rs.next()) {
                if (count >= buf_len) {
                    buf_len = idBuff.length + idBuff.length / 2;
                    long[] buf = new long[buf_len];
                    System.arraycopy(idBuff, 0, buf, 0, idBuff.length);
                    idBuff = buf;
                    localIdBuff.set(idBuff);
                }
                idBuff[count++] = rs.getLong(JDBCTable.IDX_ID);
            }

            return count;
        } finally {
            JDBCUtils.close(rs);
            db.endReadTransaction();
        }

    }

    public final ImmutableList<EntitySnapshot> loadEntities(Object... values) throws SQLException {
        JDBCDatabase db = this.table.getDatabase();
        db.beginReadTransaction();

        ResultSet rs = null;
        try {
            rs = this.executeQuery(values);
            ImmutableList.Builder list = new ImmutableList.Builder();
            while (rs.next()) {
                long id = rs.getLong(JDBCTable.IDX_ID);
                EntityReference ref = StormTable.UnsafeTools.getEntityReference(table, id);
                list.add(ref.loadSnapshot());
            }
            return list.build();
        } finally {
            JDBCUtils.close(rs);
            db.endReadTransaction();
        }
    }

    final ResultSet executeQuery(Object... values) throws SQLException {
        Debug.Assert(varConverters == null || values.length == varConverters.length);
        table.getDatabase().getConnection();
        LocalStatement stmt = this.getSearchStatement(values);

        stmt.rs = stmt.stmt.executeQuery();
        return stmt.rs;
    }

    public boolean execute(Object... values) throws SQLException {
        table.getDatabase().getConnection();
        return this.getSearchStatement(values).stmt.execute();
    }

    public int executeUpdate(Object... values) throws SQLException {
        table.getDatabase().getConnection();
        return this.getSearchStatement(values).stmt.executeUpdate();
    }

    @Override
    protected boolean canResolveNextEntity() {
        return canResolveNextEntity;
    }

    @Override
    protected int getIndexOfNextEntity(EntitySnapshot entity, Object[] values, EntityReference[] entities, int cntEntity) {
        Debug.Assert(entity != null);
        ResultSet rs = null;
        try {
            PreparedStatement stmt = makeSearchNextStatement();

            table.getDatabase().beginReadTransaction();
            if (entity.isDeleted()) {
                return -1;
            }
            rs = searchNext(stmt, entity, values);
            if (!rs.next()) {
                return -1;
            }

            long rowId = rs.getLong(JDBCTable.IDX_ID);
            if (rowId != entity.getEntityId()) {
                if (!isQueryResultContains(entity, values)) {
                    return -1;
                }
                while (true) {
                    if (!rs.next()) {
                        return -1;
                    }
                    rowId = rs.getLong(JDBCTable.IDX_ID);
                    if (rowId == entity.getEntityId()) {
                        break;
                    }
                }
            }

            while (rs.next()) {
                rowId = rs.getLong(JDBCTable.IDX_ID);
                for (int i = cntEntity; --i >= 0; ) {
                    if (entities[i].getEntityId() == rowId) {
                        return i;
                    }
                }
            }
            return cntEntity;
        } catch (Exception e) {
            Debug.wtf(e);
        } finally {
            JDBCUtils.close(rs);
            table.getDatabase().endReadTransaction();
        }
        return -1;
    }

    private boolean isQueryResultContains(EntitySnapshot entity, Object[] values) throws SQLException {
        String sql = makeSearchNextSql();

        int pos = sql.indexOf("WHERE") + 5;
        String str = sql.substring(pos);

        sql = sql.substring(0, pos);
        sql += " " + entity.getTable().getTableName() + ".rowid == " + entity.getEntityId() + " AND ";
        sql += str;

        ResultSet rs = null;
        try {
            rs = searchNext(prepareStatment(sql), entity, values);
            return rs.next();
        } finally {
            JDBCUtils.close(rs);
        }
    }

    public long getNextRowId(ORMEntity entity, Object[] values) {
        EntitySnapshot dm = entity.getEntityReference().tryLoadSnapshot();
        Debug.Assert(dm != null);
        long nextRow = -1;
        ResultSet rs = null;
        try {
            PreparedStatement stmt = makeSearchNextStatement();
            rs = searchNext(stmt, dm, values);
            while (rs.next()) {
                if (rs.getLong(JDBCTable.IDX_ID) == dm.getEntityId()) {
                    nextRow = rs.next() ? rs.getLong(JDBCTable.IDX_ID) : 0;
                    rs.close();
                    break;
                }
                int idxCol = JDBCTable.IDX_ID;
                for (SortableColumn col : this.orderBy) {
                    ORMField p = col.getColumn();
                    Object v;
                    if (col.isJoined()) {
                        EntitySnapshot s = resolveJoinedEntity(dm, col);
                        v = IOEntity.getProperty(s, p);
                    } else {
                        v = IOEntity.getProperty(dm, p);
                    }
                    Object v2 = rs.getObject(++idxCol);
                    if (v != null) {
                        if (!v.equals(v2)) {
                            /**
                             * 	Caution) rs.getObject() randomly returns type of rowid.
                             * 	sometimes it is integer and sometimes it is long...
                             * 	it always returns false when comparing integer to long ㅡ,.ㅡ
                             */
                            if (v instanceof Number && v2 instanceof Number) {
                                Number n1 = (Number) v;
                                Number n2 = (Number) v2;
                                if (n1 instanceof Float || n1 instanceof Double) {
                                    if (n1.doubleValue() == n2.doubleValue()) {
                                        continue;
                                    }
                                } else if (n1.longValue() == n2.longValue()) {
                                    continue;
                                }
                            }
                            return nextRow;
                        }
                    } else if (v2 != null) {
                        return nextRow;
                    }
                }
            }
        } catch (Exception e) {
            Debug.wtf(e);
        } finally {
            JDBCUtils.close(rs);
        }
        return nextRow;
    }

    ///////////////////////////////////////////////////////////

    private PreparedStatement prepareStatment(String sql) throws SQLException {
        return table.getDatabase().getConnection().prepareStatement(sql);
    }

    private LocalStatement getLocalStatement() {
        JDBCDatabase.LocalConnection localConn = table.getDatabase().getLocalConnection();
        LocalStatement localStmt = (LocalStatement) localConn.getProperty(this);
        if (localStmt == null) {
            localStmt = new LocalStatement();
            localConn.setProperty(this, localStmt);
        }
        return localStmt;
    }

    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    private LocalStatement getSearchStatement(Object... values) throws SQLException {
        DebugUtil.execStatementCount++;

        LocalStatement localStmt = getLocalStatement();
        if (localStmt.stmt == null || localStmt.stmt.isClosed()) {
            localStmt.stmt = this.prepareStatment(this.query$);
        } else if (Debug.DEBUG && localStmt.rs != null) {
            // TODO 다시 점검.
            Debug.Assert(localStmt.rs.isClosed());
        }

        PreparedStatement stmt = localStmt.stmt;
        this.setParameters(stmt, values);


        return localStmt;
    }

    private void setParameters(PreparedStatement stmt, Object[] values) throws SQLException {
        if (values == null) return;

        try {
            JDBCWriter out = new JDBCWriter(stmt);
            for (int idx = 0; idx < values.length; idx++) {
                Object v = values[idx];
                if (v != null && varConverters != null) {
                    IOAdapter adapter = varConverters[idx];
                    adapter.writeCompatible(v, out);
                } else {
                    out.writeNull();
                }
                out.incColumnIndex();
            }
        } catch (SQLException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw Debug.wtf(e);
        }
    }

    final String getSQL() {
        return this.query$;
    }

    private String makeSearchNextSql() {
        String sql = this.getBaseQuery();
        int baseQueryLen = sql.length();
        int where_p = getWherePos(sql) + 7;
        boolean no_where = where_p > sql.length();
        if (no_where) {
            int p = sql.indexOf("\nGROUP BY");
            if (p > 0) {
                sql = sql.substring(0, p) + "\nWHERE " + sql.substring(p);
                where_p = p + 7;
            } else {
                sql += "\nWHERE ";
            }
        }
        // JJ todo : sort 쿼리
        boolean isJoined = this.isJoinedQuery();

        StringBuilder sb = new StringBuilder(sql.substring(0, where_p));
        for (SortableColumn order : this.orderBy) {
            sb.append(order.getColumnName());
            sb.append(order.isDescentOrder() ? " <= " : " >= ").append("? AND ");
        }

        if (no_where) {
            sb.setLength(sb.length() - 5);
        }
        sb.append(sql.substring(where_p));

        StringBuilder select_columns = new StringBuilder("SELECT ");
        AbstractColumn idColumn = table.getDefaultOrderBy()[0];

        select_columns.append(idColumn.getColumnName());
        for (SortableColumn order : this.orderBy) {
            /**
             * 현재 this.orderBy == table.getDefaultOrderBy() 인 상황에서,
             * rowid 가 중복되는 현상이 있으나, 무시한다.
             */
            select_columns.append(", ");
            select_columns.append(order.getColumnName());
        }

        int from_p = sql.indexOf(" FROM ");
        sb.replace(0, from_p, select_columns.toString());

        // 연관 이슈 : #113787
        int index = this.query$.toLowerCase().indexOf("order by");
        sb.append(" ");
        sb.append(this.query$.substring(index));
        return sb.toString();
    }

    private PreparedStatement makeSearchNextStatement() throws SQLException, NoSuchFieldException {
        LocalStatement localStatement = this.getLocalStatement();
        PreparedStatement findNextStmt = localStatement.searchNext;
        if (findNextStmt != null) {
            return findNextStmt;
        }

        String sql = null;
        try {
            sql = makeSearchNextSql();
            findNextStmt = prepareStatment(sql);
            localStatement.searchNext = findNextStmt;
        } catch (SQLException sqlException) {
            System.out.println(sql);
            throw sqlException;
        }
        return findNextStmt;
    }

    private boolean isJoinedQuery() {
        return this.query$.indexOf("OUTER JOIN") > 0;
    }

    private EntitySnapshot resolveJoinedEntity(EntitySnapshot dm, SortableColumn col) throws Exception {
        ForeignKey fk = col.getJoinedForeignKey();
        StormQuery query = col.getDeclaredTable().createQuery("WHERE " + fk.getKey() + " == " + dm.getEntityId(), null);
        ImmutableList<EntitySnapshot> entities = query.loadEntities();
        return entities.size() == 0 ? null : entities.get(0);
    }

    private ResultSet searchNext(PreparedStatement stmt, EntitySnapshot dm, Object[] values) throws SQLException {
        JDBCWriter out = new JDBCWriter(stmt);
        try {
            for (SortableColumn col : this.orderBy) {
                if (!col.isJoined()) {
                    out.write(dm, col);
                } else {
                    EntitySnapshot entity = resolveJoinedEntity(dm, col);
                    if (entity == null) {
                        // can not search with nullable joined column
                        out.writeInt(0);
                    } else {
                        out.write(entity, col);
                    }
                }
            }
        } catch (Exception e) {
            throw Debug.wtf(e);
        }

        if (values != null) {
            try {
                for (int idx = 0; idx < values.length; idx++) {
                    Object v = values[idx];
                    if (v == null) {
                        out.writeNull();
                    } else {
                        IOAdapter adapter = varConverters[idx];
                        adapter.writeCompatible(v, out);
                    }
                    out.incColumnIndex();
                }
            } catch (SQLException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw Debug.wtf(e);
            }
        }
        return stmt.executeQuery();
    }

    static IOAdapter[] getIOAdapters(ORMField[] DBFields, String sql, QueryMethod.EntityResolver entityResolver) {
        // query 문과 sort 문은 서로 분리하는 것을 잠정적인 원칙으로 한다.
        // 단, 필요에 의해 바뀔 수 있다.
        //NPDebug.Assert(sql.toLowerCase().indexOf("order by") < 0);
        //NPDebug.Assert(sql.toLowerCase().indexOf("group by") < 0);

        //ORMField[] DBFields = mainTable.getORMFields();// ORMSlot.getORMProperties(entityType, false);
        ArrayList<IOAdapter> adapters = new ArrayList<>();
        int p = 0;
        boolean isFts = sql.indexOf("MATCH") > 0;
        for (; ; ) {
            p = sql.indexOf('?', p + 1);
            if (p < 0) {
                break;
            }
            if (isFts) {
                adapters.add(stringAdapter);
                continue;
            }

            int L, R = p;
            String key;
            boolean isFirst = true;

            while (true) {
                int c;
                int last_c = 0;
                while (!Character.isJavaIdentifierPart((c = sql.charAt(R - 1)))) {
                    if (c != ' ') {
                        last_c = c;
                        if (c == ',') {
                            Debug.wtf("wrong sql " + sql);
                        }
                    }
                    R--;
                }
//				if (last_c == '(' || last_c =='"' || last_c == '\'') {
//					continue;
//				}
                L = R - 1;
                while (L > 0 && Character.isJavaIdentifierPart(sql.charAt(L - 1))) {
                    L--;
                }
                key = sql.substring(L, R);
                if (isFirst && key.equalsIgnoreCase("like")) {
                    isFirst = false;
                    R = L - 1;
                } else {
                    break;
                }
            }

            ORMField[] ormFields = DBFields;
            //StormTable table = mainTable;
            if (L > 0 && sql.charAt(--L) == '.') {
                R = L;
                while (L > 0 && Character.isJavaIdentifierPart(sql.charAt(L - 1))) {
                    L--;
                }
                String subTable = sql.substring(L, R);
                ormFields = entityResolver.getORMProperties(subTable);
                //table = entityResolver.findDBTableByName(subTable);
                //ormFields = table.getORMFields();
            }

            ORMField cvt;
            if (key.equals("rowid")) {
                cvt = entityResolver.getDefaultOrderBy()[0].getColumn();///.IDSlot.instance;
            } else {
                cvt = ORMField.getFieldByKey(key, ormFields);
                if (cvt == null) {
                    throw new RuntimeException("illegal sql statement: " + sql);
                }
            }
            adapters.add(cvt.getAdapter());
        }
        return adapters.toArray(new IOAdapter[adapters.size()]);
    }

    public static class DebugUtil {
        public static int execStatementCount;

        public static void validateQuery(String sql, ORMField[] fields, QueryMethod.EntityResolver subEntityResolver) throws Exception {
            getIOAdapters(fields, sql, subEntityResolver);
        }
    }

    @Override
    public int forEach(AbstractColumn[] columnns, Object[] searchParams, AbstractCursor.Visitor visitor) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ");
        for (AbstractColumn col : columnns) {
            sb.append(col.getColumnName());
            sb.append(", ");
        }
        sb.setLength(sb.length() - 2);
        int from = this.query$.indexOf(" FROM ");
        sb.append(this.query$.substring(from));

        PreparedStatement stmt = this.prepareStatment(sb.toString());
        this.setParameters(stmt, searchParams);

        ResultSet rs = stmt.executeQuery();
        try {
            JDBCCursor cursor = new JDBCCursor(rs, columnns);

            int cntEntity = 0;
            while (rs.next()) {
                cntEntity++;
                if (!visitor.onNext(cursor)) {
                    break;
                }
            }
            return cntEntity;
        } finally {
            rs.close();
        }
    }


}
