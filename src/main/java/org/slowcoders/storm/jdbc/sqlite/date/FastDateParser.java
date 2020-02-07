//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.slowcoders.storm.jdbc.sqlite.date;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FastDateParser implements DateParser, Serializable {
    private static final long serialVersionUID = 2L;
    static final Locale JAPANESE_IMPERIAL = new Locale("ja", "JP", "JP");
    private final String pattern;
    private final TimeZone timeZone;
    private final Locale locale;
    private final int century;
    private final int startYear;
    private transient Pattern parsePattern;
    private transient FastDateParser.Strategy[] strategies;
    private transient String currentFormatField;
    private transient FastDateParser.Strategy nextStrategy;
    private static final Pattern formatPattern = Pattern.compile("D+|E+|F+|G+|H+|K+|M+|S+|W+|X+|Z+|a+|d+|h+|k+|m+|s+|w+|y+|z+|''|'[^']++(''[^']*+)*+'|[^'A-Za-z]++");
    private static final ConcurrentMap<Locale, FastDateParser.Strategy>[] caches = new ConcurrentMap[17];
    private static final FastDateParser.Strategy ABBREVIATED_YEAR_STRATEGY = new FastDateParser.NumberStrategy(1) {
        void setCalendar(FastDateParser parser, Calendar cal, String value) {
            int iValue = Integer.parseInt(value);
            if (iValue < 100) {
                iValue = parser.adjustYear(iValue);
            }

            cal.set(1, iValue);
        }
    };
    private static final FastDateParser.Strategy NUMBER_MONTH_STRATEGY = new FastDateParser.NumberStrategy(2) {
        int modify(int iValue) {
            return iValue - 1;
        }
    };
    private static final FastDateParser.Strategy LITERAL_YEAR_STRATEGY = new FastDateParser.NumberStrategy(1);
    private static final FastDateParser.Strategy WEEK_OF_YEAR_STRATEGY = new FastDateParser.NumberStrategy(3);
    private static final FastDateParser.Strategy WEEK_OF_MONTH_STRATEGY = new FastDateParser.NumberStrategy(4);
    private static final FastDateParser.Strategy DAY_OF_YEAR_STRATEGY = new FastDateParser.NumberStrategy(6);
    private static final FastDateParser.Strategy DAY_OF_MONTH_STRATEGY = new FastDateParser.NumberStrategy(5);
    private static final FastDateParser.Strategy DAY_OF_WEEK_IN_MONTH_STRATEGY = new FastDateParser.NumberStrategy(8);
    private static final FastDateParser.Strategy HOUR_OF_DAY_STRATEGY = new FastDateParser.NumberStrategy(11);
    private static final FastDateParser.Strategy HOUR24_OF_DAY_STRATEGY = new FastDateParser.NumberStrategy(11) {
        int modify(int iValue) {
            return iValue == 24 ? 0 : iValue;
        }
    };
    private static final FastDateParser.Strategy HOUR12_STRATEGY = new FastDateParser.NumberStrategy(10) {
        int modify(int iValue) {
            return iValue == 12 ? 0 : iValue;
        }
    };
    private static final FastDateParser.Strategy HOUR_STRATEGY = new FastDateParser.NumberStrategy(10);
    private static final FastDateParser.Strategy MINUTE_STRATEGY = new FastDateParser.NumberStrategy(12);
    private static final FastDateParser.Strategy SECOND_STRATEGY = new FastDateParser.NumberStrategy(13);
    private static final FastDateParser.Strategy MILLISECOND_STRATEGY = new FastDateParser.NumberStrategy(14);
    private static final FastDateParser.Strategy ISO_8601_STRATEGY = new FastDateParser.ISO8601TimeZoneStrategy("(Z|(?:[+-]\\d{2}(?::?\\d{2})?))");

    protected FastDateParser(String pattern, TimeZone timeZone, Locale locale) {
        this(pattern, timeZone, locale, (Date)null);
    }

    protected FastDateParser(String pattern, TimeZone timeZone, Locale locale, Date centuryStart) {
        this.pattern = pattern;
        this.timeZone = timeZone;
        this.locale = locale;
        Calendar definingCalendar = Calendar.getInstance(timeZone, locale);
        int centuryStartYear;
        if (centuryStart != null) {
            definingCalendar.setTime(centuryStart);
            centuryStartYear = definingCalendar.get(1);
        } else if (locale.equals(JAPANESE_IMPERIAL)) {
            centuryStartYear = 0;
        } else {
            definingCalendar.setTime(new Date());
            centuryStartYear = definingCalendar.get(1) - 80;
        }

        this.century = centuryStartYear / 100 * 100;
        this.startYear = centuryStartYear - this.century;
        this.init(definingCalendar);
    }

    private void init(Calendar definingCalendar) {
        StringBuilder regex = new StringBuilder();
        List<FastDateParser.Strategy> collector = new ArrayList();
        Matcher patternMatcher = formatPattern.matcher(this.pattern);
        if (!patternMatcher.lookingAt()) {
            throw new IllegalArgumentException("Illegal pattern character '" + this.pattern.charAt(patternMatcher.regionStart()) + "'");
        } else {
            this.currentFormatField = patternMatcher.group();
            FastDateParser.Strategy currentStrategy = this.getStrategy(this.currentFormatField, definingCalendar);

            while(true) {
                patternMatcher.region(patternMatcher.end(), patternMatcher.regionEnd());
                if (!patternMatcher.lookingAt()) {
                    this.nextStrategy = null;
                    if (patternMatcher.regionStart() != patternMatcher.regionEnd()) {
                        throw new IllegalArgumentException("Failed to parse \"" + this.pattern + "\" ; gave up at index " + patternMatcher.regionStart());
                    }

                    if (currentStrategy.addRegex(this, regex)) {
                        collector.add(currentStrategy);
                    }

                    this.currentFormatField = null;
                    this.strategies = (FastDateParser.Strategy[])collector.toArray(new FastDateParser.Strategy[collector.size()]);
                    this.parsePattern = Pattern.compile(regex.toString());
                    return;
                }

                String nextFormatField = patternMatcher.group();
                this.nextStrategy = this.getStrategy(nextFormatField, definingCalendar);
                if (currentStrategy.addRegex(this, regex)) {
                    collector.add(currentStrategy);
                }

                this.currentFormatField = nextFormatField;
                currentStrategy = this.nextStrategy;
            }
        }
    }

    public String getPattern() {
        return this.pattern;
    }

    public TimeZone getTimeZone() {
        return this.timeZone;
    }

    public Locale getLocale() {
        return this.locale;
    }

    Pattern getParsePattern() {
        return this.parsePattern;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof FastDateParser)) {
            return false;
        } else {
            FastDateParser other = (FastDateParser)obj;
            return this.pattern.equals(other.pattern) && this.timeZone.equals(other.timeZone) && this.locale.equals(other.locale);
        }
    }

    public int hashCode() {
        return this.pattern.hashCode() + 13 * (this.timeZone.hashCode() + 13 * this.locale.hashCode());
    }

    public String toString() {
        return "FastDateParser[" + this.pattern + "," + this.locale + "," + this.timeZone.getID() + "]";
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        Calendar definingCalendar = Calendar.getInstance(this.timeZone, this.locale);
        this.init(definingCalendar);
    }

    public Object parseObject(String source) throws ParseException {
        return this.parse(source);
    }

    public Date parse(String source) throws ParseException {
        Date date = this.parse(source, new ParsePosition(0));
        if (date == null) {
            if (this.locale.equals(JAPANESE_IMPERIAL)) {
                throw new ParseException("(The " + this.locale + " locale does not support dates before 1868 AD)\nUnparseable date: \"" + source + "\" does not match " + this.parsePattern.pattern(), 0);
            } else {
                throw new ParseException("Unparseable date: \"" + source + "\" does not match " + this.parsePattern.pattern(), 0);
            }
        } else {
            return date;
        }
    }

    public Object parseObject(String source, ParsePosition pos) {
        return this.parse(source, pos);
    }

    public Date parse(String source, ParsePosition pos) {
        int offset = pos.getIndex();
        Matcher matcher = this.parsePattern.matcher(source.substring(offset));
        if (!matcher.lookingAt()) {
            return null;
        } else {
            Calendar cal = Calendar.getInstance(this.timeZone, this.locale);
            cal.clear();
            int i = 0;

            while(i < this.strategies.length) {
                FastDateParser.Strategy strategy = this.strategies[i++];
                strategy.setCalendar(this, cal, matcher.group(i));
            }

            pos.setIndex(offset + matcher.end());
            return cal.getTime();
        }
    }

    private static StringBuilder escapeRegex(StringBuilder regex, String value, boolean unquote) {
        regex.append("\\Q");

        for(int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            switch(c) {
                case '\'':
                    if (unquote) {
                        ++i;
                        if (i == value.length()) {
                            return regex;
                        }

                        c = value.charAt(i);
                    }
                    break;
                case '\\':
                    ++i;
                    if (i != value.length()) {
                        regex.append(c);
                        c = value.charAt(i);
                        if (c == 'E') {
                            regex.append("E\\\\E\\");
                            c = 'Q';
                        }
                    }
            }

            regex.append(c);
        }

        regex.append("\\E");
        return regex;
    }

    private static Map<String, Integer> getDisplayNames(int field, Calendar definingCalendar, Locale locale) {
        return definingCalendar.getDisplayNames(field, 0, locale);
    }

    private int adjustYear(int twoDigitYear) {
        int trial = this.century + twoDigitYear;
        return twoDigitYear >= this.startYear ? trial : trial + 100;
    }

    boolean isNextNumber() {
        return this.nextStrategy != null && this.nextStrategy.isNumber();
    }

    int getFieldWidth() {
        return this.currentFormatField.length();
    }

    private FastDateParser.Strategy getStrategy(String formatField, Calendar definingCalendar) {
        switch(formatField.charAt(0)) {
            case '\'':
                if (formatField.length() > 2) {
                    return new FastDateParser.CopyQuotedStrategy(formatField.substring(1, formatField.length() - 1));
                }
            case '(':
            case ')':
            case '*':
            case '+':
            case ',':
            case '-':
            case '.':
            case '/':
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case ':':
            case ';':
            case '<':
            case '=':
            case '>':
            case '?':
            case '@':
            case 'A':
            case 'B':
            case 'C':
            case 'I':
            case 'J':
            case 'L':
            case 'N':
            case 'O':
            case 'P':
            case 'Q':
            case 'R':
            case 'T':
            case 'U':
            case 'V':
            case 'Y':
            case '[':
            case '\\':
            case ']':
            case '^':
            case '_':
            case '`':
            case 'b':
            case 'c':
            case 'e':
            case 'f':
            case 'g':
            case 'i':
            case 'j':
            case 'l':
            case 'n':
            case 'o':
            case 'p':
            case 'q':
            case 'r':
            case 't':
            case 'u':
            case 'v':
            case 'x':
            default:
                return new FastDateParser.CopyQuotedStrategy(formatField);
            case 'D':
                return DAY_OF_YEAR_STRATEGY;
            case 'E':
                return this.getLocaleSpecificStrategy(7, definingCalendar);
            case 'F':
                return DAY_OF_WEEK_IN_MONTH_STRATEGY;
            case 'G':
                return this.getLocaleSpecificStrategy(0, definingCalendar);
            case 'H':
                return HOUR_OF_DAY_STRATEGY;
            case 'K':
                return HOUR_STRATEGY;
            case 'M':
                return formatField.length() >= 3 ? this.getLocaleSpecificStrategy(2, definingCalendar) : NUMBER_MONTH_STRATEGY;
            case 'S':
                return MILLISECOND_STRATEGY;
            case 'W':
                return WEEK_OF_MONTH_STRATEGY;
            case 'X':
                return FastDateParser.ISO8601TimeZoneStrategy.getStrategy(formatField.length());
            case 'Z':
                if (formatField.equals("ZZ")) {
                    return ISO_8601_STRATEGY;
                }
            case 'z':
                return this.getLocaleSpecificStrategy(15, definingCalendar);
            case 'a':
                return this.getLocaleSpecificStrategy(9, definingCalendar);
            case 'd':
                return DAY_OF_MONTH_STRATEGY;
            case 'h':
                return HOUR12_STRATEGY;
            case 'k':
                return HOUR24_OF_DAY_STRATEGY;
            case 'm':
                return MINUTE_STRATEGY;
            case 's':
                return SECOND_STRATEGY;
            case 'w':
                return WEEK_OF_YEAR_STRATEGY;
            case 'y':
                return formatField.length() > 2 ? LITERAL_YEAR_STRATEGY : ABBREVIATED_YEAR_STRATEGY;
        }
    }

    private static ConcurrentMap<Locale, FastDateParser.Strategy> getCache(int field) {
        synchronized(caches) {
            if (caches[field] == null) {
                caches[field] = new ConcurrentHashMap(3);
            }

            return caches[field];
        }
    }

    private FastDateParser.Strategy getLocaleSpecificStrategy(int field, Calendar definingCalendar) {
        ConcurrentMap<Locale, FastDateParser.Strategy> cache = getCache(field);
        FastDateParser.Strategy strategy = (FastDateParser.Strategy)cache.get(this.locale);
        if (strategy == null) {
            strategy = field == 15 ? new FastDateParser.TimeZoneStrategy(this.locale) : new FastDateParser.CaseInsensitiveTextStrategy(field, definingCalendar, this.locale);
            FastDateParser.Strategy inCache = (FastDateParser.Strategy)cache.putIfAbsent(this.locale, strategy);
            if (inCache != null) {
                return inCache;
            }
        }

        return (FastDateParser.Strategy)strategy;
    }

    private static class ISO8601TimeZoneStrategy extends FastDateParser.Strategy {
        private final String pattern;
        private static final FastDateParser.Strategy ISO_8601_1_STRATEGY = new FastDateParser.ISO8601TimeZoneStrategy("(Z|(?:[+-]\\d{2}))");
        private static final FastDateParser.Strategy ISO_8601_2_STRATEGY = new FastDateParser.ISO8601TimeZoneStrategy("(Z|(?:[+-]\\d{2}\\d{2}))");
        private static final FastDateParser.Strategy ISO_8601_3_STRATEGY = new FastDateParser.ISO8601TimeZoneStrategy("(Z|(?:[+-]\\d{2}(?::)\\d{2}))");

        ISO8601TimeZoneStrategy(String pattern) {
            super();
            this.pattern = pattern;
        }

        boolean addRegex(FastDateParser parser, StringBuilder regex) {
            regex.append(this.pattern);
            return true;
        }

        void setCalendar(FastDateParser parser, Calendar cal, String value) {
            if (value.equals("Z")) {
                cal.setTimeZone(TimeZone.getTimeZone("UTC"));
            } else {
                cal.setTimeZone(TimeZone.getTimeZone("GMT" + value));
            }

        }

        static FastDateParser.Strategy getStrategy(int tokenLen) {
            switch(tokenLen) {
                case 1:
                    return ISO_8601_1_STRATEGY;
                case 2:
                    return ISO_8601_2_STRATEGY;
                case 3:
                    return ISO_8601_3_STRATEGY;
                default:
                    throw new IllegalArgumentException("invalid number of X");
            }
        }
    }

    private static class TimeZoneStrategy extends FastDateParser.Strategy {
        private final String validTimeZoneChars;
        private final SortedMap<String, TimeZone> tzNames;
        private static final int ID = 0;
        private static final int LONG_STD = 1;
        private static final int SHORT_STD = 2;
        private static final int LONG_DST = 3;
        private static final int SHORT_DST = 4;

        TimeZoneStrategy(Locale locale) {
            super();
            this.tzNames = new TreeMap(String.CASE_INSENSITIVE_ORDER);
            String[][] zones = DateFormatSymbols.getInstance(locale).getZoneStrings();
            String[][] var3 = zones;
            int var4 = zones.length;

            for(int var5 = 0; var5 < var4; ++var5) {
                String[] zone = var3[var5];
                if (!zone[0].startsWith("GMT")) {
                    TimeZone tz = TimeZone.getTimeZone(zone[0]);
                    if (!this.tzNames.containsKey(zone[1])) {
                        this.tzNames.put(zone[1], tz);
                    }

                    if (!this.tzNames.containsKey(zone[2])) {
                        this.tzNames.put(zone[2], tz);
                    }

                    if (tz.useDaylightTime()) {
                        if (!this.tzNames.containsKey(zone[3])) {
                            this.tzNames.put(zone[3], tz);
                        }

                        if (!this.tzNames.containsKey(zone[4])) {
                            this.tzNames.put(zone[4], tz);
                        }
                    }
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("(GMT[+-]\\d{1,2}:\\d{2}").append('|');
            sb.append("[+-]\\d{4}").append('|');
            Iterator var9 = this.tzNames.keySet().iterator();

            while(var9.hasNext()) {
                String id = (String)var9.next();
                FastDateParser.escapeRegex(sb, id, false).append('|');
            }

            sb.setCharAt(sb.length() - 1, ')');
            this.validTimeZoneChars = sb.toString();
        }

        boolean addRegex(FastDateParser parser, StringBuilder regex) {
            regex.append(this.validTimeZoneChars);
            return true;
        }

        void setCalendar(FastDateParser parser, Calendar cal, String value) {
            TimeZone tz;
            if (value.charAt(0) != '+' && value.charAt(0) != '-') {
                if (value.startsWith("GMT")) {
                    tz = TimeZone.getTimeZone(value);
                } else {
                    tz = (TimeZone)this.tzNames.get(value);
                    if (tz == null) {
                        throw new IllegalArgumentException(value + " is not a supported timezone name");
                    }
                }
            } else {
                tz = TimeZone.getTimeZone("GMT" + value);
            }

            cal.setTimeZone(tz);
        }
    }

    private static class NumberStrategy extends FastDateParser.Strategy {
        private final int field;

        NumberStrategy(int field) {
            super();
            this.field = field;
        }

        boolean isNumber() {
            return true;
        }

        boolean addRegex(FastDateParser parser, StringBuilder regex) {
            if (parser.isNextNumber()) {
                regex.append("(\\p{Nd}{").append(parser.getFieldWidth()).append("}+)");
            } else {
                regex.append("(\\p{Nd}++)");
            }

            return true;
        }

        void setCalendar(FastDateParser parser, Calendar cal, String value) {
            cal.set(this.field, this.modify(Integer.parseInt(value)));
        }

        int modify(int iValue) {
            return iValue;
        }
    }

    private static class CaseInsensitiveTextStrategy extends FastDateParser.Strategy {
        private final int field;
        private final Locale locale;
        private final Map<String, Integer> lKeyValues;

        CaseInsensitiveTextStrategy(int field, Calendar definingCalendar, Locale locale) {
            super();
            this.field = field;
            this.locale = locale;
            Map<String, Integer> keyValues = FastDateParser.getDisplayNames(field, definingCalendar, locale);
            this.lKeyValues = new HashMap();
            Iterator var5 = keyValues.entrySet().iterator();

            while(var5.hasNext()) {
                Entry<String, Integer> entry = (Entry)var5.next();
                this.lKeyValues.put(((String)entry.getKey()).toLowerCase(locale), entry.getValue());
            }

        }

        boolean addRegex(FastDateParser parser, StringBuilder regex) {
            regex.append("((?iu)");
            Iterator var3 = this.lKeyValues.keySet().iterator();

            while(var3.hasNext()) {
                String textKeyValue = (String)var3.next();
                FastDateParser.escapeRegex(regex, textKeyValue, false).append('|');
            }

            regex.setCharAt(regex.length() - 1, ')');
            return true;
        }

        void setCalendar(FastDateParser parser, Calendar cal, String value) {
            Integer iVal = (Integer)this.lKeyValues.get(value.toLowerCase(this.locale));
            if (iVal != null) {
                cal.set(this.field, iVal);
            } else {
                StringBuilder sb = new StringBuilder(value);
                sb.append(" not in (");
                Iterator var6 = this.lKeyValues.keySet().iterator();

                while(var6.hasNext()) {
                    String textKeyValue = (String)var6.next();
                    sb.append(textKeyValue).append(' ');
                }

                sb.setCharAt(sb.length() - 1, ')');
                throw new IllegalArgumentException(sb.toString());
            }
        }
    }

    private static class CopyQuotedStrategy extends FastDateParser.Strategy {
        private final String formatField;

        CopyQuotedStrategy(String formatField) {
            super();
            this.formatField = formatField;
        }

        boolean isNumber() {
            char c = this.formatField.charAt(0);
            if (c == '\'') {
                c = this.formatField.charAt(1);
            }

            return Character.isDigit(c);
        }

        boolean addRegex(FastDateParser parser, StringBuilder regex) {
            FastDateParser.escapeRegex(regex, this.formatField, true);
            return false;
        }
    }

    private abstract static class Strategy {
        private Strategy() {
        }

        boolean isNumber() {
            return false;
        }

        void setCalendar(FastDateParser parser, Calendar cal, String value) {
        }

        abstract boolean addRegex(FastDateParser var1, StringBuilder var2);
    }
}
