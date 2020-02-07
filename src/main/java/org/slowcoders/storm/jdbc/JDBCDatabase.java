package org.slowcoders.storm.jdbc;

import org.joda.time.DateTimeConstants;
import org.slowcoders.io.serialize.IOAdapter;
import org.slowcoders.storm.*;
import org.slowcoders.storm.jdbc.sqlite.SQLiteConfig;
import org.slowcoders.storm.jdbc.sqlite.SQLiteErrorCode;
import org.slowcoders.storm.jdbc.sqlite.SQLiteOpenMode;
import org.slowcoders.storm.orm.ORMField;
import org.slowcoders.storm.orm.ORMFlags;
import org.slowcoders.storm.orm.TableDefinition;
import org.slowcoders.pal.PAL;
import org.slowcoders.util.Debug;
import org.slowcoders.storm.jdbc.sqlite.DBLockChecker;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;

public abstract class JDBCDatabase extends StormDatabase {

	private static final String driverType;

	private final String dbName;
	private final AbstractEntityAdapter abstractTableAdapter;
	private int version;

	private ThreadLocal<LocalConnection> connRef = new ThreadLocal<>();
	private final HashMap<Class<?>, JDBCTable<?,?,?>> tables = new HashMap<>();

	private int lockFlags;
	private Object updateLock = new Object();
	private Thread writeThread;

	private Stack<LocalConnection> connectionPool = new Stack<>();

	protected abstract StormTable.AbstractEntityResolver createAbstractEntityResolver();

	protected JDBCDatabase(String name) {
		this.dbName = name;
		this.abstractTableAdapter = new AbstractEntityAdapter();
		//IOAdapterLoader.registerDefaultAdapter(ORMEntity.class, abstractTableAdapter);

		try {
			Connection conn = this.getConnection();
			System.out.println(conn.getClientInfo());
			DatabaseMetaData md = conn.getMetaData();
			System.out.println(md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
			
			Statement stmt = this.createStatement();
			ResultSet rs = stmt.executeQuery("pragma user_version");
			rs.next();
			this.version = rs.getInt(1);
			stmt.close();

		} catch (Exception e) {
			Debug.wtf(e);
		}
	}

	public final int getVersion() {
		return version;
	}

	protected IOAdapter getAbstractReferenceAdapter() {
		return this.abstractTableAdapter;
	}

	public void init(int newVersion, JDBCMigration[] migrations) throws Exception {

		ArrayList<JDBCTable> createdTables = new ArrayList<>();
		for (JDBCTable<?,?,?> table : tables.values()) {
			table.initColumns();
			if (this.makeTable(table)) {
				createdTables.add(table);
			}
			table.initTable();
			this.makeIndexes(table);
		}
		
		for (JDBCTable<?,?,?> table : tables.values()) {
			table.init();
		}

		for (JDBCTable table : createdTables) {
			super.notifyTableCreated(table);
		}

		if (newVersion <= version) {
			return;
		}
		

		////////////////////////////////////////////////////////
		// migrate DB
		////////////////////////////////////////////////////////

		Statement stmt = this.createStatement();
		Statement stmt2 = this.createStatement();
		ResultSet rs;

		JDBCTransaction tr = this.getLocalConnection().beginUpdate();
		for (JDBCMigration m : migrations) {
			if (m.getTargetVersion() > this.version) {
				m.migrate(this);
			}
		}
		tr.commit();
		tr.close();
		
		stmt.executeUpdate("pragma user_version = " + newVersion);
		if (Debug.DEBUG) {
			rs = stmt.executeQuery("pragma user_version");
			rs.next();
			Debug.Assert(newVersion == rs.getInt(1));
		}
		this.version = newVersion;
		stmt.close();
		stmt2.close();
		
	}
	
	private void makeIndexes(JDBCTable<?, ?, ?> table) throws SQLException {
		String tableName = table.getTableName();
		Class<? extends ORMEntity> def = table.getORMDefinition();
		ORMField.visitIndexParts(def, strings -> {
			try {
				for (String index : strings) {
					String[] cols = index.split(",");
					StringBuilder sb = new StringBuilder();
					for (String col : cols) {
						col = col.trim();
						ORMField column = ORMField.getFieldByKey(col, table.getDBColumns());
						if (column == null) {
							throw new RuntimeException(table.getTableName() + " does not have column - " + col);
						}
						sb.append(col);
					}
					String indexName = tableName.toLowerCase() + '_' + sb.toString(); // index table name

					String sql = "CREATE INDEX IF NOT EXISTS " + indexName
							+ " ON " + tableName + "_rw (" + index + ");";
					Statement stmt = this.getConnection().createStatement();
					stmt.executeUpdate(sql);
				}
			} catch (SQLException e) {
				throw Debug.wtf(e);
			}

		});
	}

	private static Class<?> getEntityDefinition(Class<?> entityType) {
		for (Class<?> ifc : entityType.getInterfaces()) {
			if (ORMEntity.class.isAssignableFrom(ifc)) {
				if (ifc.getAnnotation(TableDefinition.class) != null) {
					return ifc;
				}
			}
		}
		return getEntityDefinition(entityType.getSuperclass());
	}
	
	public JDBCTable<?,?,?> findDBTable(Class<?> entityType) {
		Class<?> ed = ORMEntity.class.isAssignableFrom(entityType)
				? entityType : getEntityDefinition(entityType);
		return tables.get(ed);
	}

	public StormTable<?,?,?> findDBTableByName(String tableName) {
		for (StormTable<?,?,?> table : tables.values()) {
			if (tableName.equals(table.getTableName())) {
				return table;
			}
		}
		return null;
	}
	
	public void clearAllTableCache() {
		for (JDBCTable<?,?,?> table : tables.values()) {
			table.clearAllCache_UNSAFE_DEBUG();
		}
	}
	
	protected void registerTable(JDBCTable<?,?,?> table) {
		Class<?> tc = getEntityDefinition(table.getEntityDefinition());
		tables.put(tc, table);
	}

	private boolean makeTable(StormTable<?,?,?> table) throws SQLException {
		
		String tableName = table.getTableName();

		long startId = table.getAutoIncrementStart();

		StringBuilder sb = new StringBuilder();
		String sql;
		String viewSql = null;

		String dbColumns =  getTableCreateStatement(table);// sb.toString();//getColumnDefinitions(table);
		String oldColumns = loadDBSchemas(tableName);

		if (oldColumns == null) {
			sb.setLength(0);
			sb.append("CREATE ");
			if (table.isFts()){
				sb.append("VIRTUAL ");
			}
			sb.append("TABLE IF NOT EXISTS ").append(tableName).append("_rw");
			if (table.isFts()){
				sb.append(" USING fts4");
			}
			sb.append(" (");
			sb.append(dbColumns);
			sb.append(");\n");
			sql = sb.toString();

			sb.setLength(0);
			sb.append("CREATE VIEW IF NOT EXISTS ").append(tableName).append(" AS ")
				.append("SELECT * FROM ").append(tableName).append("_rw ")
					.append("WHERE rowid > 0");
			viewSql = sb.toString();
			System.out.println("[CreateTable: " + sql + "]");
		}
		else {
			HashMap<String, String> oldScheme = parseSchema(oldColumns);
			HashMap<String, String> newScheme = parseSchema(dbColumns);
			for (Iterator<String> it = oldScheme.keySet().iterator(); it.hasNext(); ) {
				String key = it.next();
				String old_v = oldScheme.get(key);
				String new_v = newScheme.get(key);
				if (new_v != null) {
					if (old_v.equalsIgnoreCase(new_v)) {
						it.remove();
						newScheme.remove(key);
						//oldScheme.remove(key);
					}
				}
			}
			if (newScheme.size() == 0 && oldScheme.size() == 0) {
				sql = null;
			}
			else if (newScheme.size() > 0) {
				sb.setLength(0);
				for (Iterator<Entry<String, String>> it = newScheme.entrySet().iterator(); it.hasNext(); ) {
					Entry<String, String> e = it.next();
					sb.append("alter table ").append(tableName).append("_rw");
					if (oldScheme.containsKey(e.getKey())) {
						Connection conn = this.getConnection();
						if (!conn.getMetaData().supportsAlterTableWithDropColumn()) {
							Debug.wtf(conn.getMetaData().getDatabaseProductName() + " is not supports alter column type: "
										+ tableName + "." + e.getKey());
						}
						sb.append(" alter column ");
					}
					else {
						sb.append(" add ");
					}
					sb.append(e.getKey()).append(" ").append(e.getValue()).append(';'); 
				}
				sql = sb.toString();
			}
			else {
				sql = null;
			}
		}
		
		Statement stmt = null;
		Connection conn = this.getConnection();
		try {
			if (sql != null) {
				stmt = conn.createStatement();
				stmt.executeUpdate(sql);

				if (viewSql != null){
					stmt = conn.createStatement();
					stmt.executeUpdate(viewSql);
				}

				if (oldColumns == null) {
					if (startId > 0) {
						sql = "INSERT INTO sqlite_sequence (name,seq) VALUES ('" + tableName + "_rw', " + startId + ")";
						/**
						 * when it is update not insert, we use sql query below
						 * sql = "UPDATE SQLITE_SEQUENCE SET seq = " + startId + "\nWHERE name = '" + table.getTableName() + "'";
						 */
						boolean count = stmt.execute(sql);
						System.out.println(count + "");
					}
					return true;
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		finally {
			try {
				if (stmt != null) {
					stmt.close();
				}
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return false;
	}

	private String getTableCreateStatement(StormTable table) {
		boolean isFts = table.isFts();

		StringBuilder sb = new StringBuilder();
		ArrayList<String> uniques = new ArrayList<>();
		ArrayList<String> indexes = new ArrayList<>();

		if (!isFts){
			sb.append("rowid INTEGER PRIMARY KEY");
			if (table.isAutoIncrementEnabled()) {
				sb.append(" AUTOINCREMENT");
			}
			sb.append(",\n");
		}


		for (ORMField f : table.getDBColumns()) {
			if (f.isMasterForeignKey()) {
				/**
				 * masterForeignKey uses rowid column
				 * which is already a primary key
				 * so we cannot make it unique
				 */
				continue;
			}
			String columnType = f.getDBColumnType();
			String key = f.getKey();
			sb.append(key);

			if (!isFts){
				sb.append(' ').append(columnType);
//				if ((f.getAccessFlags() & ORMFlags.Indexed) != 0) {
//					indexes.add(key);
//				}

				if ((f.getAccessFlags() & ORMFlags.Unique) != 0) {
					uniques.add(key);
				}
			}
			sb.append(",");
		}

//		String s = td.hiddenColumns().trim();
//		if (s.length() > 0) {
//			out.println("\t+ \"", s, ",\""); // @TODO 문자열 Escape 필요.
//		}

		for (String key : uniques) {
			sb.append("UNIQUE (").append(key).append("),");
		}

		ORMField.visitUniqueParts(table.getORMDefinition(), new Consumer<String[]>() {
			public void accept(String[] uniques) {
				for (String unique : uniques) {
					String[] cols = unique.split(",");
					for (String col : cols) {
						col = col.trim();
						ORMField column = ORMField.getFieldByKey(col, table.getDBColumns());
						if (column == null) {
							throw new RuntimeException(table.getTableName() + " does not have column - " + col);
						}
					}
					sb.append("UNIQUE (");
					sb.append(unique);
					sb.append("),");
				}
			}
		});

//		String s= table.getTableCreateSQL();
//		if (s == null || s.length() == 0) {
			sb.setLength(sb.length() - 1);
//		}
//		else {
//			sb.append(s);
//		}
//		String s = sb.toString();
//		String s2 = s.substring(off);
//		String s3 = table.getTableCreateSQL();
//		s2 = s2.replace("TEXT", "").replace("BLOB", "").replace("INTEGER", "");
//		s3 = s3.replace("TEXT", "").replace("BLOB", "").replace("INTEGER", "");
//		s3 = s3.substring(0, s2.length());
//		NPDebug.Assert(s2.equals(s3));
		return sb.toString();//s.substring(0, off) + table.getTableCreateSQL();
	}

	private HashMap<String, String> parseSchema(String sql) {
		HashMap<String, String> map = new HashMap<>();
		StringTokenizer st = new StringTokenizer(sql, ",");
		while (st.hasMoreTokens()) {
			String s = st.nextToken().trim();
			int p = s.indexOf(' ');
			if (p < 0) {
				continue;
			}
			String key = s.substring(0, p).trim();
			String v = s.substring(p+1).trim();
			if (key.toLowerCase().startsWith("unique") && v.contains("(")) {
				continue;
			}
			map.put(key, v);
		}
		return map;
	}

	private String loadDBSchemas(String tableName) {
		Statement stmt = null;
		Connection conn = this.getConnection();
		
		ResultSet rs = null;
		try {
			
			stmt = conn.createStatement();
			String sql = "SELECT sql FROM sqlite_master\nWHERE name='" + tableName + "_rw'";

			rs = stmt.executeQuery(sql);
			if (rs.next()) {
				String s = rs.getString(1);
				s = s.substring(s.indexOf('(') + 1, s.lastIndexOf(')'));
				return s;
			}
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		finally {
			JDBCUtils.close(rs);
			JDBCUtils.close(stmt);
		}
	}

	private void print(ResultSet rs) throws SQLException {
		int cc = rs.getMetaData().getColumnCount();
		for (int i = 0; i < cc; i ++) {
			String col = rs.getMetaData().getColumnName(i+1);
			Object value = rs.getObject(col);
			System.out.println(col + ": " + value + '\n');
		}
		System.out.println("\n");
	}

	public Connection getConnection() {
		return this.getLocalConnection().conn;
	}
	
	protected final LocalConnection getLocalConnection() {
		LocalConnection conn = connRef.get();
		int cntRetry = 0;
		while (conn == null) {
			try {
				conn = new LocalConnection(this);
			}
			catch (SQLException e) {
				if (!this.handleStorageBusyException(e, ++cntRetry, true)) {
					Debug.fatal(e);
				}
			}
			connRef.set(conn);
		}
		return conn ;
	}

	public Statement getGeneralStatement() throws SQLException {
		return this.getLocalConnection().stmtGeneral;
	}


	public Statement createStatement() throws SQLException {
		return getConnection().createStatement();
	}

	protected JDBCTransaction getCurrentUpdateTransaction() {
		LocalConnection lc = this.getLocalConnection();
		JDBCTransaction tr = lc.currentTransaction;
		return (tr == lc) ? null : tr;
	}

	Thread readThread;
	protected void beginReadTransaction() {
		LocalConnection conn = getLocalConnection();

		if (CHECK_DB_LOCK) {
			synchronized (updateLock) {
				readThread = Thread.currentThread();
				if (conn.conn instanceof DBLockChecker) {
					DBLockChecker dbConn = (DBLockChecker) conn.conn;
					Debug.Assert(!dbConn.isDBLockByOtherThreads());
				}
			}
		}

		synchronized (updateLock) {
			/**
			 * CAUTION) only in WAL-MODE,
			 * it is allowed just to change conn.readDepth
			 * without doing beginTransaction process
			 *
			 * - in WAL-MODE,
			 * because database can be changed only when updateLock is owned,
			 * read can be done anytime without transaction
			 */
			lockFlags++;
			conn.readDepth ++;
		}
	}

	protected void endReadTransaction() {
		LocalConnection conn = getLocalConnection();
		synchronized (updateLock) {
			conn.readDepth --;
			if (--lockFlags == 0) {
				updateLock.notify();
				if (CHECK_DB_LOCK) readThread = null;
				if (CHECK_DB_LOCK) checkDBLock(conn);
			}
		}
	}

	private void checkDBLock(LocalConnection conn) {
		if (writeThread == null && lockFlags == 0 && conn.currentTransaction == conn) {
			if (conn.conn instanceof DBLockChecker) {
				DBLockChecker dbConn = (DBLockChecker) conn.conn;
				Debug.Assert(!dbConn.isDBLockByCurrentThread());
				Debug.Assert(!dbConn.isDBLockByOtherThreads());
			}
		}
	}


	public <T, P> T executeInLocalTransaction(TransactionalOperation<P> op, P operationParam, ConcurrentUpdatePolicy updatePolicy) throws SQLException {

		JDBCTransaction tr = this.getCurrentUpdateTransaction();
		if (tr == null) {
			return this.executeIsolatedTransaction(op, operationParam, updatePolicy);
		}

		long transactionId = tr.getTransactionId();
		if (updatePolicy != null) {
			ConcurrentUpdatePolicy lastPolicy =  tr.getUpdatePolicy();
			if (lastPolicy != updatePolicy) {
				tr.setUpdatePolicy(updatePolicy);
				try {
					return super.doExecuteOperation(op, operationParam, transactionId);
				}
				finally {
					tr.setUpdatePolicy(lastPolicy);
				}
			}
		}

		return super.doExecuteOperation(op, operationParam, transactionId);
	}

	public static final boolean CHECK_DB_LOCK = true;

	public <T, P> T executeIsolatedTransaction(TransactionalOperation<P> op, P operationParam, ConcurrentUpdatePolicy updatePolicy) throws SQLException {

		JDBCTransaction tr = null;
		LocalConnection conn = this.getLocalConnection();
        LocalConnection tempConnection = this.popConnection();
        int cntRetry = 0;
		while (true) {
			try {
				tr = conn.beginUpdate();
				if (updatePolicy != null) {
					tr.setUpdatePolicy(updatePolicy);
				}

				long transactionId = tr.getTransactionId();

				T res = super.doExecuteOperation(op, operationParam, transactionId);

				if (tr == conn.rootTransaction) {
					synchronized (updateLock) {
						if (CHECK_DB_LOCK) this.writeThread = Thread.currentThread();

						while (lockFlags > conn.readDepth) {
							updateLock.wait();
						}

						/**
						 *  cache invalidation has to be executed in
						 *  another connection because it needs data
						 *  before updates in this transaction are
						 *  reflected
						 */
						try {
							this.connRef.set(tempConnection);
							tr.invalidateCaches();
						} finally {
							this.connRef.set(conn);
						}


						tr.commit();

						if (CHECK_DB_LOCK) this.writeThread = null;
						if (CHECK_DB_LOCK) checkDBLock(conn);
					}
				}
				else {
					tr.commit();
				}
				return res;
			}
			catch (SQLException e) {
				if (!this.handleStorageBusyException(e, ++cntRetry, true)) {
					e.printStackTrace();
					throw e;
				}
			}
			catch (InterruptedException e) {
				throw Debug.wtf(e);
			}
			catch (Exception e) {
				throw e;
//				NPDebug.wtf(e);
			}
			finally {
                this.recycleConnection(tempConnection);
				if (tr != null) {
					tr.close();
				}
			}
		}
	}


	private  LocalConnection popConnection() throws SQLException {
		LocalConnection connection;
		synchronized(connectionPool) {
			if (connectionPool.empty()) {
				connection = new LocalConnection(this);
			}
			else {
				connection = connectionPool.pop();
			}
			return connection;
		}
	}

	private  void recycleConnection(LocalConnection connection) {
		synchronized(connectionPool) {
			connectionPool.push(connection);
		}
	}

	protected boolean handleStorageBusyException(SQLException sqlError, int cntRetry, boolean isUpdateAction) {
//		if (!(sqlError instanceof SQLiteException)) {
//			return false;
//		}
//
//		SQLiteException e = (SQLiteException)sqlError;

		SQLiteErrorCode e = SQLiteErrorCode.getErrorCode(sqlError.getErrorCode());

		// see: https://www.sqlite.org/rescode.html#busy_snapshot
		String msg = this.dbName.substring(dbName.lastIndexOf('/') + 1);
		switch (e) {
			default:
				return false;

			case SQLITE_BUSY_SNAPSHOT:
				/**
				 * The SQLITE_BUSY_SNAPSHOT error code is an extended error code for SQLITE_BUSY
				 * that occurs on WAL mode databases when a database connection tries to promote a read
				 * transaction into a write transaction but finds that another database connection has
				 * already written to the database and thus invalidated prior reads.
				 *
				 * The following scenario illustrates how an SQLITE_BUSY_SNAPSHOT error might arise:
				 *
				 * 1) Process A starts a read transaction on the database and does one or more SELECT statement.
				 * Process A keeps the transaction open.
				 * 2) Process B updates the database, changing values previous read by process A.
				 * 3) Process A now tries to write to the database. But process A's view of the database content
				 * is now obsolete because process B has modified the database file after process A read from it.
				 * Hence process A gets an SQLITE_BUSY_SNAPSHOT error.
				 */
				msg += " is changed by another thread";
				break;

			case SQLITE_BUSY_RECOVERY:
				/**
				 * The SQLITE_BUSY_RECOVERY error code is an extended error code for SQLITE_BUSY
				 * that indicates that an operation could not continue because another process is busy
				 * recovering a WAL mode database file following a crash. The SQLITE_BUSY_RECOVERY
				 * error code only occurs on WAL mode databases.
				 */
				msg += " is in recovering datas";
				break;

			case SQLITE_BUSY:
				msg += " is temporary locked";
				break;

		}

		final int RETRY_TIMEOUT = isUpdateAction ? DateTimeConstants.MILLIS_PER_MINUTE * 2
				: DateTimeConstants.MILLIS_PER_SECOND * 3;
		final int RETRY_INTERVAL = 100;
		int MAX_RETRY = RETRY_TIMEOUT / RETRY_INTERVAL;
		if (Debug.DEBUG) {
			MAX_RETRY /= 2;
		}
		if (++ cntRetry > MAX_RETRY) {
			throw new RuntimeException(new BusyStorageException(msg));
		}

		try {
			Thread.sleep(RETRY_INTERVAL);
		} catch (InterruptedException e1) {
			return false;
		}
		StormTable.UnsafeTools.gTotalRetryCount ++;
		return true;

	}

	static final class LocalConnection extends JDBCTransaction {
		/*internal*/ final Connection conn;
		private final JDBCRootTransaction rootTransaction;
		public Statement stmtGeneral;
		private JDBCTransaction currentTransaction;
		private JDBCDatabase db;
		private int readDepth;

		private static final boolean ENABLE_WAL_MODE = true;
		private static Properties props;
		static {
			/**
			 * 참조: https://www.sqlite.org/threadsafe.html
			 * https://stackoverflow.com/questions/1680249/how-to-use-sqlite-in-a-multi-threaded-application
			 */
			SQLiteConfig config = new SQLiteConfig();
			//config.setOpenMode(SQLiteOpenMode.FULLMUTEX); // <- For Serialize Mode
			config.setOpenMode(SQLiteOpenMode.NOMUTEX); // <- For MultiThread Mode
			config.setTransactionMode(ENABLE_WAL_MODE ? SQLiteConfig.TransactionMode.EXCLUSIVE : SQLiteConfig.TransactionMode.DEFFERED);
			props = config.toProperties();
			/*
			 * for SQLCyper
			 * props.put( "key", "some_passphrase" )
			 */
		}
		
		LocalConnection(JDBCDatabase db) throws SQLException {
			super(ConcurrentUpdatePolicy.ErrorOnUpdateConflict);

			String dbPath = driverType + PAL.getStorage().getDatabaseDirectory() + "/" + db.dbName;
			this.db = db;
			this.conn = DriverManager.getConnection(dbPath, props);
			this.currentTransaction = this;
			this.rootTransaction = new JDBCRootTransaction(this);
			this.stmtGeneral = conn.createStatement();

			if (false) {
				/**
				 * https://www.sqlite.org/isolation.html
				 * SQLite only supports TRANSACTION_READ_COMMITTED and TRANSACTION_SERIALIZABLE
				 * Connection with TRANSACTION_READ_UNCOMMITTED can read changes before commit
				 * made by other transactions with whom shares Shared Cache
				 */
				conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			}
				
			if (ENABLE_WAL_MODE){
				/** @zee 2018.11.21
				 *
				 * WAL mode does not use RWLock. it writes changes to database
				 * once write-TR and all other read-TRs are ended.
				 * so read and write-TRs do not block each other.
				 * in case when write-TR is executed before read-TR is finished,
				 * it throws SQL_BUSY error and force to retry.
				 *
				 * it is hard to know when data are actually written to DB_File in WAL mode,
				 * so we implemented RWLock by ourselves using updateLock
				 * so that we can make use of Storm-Cache system.
				 */
				ResultSet rs = stmtGeneral.executeQuery("pragma journal_mode=WAL");
				rs.next();
				rs.close();
			}

			this.dump();
		}

		public final Connection getConnection() {
			return this.conn;
		}

		public final JDBCDatabase getDatabase() { return this.db; }

		public JDBCTransaction beginUpdate() throws SQLException {
			this.currentTransaction = this.currentTransaction.beginSubTransaction();
			return this.currentTransaction;
		}


		public JDBCTransaction beginSubTransaction() throws SQLException {
			if (this.readDepth == 0) {
				this.rootTransaction.doBegin();
			}
			return this.rootTransaction;
		}
		
		void onCurrentTransactionClosed(JDBCTransaction tr) {
			Debug.Assert(tr == this.currentTransaction);
			this.currentTransaction = tr.getOuterTransaction();
		}

		
		public void dump() throws SQLException {
			int res = conn.getTransactionIsolation();
			switch (res) {
			case Connection.TRANSACTION_NONE:
				// not supported.
				Debug.wtf("Database not supports TransactionIsolation");
				break;
			case Connection.TRANSACTION_READ_UNCOMMITTED:
				System.out.println("READ_UNCOMMITTED TRANSACTION mode");
				break;
				
			case Connection.TRANSACTION_READ_COMMITTED:
				System.out.println("READ_COMMITTED TRANSACTION mode");
				break;

			case Connection.TRANSACTION_REPEATABLE_READ:
				// 둘 중에 알맞은 값을 고른다.
				System.out.println("REPEATABLE_READ TRANSACTION mode");
				break;
				
			case Connection.TRANSACTION_SERIALIZABLE:
				/**
				 * multiple transactions can not make access to
				 * same table at the same time.
				 * it can be used when safety is most important.
				 */
				System.out.println("SERIALIZABLE TRANSACTION mode");
				break;
			}		
		}

		protected Object getProperty(Object key) {
			return properties.get(key);
		}

		protected Object setProperty(Object key, Object value) {
			return properties.put(key, value);
		}

		private HashMap<Object, Object> properties = new HashMap<>();
	}
	

	
	static {
		String driver = null;
		try {

			try {
				// try Android First
				DriverManager.registerDriver((Driver) Class.forName("org.sqldroid.SQLDroidDriver").newInstance());
				driver = "jdbc:sqldroid:";
				System.out.println("SQLDroidDriver loaded");
			} catch (Exception e) {
				Class.forName("org.sqlite.JDBC");
				driver = "jdbc:sqlite:";
				System.out.println("org.sqlite.JDBC Driver loaded");
			}

		} catch (Exception e) {
			e.printStackTrace();
			Debug.wtf(e.getMessage());
		}
		driverType = driver;
	}

	class AbstractEntityAdapter extends StormTable.Ref_Converter {
		StormTable.AbstractEntityResolver resolver;


		public AbstractEntityAdapter() {
			super();
		}

		@Override
		public StormTable<?, ?, ?> getTable(long id) {
			if (resolver == null) {
				resolver = createAbstractEntityResolver();
			}
			return resolver.getTable(id);
		}

	}

	public static void removeDatabase(String dbName) {
		String dir = PAL.getStorage().getDatabaseDirectory() + '/';
		{
			File f = new File(dir + dbName);
			if (f.exists()) {
				f.delete();
			}
		}
		{
			File f = new File(dir + dbName + "-journal");
			if (f.exists()) {
				f.delete();
			}
		}
	}

}
