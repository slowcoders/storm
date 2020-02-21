package org.slowcoders.sample.orm.def;

import org.slowcoders.sample.orm.gen.storm._TableBase;
import org.slowcoders.storm.EntityEditor;
import org.slowcoders.storm.EntityReference;
import org.slowcoders.storm.EntitySnapshot;
import org.slowcoders.storm.StormTable;
import org.slowcoders.storm.jdbc.JDBCDatabase;
import org.slowcoders.storm.jdbc.JDBCMigration;
import org.slowcoders.storm.jdbc.JDBCTable;

public class ORMDatabase extends JDBCDatabase {

    private static ORMDatabase dbStorage = new ORMDatabase();
    private static final String dbName = "storm-test.db";
    private static final int dbVersion = 1;

    private ORMDatabase() {
        super(dbName);
    }

    public static final long MAX_ITEMS_PER_TABLE = 1000L * 1000 * 1000 * 1000 * 1000 * 100;
    // can define 93 tables.

    public static abstract class Table<SNAPSHOT extends EntitySnapshot, REF extends EntityReference, EDITOR extends EntityEditor>
            extends JDBCTable<SNAPSHOT, REF, EDITOR> {

        public Table(String tableName) {
            super(dbStorage, tableName);
        }
    }

    protected StormTable.AbstractEntityResolver createAbstractEntityResolver() {
        return new StormTable.AbstractEntityResolver() {
            @Override
            public StormTable<?, ?, ?> getTable(long id) {
                return null;
            }
        };
    }

    public static void initDatabase() throws Exception {
        try {
            dbStorage.init(dbVersion, migrations);
        }
        catch (Exception e) {
            e.printStackTrace();
            JDBCDatabase.removeDatabase(dbName);
            dbStorage = new ORMDatabase();
            dbStorage.init(dbVersion, migrations);
        }
    }

    private static JDBCMigration[] migrations = new JDBCMigration[] {

    };

    public static void initialize() {

    }

}
