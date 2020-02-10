package test.storm.ormtest.invalidORM;

import org.slowcoders.storm.orm.ORMGenerator;


public class TestORMGen2 extends ORMGenerator {
		
		
	public TestORMGen2() {
	}

	public static final String CUSTOM_REF_PREFIX = "Rx";

	private static final String CUSTOM_SNAPSHOT_PREFIX = "Dx";

	@Override
	public String getEntityBaseName(Class<?> ormDefinition) {
		String s = ormDefinition.getSimpleName();
		if (s.endsWith("Entity")) {
			s = s.substring(0, s.length() - 6);
		}
		return s;
	}

	@Override
	public String getEntityModelName(Class<?> ormDefinition) {
		return "Ix" + this.getEntityBaseName(ormDefinition);
	}


	public static void main(String args[]) {
		generateDatabase(true);
	}

	public static String generateDatabase(boolean forceRegeneration) {
		try {
			TestORMGen2 gen = new TestORMGen2();

			gen.setSchemaPackage("DatabaseConfig",
					"src/test/java", "test.storm.ormtest.invalidORM");

			gen.setModelOutput(
					"src/test/gen", "test.storm.ormtest.gen2.model.api",
					"src/test/gen", "test.storm.ormtest.gen2.model.api"
			);

			gen.generateORM(forceRegeneration);
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
		
	}

}
