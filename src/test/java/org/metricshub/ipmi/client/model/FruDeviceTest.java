package org.metricshub.ipmi.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FruDeviceTest {

	@Test
	void testToString() {
		assertEquals("FRU;vendor;model;serial", new FruDevice("vendor", "model", "serial").toString());
	}

}
