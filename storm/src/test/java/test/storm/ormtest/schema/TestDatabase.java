package test.storm.ormtest.schema;

import org.slowcoders.storm.EntityEditor;
import org.slowcoders.storm.EntitySnapshot;
import org.slowcoders.storm.EntityReference;
import org.slowcoders.storm.StormTable;
import org.slowcoders.storm.jdbc.JDBCDatabase;
import org.slowcoders.storm.jdbc.JDBCMigration;
import org.slowcoders.storm.jdbc.JDBCTable;
import org.slowcoders.util.Debug;
import test.storm.ormtest.gen.model.storm._TableBase;

public class TestDatabase extends JDBCDatabase {

	private static final String dbName = "storm-test-2.db";
	private static JDBCDatabase dbStorage;
	private static final int dbVersion = 1;

	static {
		JDBCDatabase.removeDatabase(dbName);
		dbStorage = new TestDatabase(dbName);
	}

	protected TestDatabase(String name) {
		super(name);
	}

	public static final long MAX_ITEMS_PER_TABLE = 1000L * 1000 * 1000 * 1000 * 1000 * 100;
	// 총 93 종 등록 가능.

	public static final long UserIdStart = MAX_ITEMS_PER_TABLE * 2;

	@Override
	protected StormTable.AbstractEntityResolver createAbstractEntityResolver() {
		return new StormTable.AbstractEntityResolver() {
			@Override
			public StormTable<?, ?, ?> getTable(long id) {
				int v = (int) (id / MAX_ITEMS_PER_TABLE);
				StormTable table;
				switch (v) {
					case 2:
						table = _TableBase.tUser;
						break;
					default:
						table = null;
				}
				return table;
			}
		};
	}

	public static abstract class Table<SNAPSHOT extends EntitySnapshot, REF extends EntityReference, EDITOR extends EntityEditor>
			extends JDBCTable<SNAPSHOT, REF, EDITOR> {

		public Table(String tableName) {
			super(dbStorage, tableName);
		}
	}

	public static void initDatabase() throws Exception {
		//GenTestORM.generateDatabase(false);

		// Migration 테스트를 위해서
		// TestUtil 의 init 함수를 호출하는 시점에 database 를 초기화한다.
		if (true) {
			return;
		}
		dbStorage.init(dbVersion, migrations);
	}
	
	private static JDBCMigration[] migrations = new JDBCMigration[] {
			new Migration_V1()
	};

	public static void clearAllCache() {
		dbStorage.clearAllTableCache();
	}

	public static class TestUtil {

		public static void initDatabase() throws Exception {
			dbStorage.init(dbVersion, migrations);
		}

		public static void initWithMigration(int version, JDBCMigration... migrations) throws Exception {
			Debug.Assert(migrations != null);
			dbStorage.init(version, migrations);
		}
	}
}
