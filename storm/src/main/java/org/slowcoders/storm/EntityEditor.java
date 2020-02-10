package org.slowcoders.storm;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slowcoders.io.serialize.*;
import org.slowcoders.json.JSONScanner;
import org.slowcoders.storm.jdbc.JDBCDatabase;
import org.slowcoders.util.Debug;
import org.slowcoders.storm.orm.ORMField;
import org.slowcoders.storm.orm.ORMProxy;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.*;

import static org.slowcoders.io.serialize.IOAdapter.getDefaultAdapter;

public abstract class EntityEditor implements ORMEntity, IOEntity {

    private EntitySnapshot origin;

    private EntityReference editRef;

    private long trNo;

    private StormTable table;

    private ConcurrentUpdatePolicy updatePolicy;

    private HashMap<ORMField, Object> editMap = new HashMap<>();

    private static Object undefined = new Object();

    private boolean isDirty;

    private static final HashMap<Class, Object> defaultValues = new HashMap<>();

    static {
        defaultValues.put(float.class, 0);
        defaultValues.put(Float.class, 0);
        defaultValues.put(double.class, 0);
        defaultValues.put(Double.class, 0);
        defaultValues.put(long.class, 0);
        defaultValues.put(Long.class, 0);
        defaultValues.put(int.class, 0);
        defaultValues.put(Integer.class, 0);
        defaultValues.put(short.class, 0);
        defaultValues.put(Short.class, 0);
        defaultValues.put(char.class, 0);
        defaultValues.put(Character.class, 0);

        defaultValues.put(boolean.class, false);
        defaultValues.put(Boolean.class, false);

        defaultValues.put(String.class, "");

        defaultValues.put(DateTime.class, new DateTime(0));
        defaultValues.put(Duration.class, new Duration(0));
    }

    protected EntityEditor(StormRowSet table, EntitySnapshot origin) {
        if (table != null) {
            this.table = table.getTable();
        }
        this.origin = origin;
        this.editRef = origin == null ? null : origin.getEntityReference();

        if (origin != null) {
            Debug.Assert(!origin.isDeleted());
            origin.ensureLoadSubSnapshot();
        } else {
            initDefaultValues();
        }
    }

    public abstract EntityReference getEntityReference();

    protected final EntityReference getEntityReference_internal() {
        return this.editRef;
    }

    public abstract EntitySnapshot getOriginalData();

    protected EntitySnapshot getOriginalData_internal() {
        return this.origin;
    }

    protected void initDefaultValues() {
    }

    public StormTable getTable() {
        return this.table;
    }

    public final boolean isNewEntity() {
        return this.origin == null;
    }

    public final long getEntityId() {
        return getEntityReference().getEntityId();
    }

    public final ConcurrentUpdatePolicy getUpdatePolicy() {
        return this.updatePolicy;
    }

    public final void setUpdatePolicy(ConcurrentUpdatePolicy policy) {
        this.updatePolicy = policy;
    }

    protected final void ensureNotNull(ORMField f) throws RuntimeException {
        Object obj = getObjectValue(f);
        if (obj == null) {
            throw new InvalidEntityValueException(f.getDeclaringClass().getSimpleName() + "." + f.getFieldName() + " can not be null or zero");
        }
    }

    // check if outerLink value which is not nullable is null
    protected final void ensureNotNull(ORMField f, Object obj) throws RuntimeException {
        if (obj == null) {
            if (origin != null) {
                obj = origin.getFieldValue(f);
            }
            if (obj == null) {
                throw new InvalidEntityValueException(f.getDeclaringClass().getSimpleName() + "." + f.getFieldName() + " can not be null or zero");
            }
        }
    }

    public ORMEntity getStormEntity(StormTable table) {
        Debug.Assert(this.getTable() == table);
        return this;
    }

    public final long getUpdateFlags() {
        validateEditMap();
        long bit = 0;
        for (ORMField f : editMap.keySet()) {
            bit |= f.getUpdateBit();
        }
        return bit;
    }

    public final boolean isChanged() {
        validateEditMap();
        return origin == null || this.getEditMap().size() > 0;
    }

    public final boolean isChanged(ORMField field) {
        validateEditMap();
        return origin == null || this.getEditMap().containsKey(field);
    }

    private void validateEditMap() {
        if (isDirty) {
            HashMap<ORMField, Object> editMap = getEditMap();
            for (Iterator<Map.Entry<ORMField, Object>> it = editMap.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<ORMField, Object> entry = it.next();
                ORMField field = entry.getKey();
                if (!field.isMap() && !field.isCollection() && !field.isVolatileData()) {
                    continue;
                }
                if (field.isOuterLink()) {
                    continue;
                }
                Object originalData = origin.getFieldValue(field);
                Object value = entry.getValue();
                if (value == null) {
                    value = resolveDefaultValue(field.getReflectionField().getType());
                }
                if (Objects.deepEquals(originalData, value)) {
                    it.remove();
                }
            }
            isDirty = false;
        }
    }

    protected void onSave_inTR() throws SQLException, RuntimeException {
        StormTable table = this.table;
        table.getORMHelper().validateBeforeSaveEntity_inTR(this);
        EntitySnapshot old = this.origin;
        if (old == null) {
            table.createEntityInternal(this);
        } else {
            table.updateEntityInternal(this);
        }
    }

    final void doSave_inTR(long transactionId) throws SQLException {
        if (transactionId != this.trNo) {
            this.validate_inTR();
            if (this.trNo != 0) {
                Debug.trap();
            }
            this.trNo = transactionId;
            this.onSave_inTR();
        }
    }


    private static TransactionalOperation opSave = new TransactionalOperation<EntityEditor>() {
        protected Object execute_inTR(EntityEditor edit, long transactionId) throws SQLException {
            edit.doSave_inTR(transactionId);
            return null;
        }
    };

    protected final EntityReference doSave(boolean continueEdit) throws SQLException {
        StormDatabase db = this.getTable().getDatabase();
        db.executeInLocalTransaction(opSave, this, updatePolicy);
        EntityReference ref = this.getEntityReference();
        if (continueEdit) {
            this.origin = ref.doLoadSnapshot();
//			this._updateFlags = 0;
        }
        return ref;
    }

    private static TransactionalOperation opValidate = new TransactionalOperation<EntityEditor>() {
        protected Object execute_inTR(EntityEditor edit, long transactionId) throws SQLException {
            edit.doSave_inTR(transactionId);
            throw FinishValidationException.instance;
        }
    };

    /**
     * for Debug
     */
    public final void debugValidateBeforeSave() throws IllegalArgumentException {
        if (true) {
            return;
        }
        try {
            StormDatabase db = this.getTable().getDatabase();
            db.executeInLocalTransaction(opValidate, this, updatePolicy);
        } catch (FinishValidationException e) {
            return;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected void validate_inTR() throws RuntimeException {
    }

    public boolean isConflictUpdate(EntitySnapshot recent) {
        return true;
    }

    private static class FinishValidationException extends RuntimeException {
        static FinishValidationException instance = new FinishValidationException();
    }


    /*interanal*/
    final void validateFixedForeignKey(ORMField foreignKey, Object ownerRef) {
        HashMap<ORMField, Object> map = getEditMap();
        Object v = map.get(foreignKey);
        if (v == null) {
            map.put(foreignKey, ownerRef);
        } else {
            if (v instanceof ORMEntity) {
                Debug.Assert(((ORMEntity) v).getEntityReference() == ((ORMEntity) ownerRef).getEntityReference());
            } else {
                Debug.Assert(v == ownerRef);
            }
        }
    }

    public static EntityEditor asEditor(ORMProxy entity) {
        if (entity instanceof EntityEditor) {
            return (EntityEditor) entity;
        }
        return null;
    }

    protected void __saveOrDeleteJoinedEntityInternal(ORMEntity.UpdateForm origin, ORMEntity.UpdateForm entity) throws SQLException, RuntimeException {
        if (entity == origin) return;

        if (entity != null) {
            __saveJoinedEntityInternal(entity);
        } else {
            origin.getEntityReference().onDelete_inTR();
        }
    }

    protected EntityReference __saveJoinedEntityInternal(ORMProxy entity) throws SQLException, RuntimeException {
        if (entity == null) {
            return null;
        }
        EntityEditor d = EntityEditor.asEditor(entity);
        if (d != null) {
            d.doSave_inTR(this.trNo);
            return d.getEntityReference();
        }
        return entity.getEntityReference();
    }


    protected void __saveForeignEntityInternal(ORMEntity entity) throws SQLException, RuntimeException {
        if (entity != null && entity.getEntityReference() == null) {
            __saveJoinedEntityInternal(entity);
        }
    }

    protected void __saveJoinedEntitiesInternal(EditableEntities entities, StormRowSet rowSet) throws SQLException, RuntimeException {
        if (entities != null) {
            entities.onSave_inTR(this.trNo, rowSet);
        }
    }


    final <REF extends EntityReference> void setReference_Unsafe(REF ref) {
        this.editRef = ref;
    }

    void clearEditMap() {
        editMap.clear();
    }

    protected HashMap<ORMField, Object> getEditMap() {
        return this.editMap;
    }

    protected final Object getObjectValue(ORMField field) {
        try {
            field = field.getOriginColumn();
            Object v = editMap.getOrDefault(field, undefined);
            if (v != undefined) {
                return v;
            }
            if (origin == null) {
                return null;
            }
            return field.getReflectionField().get(origin);
        } catch (IllegalAccessException e) {
            throw Debug.wtf(e);
        }
    }

    protected final boolean getBooleanValue(ORMField field) {
        Object v = getObjectValue(field);
        return v == null ? false : (boolean) v;
    }

    protected final byte getByteValue(ORMField field) {
        Object v = getObjectValue(field);
        return v == null ? 0 : (byte) v;
    }

    protected final char getCharValue(ORMField field) {
        Object v = getObjectValue(field);
        return v == null ? 0 : (char) v;
    }

    protected final short getShortValue(ORMField field) {
        Object v = getObjectValue(field);
        return v == null ? 0 : (short) v;
    }

    protected final int getIntValue(ORMField field) {
        Object v = getObjectValue(field);
        return v == null ? 0 : (int) v;
    }

    protected final long getLongValue(ORMField field) {
        Object v = getObjectValue(field);
        return v == null ? 0 : (long) v;
    }

    protected final float getFloatValue(ORMField field) {
        Object v = getObjectValue(field);
        return v == null ? 0 : (float) v;
    }

    protected final double getDoubleValue(ORMField field) {
        Object v = getObjectValue(field);
        return v == null ? 0 : (double) v;
    }

    protected final void setFieldValue(ORMField field, Object value) {
        field = field.getOriginColumn();
        if (field.isOuterLink()) {
            editMap.put(field, value);
            return;
        }
        if (field.isCollection() || field.isMap() || field.isVolatileData()) {
            isDirty = origin != null;
            editMap.put(field, value);
            return;
        }
        if (origin != null) {
            Object v = origin.getFieldValue(field);
            if (ORMEntity.class.isAssignableFrom(field.getBaseType()) && value != null) {
                value = ((ORMEntity) value).getEntityReference();
            }
            Object def = value;
            if (value == null) {
                def = resolveDefaultValue(field.getReflectionField().getType());
            }
            if (Objects.deepEquals(v, def)) {
                editMap.remove(field);
                return;
            }
        }
        editMap.put(field, value);
    }

    @Override
    public void writeProperty(IOField field, DataWriter out) throws Exception {
        ORMField ormField = (ORMField) field;
        Object v = getObjectValue(ormField);
        if (v == null && !ormField.isNullable()) {
            Class<?> c = field.getReflectionField().getType();
            v = resolveDefaultValue(c);
            if (v == null && !((ORMField) field).isNullable()) {
                throw Debug.wtf("Cannot assign null value to " + getTable() + " - " + field.getFieldName());
            }
        }
        field.getAdapter().writeCompatible(v, out);
    }

    public static Object resolveDefaultValue(Class c) {
        Object v = defaultValues.get(c);
        if (v != null) {
            return v;
        }
        if (Enum.class.isAssignableFrom(c)) {
            v = 0;
        } else if (Set.class.isAssignableFrom(c)) {
            v = ImmutableSet.of();
        } else if (Map.class.isAssignableFrom(c)) {
            v = ImmutableMap.of();
        } else if (Collection.class.isAssignableFrom(c)) {
            v = ImmutableList.of();
        }
        return v;
    }

    public static String serializeEditor(EntityEditor editor) throws Exception {
        StringBuilder sb = new StringBuilder();
        EditorWriter writer = new EditorWriter(null, null, true, sb);
        writer.writeObject(editor);
        writer.close();
        return sb.toString();
    }

    public static EntityEditor deserializeEditor(JDBCDatabase database, String str) {
        try {
            StringReader rd = new StringReader(str);
            EditorReader reader = new EditorReader(database, rd);
            return (EntityEditor) editorAdapter.read(reader);
        } catch (Exception e) {
            throw Debug.wtf(e);
        }
    }

    static class EditorReader extends JSONReader {

        private JDBCDatabase database;
        private EntityEditor masterEditor;

        private EditorReader(JDBCDatabase database, Reader reader) throws Exception {
            super(IOAdapter.getLoader(true), reader);
            this.database = database;
        }

        private EditorReader(JDBCDatabase database, IOAdapterLoader loader, JSONScanner sc, boolean isMap) throws Exception {
            super(loader, sc, isMap);
            this.database = database;
        }

        private JDBCDatabase getDatabase() {
            return this.database;
        }

        @Override
        protected JSONReader createChunkedStream(IOAdapterLoader loader, JSONScanner sc, boolean isMap) throws Exception {
            return new EditorReader(database, loader, sc, isMap);
        }

        void readEditMap(EntityEditor editor) throws Exception {
            EditorReader in = (EditorReader) this.openChunkedStream();
            StormTable table = editor.getTable();
            while (!in.isClosed()) {
                String key = in.readKey();
                ORMField f = table.getORMFieldByKey(key);

                IOAdapter<Object, Object> adapter;
                Object v;
                if (f.isOuterLink()) {
                    EditorReader in2 = (EditorReader) this.openChunkedStream();
                    in2.masterEditor = editor;
                    if (List.class.isAssignableFrom(f.getReflectionField().getType())) {
                        adapter = getDefaultAdapter(EditableEntities.class);
                    } else {
                        adapter = getDefaultAdapter(ORMEntity.UpdateForm.class);
                    }
                    v = adapter.read(in2);
                    in2.isClosed();

                    Class<? extends EntityEditor> baseClass = resolveBaseEditorClass(editor.getClass());
                    Field field = baseClass.getDeclaredField(f.getKey());
                    field.setAccessible(true);
                    field.set(editor, v);
                } else {
                    if (f.isForeignKey()) {
                        String str = in.readString();
                        if (str.equals("master")) {
                            v = masterEditor;
                        } else {
                            v = f.getAdapter().decode(Long.valueOf(str), true);
                        }
                    } else {
                        adapter = f.getAdapter();
                        v = adapter.read(in);
                    }
                }
                editor.setFieldValue(f, v);
            }
        }

        void readFormArray(ArrayList<ORMEntity.UpdateForm> list) throws Exception {
            EditorReader in = (EditorReader) this.openChunkedStream();
            while (!in.isClosed()) {
                EditorReader in2 = (EditorReader) this.openChunkedStream();
                in2.masterEditor = masterEditor;
                UpdateForm form = editorAdapter.read(in2);
                list.add(form);
                in2.isClosed();
            }
        }

        private Class<? extends EntityEditor> resolveBaseEditorClass(Class<? extends EntityEditor> c) {
            while (true) {
                if (c.getSimpleName().endsWith("_Editor")) {
                    return c;
                }
                c = (Class<? extends EntityEditor>) c.getSuperclass();
            }
        }
    }

    static class EditorWriter extends JSONWriter {

        private EntityEditor masterEditor;

        private EditorWriter(EditorWriter parent, String compositeType, boolean isMap, Appendable builder) throws IOException {
            super(parent, compositeType, isMap, builder);
        }

        @Override
        protected AggregatedStream beginAggregate(String compositeType, boolean isMap) throws Exception {
            return new EditorWriter(this, compositeType, isMap, new StringBuilder());
        }

        void writeEditMap(EntityEditor editor) throws Exception {
            HashMap<ORMField, Object> map = editor.getEditMap();
            Iterator<Map.Entry<ORMField, Object>> entries = map.entrySet().iterator();

            EditorWriter out = (EditorWriter) this.beginAggregate(null, true);

            while (entries.hasNext()) {
                Map.Entry<ORMField, Object> e = entries.next();
                ORMField f = e.getKey();
                Object v = e.getValue();

                out.writeString(f.getKey());

                IOAdapter valueAdapter;
                if (f.isOuterLink()) {
                    EditorWriter out2 = (EditorWriter) out.beginAggregate(null, true);
                    out2.masterEditor = editor;
                    valueAdapter = getLoader().loadAdapter(v.getClass());
                    valueAdapter.write(v, out2);
                    out2.close();
                } else {
                    if (f.isForeignKey()) {
                        if (masterEditor == v) {
                            out.writeString("master");
                        } else {
                            EntityReference ref = ((ORMEntity) v).getEntityReference();
                            out.writeString(ref == null ? "-1" : String.valueOf(ref.getEntityId()));
                        }
                    } else {
                        valueAdapter = f.getAdapter();
                        valueAdapter.write(v, out);
                    }
                }
            }
            out.close();
        }

        void writeFormArray(ORMEntity.UpdateForm[] arr) throws Exception {
            EditorWriter out = (EditorWriter) this.beginAggregate(null, false);
            for (UpdateForm v : arr) {
                if (v == null) {
                    continue;
                }
                EditorWriter out2 = (EditorWriter) out.beginAggregate(null, true);
                out2.masterEditor = masterEditor;
                editorAdapter.write(v, out2);
                out2.close();
            }
            out.close();
        }
    }


    private static IOAdapter<ORMEntity.UpdateForm, String> editorAdapter =
            IOAdapterLoader.registerDefaultAdapter(ORMEntity.UpdateForm.class, new IOAdapter<ORMEntity.UpdateForm, String>() {

        @Override
        public ORMEntity.UpdateForm read(DataReader rd) throws Exception {
            EditorReader reader = (EditorReader) rd;

            Debug.Assert(!reader.isClosed());

            String key = reader.readKey();
            Debug.Assert(key.equals("info"));

            String info = reader.readString();
            String[] strs = info.split("/");

            String tableName = strs[0];
            long id = Long.parseLong(strs[1]);
            boolean isEditor = strs.length > 2;

            JDBCDatabase database = reader.getDatabase();
            StormTable table = database.findDBTableByName(tableName);
            if (!isEditor) {
                EntityReference ref = table.getEntityReference(id);
                return (UpdateForm) ref.loadSnapshot();
            }

            EntityEditor editor;
            if (id < 0) {
                editor = table.edit(null);
            } else {
                EntityReference ref = table.getEntityReference(id);
                editor = ref.loadSnapshot().editEntity();
            }

            Debug.Assert(!reader.isClosed());

            key = reader.readKey();
            Debug.Assert(key.equals("map"));

            reader.readEditMap(editor);
            return (UpdateForm) editor;
        }

        @Override
        public void write(ORMEntity.UpdateForm form, DataWriter wr) throws Exception {
            EditorWriter writer = (EditorWriter) wr;
            if (form == null) {
                writer.writeNull();
                return;
            }
            StormTable table;
            boolean isEditor = EntityEditor.asEditor(form) != null;
            if (isEditor) {
                table = ((EntityEditor) form).getTable();
            } else {
                table = ((EntitySnapshot) form).getTable();
            }

            EntityReference ref = form.getEntityReference();

            writer.writeString("info");

            StringBuilder sb = new StringBuilder();
            sb.append(table.getTableName());
            sb.append("/");
            sb.append(ref == null ? -1 : ref.getEntityId());

            if (!isEditor) {
                writer.writeString(sb.toString());
                return;
            }

            sb.append("/editor");
            writer.writeString(sb.toString());

            writer.writeString("map");
            writer.writeEditMap((EntityEditor) form);
        }

        @Override
        public EncodingType getPreferredTransferType() {
            return null;
        }

        @Override
        public ORMEntity.UpdateForm decode(String encoded, boolean isImmutable) throws Exception {
            return null;
        }
    });
}