package org.slowcoders.storm.orm;

import static org.slowcoders.storm.orm.ModelGen.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;

import org.slowcoders.storm.ORMEntity;
import org.slowcoders.util.SourceWriter;

public abstract class ORMGenerator {

	private static HashMap<String, String> macros = new HashMap<>();
	private String sourceWraning = "/**\nThis file is generated by Storm Generator.\nDo not modify this file.\n*/\n\n";
	private final String userClassHeader = "// Generated by Storm - ";

	private HashMap<Class<?>, ModelGen> modelMap = new HashMap<>();
	private SourceWriter entryOut;

	private String dbConfigClassName;
	private File schemaRoot;
	private String schemaPackage;
	private File schemaDir;


	private PackInfo modelPack;
	private PackInfo stormPack;

	private ArrayList<String> defaultImports = new ArrayList<>();

	static String[] systemImports = new String[] {
			"static " + ORMFlags.class.getName() + ".*",
			"java.sql.SQLException",
			"java.util.*",
			"com.google.common.collect.*",
			"org.slowcoders.util.*",
			"org.slowcoders.storm.*",
			"org.slowcoders.storm.orm.*",
			"org.slowcoders.storm.util.*",
			"org.slowcoders.io.serialize.*",
			"org.slowcoders.observable.*"
	};

	
	public ORMGenerator() {
		this.entryOut = new SourceWriter();

		for (String s: systemImports) {
			defaultImports.add(s);
		}
	}

	public void setSchemaPackage(String dbConfigClassName, String srcRoot, String packageName) {
		this.dbConfigClassName = dbConfigClassName;
		this.schemaRoot = new File(srcRoot);
		this.schemaPackage = packageName;
		this.schemaDir = new File(schemaRoot, packageName.replace('.', '/'));
	}

	public void setModelOutput(String srcRoot, String basePackage, String genRoot, String genPackage) {
		this.modelPack = new PackInfo(srcRoot, basePackage, genRoot, genPackage);
		this.stormPack = new PackInfo(srcRoot, basePackage, genRoot, genPackage);
		this.stormPack.genPackage = modelPack.genPackage + ".storm";
		//defaultImports.add(basePackage + ".*");
		if (!basePackage.equals(genPackage)) {
			//defaultImports.add(genPackage + ".*");
		}
	}

	public abstract String getEntityBaseName(Class<?> ormDefinition);

	public abstract String getEntityModelName(Class<?> ormDefinition);

	protected void generateORM(boolean forceRegeneration) throws Exception {

		File tablebase = new File(stormPack.genDir, "_TableBase.java");
		System.out.println("Generating Storm entities into " + stormPack.genDir.toString());

		long lastGenTime = tablebase.lastModified();
		
		ArrayList<Class<?>> ormTypes = new ArrayList<>();
		ArrayList<ModelGen> ormModels = new ArrayList<>();
		boolean needRegeneration = forceRegeneration;
		File[] files = schemaDir.listFiles();
		if (files != null) {
			for (File f : files) {
				if (f.isDirectory()) continue;

				String name = f.getName();
				name = name.substring(0, name.lastIndexOf('.'));
				try {
					Class<?> c = Class.forName(schemaPackage + "." + name);
					if (ORMEntity.class.isAssignableFrom(c)) {
						ormTypes.add(c);
						needRegeneration |= f.lastModified() > lastGenTime;
					}
				}
				catch(Throwable e) {
					e.printStackTrace();
					continue;
				}
			}
		}
		if (ormTypes.size() == 0) {
			System.out.println("No entity definition found in " + schemaDir.getAbsolutePath());
			return;
		}
		
		if (!needRegeneration) {
			System.out.println("ORM Refreshed");
			return;
		}
		
		/***********************************************
		 *  we first check if source is regenerated,
		 *  then do the work below
		 */
		
		for (Class<?> c : ormTypes) {
			findTableDefintionClass(c); // -> for checking;
			ModelGen model = new ModelGen(this, c, modelPack != null);
			ormModels.add(model);
			this.modelMap.put(c, model);
//			this.modelMap.put(model.table.getClass(), model);
		}


		ormModels.sort(new Comparator<ModelGen>() {

			@Override
			public int compare(ModelGen o1, ModelGen o2) {
				return o1.getBaseName().compareTo(o2.getBaseName());
			}
		});
		genStorm(ormModels);
		System.out.println("----");
		System.out.println("Done.");
	}
		
	
	final String getStorageConfig() {
		return this.dbConfigClassName;
	}


	final ModelGen getModel(Class<?> c) {
		return modelMap.get(c);
	}

	final ModelGen getModelByTableName(String tableName) {
		for (ModelGen ctx : modelMap.values()) {
			if (tableName.equals(ctx.dbTableName)) {
				return ctx;
			}
		}
		throw new RuntimeException("Unknown table name: " + tableName);
	}

	
	final SourceWriter getOutput() {
		return this.entryOut;
	}
	
	
	private void genStorm(ArrayList<ModelGen> ormModels) throws Exception {
		ModelGen._ormGen = this;

		for (ModelGen model : ormModels) {
			Class<?> c = model.getEntityType();
			Class<?> superT = getInheritedEntityDefintion(c, true);
			ModelGen superM = superT == ORMEntity.class ? null : this.getModel(superT);
			model.init(superM, this.entryOut);
		}
		
		for (ModelGen model : ormModels) {
			model.initQueries();
		}

		for (ModelGen model : ormModels) {
			model.initRelativeProperties();
		}

		String tableType = this.getStorageConfig() + ".TableBase";

		macros.put("#PACKAGE#", stormPack.genPackage);
		macros.put("#TableBase#", tableType);
		
		// gen entities.
		for (ModelGen model : ormModels) {
			this.genStorm(model);
		}
	
		genTableBase(ormModels);
	}
	
	private void genStorm(ModelGen model) throws Exception {
		
		String basename = model.getBaseName();
		String modelname = model.getModelName();

		macros.put("#BASE_REF#", model.getBaseReferenceName());
		macros.put("#BASE_SNAPSHOT#", model.getBaseSnapshotName());
		macros.put("#BASE_FORM#", modelname + "_" + MODEL_FORM_SUFFIX);
		macros.put("#BASE_EDITOR#", model.getBaseEditorName());
		macros.put("#BASE_TABLE#", model.getBaseTableName());
		macros.put("#SNAPSHOT#",  model._data);
		macros.put("#REF#",  model._ref);
		macros.put("#EDITOR#", model._editor);
		
		
		macros.put("#ENTITY_ORM#",  model.getSimpleName());
		macros.put("#MODEL_NAME#",  model.modelName);
		macros.put("#TABLE_INSTANCE#", "_TableBase." + model.dbTableName);
		macros.put("#CLASS_DECL#", model.isAbstract() ? "abstract class" : "final class"); 
		
		HashMap<String, String> imports = new HashMap<>();
		model.getImports(imports, true);

		if (false) {
			//model.dumpDefinition();
			entryOut.findAndReplace(this.macros);
			System.out.println(entryOut.toString());
			entryOut.clear();
		}

		model.genModelAPI();
		writeSource(entryOut, model.modelName + "_", stormPack, imports);

		model.genRefClass();
		writeSource(entryOut, macros.get("#BASE_REF#"), stormPack, imports);

		model.genSnapshotClass();
		writeSource(entryOut, macros.get("#BASE_SNAPSHOT#"), stormPack, imports);

		model.genEditorClass();
		writeSource(entryOut, macros.get("#BASE_EDITOR#"), stormPack, imports);

		model.genTableClass();
		writeSource(entryOut, macros.get("#BASE_TABLE#"), stormPack, imports);

//		model.genModelFormAPI(null);
//		writeSource(entryOut, macros.get("#BASE_FORM#"), stormPack, imports);

		//ZZ genStormTemplate(model, ModelGen.ReferenceSelf);
		//ZZ genStormTemplate(model, ModelGen.SnapshotSelf);

		if (modelPack != null && model.apiExported) {
//			HashMap<String, String> modelImports = new HashMap<>();
//			model.getImports(modelImports, false);
//			String f = model.genModelAPI();
//			writeSource(entryOut, f, modelPack, modelImports);

			genModelTemplate(model, null);
		}

	}

	private void genTableBase(ArrayList<ModelGen> ormModels) throws Exception {
		SourceWriter configOut;
		configOut = new SourceWriter();

		configOut.println("@SuppressWarnings(\"unchecked\")");
		configOut.println("public interface _TableBase {\n");

		for (ModelGen model : ormModels) {
			if (model.isAbstract()) {
				continue;
			}

			String table = model.getBaseTableName();
			configOut.print(table).print(" " + model.dbTableName + " = new ")
					.print(table, "(\"", model.getTaleDefinition().tableName()).println("\");\n");


			for (ORMField p : model.getAllDBFields()) {
				if (p.isForeignKey()) {
					ModelGen td = modelMap.get(p.getBaseType());
					td.slaveKeys.put(model, p);
				}
			}
		}


		configOut.println("Initializer initializer = new Initializer();\n");

		configOut.println("static class Initializer implements _TableBase {");

		configOut.println("static {");
		configOut.println("try {");
		configOut.print(this.getStorageConfig()).println(".initDatabase();");
		configOut.println("} catch (Exception e) {");
		configOut.println("throw new RuntimeException(e);");
		configOut.println("}");
		configOut.println("}");
		configOut.println("}\n");

		configOut.println("}");
		writeSource(configOut, "_TableBase", stormPack, null);
	}


	private void throwError(String string) {
		throw new RuntimeException(string);
	}

	private void genModelTemplate(ModelGen model, Instance instance) throws Exception {
		String className$ = model.modelName;
		Writer out = this.openSourceFile(modelPack.srcRoot, modelPack.srcPackage,  className$, false);

		if (out != null) {
			entryOut.print("package ").print(modelPack.srcPackage).println(";\n");

			entryOut.println("import java.sql.SQLException;\n");
			entryOut.println("import org.slowcoders.storm.orm.*;\n");
			entryOut.println("import " + schemaPackage + ".*;\n");
			entryOut.println("import " + modelPack.genPackage + ".*;\n");
			entryOut.println("import " + stormPack.genPackage + ".*;\n");

			model.genModelUserAPI();

			for (Entry<String, String> e : macros.entrySet()) {
				String key = e.getKey();
				String value = e.getValue();
				entryOut.replaceAll(key, value);
			}

			entryOut.writeAndClear(out);
			out.close();
		}
		else {
			System.out.println(className$ + " already exists. (generation skipped)");
		}
	}

	
	public static Class<?> getInheritedEntityDefintion(Class<?> model, boolean isBase) throws InstantiationException, IllegalAccessException, NoSuchFieldException, SecurityException {
		Class<?> td = null;
		
		for (Class<?> ifc : model.getInterfaces()) {
			Class<?> c = getInheritedEntityDefintion(ifc, false);
			if (c != null) {
				if (td != null && td != c) {
					throw new RuntimeException("Conflict column inheritance : " +model.getSimpleName()
						+ " extends both (" + td.getSimpleName() + " + " + c.getSimpleName() + ")");
				}
				td = c;
			}
		}
		
		if (!isBase && ORMEntity.class.isAssignableFrom(model)) {
			td = model;
		};
		return td;
	}	
	
	
	private static Class<?> getTableClass(Class<?> model) {
		TableDefinition tc = model.getAnnotation(TableDefinition.class);
		return tc != null ? model : null;
	}

	
	private static Class<?> findTableDefintionClass(Class<?> model) {
		
		Class<?> td = getTableClass(model);
		for (Class<?> ifc : model.getInterfaces()) {
			Class<?> c = findTableDefintionClass(ifc);
			if (c != null) {
				if (td != null) {
					throw new RuntimeException("Multiple entity definitions found: " +model.getSimpleName()
						+ " extends (" + td.getSimpleName() + " + " + c.getSimpleName() + ")");
				}
				td = c;
			}
		}
		
		return td;
	}
	

	private void writeSource(SourceWriter source, String klass, PackInfo pack, HashMap imports) throws IOException {
		Writer out = openSourceFile(pack.genRoot, pack.genPackage, klass, true);
		int idxFileName = klass.lastIndexOf('/');
		String extPack = (idxFileName > 1) ? "." + klass.substring(0, idxFileName).replace('/', '.') : "";
		System.out.println("Generating " + klass);
		out.write("\npackage " + pack.genPackage + extPack + ";\n\n");

		
		for (String h : defaultImports) {
			out.write("import " + h + ";\n");
		}
		out.write('\n');
		
		out.write("import " + schemaPackage + ".*;\n");
		if (pack == stormPack) {
			out.write("import " + modelPack.genPackage + ".*;\n");
		}

		if (imports != null) {
			for (Object h : imports.values()) {
				out.write("import " + h.toString() + ";\n");
			}
			out.write('\n');
		}

		out.write(sourceWraning);
		source.findAndReplace(this.macros);
		
		source.writeAndClear(out);
		out.close();
	}

	
//	static String makeTableName(ModelGen td) {
//		String simpleName = td.getBaseName();
//		return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
//	}
//

	private Writer openSourceFile(File root, String _package, String klass, boolean overwrite) throws IOException {
		
		File currPackageFolder = new File(root, _package.replace('.', '/'));

		File f = new File(currPackageFolder, klass + ".java");
		f.getParentFile().mkdirs();
		if (overwrite) {
			return new OutputStreamWriter(new FileOutputStream(f));
		}
		
		while (f.exists()) {
			if (true) return null;
			FileInputStream in = new FileInputStream(f);
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			for (String s; (s  = reader.readLine()) != null; ) {
				if (s.startsWith(userClassHeader)) {
					String tstamp = s.substring(userClassHeader.length());
					try {
						long ts = Long.parseLong(tstamp);
						if (ts > f.lastModified()) {
							break;
						}
					}
					catch (Exception e) {
						// ignore.
					}
				}
			}
			return null;
		}
		OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(f));
		if (false) {
			out.write(userClassHeader);
			out.write(Long.toString(System.currentTimeMillis() + 500));
			out.write("\n\n");
		}
		return out;
	}


	
	private static HashMap<String, String> primitive2ObjectMap = new HashMap<>();
	static {
		primitive2ObjectMap.put("boolean", "Boolean");
		primitive2ObjectMap.put("byte", "Byte");
		primitive2ObjectMap.put("short", "Short");
		primitive2ObjectMap.put("char", "Character");
		primitive2ObjectMap.put("int", "Integer");
		primitive2ObjectMap.put("long", "Long");
		primitive2ObjectMap.put("float", "Float");
		primitive2ObjectMap.put("long", "Long");
	}

	static String getObjectType(String type) {
		String s = primitive2ObjectMap.get(type);
		if (s == null) {
			s = type;
		}
		return s;
	}


	static class PackInfo {
		PackInfo(String srcRoot, String basePackage, String genRoot, String genPackage) {
			this.srcRoot = new File(srcRoot);
			this.srcPackage = basePackage;
			this.srcDir = new File(srcRoot, basePackage.replace('.', '/'));

			this.genRoot = new File(genRoot);
			this.genPackage = genPackage;
			this.genDir = new File(genRoot, genPackage.replace('.', '/'));
		}

		public File srcRoot;
		public String srcPackage;
		public File srcDir;

		public File genRoot;
		public String genPackage;
		public File genDir;
	}

}

