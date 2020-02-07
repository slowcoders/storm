package org.slowcoders.storm.jdbc;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import org.slowcoders.io.serialize.IOAdapter;
import org.slowcoders.io.serialize.DataReader;
import org.slowcoders.io.serialize.StreamReader;
import org.slowcoders.util.Debug;
import org.slowcoders.storm.EntitySnapshot;
import org.slowcoders.storm.ORMColumn;

public class JDBCReader extends DataReader {

	int colIndex;
	private ResultSet rs;
	
	JDBCReader(ResultSet rs) {
		super(IOAdapter.getLoader(true));
		this.rs = rs;
	}

	@Override
	public boolean wasNull() throws Exception {
		return rs.wasNull();
	}
	
	void readEntity(EntitySnapshot entity, ORMColumn[] columns, int baseOffset) throws SQLException {
		try {
			super.setContext_unsafe(entity);
			ResultSetMetaData md = rs.getMetaData();
			for (ORMColumn col : columns) {
				this.colIndex = baseOffset + col.getColumnIndex();
				if (Debug.DEBUG) {
					String key = md.getColumnName(this.colIndex);
					Debug.Assert(key.equals(col.getKey()));
				}
				entity.setProperty(col, this);
			}
		} catch (SQLException | RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw Debug.wtf(e);
		}
	}
	
	@Override
	public boolean readBoolean() throws Exception {
		boolean v = rs.getBoolean(colIndex);
		return v;
	}
	
	@Override
	public byte readByte() throws Exception {
		byte v = rs.getByte(colIndex);
		return v;
	}
	
	@Override
	public short readShort() throws Exception {
		short v = rs.getShort(colIndex);
		return v;
	}
	
	@Override
	public char readChar() throws Exception {
		int v = rs.getInt(colIndex);
		return (char)v;
	}
	
	@Override
	public int readInt() throws Exception {
		int v = rs.getInt(colIndex);
		return v;
	}
	
	@Override
	public long readLong() throws Exception {
		long v = rs.getLong(colIndex);
		return v;
	}
	
	@Override
	public float readFloat() throws Exception {
		float v = rs.getFloat(colIndex);
		return v;
	}
	
	@Override
	public double readDouble() throws Exception {
		double v = rs.getDouble(colIndex);
		return v;
	}
	
	
	@Override
	public Object readPrimitiveOrStrings() throws Exception {
		return rs.getObject(colIndex);
	}

	@Override
	public String readString() throws Exception {
		return rs.getString(colIndex);
	}
	
	@Override
	public Number readNumber() throws Exception {
		return (Number)rs.getObject(colIndex);
	}

	@Override
	public boolean[] readBooleanArray() throws Exception {
		byte[] bytes = this.readByteArray();
		if (bytes == null) {
			return null;
		}
		boolean[] array = new boolean[bytes.length];
		for (int i = 0; i < bytes.length; i++) {
			array[i] = bytes[i] != 0;
		}
		return array;
	}

	@Override
	public byte[] readByteArray() throws Exception {
		return rs.getBytes(colIndex);
	}

	@Override
	public short[] readShortArray() throws Exception {
		byte[] bytes = this.readByteArray();
		if (bytes == null) {
			return null;
		}
		ByteBuffer buf = ByteBuffer.wrap(bytes);
		return buf.asShortBuffer().array();
	}

	@Override
	public char[] readCharArray() throws Exception {
		byte[] bytes = this.readByteArray();
		if (bytes == null) {
			return null;
		}
		ByteBuffer buf = ByteBuffer.wrap(bytes);
		return buf.asCharBuffer().array();
	}

	@Override
	public int[] readIntArray() throws Exception {
		byte[] bytes = this.readByteArray();
		if (bytes == null) {
			return null;
		}
		ByteBuffer buf = ByteBuffer.wrap(bytes);
		return buf.asIntBuffer().array();
	}

	@Override
	public long[] readLongArray() throws Exception {
		byte[] bytes = this.readByteArray();
		if (bytes == null) {
			return null;
		}
		ByteBuffer buf = ByteBuffer.wrap(bytes);
		return buf.asLongBuffer().array();
	}

	@Override
	public float[] readFloatArray() throws Exception {
		byte[] bytes = this.readByteArray();
		if (bytes == null) {
			return null;
		}
		ByteBuffer buf = ByteBuffer.wrap(bytes);
		return buf.asFloatBuffer().array();
	}

	@Override
	public double[] readDoubleArray() throws Exception {
		byte[] bytes = this.readByteArray();
		if (bytes == null) {
			return null;
		}
		ByteBuffer buf = ByteBuffer.wrap(bytes);
		return buf.asDoubleBuffer().array();
	}

	@Override
	public String[] readStringArray() throws Exception {
		String str = this.readString();
		if (str == null) {
			return null;
		}
		String[] array = str.split("\0");
		return array;
	}
	
	@Override
	public AutoCloseStream openChunkedStream() throws Exception {
		byte[] bytes = this.readByteArray();
		if (bytes == null) {
			return null;
		}
		
		StreamReader in = new StreamReader(this.getLoader(), bytes);
		return in;
	}

	public final ResultSet getResultSet() {
		return this.rs;
	}


	public Object[] readColumns(ORMColumn[] columns) throws Exception {
		Object[] arr = new Object[columns.length];

		for (int idx = 0; idx < columns.length; idx++) {
			ORMColumn col = columns[idx];
				this.colIndex = idx + JDBCTable.COLUMN_START;
			Object v = col.getAdapter().read(this);
			arr[idx] = v;
		}
		return arr;
	}
}
