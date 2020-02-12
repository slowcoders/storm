package test.storm.ormtest;

import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Test;
import org.slowcoders.storm.jdbc.JDBCDatabase;
import org.slowcoders.storm.jdbc.JDBCMigration;
import org.slowcoders.storm.jdbc.JDBCUtils;
import org.slowcoders.util.Debug;
import test.storm.ormtest.gen.model.IxUser;
import test.storm.ormtest.gen.model.storm._TableBase;
import test.storm.ormtest.gen.model.storm.Post_Table;
import test.storm.ormtest.schema.TestDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

public class MigrationTest {

    private int migrationVersion = getVersion() + 1;

    static {
        Post_Table tPost = _TableBase.tPost;
    }

    /**
     *  add column and check schema to
     *  see if column is added
     */
    @Test
    public void testAddColumn() throws Exception {
        JDBCMigration m = new JDBCMigration(migrationVersion) {
            @Override
            protected void migrate(JDBCDatabase db) throws SQLException {
                String sql = "ALTER TABLE tUser_rw ADD COLUMN _age INTEGER";
                Connection conn = db.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.executeUpdate();
            }
        };

        TestDatabase.TestUtil.initWithMigration(migrationVersion, m);

        String sql = "SELECT sql FROM sqlite_master WHERE name = 'tUser_rw'";

        Connection conn = _TableBase.tUser.getDatabase().getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);

        ResultSet rs = stmt.executeQuery();
        Debug.Assert(rs.next());

        String schema = rs.getString(1);
        int idx = schema.indexOf("_age");

        Assert.assertTrue(idx > 0);
    }

    /**
     *  change value of columns
     */
    @Test
    public void testUpdateColumnValue() throws Exception {
        prepareUser("Mark");
        prepareUser("Jane");
        prepareUser("John");

        JDBCMigration m = new JDBCMigration(migrationVersion) {
            @Override
            protected void migrate(JDBCDatabase db) throws SQLException {
                String sql = "UPDATE tUser_rw SET _name = 'Coder'";
                Connection conn = db.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.executeUpdate();
            }
        };

        TestDatabase.TestUtil.initWithMigration(migrationVersion, m);

        ImmutableList<IxUser.Snapshot> users = _TableBase.tUser.loadEntities();
        users.forEach(user -> Assert.assertEquals("Coder", user.getName()));
    }

    /**
     *  update data with storm entity
     *  and see if updated has been made right
     */
    @Test
    public void testUpdateWithEntityEditor() throws Exception {
        String emailAddress = "John_" + System.currentTimeMillis() + "@ggg.com";

        prepareUser(emailAddress);

        JDBCMigration m = new JDBCMigration(migrationVersion) {
            @Override
            protected void migrate(JDBCDatabase db) throws SQLException {
                IxUser userRef = _TableBase.tUser.findByEmailAddress(emailAddress);
                IxUser.Editor editor = userRef.loadSnapshot().editEntity();
                editor.setName(emailAddress);
                editor.save();
            }
        };

        TestDatabase.TestUtil.initWithMigration(migrationVersion, m);

        IxUser userRef = _TableBase.tUser.findByEmailAddress(emailAddress);
        IxUser.Snapshot data = userRef.loadSnapshot();

        Assert.assertEquals(emailAddress, data.getName());
    }

    /**
     *  if there are multiple migrations that
     *  have to ben executed,
     *  check if they are all done properly
     */
    @Test
    public void testMultipleMigrations() throws Exception {
        AtomicInteger atomicInteger = new AtomicInteger(0);
        JDBCMigration m1 = new JDBCMigration(migrationVersion) {
            @Override
            protected void migrate(JDBCDatabase db) throws SQLException {
                atomicInteger.incrementAndGet();
            }
        };
        JDBCMigration m2 = new JDBCMigration(++migrationVersion) {
            @Override
            protected void migrate(JDBCDatabase db) throws SQLException {
                atomicInteger.incrementAndGet();
            }
        };

        TestDatabase.TestUtil.initWithMigration(migrationVersion, m1, m2);

        Assert.assertEquals(2, atomicInteger.get());
    }

    /**
     *  check if user_version is updated
     *  to version of migration
     */
    @Test
    public void testUpdateUserVersion() throws Exception {
        JDBCMigration m = new JDBCMigration(migrationVersion) {
            @Override
            protected void migrate(JDBCDatabase db) throws SQLException {
                System.out.println("successfully migrated");
            }
        };

        TestDatabase.TestUtil.initWithMigration(migrationVersion, m);

        Assert.assertEquals(migrationVersion, getVersion());
    }

    private void prepareUser(String name) throws SQLException {
        String sql = "INSERT INTO tUser (_emailAddress) VALUES (?)";
        Connection conn = _TableBase.tUser.getDatabase().getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, name);
        stmt.executeUpdate();
    }

    private int getVersion()  {
        ResultSet rs = null;
        try {
            Connection conn = _TableBase.tUser.getDatabase().getConnection();
            PreparedStatement stmt = conn.prepareStatement("pragma user_version");
            rs = stmt.executeQuery();

            Debug.Assert(rs.next());

            long version = rs.getLong(1);
            return (int) version;
        } catch (Exception e) {
            throw Debug.wtf(e);
        } finally {
            JDBCUtils.close(rs);
        }
    }
}
