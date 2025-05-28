package com.github.piantino.keycloak;

import java.sql.Timestamp;
import java.util.Map;

public class DbProviderUtils {
	private static final String TRUE_VALUE = "y";

	private DbProviderUtils() {
		// empty
	}

	public static boolean toBoolean(Map<String, Object> data, String key) {
		if (!hasColumn(data, key)) {
			return false;
		}
		
		Object value = data.get(key);

		if (value == null) {
			return false;
		}
		
		if (value instanceof String) {
			return TRUE_VALUE.equals(((String) value));
		}
		
		return (Boolean) value;
	}

	public static String toAttributeValue(Object value) {
		if (value == null) {
			return null;
		}
		
		if (value instanceof Timestamp) {
			Timestamp time = (Timestamp) value;
			return time.toLocalDateTime().toString();
		}
		
		return value.toString();
	}

	public static boolean hasColumn(Map<String, Object> data, String key) {
		return data.get(key) != null;
	}
}
