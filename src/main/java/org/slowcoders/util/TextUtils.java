package org.slowcoders.util;

import java.util.ArrayList;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class TextUtils {

	public static String toHexaString(byte[] bytes) {
		if (bytes == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(toSingleHexaDigit(b >> 4));
			sb.append(toSingleHexaDigit(b));
		}
		return sb.toString();
	}
	
	public static byte[] hexaDigitsToByteArray(String digits) {
		byte[] data = new byte[digits.length()/2];
		for (int i = 0; i < data.length; i ++) {
			char c1 = digits.charAt(i*2);
			char c2 = digits.charAt(i*2+1);
			Debug.Assert(TextUtils.isHexaDigit(c1));
			Debug.Assert(TextUtils.isHexaDigit(c2));
			int bb = ((c1 & 0xF) << 4) + (c2 & 0xF);
			data[i] = (byte)bb;
		}
		return data;
	}
	
	public static char toSingleHexaDigit(int c) {
		c &= 0xF;
		if (c < 10) {
			return (char)(c + '0');
		}
		else {
			return (char)(c - 10 + 'A');
		}
	}
	
	public static ArrayList<String> toStringList(String text, char delimiter, boolean trimWhitespace) {
		ArrayList<String> list = new ArrayList<String>();
		
		int left = 0;
		while (true) {
			int right = text.indexOf(delimiter, left);
			if (right < 0) {
				break;
			}
			String s = text.substring(left, right);
			if (trimWhitespace) {
				s = s.trim();
			}
			list.add(s);
			left = right + 1;
		}
		return list;
	}

    private static final DateTimeFormatter utcFormat = 
    		DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ").withZoneUTC();
    
	public static String toUniversalTime(DateTime time) {
		return utcFormat.print(time.getMillis());
	}

	public static char getLastHexDigit(int i) {
		i &= 0xF;
		if (i < 10) {
			return (char)('0' + i);
		}
		else {
			return (char)('A' + (i - 10));
		}
	}

	public static boolean isHexaDigit(char c1) {
		// TODO Auto-generated method stub
		return false;
	}

}
