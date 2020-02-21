package org.slowcoders.sample.orm;

import org.slowcoders.storm.orm.ORMGenerator;
import org.slowcoders.util.Debug;

public class GenORM extends ORMGenerator {

    @Override
    public String getEntityBaseName(Class<?> ormDefinition) {
        String name = ormDefinition.getSimpleName();
        return name.substring(0, name.indexOf("_ORM"));
    }

    @Override
    public String getEntityModelName(Class<?> ormDefinition) {
        String baseName = getEntityBaseName(ormDefinition);
        return "Ix" + baseName;
    }

    public static void main(String[] args) {
        try {
            GenORM gen = new GenORM();
            gen.setSchemaPackage(
                    "ORMDatabase", "src/main/java", "org.slowcoders.sample.orm.def"
            );
            gen.setModelOutput(
                    "src/main/java", "org.slowcoders.sample.orm.gen",
                    "src/main/java", "org.slowcoders.sample.orm.gen"
            );

            gen.generateORM(false);
        } catch (Exception e) {
            throw Debug.wtf(e);
        }
    }
}
