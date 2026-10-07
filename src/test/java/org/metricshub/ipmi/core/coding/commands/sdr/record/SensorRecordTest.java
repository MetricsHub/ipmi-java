package org.metricshub.ipmi.core.coding.commands.sdr.record;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SensorRecordTest {

	/**
	 * Build a raw SDR record: 2-byte record ID (LE), SDR version 0x51, record type, record length, then the payload.
	 */
	private static byte[] record(int id, int type, byte[] payload) {
		byte[] raw = new byte[5 + payload.length];
		raw[0] = (byte) (id & 0xff);
		raw[1] = (byte) ((id >> 8) & 0xff);
		raw[2] = 0x51;
		raw[3] = (byte) type;
		raw[4] = (byte) payload.length;
		System.arraycopy(payload, 0, raw, 5, payload.length);
		return raw;
	}

	@Test
	void vendorDefinedRecordTypeIsDecodedAsOemRecordWithWholePayload() {
		// Record type D0h as emitted by GIGABYTE/NVIDIA BMCs: vendor-defined layout, no manufacturer ID field
		byte[] payload = {0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f};

		SensorRecord record = SensorRecord.populateSensorRecord(record(0x0004, 0xd0, payload));

		OemRecord oem = assertInstanceOf(OemRecord.class, record);
		assertEquals(4, oem.getId());
		assertEquals(0, oem.getManufacturerId(), "manufacturer ID is unknown for vendor-defined record types");
		assertArrayEquals(payload, oem.getOemData(), "the complete type-specific payload must be preserved");
	}

	@Test
	void lastOemRecordTypeIsAccepted() {
		byte[] payload = {0x01, 0x02};

		OemRecord oem = assertInstanceOf(OemRecord.class, SensorRecord.populateSensorRecord(record(0x0010, 0xff, payload)));

		assertArrayEquals(payload, oem.getOemData());
	}

	@Test
	void standardOemRecordTypeC0ParsesManufacturerIdAndData() {
		// C0h layout: 3-byte manufacturer ID (LE, 0x000157 = 343) followed by OEM data
		byte[] payload = {0x57, 0x01, 0x00, 0x21, 0x22};

		OemRecord oem = assertInstanceOf(OemRecord.class, SensorRecord.populateSensorRecord(record(0x0020, 0xc0, payload)));

		assertEquals(343, oem.getManufacturerId());
		assertArrayEquals(new byte[] {0x21, 0x22}, oem.getOemData());
	}

	@Test
	void shortOemRecordDoesNotThrow() {
		// C0h record without even a complete manufacturer ID: no ID is fabricated and the bytes are kept as payload
		OemRecord oem = assertInstanceOf(OemRecord.class, SensorRecord.populateSensorRecord(record(0x0030, 0xc0, new byte[] {0x57})));

		assertEquals(0, oem.getManufacturerId());
		assertArrayEquals(new byte[] {0x57}, oem.getOemData());

		// header only
		OemRecord empty = assertInstanceOf(OemRecord.class, SensorRecord.populateSensorRecord(record(0x0031, 0xc0, new byte[0])));
		assertEquals(0, empty.getOemData().length);
	}

	@Test
	void unknownStandardRecordTypeIsRejected() {
		// 05h is reserved by IPMI 2.0 and not an OEM type: callers are expected to skip it
		assertThrows(IllegalArgumentException.class, () -> SensorRecord.populateSensorRecord(record(0x0040, 0x05, new byte[] {0x00})));
	}

	@Test
	void recordShorterThanHeaderIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> SensorRecord.populateSensorRecord(new byte[] {0x00, 0x00, 0x51}));
	}
}
