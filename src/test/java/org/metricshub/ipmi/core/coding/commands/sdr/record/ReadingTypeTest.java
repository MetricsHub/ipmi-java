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
	void osBootHasTheInstallationOffsetsOfRevision11() {
		// IPMI 2.0 rev 1.1 table 42-3, sensor type 1Fh, offsets 07h-0Ah (a Dell iDRAC logs 07h)
		assertEquals(ReadingType.BootCompleted, ReadingType.parseInt(SensorType.OsBoot, 0x6f, 6));
		assertEquals(ReadingType.BaseOsInstallationStarted, ReadingType.parseInt(SensorType.OsBoot, 0x6f, 7));
		assertEquals(ReadingType.BaseOsInstallationCompleted, ReadingType.parseInt(SensorType.OsBoot, 0x6f, 8));
		assertEquals(ReadingType.BaseOsInstallationAborted, ReadingType.parseInt(SensorType.OsBoot, 0x6f, 9));
		assertEquals(ReadingType.BaseOsInstallationFailed, ReadingType.parseInt(SensorType.OsBoot, 0x6f, 0x0a));
		assertEquals(ReadingType.Unknown, ReadingType.parseInt(SensorType.OsBoot, 0x6f, 0x0b));
	}

	@Test
	void anUndefinedStateIsUnknown() {
		// HP iLO asserts state 6 of the Device Present reading type (08h), which defines states 0 and 1
		assertEquals(ReadingType.Unknown, ReadingType.parseInt(SensorType.Fan, 0x08, 6));
	}
}
