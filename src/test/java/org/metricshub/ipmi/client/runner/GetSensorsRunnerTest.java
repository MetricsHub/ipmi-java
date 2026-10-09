package org.metricshub.ipmi.client.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.client.IpmiClientConfiguration;
import org.metricshub.ipmi.client.Utils;
import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;
import org.metricshub.ipmi.core.coding.PayloadCoder;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReadingResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.CompactSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.connection.ConnectionException;

class GetSensorsRunnerTest {

	private static final String DEVICE_NAME = "name";
	private static final int OEM_EVENT_READING_TYPE = 127;

	/** A reading the BMC vouches for: available and scanned. */
	private static GetSensorReadingResponseData validReading() {
		final GetSensorReadingResponseData data = new GetSensorReadingResponseData();
		data.setSensorStateValid(true);
		data.setScanningEnabled(true);
		return data;
	}

	@Test
	void testBuildStates() {

		// check arguments null
		assertEquals(Utils.EMPTY, GetSensorsRunner.buildStates(null, new CompactSensorRecord()));
		assertEquals(Utils.EMPTY, GetSensorsRunner.buildStates(validReading(), null));

		// check CompactSensorRecord oem type
		{
			final byte[] raw = { 1, 2, 3, 127 };
			final GetSensorReadingResponseData data = validReading();
			data.setRaw(raw);
			data.setStatesAsserted(new boolean[] { true, true });
			final CompactSensorRecord record = new CompactSensorRecord();
			record.setName(DEVICE_NAME);
			record.setEventReadingType(OEM_EVENT_READING_TYPE);

			assertEquals("name=0x7f03", GetSensorsRunner.buildStates(data, record));
		}

		// check CompactSensorRecord
		{
			final boolean[] statesAsserted = { true, false };
			final GetSensorReadingResponseData data = validReading();
			data.setStatesAsserted(statesAsserted);

			final CompactSensorRecord record = new CompactSensorRecord();
			record.setName(DEVICE_NAME);
			record.setSensorType(SensorType.PowerUnit);
			record.setEventReadingType(7535);

			assertEquals("name=Power up", GetSensorsRunner.buildStates(data, record));
		}

		// check FullSensorRecord oem
		{
			final byte[] raw = { 1, 2, 3, 127 };
			final GetSensorReadingResponseData data = validReading();
			data.setRaw(raw);
			data.setStatesAsserted(new boolean[] { true, true });
			final FullSensorRecord record = new FullSensorRecord();
			record.setName(DEVICE_NAME);
			record.setEventReadingType(OEM_EVENT_READING_TYPE);

			assertEquals("name=0x7f03", GetSensorsRunner.buildStates(data, record));
		}

		// check FullSensorRecord
		{
			final boolean[] statesAsserted = { true, true };
			final GetSensorReadingResponseData data = validReading();
			data.setStatesAsserted(statesAsserted);

			final FullSensorRecord record = new FullSensorRecord();
			record.setName(DEVICE_NAME);
			record.setSensorType(SensorType.PowerUnit);
			record.setEventReadingType(7535);

			assertEquals("name=Power up|name=Hard reset", GetSensorsRunner.buildStates(data, record));
		}
	}

	@Test
	void everyOemEventReadingTypeReportsTheRawStates() {
		// Cisco IMC and Dell iDRAC use 70h for their Entity Presence and Module/Board sensors
		for (int eventReadingType = 0x70; eventReadingType <= 0x7f; eventReadingType++) {
			final GetSensorReadingResponseData data = validReading();
			data.setRaw(new byte[] { 1, 2, 3, 0 });
			data.setStatesAsserted(new boolean[] { true, true });
			final CompactSensorRecord record = new CompactSensorRecord();
			record.setName(DEVICE_NAME);
			record.setSensorType(SensorType.EntityPresence);
			record.setEventReadingType(eventReadingType);

			assertEquals("name=0x0003", GetSensorsRunner.buildStates(data, record), Integer.toHexString(eventReadingType));

			// No state asserted (bit 7 of byte 4 is reserved and set): no state, as for any discrete sensor
			final GetSensorReadingResponseData idle = validReading();
			idle.setRaw(new byte[] { 1, 2, 0, (byte) 0x80 });
			idle.setStatesAsserted(new boolean[15]);
			assertEquals(Utils.EMPTY, GetSensorsRunner.buildStates(idle, record), Integer.toHexString(eventReadingType));
		}
	}

	@Test
	void aRefusedReadingCostsOnlyThatSensor() throws Exception {
		final AtomicReference<Exception> failure = new AtomicReference<>();
		final IpmiConnector connector = new IpmiConnector(0, 0) {
			@Override
			public ResponseData sendMessage(ConnectionHandle handle, PayloadCoder request) throws Exception {
				throw failure.get();
			}
		};
		try {
			final GetSensorsRunner runner = new GetSensorsRunner(
					new IpmiClientConfiguration("bmc", "user", new char[0], null, false, 1)) {
				@Override
				protected IpmiConnector getConnector() {
					return connector;
				}

				@Override
				protected ConnectionHandle getHandle() {
					return new ConnectionHandle(0, InetAddress.getLoopbackAddress(), 623);
				}
			};
			final CompactSensorRecord record = new CompactSensorRecord();
			record.setName(DEVICE_NAME);

			// HP iLO answers D4h once the session was revoked
			failure.set(new IPMIException(CompletionCode.InsufficentPrivilege));
			assertNull(runner.getSensorRecordReading(record));

			failure.set(new IPMIException(CompletionCode.DataNotPresent));
			assertNull(runner.getSensorRecordReading(record));

			// A BMC that does not answer fails the walk
			failure.set(new ConnectionException("Message timed out"));
			assertThrows(ConnectionException.class, () -> runner.getSensorRecordReading(record));
		} finally {
			connector.tearDown();
		}
	}

	@Test
	void statesOfAnUnavailableOrUnscannedSensorAreNotReported() {
		final boolean[] statesAsserted = { true, false };
		final CompactSensorRecord record = new CompactSensorRecord();
		record.setName(DEVICE_NAME);
		record.setSensorType(SensorType.PowerUnit);
		record.setEventReadingType(7535);

		final GetSensorReadingResponseData unavailable = validReading();
		unavailable.setSensorStateValid(false);
		unavailable.setStatesAsserted(statesAsserted);
		assertEquals(Utils.EMPTY, GetSensorsRunner.buildStates(unavailable, record));

		final GetSensorReadingResponseData notScanned = validReading();
		notScanned.setScanningEnabled(false);
		notScanned.setStatesAsserted(statesAsserted);
		assertEquals(Utils.EMPTY, GetSensorsRunner.buildStates(notScanned, record));
	}

	@Test
	void testBuildOemState() {

		// check raw null
		{
			final Exception exception = assertThrows(
					IllegalArgumentException.class,
					() -> GetSensorsRunner.buildOemState(null, DEVICE_NAME));

			assertEquals("Invalid IPMI raw command date for device name.", exception.getMessage());
		}

		// check raw empty
		{
			final byte[] raw = {};

			final Exception exception = assertThrows(
					IllegalArgumentException.class,
					() -> GetSensorsRunner.buildOemState(raw, DEVICE_NAME));

			assertEquals("Invalid IPMI raw command date for device name.", exception.getMessage());
		}

		// check raw without any state byte
		{
			final byte[] raw = { 1, 2 };

			final Exception exception = assertThrows(
					IllegalArgumentException.class,
					() -> GetSensorsRunner.buildOemState(raw, DEVICE_NAME));

			assertEquals("Invalid IPMI raw command date for device name.", exception.getMessage());
		}

		// check one state byte: the second one is optional (IPMI 2.0 Table 35-15)
		{
			final byte[] raw = { 1, 2, 3 };
			assertEquals("name=0x03", GetSensorsRunner.buildOemState(raw, DEVICE_NAME));
		}

		// check two state bytes
		{
			final byte[] raw = { 1, 2, 3, 127 };
			assertEquals("name=0x7f03", GetSensorsRunner.buildOemState(raw, DEVICE_NAME));
		}
	}
}
