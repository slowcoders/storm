//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.slowcoders.storm.jdbc.sqlite;

import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import org.slowcoders.storm.jdbc.sqlite.SQLiteConfig.DateClass;
import org.slowcoders.storm.jdbc.sqlite.SQLiteConfig.DatePrecision;
import org.slowcoders.storm.jdbc.sqlite.SQLiteConfig.Pragma;
import org.slowcoders.storm.jdbc.sqlite.SQLiteConfig.TransactionMode;
import org.slowcoders.storm.jdbc.sqlite.date.FastDateFormat;

public class SQLiteConnectionConfig implements Cloneable {
    private SQLiteConfig.DateClass dateClass;
    private SQLiteConfig.DatePrecision datePrecision;
    private String dateStringFormat;
    private FastDateFormat dateFormat;
    private int transactionIsolation;
    private SQLiteConfig.TransactionMode transactionMode;
    private boolean autoCommit;
    private static final Map<SQLiteConfig.TransactionMode, String> beginCommandMap = new EnumMap(SQLiteConfig.TransactionMode.class);

    public static SQLiteConnectionConfig fromPragmaTable(Properties pragmaTable) {
        return new SQLiteConnectionConfig(DateClass.getDateClass(pragmaTable.getProperty(Pragma.DATE_CLASS.pragmaName, DateClass.INTEGER.name())), DatePrecision.getPrecision(pragmaTable.getProperty(Pragma.DATE_PRECISION.pragmaName, DatePrecision.MILLISECONDS.name())), pragmaTable.getProperty(Pragma.DATE_STRING_FORMAT.pragmaName, "yyyy-MM-dd HH:mm:ss.SSS"), 8, TransactionMode.getMode(pragmaTable.getProperty(SQLiteConfig.Pragma.TRANSACTION_MODE.pragmaName, SQLiteConfig.TransactionMode.DEFERRED.name())), true);
    }

    public SQLiteConnectionConfig(DateClass dateClass, DatePrecision datePrecision, String dateStringFormat, int transactionIsolation, TransactionMode transactionMode, boolean autoCommit) {
        this.dateClass = DateClass.INTEGER;
        this.datePrecision = DatePrecision.MILLISECONDS;
        this.dateStringFormat = "yyyy-MM-dd HH:mm:ss.SSS";
        this.dateFormat = FastDateFormat.getInstance(this.dateStringFormat);
        this.transactionIsolation = 8;
        this.transactionMode = TransactionMode.DEFERRED;
        this.autoCommit = true;
        this.setDateClass(dateClass);
        this.setDatePrecision(datePrecision);
        this.setDateStringFormat(dateStringFormat);
        this.setTransactionIsolation(transactionIsolation);
        this.setTransactionMode(transactionMode);
        this.setAutoCommit(autoCommit);
    }

    public SQLiteConnectionConfig copyConfig() {
        return new SQLiteConnectionConfig(this.dateClass, this.datePrecision, this.dateStringFormat, this.transactionIsolation, this.transactionMode, this.autoCommit);
    }

    public long getDateMultiplier() {
        return this.datePrecision == DatePrecision.MILLISECONDS ? 1L : 1000L;
    }

    public DateClass getDateClass() {
        return this.dateClass;
    }

    public void setDateClass(DateClass dateClass) {
        this.dateClass = dateClass;
    }

    public DatePrecision getDatePrecision() {
        return this.datePrecision;
    }

    public void setDatePrecision(DatePrecision datePrecision) {
        this.datePrecision = datePrecision;
    }

    public String getDateStringFormat() {
        return this.dateStringFormat;
    }

    public void setDateStringFormat(String dateStringFormat) {
        this.dateStringFormat = dateStringFormat;
        this.dateFormat = FastDateFormat.getInstance(dateStringFormat);
    }

    public FastDateFormat getDateFormat() {
        return this.dateFormat;
    }

    public boolean isAutoCommit() {
        return this.autoCommit;
    }

    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public int getTransactionIsolation() {
        return this.transactionIsolation;
    }

    public void setTransactionIsolation(int transactionIsolation) {
        this.transactionIsolation = transactionIsolation;
    }

    public TransactionMode getTransactionMode() {
        return this.transactionMode;
    }

    public void setTransactionMode(TransactionMode transactionMode) {
        if (transactionMode == TransactionMode.DEFFERED) {
            transactionMode = TransactionMode.DEFERRED;
        }

        this.transactionMode = transactionMode;
    }

    String transactionPrefix() {
        return (String)beginCommandMap.get(this.transactionMode);
    }

    static {
        beginCommandMap.put(TransactionMode.DEFERRED, "begin;");
        beginCommandMap.put(TransactionMode.IMMEDIATE, "begin immediate;");
        beginCommandMap.put(TransactionMode.EXCLUSIVE, "begin exclusive;");
    }
}
