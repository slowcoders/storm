//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.slowcoders.storm.jdbc.sqlite;

import java.sql.Connection;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public class SQLiteConfig {
    public static final String DEFAULT_DATE_STRING_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    private final Properties pragmaTable;
    private int openModeFlag;
    private final int busyTimeout;
    private final SQLiteConnectionConfig defaultConnectionConfig;
    private static final String[] OnOff = new String[]{"true", "false"};
    static final Set<String> pragmaSet = new TreeSet();

    public SQLiteConfig() {
        this(new Properties());
    }

    public SQLiteConfig(Properties prop) {
        this.openModeFlag = 0;
        this.pragmaTable = prop;
        String openMode = this.pragmaTable.getProperty(SQLiteConfig.Pragma.OPEN_MODE.pragmaName);
        if (openMode != null) {
            this.openModeFlag = Integer.parseInt(openMode);
        } else {
            this.setOpenMode(SQLiteOpenMode.READWRITE);
            this.setOpenMode(SQLiteOpenMode.CREATE);
        }

        this.setSharedCache(Boolean.parseBoolean(this.pragmaTable.getProperty(SQLiteConfig.Pragma.SHARED_CACHE.pragmaName, "false")));
        this.setOpenMode(SQLiteOpenMode.OPEN_URI);
        this.busyTimeout = Integer.parseInt(this.pragmaTable.getProperty(SQLiteConfig.Pragma.BUSY_TIMEOUT.pragmaName, "3000"));
        this.defaultConnectionConfig = SQLiteConnectionConfig.fromPragmaTable(this.pragmaTable);
    }

//    public SQLiteConnectionConfig newConnectionConfig() {
//        return this.defaultConnectionConfig.copyConfig();
//    }

//    public Connection createConnection(String url) throws SQLException {
//        return JDBC.createConnection(url, this.toProperties());
//    }

    public void apply(Connection conn) throws SQLException {
        HashSet<String> pragmaParams = new HashSet();
        SQLiteConfig.Pragma[] var3 = SQLiteConfig.Pragma.values();
        int var4 = var3.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            SQLiteConfig.Pragma each = var3[var5];
            pragmaParams.add(each.pragmaName);
        }

        pragmaParams.remove(SQLiteConfig.Pragma.OPEN_MODE.pragmaName);
        pragmaParams.remove(SQLiteConfig.Pragma.SHARED_CACHE.pragmaName);
        pragmaParams.remove(SQLiteConfig.Pragma.LOAD_EXTENSION.pragmaName);
        pragmaParams.remove(SQLiteConfig.Pragma.DATE_PRECISION.pragmaName);
        pragmaParams.remove(SQLiteConfig.Pragma.DATE_CLASS.pragmaName);
        pragmaParams.remove(SQLiteConfig.Pragma.DATE_STRING_FORMAT.pragmaName);
        pragmaParams.remove(SQLiteConfig.Pragma.PASSWORD.pragmaName);
        pragmaParams.remove(SQLiteConfig.Pragma.HEXKEY_MODE.pragmaName);
        Statement stat = conn.createStatement();

        try {
            String key;
            if (this.pragmaTable.containsKey(SQLiteConfig.Pragma.PASSWORD.pragmaName)) {
                String password = this.pragmaTable.getProperty(SQLiteConfig.Pragma.PASSWORD.pragmaName);
                if (password != null && !password.isEmpty()) {
                    String hexkeyMode = this.pragmaTable.getProperty(SQLiteConfig.Pragma.HEXKEY_MODE.pragmaName);
                    if (SQLiteConfig.HexKeyMode.SSE.name().equalsIgnoreCase(hexkeyMode)) {
                        key = "pragma hexkey = '%s'";
                    } else if (SQLiteConfig.HexKeyMode.SQLCIPHER.name().equalsIgnoreCase(hexkeyMode)) {
                        key = "pragma key = \"x'%s'\"";
                    } else {
                        key = "pragma key = '%s'";
                    }

                    stat.execute(String.format(key, password.replace("'", "''")));
                    stat.execute("select 1 from sqlite_master");
                }
            }

            Iterator var13 = this.pragmaTable.keySet().iterator();

            while(var13.hasNext()) {
                Object each = var13.next();
                key = each.toString();
                if (pragmaParams.contains(key)) {
                    String value = this.pragmaTable.getProperty(key);
                    if (value != null) {
                        stat.execute(String.format("pragma %s=%s", key, value));
                    }
                }
            }
        } finally {
            if (stat != null) {
                stat.close();
            }

        }

    }

    private void set(SQLiteConfig.Pragma pragma, boolean flag) {
        this.setPragma(pragma, Boolean.toString(flag));
    }

    private void set(SQLiteConfig.Pragma pragma, int num) {
        this.setPragma(pragma, Integer.toString(num));
    }

    private boolean getBoolean(SQLiteConfig.Pragma pragma, String defaultValue) {
        return Boolean.parseBoolean(this.pragmaTable.getProperty(pragma.pragmaName, defaultValue));
    }

    public boolean isEnabledSharedCache() {
        return this.getBoolean(SQLiteConfig.Pragma.SHARED_CACHE, "false");
    }

    public boolean isEnabledLoadExtension() {
        return this.getBoolean(SQLiteConfig.Pragma.LOAD_EXTENSION, "false");
    }

    public int getOpenModeFlags() {
        return this.openModeFlag;
    }

    public void setPragma(SQLiteConfig.Pragma pragma, String value) {
        this.pragmaTable.put(pragma.pragmaName, value);
    }

    public Properties toProperties() {
        this.pragmaTable.setProperty(SQLiteConfig.Pragma.OPEN_MODE.pragmaName, Integer.toString(this.openModeFlag));
        this.pragmaTable.setProperty(SQLiteConfig.Pragma.TRANSACTION_MODE.pragmaName, this.defaultConnectionConfig.getTransactionMode().getValue());
        this.pragmaTable.setProperty(SQLiteConfig.Pragma.DATE_CLASS.pragmaName, this.defaultConnectionConfig.getDateClass().getValue());
        this.pragmaTable.setProperty(SQLiteConfig.Pragma.DATE_PRECISION.pragmaName, this.defaultConnectionConfig.getDatePrecision().getValue());
        this.pragmaTable.setProperty(SQLiteConfig.Pragma.DATE_STRING_FORMAT.pragmaName, this.defaultConnectionConfig.getDateStringFormat());
        return this.pragmaTable;
    }

    static DriverPropertyInfo[] getDriverPropertyInfo() {
        SQLiteConfig.Pragma[] pragma = SQLiteConfig.Pragma.values();
        DriverPropertyInfo[] result = new DriverPropertyInfo[pragma.length];
        int index = 0;
        SQLiteConfig.Pragma[] var3 = SQLiteConfig.Pragma.values();
        int var4 = var3.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            SQLiteConfig.Pragma p = var3[var5];
            DriverPropertyInfo di = new DriverPropertyInfo(p.pragmaName, (String)null);
            di.choices = p.choices;
            di.description = p.description;
            di.required = false;
            result[index++] = di;
        }

        return result;
    }

    public void setOpenMode(SQLiteOpenMode mode) {
        this.openModeFlag |= mode.flag;
    }

    public void resetOpenMode(SQLiteOpenMode mode) {
        this.openModeFlag &= ~mode.flag;
    }

    public void setSharedCache(boolean enable) {
        this.set(SQLiteConfig.Pragma.SHARED_CACHE, enable);
    }

    public void enableLoadExtension(boolean enable) {
        this.set(SQLiteConfig.Pragma.LOAD_EXTENSION, enable);
    }

    public void setReadOnly(boolean readOnly) {
        if (readOnly) {
            this.setOpenMode(SQLiteOpenMode.READONLY);
            this.resetOpenMode(SQLiteOpenMode.CREATE);
            this.resetOpenMode(SQLiteOpenMode.READWRITE);
        } else {
            this.setOpenMode(SQLiteOpenMode.READWRITE);
            this.setOpenMode(SQLiteOpenMode.CREATE);
            this.resetOpenMode(SQLiteOpenMode.READONLY);
        }

    }

    public void setCacheSize(int numberOfPages) {
        this.set(SQLiteConfig.Pragma.CACHE_SIZE, numberOfPages);
    }

    public void enableCaseSensitiveLike(boolean enable) {
        this.set(SQLiteConfig.Pragma.CASE_SENSITIVE_LIKE, enable);
    }

    /** @deprecated */
    public void enableCountChanges(boolean enable) {
        this.set(SQLiteConfig.Pragma.COUNT_CHANGES, enable);
    }

    public void setDefaultCacheSize(int numberOfPages) {
        this.set(SQLiteConfig.Pragma.DEFAULT_CACHE_SIZE, numberOfPages);
    }

    /** @deprecated */
    public void enableEmptyResultCallBacks(boolean enable) {
        this.set(SQLiteConfig.Pragma.EMPTY_RESULT_CALLBACKS, enable);
    }

    private static String[] toStringArray(SQLiteConfig.PragmaValue[] list) {
        String[] result = new String[list.length];

        for(int i = 0; i < list.length; ++i) {
            result[i] = list[i].getValue();
        }

        return result;
    }

    public void setEncoding(SQLiteConfig.Encoding encoding) {
        this.setPragma(SQLiteConfig.Pragma.ENCODING, encoding.typeName);
    }

    public void enforceForeignKeys(boolean enforce) {
        this.set(SQLiteConfig.Pragma.FOREIGN_KEYS, enforce);
    }

    /** @deprecated */
    public void enableFullColumnNames(boolean enable) {
        this.set(SQLiteConfig.Pragma.FULL_COLUMN_NAMES, enable);
    }

    public void enableFullSync(boolean enable) {
        this.set(SQLiteConfig.Pragma.FULL_SYNC, enable);
    }

    public void incrementalVacuum(int numberOfPagesToBeRemoved) {
        this.set(SQLiteConfig.Pragma.INCREMENTAL_VACUUM, numberOfPagesToBeRemoved);
    }

    public void setJournalMode(SQLiteConfig.JournalMode mode) {
        this.setPragma(SQLiteConfig.Pragma.JOURNAL_MODE, mode.name());
    }

    public void setJounalSizeLimit(int limit) {
        this.set(SQLiteConfig.Pragma.JOURNAL_SIZE_LIMIT, limit);
    }

    public void useLegacyFileFormat(boolean use) {
        this.set(SQLiteConfig.Pragma.LEGACY_FILE_FORMAT, use);
    }

    public void setLockingMode(SQLiteConfig.LockingMode mode) {
        this.setPragma(SQLiteConfig.Pragma.LOCKING_MODE, mode.name());
    }

    public void setPageSize(int numBytes) {
        this.set(SQLiteConfig.Pragma.PAGE_SIZE, numBytes);
    }

    public void setMaxPageCount(int numPages) {
        this.set(SQLiteConfig.Pragma.MAX_PAGE_COUNT, numPages);
    }

    public void setReadUncommited(boolean useReadUncommitedIsolationMode) {
        this.set(SQLiteConfig.Pragma.READ_UNCOMMITED, useReadUncommitedIsolationMode);
    }

    public void enableRecursiveTriggers(boolean enable) {
        this.set(SQLiteConfig.Pragma.RECURSIVE_TRIGGERS, enable);
    }

    public void enableReverseUnorderedSelects(boolean enable) {
        this.set(SQLiteConfig.Pragma.REVERSE_UNORDERED_SELECTS, enable);
    }

    public void enableShortColumnNames(boolean enable) {
        this.set(SQLiteConfig.Pragma.SHORT_COLUMN_NAMES, enable);
    }

    public void setSynchronous(SQLiteConfig.SynchronousMode mode) {
        this.setPragma(SQLiteConfig.Pragma.SYNCHRONOUS, mode.name());
    }

    public void setHexKeyMode(SQLiteConfig.HexKeyMode mode) {
        this.setPragma(SQLiteConfig.Pragma.HEXKEY_MODE, mode.name());
    }

    public void setTempStore(SQLiteConfig.TempStore storeType) {
        this.setPragma(SQLiteConfig.Pragma.TEMP_STORE, storeType.name());
    }

    public void setTempStoreDirectory(String directoryName) {
        this.setPragma(SQLiteConfig.Pragma.TEMP_STORE_DIRECTORY, String.format("'%s'", directoryName));
    }

    public void setUserVersion(int version) {
        this.set(SQLiteConfig.Pragma.USER_VERSION, version);
    }

    public void setApplicationId(int id) {
        this.set(SQLiteConfig.Pragma.APPLICATION_ID, id);
    }

    public void setTransactionMode(SQLiteConfig.TransactionMode transactionMode) {
        this.defaultConnectionConfig.setTransactionMode(transactionMode);
    }

    public void setTransactionMode(String transactionMode) {
        this.setTransactionMode(SQLiteConfig.TransactionMode.getMode(transactionMode));
    }

    public SQLiteConfig.TransactionMode getTransactionMode() {
        return this.defaultConnectionConfig.getTransactionMode();
    }

    public void setDatePrecision(String datePrecision) throws SQLException {
        this.defaultConnectionConfig.setDatePrecision(SQLiteConfig.DatePrecision.getPrecision(datePrecision));
    }

    public void setDateClass(String dateClass) {
        this.defaultConnectionConfig.setDateClass(SQLiteConfig.DateClass.getDateClass(dateClass));
    }

    public void setDateStringFormat(String dateStringFormat) {
        this.defaultConnectionConfig.setDateStringFormat(dateStringFormat);
    }

    public void setBusyTimeout(int milliseconds) {
        this.setPragma(SQLiteConfig.Pragma.BUSY_TIMEOUT, Integer.toString(milliseconds));
    }

    public int getBusyTimeout() {
        return this.busyTimeout;
    }

    static {
        SQLiteConfig.Pragma[] var0 = SQLiteConfig.Pragma.values();
        int var1 = var0.length;

        for(int var2 = 0; var2 < var1; ++var2) {
            SQLiteConfig.Pragma pragma = var0[var2];
            pragmaSet.add(pragma.pragmaName);
        }

    }

    public static enum DateClass implements SQLiteConfig.PragmaValue {
        INTEGER,
        TEXT,
        REAL;

        private DateClass() {
        }

        public String getValue() {
            return this.name();
        }

        public static SQLiteConfig.DateClass getDateClass(String dateClass) {
            return valueOf(dateClass.toUpperCase());
        }
    }

    public static enum DatePrecision implements SQLiteConfig.PragmaValue {
        SECONDS,
        MILLISECONDS;

        private DatePrecision() {
        }

        public String getValue() {
            return this.name();
        }

        public static SQLiteConfig.DatePrecision getPrecision(String precision) {
            return valueOf(precision.toUpperCase());
        }
    }

    public static enum TransactionMode implements SQLiteConfig.PragmaValue {
        /** @deprecated */
        @Deprecated
        DEFFERED,
        DEFERRED,
        IMMEDIATE,
        EXCLUSIVE;

        private TransactionMode() {
        }

        public String getValue() {
            return this.name();
        }

        public static SQLiteConfig.TransactionMode getMode(String mode) {
            return "DEFFERED".equalsIgnoreCase(mode) ? DEFERRED : valueOf(mode.toUpperCase());
        }
    }

    public static enum HexKeyMode implements SQLiteConfig.PragmaValue {
        NONE,
        SSE,
        SQLCIPHER;

        private HexKeyMode() {
        }

        public String getValue() {
            return this.name();
        }
    }

    public static enum TempStore implements SQLiteConfig.PragmaValue {
        DEFAULT,
        FILE,
        MEMORY;

        private TempStore() {
        }

        public String getValue() {
            return this.name();
        }
    }

    public static enum SynchronousMode implements SQLiteConfig.PragmaValue {
        OFF,
        NORMAL,
        FULL;

        private SynchronousMode() {
        }

        public String getValue() {
            return this.name();
        }
    }

    public static enum LockingMode implements SQLiteConfig.PragmaValue {
        NORMAL,
        EXCLUSIVE;

        private LockingMode() {
        }

        public String getValue() {
            return this.name();
        }
    }

    public static enum JournalMode implements SQLiteConfig.PragmaValue {
        DELETE,
        TRUNCATE,
        PERSIST,
        MEMORY,
        WAL,
        OFF;

        private JournalMode() {
        }

        public String getValue() {
            return this.name();
        }
    }

    public static enum Encoding implements SQLiteConfig.PragmaValue {
        UTF8("'UTF-8'"),
        UTF16("'UTF-16'"),
        UTF16_LITTLE_ENDIAN("'UTF-16le'"),
        UTF16_BIG_ENDIAN("'UTF-16be'"),
        UTF_8(UTF8),
        UTF_16(UTF16),
        UTF_16LE(UTF16_LITTLE_ENDIAN),
        UTF_16BE(UTF16_BIG_ENDIAN);

        public final String typeName;

        private Encoding(String typeName) {
            this.typeName = typeName;
        }

        private Encoding(SQLiteConfig.Encoding encoding) {
            this.typeName = encoding.getValue();
        }

        public String getValue() {
            return this.typeName;
        }

        public static SQLiteConfig.Encoding getEncoding(String value) {
            return valueOf(value.replaceAll("-", "_").toUpperCase());
        }
    }

    private interface PragmaValue {
        String getValue();
    }

    public static enum Pragma {
        OPEN_MODE("open_mode", "Database open-mode flag", (String[])null),
        SHARED_CACHE("shared_cache", "Enable SQLite Shared-Cache mode, native driver only", SQLiteConfig.OnOff),
        LOAD_EXTENSION("enable_load_extension", "Enable SQLite load_extention() function, native driver only", SQLiteConfig.OnOff),
        CACHE_SIZE("cache_size"),
        MMAP_SIZE("mmap_size"),
        CASE_SENSITIVE_LIKE("case_sensitive_like", SQLiteConfig.OnOff),
        COUNT_CHANGES("count_changes", SQLiteConfig.OnOff),
        DEFAULT_CACHE_SIZE("default_cache_size"),
        EMPTY_RESULT_CALLBACKS("empty_result_callback", SQLiteConfig.OnOff),
        ENCODING("encoding", SQLiteConfig.toStringArray(SQLiteConfig.Encoding.values())),
        FOREIGN_KEYS("foreign_keys", SQLiteConfig.OnOff),
        FULL_COLUMN_NAMES("full_column_names", SQLiteConfig.OnOff),
        FULL_SYNC("fullsync", SQLiteConfig.OnOff),
        INCREMENTAL_VACUUM("incremental_vacuum"),
        JOURNAL_MODE("journal_mode", SQLiteConfig.toStringArray(SQLiteConfig.JournalMode.values())),
        JOURNAL_SIZE_LIMIT("journal_size_limit"),
        LEGACY_FILE_FORMAT("legacy_file_format", SQLiteConfig.OnOff),
        LOCKING_MODE("locking_mode", SQLiteConfig.toStringArray(SQLiteConfig.LockingMode.values())),
        PAGE_SIZE("page_size"),
        MAX_PAGE_COUNT("max_page_count"),
        READ_UNCOMMITED("read_uncommited", SQLiteConfig.OnOff),
        RECURSIVE_TRIGGERS("recursive_triggers", SQLiteConfig.OnOff),
        REVERSE_UNORDERED_SELECTS("reverse_unordered_selects", SQLiteConfig.OnOff),
        SECURE_DELETE("secure_delete", new String[]{"true", "false", "fast"}),
        SHORT_COLUMN_NAMES("short_column_names", SQLiteConfig.OnOff),
        SYNCHRONOUS("synchronous", SQLiteConfig.toStringArray(SQLiteConfig.SynchronousMode.values())),
        TEMP_STORE("temp_store", SQLiteConfig.toStringArray(SQLiteConfig.TempStore.values())),
        TEMP_STORE_DIRECTORY("temp_store_directory"),
        USER_VERSION("user_version"),
        APPLICATION_ID("application_id"),
        TRANSACTION_MODE("transaction_mode", SQLiteConfig.toStringArray(SQLiteConfig.TransactionMode.values())),
        DATE_PRECISION("date_precision", "\"seconds\": Read and store integer dates as seconds from the Unix Epoch (SQLite standard).\n\"milliseconds\": (DEFAULT) Read and store integer dates as milliseconds from the Unix Epoch (Java standard).", SQLiteConfig.toStringArray(SQLiteConfig.DatePrecision.values())),
        DATE_CLASS("date_class", "\"integer\": (Default) store dates as number of seconds or milliseconds from the Unix Epoch\n\"text\": store dates as a string of text\n\"real\": store dates as Julian Dates", SQLiteConfig.toStringArray(SQLiteConfig.DateClass.values())),
        DATE_STRING_FORMAT("date_string_format", "Format to store and retrieve dates stored as text. Defaults to \"yyyy-MM-dd HH:mm:ss.SSS\"", (String[])null),
        BUSY_TIMEOUT("busy_timeout", (String[])null),
        HEXKEY_MODE("hexkey_mode", SQLiteConfig.toStringArray(SQLiteConfig.HexKeyMode.values())),
        PASSWORD("password", (String[])null);

        public final String pragmaName;
        public final String[] choices;
        public final String description;

        private Pragma(String pragmaName) {
            this(pragmaName, (String[])null);
        }

        private Pragma(String pragmaName, String[] choices) {
            this(pragmaName, (String)null, choices);
        }

        private Pragma(String pragmaName, String description, String[] choices) {
            this.pragmaName = pragmaName;
            this.description = description;
            this.choices = choices;
        }

        public final String getPragmaName() {
            return this.pragmaName;
        }
    }
}
