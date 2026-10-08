package org.metricshub.ipmi.core.coding.commands.sel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
		SelRecord record = SelRecord
				.populateSelRecord(
						bytes(0x01, 0x00, 0x02, 0x10, 0x20, 0x30, 0x40, 0x20, 0x00, 0x04, 0x01, 0x05, 0x01, 0x07, 0x00, 0x00));

		assertEquals(1, record.getRecordId());
		assertEquals(SelRecordType.System, record.getRecordType());
		assertEquals(new Date(TIMESTAMP * 1000L), record.getTimestamp());
		assertEquals(SensorType.Temperature, record.getSensorType());
		assertEquals(5, record.getSensorNumber());
		assertEquals(ReadingType.UpperNonCriticalGoingHigh, record.getEvent());
		assertNull(record.getManufacturerId());
		assertNull(record.getOemData());
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
		SelRecord record = SelRecord
				.populateSelRecord(bytes(0x03, 0x00, 0xe0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13));

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
		SelRecord record = SelRecord
				.populateSelRecord(bytes(0x04, 0x00, 0x10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13));

		assertEquals(4, record.getRecordId());
		assertEquals(SelRecordType.Reserved, record.getRecordType());
		assertNull(record.getTimestamp());
		assertNull(record.getOemData());
	}
}
