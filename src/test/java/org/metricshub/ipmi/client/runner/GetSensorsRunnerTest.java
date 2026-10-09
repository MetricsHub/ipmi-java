package org.metricshub.ipmi.client.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.client.Utils;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReadingResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.CompactSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;

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
