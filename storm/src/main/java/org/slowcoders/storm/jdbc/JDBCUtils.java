package org.slowcoders.storm.jdbc;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import org.slowcoders.json.JSONException;
import org.slowcoders.json.JSONObject;
import org.slowcoders.util.Debug;

public class JDBCUtils {

	public static void close(ResultSet rs) {
		try {
			if (rs != null)  rs.close();
		} catch (SQLException e) {
			Debug.ignoreException(e);
		}
	}

	public static void close(Statement stmt) {
		try {
			if (stmt != null) {
				stmt.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static JSONObject toJSON(ResultSet rs) throws SQLException, JSONException {
		ResultSetMetaData md = rs.getMetaData();
		JSONObject obj = new JSONObject();
		for (int i = md.getColumnCount(); i > 0; i--) {
			String key = md.getColumnName(i);
			Object v = rs.getObject(i);
			obj.put(key, v);
		}
		return obj;
	}

}
