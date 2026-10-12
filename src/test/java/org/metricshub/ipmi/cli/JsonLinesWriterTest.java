package org.metricshub.ipmi.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonLinesWriterTest {

	@Test
	void writesOneUtf8ObjectPerLineWithNestedValues() throws Exception {
		Map<String, Object> nested = new LinkedHashMap<String, Object>();
		nested.put("id", 17);
		nested.put("supported", true);
		Map<String, Object> object = new LinkedHashMap<String, Object>();
		object.put("name", "Temp \"CPU\" \\ é\t\n\r\u0001");
		object.put("reading", 22.5);
		object.put("count", 3L);
		object.put("on", false);
		object.put("missing", null);
		object.put("states", Arrays.asList("Device Present", 1));
		object.put("empty", Arrays.asList());
		object.put("suite", nested);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		// A latin-1 stream: the writer bypasses its charset
		JsonLinesWriter.write(object, new PrintStream(bytes, true, "ISO-8859-1"));
		assertEquals(
				"{\"name\":\"Temp \\\"CPU\\\" \\\\ é\\t\\n\\r\\u0001\",\"reading\":22.5,\"count\":3,\"on\":false,"
						+ "\"missing\":null,\"states\":[\"Device Present\",1],\"empty\":[],\"suite\":{\"id\":17,\"supported\":true}}"
						+ System.lineSeparator(),
				bytes.toString("UTF-8"));
	}
}
