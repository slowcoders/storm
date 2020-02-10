package test.storm.ormtest;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slowcoders.storm.*;
import org.slowcoders.storm.orm.ORMField;
import org.slowcoders.storm.orm.ORMGenerator;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;


public class GenORMTest {

    private static List<Class> ormList = new ArrayList<>();

    private static final String schemaPackage = "test.storm.ormtest.schema";
    private static final String schemaPath = "src/test/java/test/storm/ormtest/schema";

    private static final String srcOutputPackage = "test.storm.ormtest.gen.model.storm";
    private static final String srcOutputPath = "src/test/gen/test/storm/ormtest/gen";

    @BeforeClass
    public static void setupBeforeClass() throws Exception {
        Generator gen = new Generator();
        gen.setSchemaPackage("TestDatabase",
                "src/test/java", "test.storm.ormtest.schema");

        gen.setModelOutput(
            "src/test/gen", "test.storm.ormtest.gen.model",
            "src/test/gen", "test.storm.ormtest.gen.model");

        gen.generateORM(true);

        fillORMClasses(ormList);

        Assert.assertTrue(ormList.size() > 0);
    }

    /**
     *  check if source output dir created
     */
    @Test
    public void genDir() {
        File f = new File(srcOutputPath);
        Assert.assertTrue(f.exists());
    }

    /**
     *  after generating source
     *  check if we have snapshot, reference, editor, table classes
     *  for each orm entity class
     */
    @Test
    public void genEntityFiles() throws ClassNotFoundException {
        HashMap<Class, EntitySet> entitySet = getEntitySets();
        for (EntitySet set : entitySet.values()) {
            Assert.assertNotNull(set.editor);
            Assert.assertNotNull(set.reference);
            Assert.assertNotNull(set.snapshot);
            Assert.assertNotNull(set.table);
        }
    }

    @Test
    public void genReferenceApi() throws Exception {
        loopEntity((set, fields) -> {
            Class<? extends EntityReference> ref = set.reference;

            for (ORMField field : fields) {
                if (field.isOuterLink()) {
                    Field f = ref.getDeclaredField(field.getKey());
                    Class<?> type = f.getType();
                    if (field.isUnique()) {
                        Assert.assertTrue(field.getBaseType().isAssignableFrom(type));
                        Method m = ref.getDeclaredMethod("get" + field.getFieldName());
                    } else {
                        Assert.assertTrue(StormRowSet.class.isAssignableFrom(type));
                        Method m = ref.getDeclaredMethod("find" + field.getFieldName());
                    }
                }
            }
        });
    }

    @Test
    public void genSnapshotApi() throws Exception {

    }

    @Test
    public void genEditorApi() throws Exception {
        loopEntity((set, fields) -> {
            Class<? extends EntityEditor> editor = set.editor;

            for (ORMField field : fields) {
                if (field.isOuterLink()) {
                    editor.getDeclaredMethod("edit" + field.getFieldName());
                    if (field.isVolatileData() && field.isNullable() && !field.isCollection() && field.isUnique()) {
                        editor.getDeclaredMethod("get" + field.getFieldName());
                        editor.getDeclaredMethod("set" + field.getFieldName(), field.getBaseType());
                    } else {

                    }
                } else {
                    Field f = null;
                    try {
                        f = editor.getDeclaredField(field.getKey());
                    } catch (NoSuchFieldException e) {
                        // editor must not have
                        // fields that are not outerlink
                    }
                    Assert.assertNull(f);

                    editor.getDeclaredMethod("get" + field.getFieldName());

                    Class<?> type = field.getBaseType();
                    if (Collection.class.isAssignableFrom(type)) {
                        type = Collection.class;
                    }
                    if (field.isForeignKey() && field.isUnique()) {
                        editor.getDeclaredMethod("__set" + field.getFieldName(), type);
                    } else {
                        editor.getDeclaredMethod("set" + field.getFieldName(), type);
                    }
                }
            }
        });
    }

    private void loopEntity(Loop func) throws Exception {
        HashMap<Class, EntitySet> entities = getEntitySets();

        for (Map.Entry<Class, EntitySet> entry : entities.entrySet()) {
            Class ormDef = entry.getKey();
            EntitySet set = entities.get(ormDef);
            ORMField[] fields = getORMEntityFields(ormDef);

            func.loopEntity(set, fields);
        }
    }

    private ORMField[] getORMEntityFields(Class c) throws IllegalAccessException {
        ArrayList<ORMField> list = new ArrayList<>();
        Field[] fields = c.getDeclaredFields();
        for (Field field : fields) {
            if (ORMField.class.isAssignableFrom(field.getType())) {
                list.add((ORMField) field.get(null));
            }
        }
        return list.toArray(new ORMField[list.size()]);
    }

    private static void fillORMClasses(List<Class> list) throws ClassNotFoundException {
        File dir = new File(schemaPath);
        File[] fs = dir.listFiles();
        for (File f : fs) {
            String name = f.getName();
            if (name.endsWith("ORM.java")) {
                name = name.substring(0, name.indexOf('.'));
                Class c = Class.forName("test.storm.ormtest.schema." + name);
                if (ORMEntity.class.isAssignableFrom(c)) {
                    list.add(c);
                }
            }
        }
    }

    private HashMap<Class, EntitySet> getEntitySets() throws ClassNotFoundException {
        HashMap<Class, EntitySet> map = new HashMap<>();
        HashMap<String, Class> map2 = new HashMap<>();

        for (Class c : ormList) {
            String name = c.getSimpleName();
            name = name.substring(0, name.indexOf("_"));
            map.put(c, new EntitySet());
            map2.put(name, c);
        }

        File dir = new File(srcOutputPath + "/model/storm");
        File[] fs = dir.listFiles();
        for (File f : fs) {
            String name = f.getName();
            if (name.indexOf("_") > 0) {
                String[] strs = name.split("_");
                String key = strs[0];
                String def = strs[1];
                name = name.substring(0, name.indexOf("."));

                Class ormDef = map2.get(key);
                EntitySet set = map.get(ormDef);
                if (set != null) {
                    if (def.startsWith("Editor")) {
                        set.editor = (Class<? extends EntityEditor>) Class.forName(srcOutputPackage + "." + name);
                    } else if (def.startsWith("Reference")) {
                        set.reference = (Class<? extends EntityReference>) Class.forName(srcOutputPackage + "." + name);
                    } else if (def.startsWith("Snapshot")) {
                        set.snapshot = (Class<? extends EntitySnapshot>) Class.forName(srcOutputPackage + "." + name);
                    } else if (def.startsWith("Table")) {
                        set.table = (Class<? extends StormTable>) Class.forName(srcOutputPackage + "." + name);
                    }
                }
            }
        }
        return map;
    }

    private static final class EntitySet {
        private Class<? extends EntityEditor> editor;
        private Class<? extends EntityReference> reference;
        private Class<? extends EntitySnapshot> snapshot;
        private Class<? extends StormTable> table;
    }

    interface Loop {
        void loopEntity(EntitySet set, ORMField[] fields) throws Exception;
    }

    private static final class Generator extends ORMGenerator {
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

        @Override
        protected void generateORM(boolean forceRegeneration) throws Exception {
            super.generateORM(forceRegeneration);
        }
    }
}
