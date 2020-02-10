package test.storm.ormtest;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
		EntityCacheTest.class,
		JoinTest.class,
		QueryTest.class,
		MultiSnapshotJoinTest.class,
		UniqueSnapshotJoinTest.class,
		MultiVolatileJoinTest.class,
		TransactionTest.class,
		MasterForeignKeyTest.class,
		GhostReferenceTest.class
})
public class StormTestMain {

}
