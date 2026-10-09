package org.metricshub.ipmi.core.coding.commands.sdr.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocatorRecordTest {

	private static final int RECORD_ID = 0x0042;

	/** An SDR record: ID, SDR version 1.5, type, length, payload. */
	private static byte[] record(int type, int... payload) {
		byte[] result = new byte[5 + payload.length];
		result[0] = (byte) RECORD_ID;
		result[1] = (byte) (RECORD_ID >> 8);
		result[2] = 0x51;
		result[3] = (byte) type;
		result[4] = (byte) payload.length;
		for (int i = 0; i < payload.length; i++) {
			result[5 + i] = (byte) payload[i];
		}
		return result;
	}

	@Test
	void fruDeviceLocatorKeepsItsRecordIdAndReadsTheLunFromBits4And3() {
		// IPMI 2.0 Table 43-7: access address 20h, device ID 3, logical + LUN 1 + bus 2, channel 1, reserved, FRU
		// inventory device, modifier, system board, instance 1, OEM, 8-bit ASCII ID string
		FruDeviceLocatorRecord locator = assertInstanceOf(
				FruDeviceLocatorRecord.class,
				SensorRecord
						.populateSensorRecord(
								record(0x11, 0x40, 0x03, 0x8a, 0x10, 0x00, 0x10, 0x00, 0x07, 0x01, 0x00, 0xc3, 'F', 'R', 'U')));

		assertEquals(RECORD_ID, locator.getId(), "the SDR record ID, not the device ID");
		assertEquals(3, locator.getDeviceId());
		assertTrue(locator.isLogical());
		assertEquals(1, locator.getAccessLun());
		assertEquals(0x20, locator.getDeviceAccessAddress());
		assertEquals(7, locator.getFruEntityId());
		assertEquals(1, locator.getFruEntityInstance());
		assertEquals("FRU", locator.getName());
	}

	@Test
	void genericDeviceLocatorReadsBusSpanAndNameWhereTheSpecPutsThem() {
		// IPMI 2.0 Table 43-10: access address 20h, slave address 21h, LUN 1 + bus 5, address span 7, reserved,
		// device type, modifier, system board, instance 1, OEM, 8-bit ASCII ID string at byte 16
		GenericDeviceLocatorRecord locator = assertInstanceOf(
				GenericDeviceLocatorRecord.class,
				SensorRecord
						.populateSensorRecord(
								record(0x10, 0x40, 0x42, 0x0d, 0x07, 0x00, 0x10, 0x00, 0x07, 0x01, 0x00, 0xc4, 'N', 'A', 'M', 'E')));

		assertEquals(RECORD_ID, locator.getId());
		assertEquals(0x21, locator.getDeviceSlaveAddress());
		assertEquals(1, locator.getAccessLun());
		assertEquals(5, locator.getBusId());
		assertEquals(7, locator.getAddressSpan());
		assertEquals("NAME", locator.getName());
	}
}
