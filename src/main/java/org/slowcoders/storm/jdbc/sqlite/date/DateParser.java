//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.slowcoders.storm.jdbc.sqlite.date;

import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public interface DateParser {
    Date parse(String var1) throws ParseException;

    Date parse(String var1, ParsePosition var2);

    String getPattern();

    TimeZone getTimeZone();

    Locale getLocale();

    Object parseObject(String var1) throws ParseException;

    Object parseObject(String var1, ParsePosition var2);
}
