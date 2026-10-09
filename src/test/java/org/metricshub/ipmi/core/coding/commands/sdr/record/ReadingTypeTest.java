package org.metricshub.ipmi.core.coding.commands.sdr.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReadingTypeTest {

	@Test
	void eventReadingTypes70hTo7FhAreOem() {
		assertFalse(ReadingType.isOem(0x6f), "sensor-specific");
		assertTrue(ReadingType.isOem(0x70));
		assertTrue(ReadingType.isOem(0x7f));
		assertFalse(ReadingType.isOem(0x80), "reserved");
	}

	@Test
	void statesOfAnOemEventReadingTypeAreUnknownOemEvents() {
		assertEquals(ReadingType.UnknownOEMEvent, ReadingType.parseInt(SensorType.EntityPresence, 0x70, 3));
		assertEquals(ReadingType.UnknownOEMEvent, ReadingType.parseInt(SensorType.ModuleBoard, 0x75, 0));
	}

	@Test
	void anUndefinedStateIsUnknown() {
		// HP iLO asserts state 6 of the Device Present reading type (08h), which defines states 0 and 1
		assertEquals(ReadingType.Unknown, ReadingType.parseInt(SensorType.Fan, 0x08, 6));
	}
}
