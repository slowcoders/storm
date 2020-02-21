package org.slowcoders.sample.orm.def;

import org.slowcoders.storm.jdbc.JDBCDatabase;
import org.slowcoders.storm.jdbc.JDBCMigration;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Migration_V1 extends JDBCMigration {

    protected Migration_V1() {
        super(2);
    }

    @Override
    protected void migrate(JDBCDatabase db) throws SQLException {
        String sql = null;
        PreparedStatement stmt = db.getConnection().prepareStatement(sql);
        stmt.executeUpdate();
    }
}
