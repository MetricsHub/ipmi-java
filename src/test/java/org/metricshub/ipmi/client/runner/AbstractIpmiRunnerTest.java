package org.metricshub.ipmi.client.runner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.client.IpmiClientConfiguration;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.OemRecord;

class AbstractIpmiRunnerTest {

	/**
	 * Minimal concrete runner: no session, no connector, just the decoding helpers under test.
	 */
	private static final AbstractIpmiRunner<Void> RUNNER = new AbstractIpmiRunner<Void>(
			new IpmiClientConfiguration("bmc", "user", new char[0], null, false, 1)) {
		@Override
		public Void call() {
			return null;
		}
	};

	private static byte[] record(int type, byte[] payload) {
		byte[] raw = new byte[5 + payload.length];
		raw[2] = 0x51;
		raw[3] = (byte) type;
		raw[4] = (byte) payload.length;
		System.arraycopy(payload, 0, raw, 5, payload.length);
		return raw;
	}

	@Test
	void decodeRecordReturnsOemRecordForVendorDefinedType() {
		assertInstanceOf(OemRecord.class, RUNNER.decodeRecord(record(0xd0, new byte[] { 1, 2, 3 })));
	}

	@Test
	void decodeRecordSkipsRecordsItCannotDecodeInsteadOfThrowing() {
		// reserved (non-OEM) record type
		assertNull(RUNNER.decodeRecord(record(0x05, new byte[] { 1 })));
		// shorter than the SDR header
		assertNull(RUNNER.decodeRecord(new byte[] { 0x00 }));
		// full sensor record whose body is missing: decoder runs out of bytes
		assertNull(RUNNER.decodeRecord(record(0x01, new byte[] { 1, 2, 3 })));
	}

	@Test
	void decodeRecordStillDecodesValidRecords() {
		// A Full Sensor Record (type 01h) of minimal valid size: 48 bytes, the last one being the ID string type/length
		byte[] payload = new byte[43];
		payload[0] = 0x20; // sensor owner: BMC
		payload[7] = 0x01; // sensor type: temperature
		payload[8] = 0x01; // event/reading type: threshold
		payload[16] = 0x01; // base unit: degrees C
		payload[42] = (byte) 0xc0; // 8-bit ASCII, zero-length name
		assertInstanceOf(FullSensorRecord.class, RUNNER.decodeRecord(record(0x01, payload)));
	}

	@Test
	void isTruncatedDetectsWholeRecordResponsesShorterThanDeclared() {
		byte[] complete = record(0xd0, new byte[] { 1, 2, 3, 4, 5 });
		assertFalse(AbstractIpmiRunner.isTruncated(complete));

		byte[] truncated = new byte[complete.length - 2];
		System.arraycopy(complete, 0, truncated, 0, truncated.length);
		assertTrue(AbstractIpmiRunner.isTruncated(truncated), "declared length 5 but only 3 payload bytes returned");

		assertTrue(AbstractIpmiRunner.isTruncated(new byte[] { 0x00, 0x00, 0x51 }), "header itself is incomplete");
		assertTrue(AbstractIpmiRunner.isTruncated(null));
	}
}
