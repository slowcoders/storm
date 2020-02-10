package test.storm.ormtest.schema;

import org.slowcoders.storm.orm.ORMGenerator;
import org.slowcoders.util.Debug;


public class GenTestORM2 extends ORMGenerator {


    @Override
    public String getEntityBaseName(Class<?> ormDefinition) {
        String s = ormDefinition.getSimpleName();
        if (s.endsWith("_ORM")) {
            s = s.substring(0, s.length() - "_ORM".length());
        }
        return s;
    }

    @Override
    public String getEntityModelName(Class<?> ormDefinition) {
        return "Ix" + this.getEntityBaseName(ormDefinition);
    }


    public static void main(String args[]) {
    	boolean forceRegeneration = true;
    	if (args.length > 0 && args[0].equals("-r")) {
    		forceRegeneration = false;
    	}
        generateDatabase(forceRegeneration);
    }

    public static void generateDatabase(boolean forceRegeneration) {
        try {
            GenTestORM2 gen = new GenTestORM2();
            if(true) {
                gen.setSchemaPackage("TestDatabase",
                        "src/test/java", "test.storm.ormtest2.schema");

                if (true) gen.setModelOutput(
                        "src/test/gen", "test.storm.ormtest.gen.model2",
                        "src/test/gen", "test.storm.ormtest.gen.model2"
                );
            }
            gen.generateORM(forceRegeneration);

        } catch (Exception e) {
            Debug.wtf(e);
    		System.exit(-1);
        }
    }

}
