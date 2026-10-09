package org.metricshub.ipmi.core.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PropertiesManagerTest {

	@Test
	void aMissingResourceIsSkipped() {
		PropertiesManager manager = PropertiesManager.getInstance();
		manager.loadProperties("/does-not-exist.properties");
		assertEquals("30000", manager.getProperty("pingPeriod"), "the packaged properties must still be there");
		assertNull(manager.getProperty("cleaningFrequency"), "cleaningFrequency is not a setting");
	}
}
