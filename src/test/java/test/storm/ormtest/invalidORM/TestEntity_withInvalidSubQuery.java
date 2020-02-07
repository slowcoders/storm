package test.storm.ormtest.invalidORM;

import org.slowcoders.storm.ORMColumn;
import org.slowcoders.storm.ORMEntity;
import org.slowcoders.storm.orm.TableDefinition;
import org.slowcoders.storm.orm.Where;
import test.storm.ormtest.schema.Post_ORM;

import static org.slowcoders.storm.orm.ORMFieldFactory._Column;

@TableDefinition(
	tableName = "InvalidTable"
)
public interface TestEntity_withInvalidSubQuery extends ORMEntity {
	
	public final ORMColumn Name = _Column("_name", 0,
			String.class);

	interface Queries {
		@Where("_subject = {subject}")
		Post_ORM findByName(String title);
	}
}
