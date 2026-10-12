package org.metricshub.ipmi.cli;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Writes one JSON object per line (<a href="https://jsonlines.org/">JSON Lines</a>), in UTF-8 whatever the charset of
 * the output stream. Values are {@link Number}s, {@link Boolean}s, {@link List}s (arrays), {@link Map}s (nested
 * objects) and anything else as a string; a {@code null} value prints {@code null}, so callers omit absent values
 * from the map instead.
 */
final class JsonLinesWriter {

	private static final int CONTROL_CHARACTERS = 0x20;

	private JsonLinesWriter() {}

	/**
	 * Writes a map as one JSON object on one line, in the order of the map.
	 *
	 * @param object the fields of the object
	 * @param output where to write, as UTF-8 bytes
	 */
	static void write(Map<String, ?> object, PrintStream output) {
		StringBuilder json = new StringBuilder();
		writeValue(object, json);
		json.append(System.lineSeparator());
		byte[] utf8 = json.toString().getBytes(StandardCharsets.UTF_8);
		output.write(utf8, 0, utf8.length);
		output.flush();
	}

	private static void writeValue(Object value, StringBuilder json) {
		if (value == null) {
			json.append("null");
		} else if (value instanceof Number || value instanceof Boolean) {
			json.append(value);
		} else if (value instanceof Map) {
			json.append('{');
			boolean first = true;
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
				json.append(first ? "" : ",");
				writeString(String.valueOf(entry.getKey()), json);
				json.append(':');
				writeValue(entry.getValue(), json);
				first = false;
			}
			json.append('}');
		} else if (value instanceof List) {
			json.append('[');
			boolean first = true;
			for (Object element : (List<?>) value) {
				json.append(first ? "" : ",");
				writeValue(element, json);
				first = false;
			}
			json.append(']');
		} else {
			writeString(String.valueOf(value), json);
		}
	}

	private static void writeString(String value, StringBuilder json) {
		json.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
			case '"':
				json.append("\\\"");
				break;
			case '\\':
				json.append("\\\\");
				break;
			case '\b':
				json.append("\\b");
				break;
			case '\f':
				json.append("\\f");
				break;
			case '\n':
				json.append("\\n");
				break;
			case '\r':
				json.append("\\r");
				break;
			case '\t':
				json.append("\\t");
				break;
			default:
				if (c < CONTROL_CHARACTERS) {
					json.append(String.format("\\u%04x", (int) c));
				} else {
					json.append(c);
				}
				break;
			}
		}
		json.append('"');
	}
}
