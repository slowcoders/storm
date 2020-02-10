package org.slowcoders.storm;

public class ColumnAndValue {
    public final ORMColumn column;
    public final String value;
    public final boolean isQuotedText;

    public ColumnAndValue(ORMColumn column, String value, boolean isQuotedText) {
        this.column = column;
        this.value = value;
        this.isQuotedText = isQuotedText;
    }

    public ColumnAndValue(ORMColumn column, String value) {
        this(column, value, false);
    }
}
