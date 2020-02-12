package org.slowcoders.storm.jdbc;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import org.slowcoders.io.serialize.IOAdapter;
import org.slowcoders.io.serialize.IOEntity;
import org.slowcoders.util.Debug;
import org.slowcoders.storm.*;
import org.slowcoders.storm.orm.ForeignKey;
import org.slowcoders.storm.ORMColumn;
import org.slowcoders.storm.ORMEntity;
import org.slowcoders.storm.orm.ORMField;
import org.slowcoders.storm.orm.OuterLink;

public abstract class JDBCTable<SNAPSHOT extends EntitySnapshot, REF extends EntityReference, EDITOR extends EntityEditor>
extends StormTable<SNAPSHOT, REF, EDITOR> {

	public static final boolean ENABLE_SQL_OUTER_JOIN = false;

	private JDBCStatement stmtFind;
	private JDBCStatement stmtLoad;
	private JDBCStatement stmtLoadGhost;
	private JDBCStatement stmtInsert;
	private JDBCStatement stmtDelete;
	private JDBCStatement stmtLoadFK;
	private JDBCStatement stmtPurgeGhost;

	private JDBCDatabase dbStorage;
	private ORMField[] joinedSnapshots;

	private int columnCount;

	private String joinedFrom$;


	private static ORMField[] noProperties = new ORMField[0];
	private static IOAdapter<Object, ?>[] rowIdOnly = new IOAdapter[] { ID_Converter.instance };

	static final int COLUMN_START = 1;
	static final int IDX_ID = COLUMN_START;
	private static final Object[] emptyData = new Object[0];

	public JDBCTable(JDBCDatabase storage, String tableName) {
		super(tableName);
		this.dbStorage = storage;
		if (this.getTableName() != null) {
			dbStorage.registerTable(this);
		}
	}

	public final JDBCDatabase getDatabase() {
		return this.dbStorage;
	}

	protected final void initTable() throws Exception {
		String tableName = getTableName();
		JDBCStatement stmtAllId = new JDBCStatement(this, "SELECT " + tableName + ".rowid FROM " + this.getTableName(), null, false);
		super.setBaseQuery_unsafe(stmtAllId);

		ResultSet rs = dbStorage.getConnection().createStatement().executeQuery("select * from " + this.getTableName());
		ResultSetMetaData md = rs.getMetaData();
		this.columnCount = md.getColumnCount();

		ORMColumn[] columnFields = super.getDBColumns();
		long modifyFlag = ORMField.OUTER_LINK_UPDATE_BIT;
		for (int i = IDX_ID; i <= columnCount; i ++) {
			String key = md.getColumnLabel(i).intern();
			for (ORMColumn col : columnFields) {
				if (col.getKey() == key) {
					long bit;
					boolean immutable = col.isImmutable() && !col.isNullable();
					if (immutable) {
						bit = ORMField.IMMUTABLE_COLUMN_UPDATE_BIT;
					}
					else if (modifyFlag > 0){
						bit = modifyFlag <<= 1;
					}
					else {
						bit = modifyFlag;
					}
					col.setColumnIndex_unsafe(i, bit);
					break;
				}
			}
		}
		rs.close();
		for (ORMColumn col : columnFields) {
			Debug.Assert(col.getColumnIndex() != 0);
		}

		super.initTable();
	}

	protected void init() throws Exception {
		this.init_internal();
	}

	private void init_internal() throws Exception {
		if (this.stmtLoad != null) {
			return;
		}

        ORMColumn[] columnFields = super.getDBColumns();
		String tableName = this.getTableName();
		StringBuilder sb = new StringBuilder();

		/*
		 * make Load statement
		 */
        sb.append("SELECT ").append(tableName).append("_rw.*");
        int joinTableInsertPos = sb.length();
        sb.append(" FROM ").append(tableName).append("_rw");

        if (ENABLE_SQL_OUTER_JOIN) {
        	makeOuterJoinSQL(sb, joinTableInsertPos);
        }
        this.joinedFrom$ = sb.toString().replace(".*", ".rowid");

        sb.append("\nWHERE ").append(tableName).append("_rw.rowid = ?");
        stmtLoad = new JDBCStatement(this, sb.toString(), new IOAdapter[] { ID_Converter.instance });

		/*
		 * make Find statement
		 */
		sb.setLength(0);
		sb.append("SELECT ").append(tableName).append(".rowid");
		sb.append(" FROM ").append(tableName);
		sb.append("\nWHERE ").append(tableName).append(".rowid = ?");
		stmtFind = new JDBCStatement(this, sb.toString(), new IOAdapter[] { ID_Converter.instance});


		/*
         * make LoadForeignKey statement
         */
		if (this.getForeignKeys().length > 0) {
			sb.setLength(0);
			sb.append("SELECT ");
			for (ORMColumn c : this.getForeignKeys()) {
				sb.append(c.getKey()).append(", ");
			}
			sb.setLength(sb.length() - 2);
			sb.append(" FROM ").append(tableName).append("_rw");
			sb.append("\nWHERE ").append("rowid = ?");
			stmtLoadFK = new JDBCStatement(this, sb.toString(), rowIdOnly);
		}


        if (this.getGhostColumns().length > 0) {
    		sb.setLength(0);
    		sb.append("SELECT ");
	        for (ORMColumn c : this.getGhostColumns()) {
	        	sb.append(c.getKey()).append(", ");
	        }
	        sb.setLength(sb.length() - 2);
	        sb.append(" FROM ").append(tableName).append("_rw");
	        sb.append("\nWHERE ").append("rowid = ?");
	        stmtLoadGhost = new JDBCStatement(this, sb.toString(), rowIdOnly);// new IOAdapter[] { ID_Converter.instance, ID_Converter.instance });
        }


        /*
         * make Insert statement
         */
		sb.setLength(0);
		String insCmd = "INSERT";
		sb.append(insCmd).append(" INTO ").append(tableName).append("_rw (");
		for (ORMColumn si : columnFields) {
			sb.append(si.getKey()).append(", ");
		}
		if (!super.isAutoIncrementEnabled()) {
			sb.append("rowid, ");
		}
		sb.setLength(sb.length() - 2);
		sb.append(") VALUES (");
		for (ORMColumn si : columnFields) {
			sb.append("?, ");
		}
		if (!super.isAutoIncrementEnabled()) {
			sb.append("?, ");
		}
		sb.setLength(sb.length() - 2);
		sb.append(");");

		ORMField[] fields = super.getORMFields();
		IOAdapter[] adapters = new IOAdapter[fields.length];
		for (int i = adapters.length; --i > 0; ) {
			adapters[i] = fields[i].getAdapter();
		}
		this.stmtInsert = new JDBCStatement(this, sb.toString(), adapters);

        /*
         * make Delete statement
         */
        sb.setLength(0);
        if (true) {
			sb.append("UPDATE ").append(getTableName()).append("_rw SET rowid=-rowid");
			for (ORMColumn c : super.getGhostColumns()) {
				if (!c.isMasterForeignKey()) {
					sb.append(", ").append(c.getKey()).append("=NULL");
				}
			}
		} else {
			sb.append("DELETE FROM ").append(tableName);
		}
		sb.append("\nWHERE rowid = ?;");
		this.stmtDelete = new JDBCStatement(this, sb.toString(), rowIdOnly);

		sb.setLength(0);
		sb.append("DELETE FROM ").append(getTableName()).append("_rw WHERE rowid == ?");
		stmtPurgeGhost = new JDBCStatement(this, sb.toString(), rowIdOnly);

		/*
		 * delete ghost
		 */
		sb.setLength(0);
		sb.append("DELETE FROM ").append(getTableName()).append("_rw WHERE rowid < 0");
		Connection conn = dbStorage.getConnection();
		Statement stmt = conn.createStatement();
		try {
			stmt.executeUpdate(sb.toString());
		} finally {
			JDBCUtils.close(stmt);
		}


	}


	private void makeOuterJoinSQL(StringBuilder sb, int joinTableInsertPos) throws Exception {
		// for now, we disable this functionality.
		// if it is enabled, it has advantage that
		// number of db access can be reduced when loading snapshot.
		// but because it does not use storm cache system,
		// its effectiveness seems to be trivial compared to
		// complexity of implementation
        int cntJoin = 0;

		String tableName = this.getTableName() + "_rw";
    	ArrayList<ORMField> joinedSnapshots = new ArrayList<>();
        for (ORMField p : super.getORMFields()) {
        	if (p.isDBColumn() || p.isVolatileData()) continue;
			OuterLink subSlot = (OuterLink)p;
			if (subSlot.getFunction() != null) {
				continue;
			}
	        StormTable<?, ?, ?> subTable = dbStorage.findDBTable(p.getBaseType());

			ForeignKey fk = ORMField.attachForeignKey(subSlot, subTable.getForeignKeys(), this.getEntityDefinition());
        	if (fk.isUnique()) {
		        joinedSnapshots.add(p);
		        String subTableName = subTable.getTableName() + "_rw";
		        String joinName = subTableName;// "_J" + (++cntJoin);
		        String s = ", " + joinName + ".*";
        		sb.insert(joinTableInsertPos, s);
        		joinTableInsertPos += s.length();
		        sb.append("\nLEFT OUTER JOIN ").append(subTableName).append(" AS ").append(joinName).append(" ON ")
		        	.append(tableName).append(".rowid = ").append(joinName).append(".").append(fk.getKey());
        	}
        }

        if (joinedSnapshots.size() == 0) {
        	this.joinedSnapshots = noProperties;
        }
        else {
        	this.joinedSnapshots = joinedSnapshots.toArray(new ORMField[joinedSnapshots.size()]);
        }

		int cntCollision = 0;
    	// 계층적 소유 구조를 가진 Entity 로딩 시, Recursive 하게 Outer-Join 처리
        for (ORMField p : this.joinedSnapshots) {
	        JDBCTable<?, ?, ?> subTable = (JDBCTable<?, ?, ?>)dbStorage.findDBTable(p.getBaseType());
	        subTable.init_internal();
	        String sql = subTable.stmtLoad.getSQL();
	        int start, end;
	        start = sql.indexOf(".*,");
	        if (start > 0) {
	        	for (int left = start + 4; start > 0; ) {
	        		int right = sql.indexOf(".*,", left);
	        		if (right < 0) {
	        			break;
					}
	        		String jt = sql.substring(left, right + 3);
	        		if (sb.indexOf(jt) > 0) {
						String tn = sql.substring(left, right);
						String newName = tn + (++cntCollision);
	        			sql = sql.replace(tn, newName);
	        			sql = sql.replace(newName + " AS ", tn + " AS ");
	        			left = right + 5;
					}
	        		else {
	        			left = right + 4;
					}
				}
	        	start += 2;
		        end = sql.indexOf(" FROM ");
		        String s = sql.substring(start, end);
		        sb.insert(joinTableInsertPos, s);
		        joinTableInsertPos += s.length();

		        start = sql.indexOf("\nLEFT OUTER JOIN ");
		        end = sql.indexOf("\nWHERE ");
		        sb.append(sql.substring(start, end));
	        }
        }
   	}

	protected SNAPSHOT doLoadSnapshot(EntityReference ref, boolean mustExist) {
		int cntRetry = 0;
		ResultSet rs = null;
		while (true) {
			dbStorage.beginReadTransaction();
			try {

				rs = this.stmtLoad.executeQuery(ref);
				if (!rs.next()) {
					if (!mustExist){
						return null;
					}
					throw new InvalidEntityReferenceException(this.getTableName() + " " + ref + " does not exist");
					// Workaround - Ghost 오류 (2019/12/26)
//					final boolean WORK_AROUND_GHOST_BUG = false;
//					if (WORK_AROUND_GHOST_BUG && ref.getEntityId() > 0) {
//						rs.close();
//						rs = this.stmtLoad.executeQuery(-ref.getEntityId());
//
//						if (!rs.next()) {
//							throw new InvalidEntityReferenceException(this.getTableName() + " " + ref + " does not exist");
//						}
//					} else {
//						throw new InvalidEntityReferenceException(this.getTableName() + " " + ref + " does not exist");
//					}
				}

				JDBCReader reader = new JDBCReader(rs);
				EntitySnapshot entity = readSnapshot(ref, reader, 0);
				return (SNAPSHOT) entity;
			}
			catch (SQLException e) {
				if (!dbStorage.handleStorageBusyException(e, ++cntRetry, false)){
					throw Debug.wtf(e);
				}
			}
			finally {
				JDBCUtils.close(rs);
				dbStorage.endReadTransaction();
			}
		}
	}

	@Override
	public EntitySnapshot[] loadSnapshots(REF[] refs, int offset, int count) {
		String sql = stmtLoad.getSQL();
		int cutPos = sql.indexOf("WHERE");
		sql = sql.substring(0, cutPos);

		StringBuilder sb = new StringBuilder();
		sb.append(sql);
		sb.append(" WHERE rowid IN (");

		int refCount = 0;
		for (int i = offset, end = offset + count; i < end; i++) {
			REF ref = refs[i];
			if (ref.getAttachedSnapshot() != null) {
				continue;
			}
			refCount++;
			sb.append(ref.getEntityId());
			sb.append(", ");
		}
		if (refCount == 0) {
			return null;
		}
		sb.setLength(sb.length() - 2);
		sb.append(")");

		int cntRetry = 0;
		ResultSet rs = null;
		EntitySnapshot[] snapshots = new EntitySnapshot[refCount];

		while (true) {
			dbStorage.beginReadTransaction();
			try {
				JDBCStatement stmt = new JDBCStatement(this, sb.toString(), null);
				rs = stmt.executeQuery();
				int countSnapshot = 0;

				while (rs.next()) {
					long id = rs.getLong(IDX_ID);
					JDBCReader reader = new JDBCReader(rs);
					REF ref = findEntityReference(id);
					snapshots[countSnapshot++] = readSnapshot(ref, reader, 0);
				}
				return snapshots;
			}
			catch (SQLException e) {
				if (!dbStorage.handleStorageBusyException(e, ++cntRetry, false)){
					throw Debug.wtf(e);
				}
			}
			finally {
				JDBCUtils.close(rs);
				dbStorage.endReadTransaction();
			}
		}
	}

	protected final void loadForeignKeys(EntityReference ref) throws SQLException {
		if (ref.isForeignKeyLoaded()) return;
		if (this.stmtLoadFK == null) {
			super.markForeignKeyLoaded(ref);
			return;
		}

		ResultSet rs = null;
		try {
			rs = this.stmtLoadFK.executeQuery(ref.getEntityId());
			if (!rs.next()) {
				throw new InvalidEntityReferenceException(ref + " is not exist");
			}

			initForeignKeys(ref, rs);
		}
		catch (Exception e) {
			throw Debug.wtf(e);
		}
		finally {
			JDBCUtils.close(rs);
			super.markForeignKeyLoaded(ref);
		}
	}


	private void initForeignKeys(EntityReference ref, ResultSet rs) throws SQLException {
		try {

			ORMColumn columns[] = this.getForeignKeys();
			for (int col = 0; col < columns.length; col++) {
				ORMColumn fk = columns[col];
				Object v = rs.getObject(col + COLUMN_START);
				v = fk.getAdapter().decode(v, isImmutable());
				Field f = fk.getRefField();
				Debug.Assert(f != null && (fk.isNullable() || v != null));
				f.set(ref, v);
			}
		} catch (Exception e){
			throw Debug.wtf(e);
		}
	}


	protected final void loadGhostColumns(EntityReference ref) throws SQLException {
		Object ghostData[] = getGhostData(ref);
		// if reference already has ghost data we skip loading
		if (ghostData != null) return;

		ORMColumn columns[] = this.getGhostColumns();
		// this table has no ghost columns
		if (columns.length == 0) {
			attachGhostData(ref, emptyData);
			return;
		}

		ResultSet rs = null;
		try {
			rs = this.stmtLoadGhost.executeQuery(ref.getEntityId());
			if (!rs.next()) {
				throw new InvalidEntityReferenceException(ref + " is not exist");
			}

			JDBCReader reader = new JDBCReader(rs);
			ghostData = reader.readColumns(columns);

			super.attachGhostData(ref, ghostData);
		}
		catch (Exception e) {
			throw Debug.wtf(e);
		}
		finally {
			JDBCUtils.close(rs);
		}
	}


	private EntitySnapshot readSnapshot(EntityReference ref0, JDBCReader reader, int baseOffset) throws SQLException {

		synchronized (ref0) {
			EntitySnapshot entity = ref0.getAttachedSnapshot();
			if (entity != null) {
				return entity;
			}
			try {
				// creates empty snapshot with given reference
				// which is to be filled by function below
				entity = createEntitySnapshot(ref0);

				// fill snapshot with queried data
				reader.readEntity(entity, this.getDBColumns(), baseOffset);

				// if reference has ghost data attach it to snapshot
				Object[] ghostData = getGhostData(ref0);
				if (ghostData != null){
					ORMColumn[] columns = super.getGhostColumns();
					int idx = 0;
					for (ORMColumn col : columns){
						col.getReflectionField().set(entity, ghostData[idx++]);
					}
				}

				if (ENABLE_SQL_OUTER_JOIN) {
					Debug.shouldNotBeHere("cannot use synchronized(ref)");
					EntityReference ref;
					baseOffset += this.columnCount;
					for (ORMField p : this.joinedSnapshots) {
						JDBCTable<?, ?, ?> subTable = (JDBCTable<?, ?, ?>) dbStorage.findDBTable(p.getBaseType());
						long id = reader.getResultSet().getLong(baseOffset + IDX_ID);
						if (id == 0) {
							continue;
						}
						ORMEntity subEntity = ref = subTable.makeEntityReference(id);
						subEntity = ref.getAttachedSnapshot();
						if (subEntity == null) {
							subEntity = subTable.readSnapshot(ref, reader, baseOffset);
						}
						baseOffset += subTable.columnCount;
						IOEntity.setProperty(entity, p, subEntity);
					}
				}
				super.attachLoadedSnapshot(entity);
				return entity;
			} catch (IllegalAccessException e){
				throw Debug.wtf(e);
			}
		}


	}


	protected REF findUnique(StormQuery statment, Object... uniqueParts) {
		ResultSet rs = null;
		long id;
		try {
	        rs = ((JDBCStatement)statment).executeQuery(uniqueParts);
	        if (!rs.next()) {
	        	return null;//throw new IllegalArgumentException("invalid unique id");
	        }
	        id = rs.getLong(IDX_ID);
		}
		catch (SQLException e) {
			throw Debug.wtf(e);
		}
        finally {
			JDBCUtils.close(rs);
        }
		return super.makeEntityReference(id);
	}

	private void handleException(Exception e) {
		e.printStackTrace();
	}

	@Override
	protected boolean delete_inTR(EntityReference ref) throws SQLException {
		if (ref == null || ref.isDeleted()) {
			return false;
		}

		this.loadGhostColumns(ref);
		/**
		 * before deleting entity, foreignKeys have to be loaded first
		 * to notify them to invalidate cache
		 * */
		loadForeignKeys(ref);

		int cntDeleted = this.stmtDelete.executeUpdate(ref);
		JDBCTransaction transaction = dbStorage.getCurrentUpdateTransaction();
		transaction.addDeletedEntity(ref);
		return cntDeleted > 0;
	}

	protected final REF[] makeEntityReferenceList(long[] ids, int count) throws SQLException {
		return super.makeEntityReferenceList(ids, count);
	}

//	protected EntitySnapshot reloadUniqueContent_unsafe(EntitySnapshot entity, ORMColumn[] uniques) {
//		try {
//			StringBuilder sb = new StringBuilder();
//			EntitySnapshot found = null;
//			sb.append("SELECT rowid FROM ").append(getTableName()).append("\nWHERE ");
//			for (ORMField si : uniques) {
//				sb.append(si.getKey()).append(" = ? AND ");
//			}
//			sb.setLength(sb.length() - 5);
//			Connection conn = dbStorage.getConnection();
//			PreparedStatement stmt = conn.prepareStatement(sb.toString());
//			JDBCWriter out = new JDBCWriter(stmt);
//			out.write(entity, uniques);
//			/*
//			int i = 0;
//			for (ORMColumn si : uniques) {
//					Object v = serializer.getDBValue(si, entity);
//					stmt.setObject(++i, v);
//			}
//			*/
//			ResultSet rs = stmt.executeQuery();
//			if (rs.next()) {
//				long id = rs.getLong(IDX_ID);
//				if (!rs.next()) {
//					REF ref = super.makeEntityReference(id);
//					found = (SNAPSHOT)ref.tryLoadSnapshot();
//				}
//			}
//			stmt.close();
//			return found;
//		}
//		catch (Exception e) {
//			throw NPDebug.wtf(e);
//		}
//
//	}
	@Override
	public REF findFirst(StormQuery statement, Object... values) {
		ResultSet rs = null;
        try {
	        rs = ((JDBCStatement)statement).executeQuery(values);
	        if (!rs.next()) return null;

			long id = rs.getLong(IDX_ID);
			return super.makeEntityReference(id);
        }
        catch (Exception e) {
        	throw Debug.wtf(e);
        }
        finally {
			JDBCUtils.close(rs);
		}
	}



	@Override
	protected SNAPSHOT loadFirst(StormQuery statement, Object... values) {
    	dbStorage.beginReadTransaction();
        try {
        	SNAPSHOT entity = null;
            ResultSet rs = ((JDBCStatement)statement).executeQuery(values);
	        if (rs.next()) {
	        	long id = rs.getLong(IDX_ID);
	        	entity = (SNAPSHOT)super.makeEntityReference(id).loadSnapshot();
	        }

	        JDBCUtils.close(rs);
	        return entity;
		} catch (SQLException e) {
			throw Debug.wtf(e);
		}
        finally {
        	dbStorage.endReadTransaction();
        }
	}



	@Override
	protected final EntityReference createEntityInternal(EntityEditor editor) throws SQLException, RuntimeException {
		ORMColumn[] pis = this.getDBColumns();
		PreparedStatement stmt = stmtInsert.getStatement();
		JDBCWriter out = new JDBCWriter(stmt);
		for (ORMColumn col : pis) {
			out.write(editor, (AbstractColumn) col.getOriginColumn());
		}

		int rowidColumn = COLUMN_START + pis.length;
		if (!super.isAutoIncrementEnabled()) {
			long id = super.getNewEntityId(editor);
			stmt.setLong(rowidColumn, id);
		}

		ResultSet rs = null;
		try {
			stmt.execute();
			rs = stmt.getGeneratedKeys();
			rs.next();
			long id = rs.getLong(IDX_ID);

			REF ref = super.attachReferenceIntoEditor_unsafe(id, editor);
			getDatabase().getCurrentUpdateTransaction().addInsertedEntity(ref, editor);
			return ref;
		}
		catch (Throwable ex) {
			throw ex;
		}
		finally {
			JDBCUtils.close(stmt);
			JDBCUtils.close(rs);
		}
	}



	@Override
	protected void doUpdateEntities(ColumnAndValue[] columnValues, Collection<? extends EntityReference> entities) throws SQLException {
		if (columnValues.length == 0 || entities.size() == 0) {
			return;
		}

		TransactionalOperation op = new TransactionalOperation<Void>() {
			protected Object execute_inTR(Void param, long transactionId) throws SQLException {
				return updateEntities_inTR(columnValues, entities);
			}
		};

		dbStorage.executeInLocalTransaction(op, null);
		return;
	}

	private int updateEntities_inTR(ColumnAndValue[] columnValues, Collection<? extends EntityReference> entities) throws SQLException {
		StringBuilder sb = new StringBuilder();
		sb.append("UPDATE ").append(getTableName()).append("_rw SET ");
		long modifyFlags = 0;
		for (ColumnAndValue cv : columnValues) {
			long updateBit = cv.column.getUpdateBit();
			modifyFlags |= updateBit;
			sb.append(cv.column.getKey()).append("=");
			if (cv.isQuotedText) sb.append('"');
			sb.append(cv.value);
			if (cv.isQuotedText) sb.append('"');
			sb.append(", ");
		}
		sb.setLength(sb.length() - 2);
		sb.append("\nWHERE rowid in (");

		for (EntityReference ref : entities) {
			sb.append(ref.getEntityId()).append(',');
		}
		sb.setLength(sb.length() - 1);
		sb.append(')');

		Connection conn = dbStorage.getConnection();
		Statement stmt = conn.createStatement();
		try {
			int cntUpdate = stmt.executeUpdate(sb.toString());
			JDBCTransaction tr = getDatabase().getCurrentUpdateTransaction();
			for (EntityReference ref : entities) {
				tr.addUpdatedEntity(ref, modifyFlags);
			}
			return cntUpdate;
		}
		finally {
			JDBCUtils.close(stmt);
		}
	}

	@Override
	protected void deleteEntities(Long[] ids) throws SQLException {
		StringBuilder sb = new StringBuilder();
		sb.append("DELETE FROM ").append(getTableName() + "_rw")
				.append(" WHERE rowid IN (");
		for (long id : ids){
			sb.append(id);
			sb.append(", ");
		}
		sb.setLength(sb.length() - 2);
		sb.append(")");
		Connection conn = dbStorage.getConnection();
		Statement stmt = conn.createStatement();
		try {
			stmt.executeUpdate(sb.toString());
		}
		finally {
			JDBCUtils.close(stmt);
		}
	}

	@Override
	protected final void updateEntityInternal(EntityEditor editor) throws SQLException, RuntimeException {
		EntitySnapshot origin = editor.getOriginalData();
		EntitySnapshot recent = origin.getEntityReference().getAttachedSnapshot();
		JDBCTransaction transaction = getDatabase().getCurrentUpdateTransaction();
		if (!transaction.canOverwriteConflictEntity() && (recent == null || origin != recent)) {
			if (editor.isConflictUpdate(recent)) {
				/**
				 * if origin has already been invalidated
				 * by changes of itself or linked entities
				 * we throw error
				 */
				throw new ConflictedUpdateException(origin);
			}
		}

		StringBuilder sb = new StringBuilder();

		sb.append("UPDATE ").append(getTableName()).append("_rw SET ");

		HashMap<ORMField, Object> map = UnsafeTools.getEditMap(editor);
		boolean noChanged = true;
		for (ORMField f : map.keySet()) {
			if (!f.isOuterLink()) {
				sb.append(f.getKey()).append("=?, ");
				noChanged = false;
			}
		}
		if (noChanged) {
			return;
		}

		sb.setLength(sb.length() - 2);
		sb.append(" ");
		sb.append("\nWHERE rowid == ").append(origin.getEntityId());


		Connection conn = dbStorage.getConnection();
		PreparedStatement stmt = conn.prepareStatement(sb.toString());
		JDBCWriter out = new JDBCWriter(stmt);
		for (Map.Entry<ORMField, Object> entry : map.entrySet()) {
			ORMField f = entry.getKey();
			if (!f.isOuterLink()) {
				try {
					out.write(editor, (AbstractColumn) f.getOriginColumn());
				} catch (RuntimeException e) {
					throw e;
				} catch (Exception e) {
					Debug.wtf(e);
				}
			}
		}

		try {
			int cntUpdate = stmt.executeUpdate();
			if (cntUpdate == 0) {
				throw Debug.wtf("update on deleted entity");
			}
			transaction.addUpdatedEntity(origin.getEntityReference(), editor);

		}
		finally {
			JDBCUtils.close(stmt);
		}
	}

	public JDBCStatement createQuery(String query, SortableColumn[] orderBy) {
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT ").append(this.getTableName()).append(".rowid FROM ").append(this.getTableName());
		sb.append('\n').append(query);
		return new JDBCStatement(this, sb.toString(), orderBy, false);
	}

	public JDBCStatement createJoinedQuery(String query, SortableColumn... orderBy) throws Exception {
		StringBuilder sb = new StringBuilder();
		sb.append(joinedFrom$);
		sb.append('\n').append(query);
		return new JDBCStatement(this, sb.toString(), orderBy, true);
	}
	
	final String getJoinedFrom() {
		return this.joinedFrom$;
	}
	
	protected void clearAllCache_UNSAFE_DEBUG() {
		super.clearAllCache_UNSAFE_DEBUG();
	}

	@Override
	public void purgeGhost(long entityId) throws SQLException {
		stmtPurgeGhost.executeUpdate(entityId);
	}

	@Override
	protected REF findEntityReference_impl(long entityId, boolean mustExist) throws InvalidEntityReferenceException {
		Debug.Assert(entityId > 0);
		ResultSet rs = null;
		try {
			JDBCStatement stmt = stmtLoadFK == null ? stmtFind : stmtLoadFK;
			rs = stmt.executeQuery(entityId);
			if (!rs.next()){
				if (mustExist) {
					throw new InvalidEntityReferenceException(getTableName() + entityId + " does not exist");
				}
				return null;
			}
			REF ref = super.makeEntityReference(entityId);
			if (stmtLoadFK != null) {
				this.initForeignKeys(ref, rs);
			}
			return ref;
		} catch (SQLException e) {
			throw Debug.wtf(e);
		} finally {
			JDBCUtils.close(rs);
		}
	}
}
