package org.metricshub.ipmi.core.coding.commands.sel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.sdr.record.ReadingType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;

class SelRecordTest {

	private static byte[] bytes(int... values) {
		byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = (byte) values[i];
		}
		return result;
	}

	private static final int TIMESTAMP = 0x40302010;

	@Test
	void systemEventRecordIsDecoded() {
		// IPMI 2.0 section 32.1: record 1, type 02h, timestamp, generator 0020h, EvM rev 04h, temperature sensor 5,
		// assertion of threshold event offset 7 (upper non-critical going high), event data
		SelRecord record = SelRecord.populateSelRecord(
				bytes(0x01, 0x00, 0x02, 0x10, 0x20, 0x30, 0x40, 0x20, 0x00, 0x04, 0x01, 0x05, 0x01, 0x07, 0x00, 0x00));

		assertEquals(1, record.getRecordId());
		assertEquals(SelRecordType.System, record.getRecordType());
		assertEquals(new Date(TIMESTAMP * 1000L), record.getTimestamp());
		assertEquals(SensorType.Temperature, record.getSensorType());
		assertEquals(5, record.getSensorNumber());
		assertEquals(ReadingType.UpperNonCriticalGoingHigh, record.getEvent());
		assertEquals(0x0020, record.getGeneratorId());
		assertEquals(0x01, record.getEventType());
		assertArrayEquals(bytes(0x07, 0x00, 0x00), record.getEventData());
		assertNull(record.getManufacturerId());
		assertNull(record.getOemData());
	}

	@Test
	void theGeneratorIdAndEventDataAreThoseOfTheRecord() {
		// Logged by system software ID 41h on channel 1, deassertion of a sensor-specific event with its data bytes
		SelRecord record = SelRecord.populateSelRecord(
				bytes(0x05, 0x00, 0x02, 0xff, 0xff, 0xff, 0xff, 0x41, 0x10, 0x04, 0x09, 0x01, 0xef, 0xa0, 0x12, 0x34));

		assertEquals(0x1041, record.getGeneratorId());
		assertEquals(0x6f, record.getEventType());
		assertEquals(EventDirection.Deassertion, record.getEventDirection());
		assertEquals(ReadingType.PowerOffOrDown, record.getEvent());
		assertArrayEquals(bytes(0xa0, 0x12, 0x34), record.getEventData());
		assertEquals(new Date(0xffffffffL * 1000), record.getTimestamp(), "FFFFFFFFh is not negative");
	}

	@Test
	void aShortRecordIsRefusedWithItsLength() {
		IllegalArgumentException refused = assertThrows(
				IllegalArgumentException.class,
				() -> SelRecord.populateSelRecord(bytes(0x01, 0x00, 0x02)));
		assertEquals("A SEL record is 16 bytes, not 3", refused.getMessage());
	}

	@Test
	void oemTimestampedRecordKeepsItsManufacturerIdAndData() {
		// Section 32.2: type C0h, timestamp, manufacturer ID 0x004C4C (3 bytes, LS first), 6 OEM bytes
		SelRecord record = SelRecord
				.populateSelRecord(bytes(0x02, 0x00, 0xc0, 0x10, 0x20, 0x30, 0x40, 0x4c, 0x4c, 0x00, 1, 2, 3, 4, 5, 6));

		assertEquals(SelRecordType.OemTimestamped, record.getRecordType());
		assertEquals(new Date(TIMESTAMP * 1000L), record.getTimestamp());
		assertEquals(0x4c4c, record.getManufacturerId());
		assertArrayEquals(bytes(1, 2, 3, 4, 5, 6), record.getOemData());
		assertNull(record.getSensorType(), "an OEM record has no system event fields");
		assertNull(record.getEvent());
		assertNull(record.getEventDirection());
	}

	@Test
	void oemNonTimestampedRecordKeepsItsData() {
		// Section 32.3: type E0h, 13 OEM bytes
		SelRecord record = SelRecord.populateSelRecord(bytes(0x03, 0x00, 0xe0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13));

		assertEquals(SelRecordType.OemNonTimestamped, record.getRecordType());
		assertNull(record.getTimestamp());
		assertNull(record.getManufacturerId());
		assertArrayEquals(bytes(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13), record.getOemData());
	}

	@Test
	void recordTypeRangesIncludeTheirBoundaries() {
		assertEquals(SelRecordType.OemTimestamped, SelRecordType.parseInt(0xc0));
		assertEquals(SelRecordType.OemTimestamped, SelRecordType.parseInt(0xdf));
		assertEquals(SelRecordType.OemNonTimestamped, SelRecordType.parseInt(0xe0));
		assertEquals(SelRecordType.OemNonTimestamped, SelRecordType.parseInt(0xff));
		assertEquals(SelRecordType.Reserved, SelRecordType.parseInt(0x10));
	}

	@Test
	void reservedRecordTypeDoesNotAbortTheWalk() {
		SelRecord record = SelRecord.populateSelRecord(bytes(0x04, 0x00, 0x10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13));

		assertEquals(4, record.getRecordId());
		assertEquals(SelRecordType.Reserved, record.getRecordType());
		assertNull(record.getTimestamp());
		assertNull(record.getOemData());
	}
}
