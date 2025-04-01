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

	@Test
	void testBuildStates() {

		// check arguments null
		assertEquals(Utils.EMPTY, GetSensorsRunner.buildStates(null, new CompactSensorRecord()));
		assertEquals(Utils.EMPTY, GetSensorsRunner.buildStates(new GetSensorReadingResponseData(), null));

		// check CompactSensorRecord oem type
		{
			final byte[] raw = {1, 2, 3, 127};
			final GetSensorReadingResponseData data = new GetSensorReadingResponseData();
			data.setRaw(raw);
			final CompactSensorRecord record = new CompactSensorRecord();
			record.setName(DEVICE_NAME);
			record.setEventReadingType(OEM_EVENT_READING_TYPE);

			assertEquals("name=0x7f03",  GetSensorsRunner.buildStates(data, record));
		}

		// check CompactSensorRecord
		{
			final boolean[] statesAsserted = {true, false};
			final GetSensorReadingResponseData data = new GetSensorReadingResponseData();
			data.setStatesAsserted(statesAsserted);

			final CompactSensorRecord record = new CompactSensorRecord();
			record.setName(DEVICE_NAME);
			record.setSensorType(SensorType.PowerUnit);
			record.setEventReadingType(7535);

			assertEquals("name=Power up",  GetSensorsRunner.buildStates(data, record));
		}

		// check FullSensorRecord oem
		{
			final byte[] raw = {1, 2, 3, 127};
			final GetSensorReadingResponseData data = new GetSensorReadingResponseData();
			data.setRaw(raw);
			final FullSensorRecord record = new FullSensorRecord();
			record.setName(DEVICE_NAME);
			record.setEventReadingType(OEM_EVENT_READING_TYPE);

			assertEquals("name=0x7f03",  GetSensorsRunner.buildStates(data, record));
		}

		// check FullSensorRecord
		{
			final boolean[] statesAsserted = {true, true};
			final GetSensorReadingResponseData data = new GetSensorReadingResponseData();
			data.setStatesAsserted(statesAsserted);

			final FullSensorRecord record = new FullSensorRecord();
			record.setName(DEVICE_NAME);
			record.setSensorType(SensorType.PowerUnit);
			record.setEventReadingType(7535);

			assertEquals("name=Power up|name=Hard reset",  GetSensorsRunner.buildStates(data, record));
		}
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

		// check raw length < 4
		{
			final byte[] raw = {1, 2, 3};

			final Exception exception = assertThrows(
					IllegalArgumentException.class,
					() -> GetSensorsRunner.buildOemState(raw, DEVICE_NAME));

			assertEquals("Invalid IPMI raw command date for device name.", exception.getMessage());
		}

		// check ok
		{
			final byte[] raw = {1, 2, 3, 127};
			assertEquals("name=0x7f03", GetSensorsRunner.buildOemState(raw, DEVICE_NAME));
		}
	}
}
