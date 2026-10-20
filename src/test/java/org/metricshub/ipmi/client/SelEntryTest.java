package org.metricshub.ipmi.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.metricshub.ipmi.core.transport.FakeSel.selRecord;
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
	void theOsInstallationEventsOfRevision11AreNamed() {
		// IPMI 2.0 rev 1.1 table 42-3, sensor type 1Fh, offsets 07h-0Ah, in the wording of ipmitool
		String[] descriptions = {
				"Installation started",
				"Installation completed",
				"Installation aborted",
				"Installation failed" };
		for (int offset = 0x07; offset <= 0x0a; offset++) {
			SelEntry entry = entry(
					systemEvent(4, 0xffffffffL, SensorType.OsBoot.getCode(), 0, 0x6f, true, offset, 0xff, 0xff));
			assertEquals(descriptions[offset - 0x07], entry.description());
		}
	}

	@Test
	void theWatchdog1EventsAreNamed() {
		// IPMI 2.0 table 42-3, sensor type 11h, offsets 00h-07h, in the wording of ipmitool
		String[] descriptions = {
				"BIOS Reset",
				"OS Reset",
				"OS Shut Down",
				"OS Power Down",
				"OS Power Cycle",
				"OS NMI/Diag Interrupt",
				"OS Expired",
				"OS pre-timeout Interrupt" };
		for (int offset = 0; offset < descriptions.length; offset++) {
			SelEntry entry = entry(
					systemEvent(5, 0xffffffffL, SensorType.Watchdog1.getCode(), 0, 0x6f, true, offset, 0xff, 0xff));
			assertEquals(descriptions[offset], entry.description());
		}
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

		SelEntry oemSensor = entry(systemEvent(1, 0xffffffffL, 0xc1, 5, 0x6f, true, 0x02, 0xff, 0xff));
		assertEquals(SensorType.Oem, oemSensor.sensorType().get());
		assertEquals(0xc1, oemSensor.sensorTypeCode().getAsInt());
		assertEquals("OEM sensor type C1h, event type 6Fh offset 2h", oemSensor.description());
		assertEquals(
				"SEL entry 1: Oem C1h sensor 5, OEM sensor type C1h, event type 6Fh offset 2h (assertion)",
				oemSensor.toString());

		SelEntry reserved = entry(systemEvent(1, 0xffffffffL, 0x76, 5, 0x6f, true, 0x02, 0xff, 0xff));
		assertEquals(SensorType.Reserved, reserved.sensorType().get());
		assertEquals(0x76, reserved.sensorTypeCode().getAsInt());
		assertEquals(ReadingType.Unknown, reserved.event().get());
		assertEquals("Reserved sensor type 76h, event type 6Fh offset 2h", reserved.description());

		assertFalse(entry(selRecord(2, 0xe0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)).sensorTypeCode().isPresent());
	}

	@Test
	void theGenericAndThresholdEventsOfAnOemSensorTypeAreNamed() {
		SelEntry presence = entry(systemEvent(1, 0xffffffffL, 0xc0, 49, 0x08, true, 0x01, 0xff, 0xff));
		assertEquals(ReadingType.DevicePresent, presence.event().get());
		assertEquals("Device Present", presence.description());
		SelEntry threshold = entry(systemEvent(2, 0xffffffffL, 0xc0, 54, 0x01, true, 0x09, 0xff, 0xff));
		assertEquals(ReadingType.UpperCriticalGoingHigh, threshold.event().get());
		assertEquals("Upper Critical Going High", threshold.description());
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
