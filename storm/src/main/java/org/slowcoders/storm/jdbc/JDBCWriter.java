package org.slowcoders.storm.jdbc;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.slowcoders.io.serialize.DataWriter;
import org.slowcoders.io.serialize.IOAdapter;
import org.slowcoders.io.serialize.IOEntity;
import org.slowcoders.io.serialize.StreamWriter;
import org.slowcoders.storm.AbstractColumn;
import org.slowcoders.storm.EntitySnapshot;
import org.slowcoders.storm.EntityEditor;
import org.slowcoders.util.Debug;

public class JDBCWriter extends DataWriter {

	int colIndex;
	PreparedStatement stmt;
	
	JDBCWriter(PreparedStatement stmt) {
		super(IOAdapter.getLoader(false));
		this.stmt = stmt;
		this.colIndex = JDBCTable.COLUMN_START;
	}
	
	public void write(EntitySnapshot entity, AbstractColumn col) throws SQLException {
		try {
			IOEntity.writeProperty_unsafe(col.getColumn(), entity, this);
			this.colIndex ++;
		} catch (SQLException | RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw Debug.wtf(e);
		}
	}

	public void write(EntityEditor editor, AbstractColumn col) throws SQLException {
		try {
			editor.writeProperty(col.getColumn(), this);
			this.colIndex++;
		} catch (Exception e) {
			throw Debug.wtf(e);
		}
	}

	final void incColumnIndex() {
		this.colIndex ++;
	}
	
	@Override
	public void writeBoolean(boolean value) throws Exception {
		stmt.setBoolean(colIndex, value);
	}

	@Override
	public void writeByte(byte value) throws Exception {
		stmt.setByte(colIndex, value);
	}

	@Override
	public void writeChar(char value) throws Exception {
		stmt.setInt(colIndex, value);
	}

	@Override
	public void writeShort(short value) throws Exception {
		stmt.setShort(colIndex, value);
	}

	@Override
	public void writeInt(int value) throws Exception {
		stmt.setInt(colIndex, value);
	}

	@Override
	public void writeLong(long value) throws Exception {
		stmt.setLong(colIndex, value);
	}

	@Override
	public void writeFloat(float value) throws Exception {
		stmt.setFloat(colIndex, value);
	}

	@Override
	public void writeDouble(double value) throws Exception {
		stmt.setDouble(colIndex, value);
	}

	@Override
	public void writeString(String value) throws Exception {
		stmt.setString(colIndex, value);
	}

	@Override
	public void writeNumber(Number value) throws Exception {
		stmt.setObject(colIndex, value);
	}

	@Override
	public void writeNull() throws Exception {
		stmt.setObject(colIndex, null);
	}
	
	private boolean writeNullArray(Object array) throws Exception {
		if (array == null) {
			stmt.setBytes(colIndex, null);
			return true;
		}
		return false;
	}
	
	public void writeBooleanArray(boolean[] values) throws Exception {
		if (writeNullArray(values)) return;
		
		ByteBuffer buff = ByteBuffer.allocate(values.length * 1);
		for (int i = 0; i < values.length; i ++) {
			buff.put(values[i] ? (byte)1 : (byte)0);
		}
		this.writeByteArray(buff.array());
	}
	

	public void writeByteArray(byte[] values) throws Exception {
		if (writeNullArray(values)) return;

		stmt.setBytes(colIndex, values);
	}
	
	public void writeCharArray(char[] values) throws Exception {
		if (writeNullArray(values)) return;

		stmt.setString(colIndex, new String(values));
	}
	
	public void writeShortArray(short[] values) throws Exception {
		ByteBuffer buff = ByteBuffer.allocate(values.length * 2);
		buff.asShortBuffer().put(values);
		this.writeByteArray(buff.array());
	}
	
	public void writeIntArray(int[] values) throws Exception {
		if (writeNullArray(values)) return;

		ByteBuffer buff = ByteBuffer.allocate(values.length * 4);
		buff.asIntBuffer().put(values);
		this.writeByteArray(buff.array());
	}
	
	public void writeLongArray(long[] values) throws Exception {
		if (writeNullArray(values)) return;

		ByteBuffer buff = ByteBuffer.allocate(values.length * 8);
		buff.asLongBuffer().put(values);
		this.writeByteArray(buff.array());
	}
	
	public void writeFloatArray(float[] values) throws Exception {
		if (writeNullArray(values)) return;

		ByteBuffer buff = ByteBuffer.allocate(values.length * 4);
		buff.asFloatBuffer().put(values);
		this.writeByteArray(buff.array());
	}
	
	public void writeDoubleArray(double[] values) throws Exception {
		if (writeNullArray(values)) return;

		ByteBuffer buff = ByteBuffer.allocate(values.length * 8);
		buff.asDoubleBuffer().put(values);
		this.writeByteArray(buff.array());
	}

	public void writeStringArray(String[] values) throws Exception {
		if (writeNullArray(values)) return;

		StringBuilder sb = new StringBuilder();
		for (String value : values) {
			sb.append(value.length());
			sb.append(value);
		}
		this.writeString(sb.toString());
	}

	@Override
	protected AggregatedStream beginAggregate(String compositeType, boolean isMap) throws Exception {
		return new StreamWriter(this, compositeType, isMap);
	}


}
