package org.slowcoders.util;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map.Entry;

public class SourceWriter  {

	StringBuilder buf;
	private int indent;
	private boolean atLineStart;

	public SourceWriter() {
		this.buf = new StringBuilder();
	}

	public void findAndReplace(HashMap<String, String> macros) {
		for (Entry<String, String> e : macros.entrySet()) {
			String key = e.getKey();
			String value = e.getValue();
			this.replaceAll(key, value);
		}
	}

	public void replaceAll(String key, String value) {
		int p = 0;
		int key_len = key.length();
		while (true) {
			p = buf.indexOf(key, p);
			if (p < 0) {
				return;
			}
			buf.replace(p, p + key_len, value);
			p += key_len;
		}
	}

	public void indentIn() {
		this.indent ++;
	}

	public void indentOut() {
		this.indent --;
	}

	public void removeTail(int len) {
		int L = buf.length() - len;
		buf.setLength(L);
		this.atLineStart = (--L >= 0 && buf.charAt(L) == '\n');
	}

	public void println(String text) throws IOException {
		print(text);
		println();
	}

	public SourceWriter printIf(boolean condition, String text) throws IOException {
		if (condition) {
			print(text);
		}
		return this;
	}

	public SourceWriter print(Object... args) throws IOException {
		for (Object o : args) {
			print(o.toString());
		}
		return this;
	}

	public SourceWriter println(Object... args) throws IOException {
		this.print(args);
		println();
		return this;
	}
	
	public SourceWriter print(String text) throws IOException {
		Debug.Assert(text != null);
		int tab = this.indent;
		int t_len = text.length();

		int R;
		for (int i = 0; i < t_len; i ++) {
			char ch = text.charAt(i);
			if (ch == '}' || ch == ')') {
				this.indent --;
			}

			if (atLineStart) {
				atLineStart = false;
				for (tab = this.indent; --tab >= 0;) buf.append('\t');
			}

			if (ch == '{' || ch == '(') {
				this.indent ++; 
			}
			buf.append(ch);
			this.atLineStart = (ch == '\n');
		}

		return this;
	}

	public void println() throws IOException {
		buf.append('\n');
		this.atLineStart = true;
	}


	public void writeAndClear(Writer out) throws IOException {
		out.write(buf.toString());
		buf.setLength(0);
	}

	
	public String toString() {
		return this.buf.toString();
	}
	
	public void clear() {
		buf.setLength(0);
	}

	public int getBufferdLength() {
		return buf.length();
	}
}