package org.metricshub.ipmi.client.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;

class DeviceDescriptionTest {

	@Test
	void testGetDeviceType() {
		for (EntityId entityId: EntityId.values()) {
			assertNotNull(DeviceDescription.getDeviceType(entityId));
		}
	}

}
