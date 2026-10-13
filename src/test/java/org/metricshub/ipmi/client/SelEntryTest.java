package org.metricshub.ipmi.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.metricshub.ipmi.core.transport.FakeSel.systemEvent;

import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.sdr.record.ReadingType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;
import org.metricshub.ipmi.core.coding.commands.sel.SelRecord;

/**
 * The corners of {@link SelEntry} that the fake BMC of {@link IpmiClientTest} does not reach.
 */
class SelEntryTest {

	private static SelEntry entry(byte[] record) {
		return new SelEntry(SelRecord.populateSelRecord(record));
	}

	@Test
	void anUnspecifiedTimestampIsNeitherADateNorPreInit() {
		SelEntry entry = entry(systemEvent(1, 0xffffffffL, SensorType.Fan.getCode(), 1, 0x0a, true, 0x01, 0xff, 0xff));
		assertFalse(entry.timestamp().isPresent());
		assertFalse(entry.preInitSeconds().isPresent());
		assertEquals("SEL entry 1: Fan sensor 1, Transition to In Test (assertion)", entry.toString());
	}

	@Test
	void aTimestampAfter2038IsADate() {
		SelEntry entry = entry(systemEvent(1, 0x90000000L, SensorType.Fan.getCode(), 1, 0x0a, true, 0x01, 0xff, 0xff));
		assertEquals(Instant.ofEpochSecond(0x90000000L), entry.timestamp().get());
	}

	@Test
	void theLastPreInitTimestampIsPreInit() {
		SelEntry entry = entry(systemEvent(1, 0x20000000L, SensorType.Fan.getCode(), 1, 0x0a, true, 0x01, 0xff, 0xff));
		assertFalse(entry.timestamp().isPresent());
		assertEquals(0x20000000L, entry.preInitSeconds().getAsLong());
		assertEquals(Instant.ofEpochSecond(0x20000001L), SelEntry.absolute(new Date(0x20000001L * 1000), 0).get());
	}

	@Test
	void eventsTheSpecificationDoesNotNameAreDescribedByTheirCodes() {
		SelEntry unknown = entry(systemEvent(1, 0xffffffffL, SensorType.Fan.getCode(), 1, 0x01, true, 0x0f, 0xff, 0xff));
		assertEquals(ReadingType.Unknown, unknown.event().get());
		assertEquals("Unknown event type 01h offset Fh", unknown.description());

		SelEntry oem = entry(systemEvent(1, 0xffffffffL, SensorType.Fan.getCode(), 1, 0x70, true, 0x02, 0xff, 0xff));
		assertEquals(ReadingType.UnknownOEMEvent, oem.event().get());
		assertEquals(0x70, oem.eventType().getAsInt());
		assertEquals("OEM event type 70h offset 2h", oem.description());

		SelEntry oemSensor = entry(systemEvent(1, 0xffffffffL, 0xc0, 1, 0x6f, true, 0x02, 0xff, 0xff));
		assertEquals(SensorType.Oem, oemSensor.sensorType().get());
		assertEquals("OEM event type 6Fh offset 2h", oemSensor.description());
	}

	@Test
	void theStateDescriptionsOfIpmiutilAreUsed() {
		SelEntry asserted = entry(
				systemEvent(1, 0xffffffffL, SensorType.PowerSupply.getCode(), 1, 0x6f, true, 0x01, 0xff, 0xff));
		assertEquals(ReadingType.PowerSupplyFailureDetected, asserted.event().get());
		assertEquals("Failure detected", asserted.description());
	}

	@Test
	void theDataArraysAreCopies() {
		SelEntry entry = entry(systemEvent(1, 0xffffffffL, SensorType.Fan.getCode(), 1, 0x0a, true, 0x01, 0xff, 0xff));
		entry.eventData()[0] = 0x7f;
		assertEquals(0x01, entry.eventData()[0]);
	}

	@Test
	void entriesAreEqualWhenTheirRecordsAre() {
		byte[] record = systemEvent(1, 0xffffffffL, SensorType.Fan.getCode(), 1, 0x0a, true, 0x01, 0xff, 0xff);
		assertEquals(entry(record), entry(record.clone()));
		assertEquals(entry(record).hashCode(), entry(record.clone()).hashCode());
		assertFalse(
				entry(record).equals(entry(systemEvent(1, 5, SensorType.Fan.getCode(), 1, 0x0a, true, 0x01, 0xff, 0xff))));
		assertFalse(entry(record).equals("not an entry"));
	}

	@Test
	void aShortRecordIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> SelRecord.populateSelRecord(new byte[15]));
		assertThrows(IllegalArgumentException.class, () -> SelRecord.populateSelRecord(null));
	}
}
