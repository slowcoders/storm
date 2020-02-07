package test.storm.ormtest.schema;

import org.slowcoders.storm.jdbc.JDBCDatabase;
import org.slowcoders.storm.jdbc.JDBCMigration;

import java.sql.SQLException;

public class Migration_V1 extends JDBCMigration {
	public static final int targetVersion = 1;

	Migration_V1() {
		super(targetVersion);
	}

	@Override
	protected void migrate(JDBCDatabase db) throws SQLException {
		System.out.println("DB migrated succesfully v." + db.getVersion() + " -> v." + targetVersion);  
	}

}
