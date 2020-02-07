package org.slowcoders.storm;

import java.util.HashMap;

public interface AbstractColumn {

	String getColumnName();

	ORMColumn getColumn();

	static AbstractColumn evaluate(String function) {
		return GlobalFunc.get(function);
	}

	class GlobalFunc implements AbstractColumn {

		private static HashMap<String, GlobalFunc> globalFunctions = new HashMap<>(); 
		
		protected final String function;

		GlobalFunc(String function) {
			this.function = function;
		}
		
		static AbstractColumn get(String function) {
			synchronized (globalFunctions) {
				GlobalFunc func = globalFunctions.get(function);
				if (func == null) {
					func = new GlobalFunc(function);
					globalFunctions.put(function, func);
				}
				return func;
			}
		}

		public String toString() {
			return this.getColumnName();
		}

		@Override
		public String getColumnName() {
			return function;
		}

		@Override
		public ORMColumn getColumn() {
			return null;
		}
	}
	
}

