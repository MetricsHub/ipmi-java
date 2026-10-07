package org.metricshub.ipmi.core.coding.commands.sdr.record;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		byte[] payload = { 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f };

		SensorRecord record = SensorRecord.populateSensorRecord(record(0x0004, 0xd0, payload));

		OemRecord oem = assertInstanceOf(OemRecord.class, record);
		assertEquals(4, oem.getId());
		assertEquals(0, oem.getManufacturerId(), "manufacturer ID is unknown for vendor-defined record types");
		assertArrayEquals(payload, oem.getOemData(), "the complete type-specific payload must be preserved");
	}

	@Test
	void lastOemRecordTypeIsAccepted() {
		byte[] payload = { 0x01, 0x02 };

		OemRecord oem = assertInstanceOf(OemRecord.class, SensorRecord.populateSensorRecord(record(0x0010, 0xff, payload)));

		assertArrayEquals(payload, oem.getOemData());
	}

	@Test
	void standardOemRecordTypeC0ParsesManufacturerIdAndData() {
		// C0h layout: 3-byte manufacturer ID (LE, 0x000157 = 343) followed by OEM data
		byte[] payload = { 0x57, 0x01, 0x00, 0x21, 0x22 };

		OemRecord oem = assertInstanceOf(OemRecord.class, SensorRecord.populateSensorRecord(record(0x0020, 0xc0, payload)));

		assertEquals(343, oem.getManufacturerId());
		assertArrayEquals(new byte[] { 0x21, 0x22 }, oem.getOemData());
	}

	@Test
	void shortOemRecordDoesNotThrow() {
		// C0h record without even a complete manufacturer ID: no ID is fabricated and the bytes are kept as payload
		OemRecord oem = assertInstanceOf(
				OemRecord.class,
				SensorRecord.populateSensorRecord(record(0x0030, 0xc0, new byte[]
				{ 0x57 })));

		assertEquals(0, oem.getManufacturerId());
		assertArrayEquals(new byte[] { 0x57 }, oem.getOemData());

		// header only
		OemRecord empty = assertInstanceOf(
				OemRecord.class,
				SensorRecord.populateSensorRecord(record(0x0031, 0xc0, new byte[0])));
		assertEquals(0, empty.getOemData().length);
	}

	@Test
	void unknownStandardRecordTypeIsRejected() {
		// 05h is reserved by IPMI 2.0 and not an OEM type: callers are expected to skip it
		assertThrows(
				IllegalArgumentException.class,
				() -> SensorRecord.populateSensorRecord(record(0x0040, 0x05, new byte[]
				{ 0x00 })));
	}

	/**
	 * Build a byte array from int values, so that test records can use unsigned notation.
	 */
	private static byte[] bytes(int... values) {
		byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = (byte) values[i];
		}
		return result;
	}

	// Record bytes 5-9, common to the Full, Compact and Event-Only sensor records: owner ID 20h (system software ID),
	// channel 5, LUN 2, sensor number 9Ah, entity 7 (system board), logical entity instance 3
	private static final int[] SENSOR_KEY_AND_ENTITY = { 0x41, 0x52, 0x9a, 0x07, 0x83 };

	private static byte[] sensorRecord(int type, int... body) {
		int[] payload = new int[SENSOR_KEY_AND_ENTITY.length + body.length];
		System.arraycopy(SENSOR_KEY_AND_ENTITY, 0, payload, 0, SENSOR_KEY_AND_ENTITY.length);
		System.arraycopy(body, 0, payload, SENSOR_KEY_AND_ENTITY.length, body.length);
		return record(0x0042, type, bytes(payload));
	}

	// @formatter:off
	private static final byte[] FULL_SENSOR_RECORD = sensorRecord(0x01,
			0x04, // 10: initialization, thresholds present
			0x24, // 11: capabilities, hysteresis readable/settable, thresholds readable
			0x02, 0x01, // 12-13: voltage sensor, threshold reading type
			0x00, 0x00, 0x00, 0x00, 0x3f, 0x00, // 14-19: masks, all thresholds readable
			0x00, 0x04, 0x00, // 20-22: unsigned, no rate, volts, no modifier unit
			0x00, // 23: linear
			0x0a, 0x04, 0x05, 0x10, 0x01, 0xe1, // 24-29: M=10, B=5, input, R exp=-2, B exp=1
			0x00, // 30: analog characteristics
			0x64, 0x78, 0x50, 0xff, 0x00, // 31-35: nominal, normal max/min, sensor max/min
			0xc8, 0xbe, 0xb4, 0x14, 0x1e, 0x28, // 36-41: UNR, UC, UNC, LNR, LC, LNC
			0x00, 0x00, 0x00, 0x00, 0x00, // 42-46: hysteresis, reserved, OEM
			0xc5, 'V', 'c', 'o', 'r', 'e'); // 47-52: 8-bit ASCII ID string

	private static final byte[] COMPACT_SENSOR_RECORD = sensorRecord(0x02,
			0x00, // 10: initialization
			0x38, // 11: capabilities, fixed hysteresis, thresholds readable/settable
			0x01, 0x01, // 12-13: temperature sensor, threshold reading type
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 14-19: masks
			0x92, 0x01, 0x04, // 20-22: per ms, divided by modifier unit, degrees C, volts
			0x53, 0x85, // 23-24: input, alpha modifier, share count 3, entity instance increments, offset 5
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 25-30: hysteresis, reserved, OEM
			0xc4, 'T', 'e', 'm', 'p'); // 31-35: 8-bit ASCII ID string

	private static final byte[] EVENT_ONLY_RECORD = sensorRecord(0x03,
			0x12, 0x6f, // 10-11: system event sensor, sensor-specific reading type
			0x82, 0x11, // 12-13: output, numeric modifier, share count 2, offset 17
			0x00, 0x00, // 14-15: reserved, OEM
			0xc4, 'B', 'I', 'O', 'S'); // 16-20: 8-bit ASCII ID string
	// @formatter:on

	@Test
	void fullSensorRecordIsDecoded() {
		FullSensorRecord full = assertInstanceOf(
				FullSensorRecord.class,
				SensorRecord.populateSensorRecord(FULL_SENSOR_RECORD));

		assertEquals(0x42, full.getId());
		assertEquals(0x20, full.getSensorOwnerId());
		assertEquals(AddressType.SystemSoftwareId, full.getAddressType());
		assertEquals(5, full.getChannelNumber());
		assertEquals(2, full.getSensorOwnerLun());
		assertEquals((byte) 0x9a, full.getSensorNumber());
		assertEquals(EntityId.SystemBoard, full.getEntityId());
		assertFalse(full.isEntityPhysical());
		assertEquals(3, full.getEntityInstanceNumber());
		assertTrue(full.isHysteresisReadable());
		assertTrue(full.isThresholdsReadable());
		assertEquals(SensorType.Voltage, full.getSensorType());
		assertEquals(0x01, full.getEventReadingType());
		assertEquals(RateUnit.None, full.getRateUnit());
		assertEquals(ModifierUnitUsage.None, full.getModifierUnitUsage());
		assertEquals(SensorUnit.Volts, full.getSensorBaseUnit());
		assertEquals(SensorUnit.Unspecified, full.getSensorModifierUnit());
		assertEquals(SensorDirection.Input, full.getSensorDirection());
		assertEquals("Vcore", full.getName());

		// y = (M * x + B * 10^Bexp) * 10^Rexp = (10 * x + 50) / 100
		assertEquals(10.5, full.getNominalReading(), 1e-9);
		assertEquals(12.5, full.getNormalMaximum(), 1e-9);
		assertEquals(8.5, full.getNormalMinimum(), 1e-9);
		assertEquals(26.0, full.getSensorMaximumReading(), 1e-9);
		assertEquals(0.5, full.getSensorMinmumReading(), 1e-9);
		assertEquals(20.5, full.getUpperNonRecoverableThreshold(), 1e-9);
		assertEquals(19.5, full.getUpperCriticalThreshold(), 1e-9);
		assertEquals(18.5, full.getUpperNonCriticalThreshold(), 1e-9);
		assertEquals(2.5, full.getLowerNonRecoverableThreshold(), 1e-9);
		assertEquals(3.5, full.getLowerCriticalThreshold(), 1e-9);
		assertEquals(4.5, full.getLowerNonCriticalThreshold(), 1e-9);
		assertEquals(0.05, full.getSensorResolution(), 1e-9);
	}

	@Test
	void compactSensorRecordIsDecoded() {
		CompactSensorRecord compact = assertInstanceOf(
				CompactSensorRecord.class,
				SensorRecord.populateSensorRecord(COMPACT_SENSOR_RECORD));

		assertEquals(0x42, compact.getId());
		assertEquals(0x20, compact.getSensorOwnerId());
		assertEquals(AddressType.SystemSoftwareId, compact.getAddressType());
		assertEquals(5, compact.getChannelNumber());
		assertEquals(2, compact.getSensorOwnerLun());
		assertEquals((byte) 0x9a, compact.getSensorNumber());
		assertEquals(EntityId.SystemBoard, compact.getEntityId());
		assertFalse(compact.isEntityPhysical());
		assertEquals(3, compact.getEntityInstanceNumber());
		assertFalse(compact.isHysteresisReadable());
		assertTrue(compact.isThresholdsReadable());
		assertEquals(SensorType.Temperature, compact.getSensorType());
		assertEquals(0x01, compact.getEventReadingType());
		assertEquals(RateUnit.Miliseconds, compact.getRateUnit());
		assertEquals(ModifierUnitUsage.Divide, compact.getModifierUnitUsage());
		assertEquals(SensorUnit.DegreesC, compact.getSensorBaseUnit());
		assertEquals(SensorUnit.Volts, compact.getSensorModifierUnit());
		assertEquals(SensorDirection.Input, compact.getSensorDirection());
		assertEquals(InstanceModifierType.Alpha, compact.getIdInstanceModifierType());
		assertEquals(3, compact.getShareCount());
		assertTrue(compact.isEntityInstanceIncrements());
		assertEquals(5, compact.getIdInstanceModifierOffset());
		assertEquals("Temp", compact.getName());
	}

	@Test
	void compactSensorRecordWithoutIdStringHasNoName() {
		byte[] truncated = new byte[31];
		System.arraycopy(COMPACT_SENSOR_RECORD, 0, truncated, 0, truncated.length);

		CompactSensorRecord compact = assertInstanceOf(
				CompactSensorRecord.class,
				SensorRecord.populateSensorRecord(truncated));

		assertEquals(SensorType.Temperature, compact.getSensorType());
		assertNull(compact.getName());
	}

	@Test
	void eventOnlyRecordIsDecoded() {
		EventOnlyRecord eventOnly = assertInstanceOf(
				EventOnlyRecord.class,
				SensorRecord.populateSensorRecord(EVENT_ONLY_RECORD));

		assertEquals(0x42, eventOnly.getId());
		assertEquals(0x20, eventOnly.getSensorOwnerId());
		assertEquals(AddressType.SystemSoftwareId, eventOnly.getAddressType());
		assertEquals(5, eventOnly.getChannelNumber());
		assertEquals(2, eventOnly.getSensorOwnerLun());
		assertEquals((byte) 0x9a, eventOnly.getSensorNumber());
		assertEquals(EntityId.SystemBoard, eventOnly.getEntityId());
		assertFalse(eventOnly.isEntityPhysical());
		assertEquals(3, eventOnly.getEntityInstanceNumber());
		assertEquals(SensorType.SystemEvent, eventOnly.getSensorType());
		assertEquals(0x6f, eventOnly.getEventReadingType());
		assertEquals(SensorDirection.Output, eventOnly.getSensorDirection());
		assertEquals(InstanceModifierType.Numeric, eventOnly.getIdInstanceModifierType());
		assertEquals(2, eventOnly.getShareCount());
		assertFalse(eventOnly.isEntityInstanceIncrements());
		assertEquals(17, eventOnly.getIdInstanceModifierOffset());
		assertEquals("BIOS", eventOnly.getName());
	}

	@Test
	void recordShorterThanHeaderIsRejected() {
		assertThrows(
				IllegalArgumentException.class,
				() -> SensorRecord.populateSensorRecord(new byte[]
				{ 0x00, 0x00, 0x51 }));
	}
}
