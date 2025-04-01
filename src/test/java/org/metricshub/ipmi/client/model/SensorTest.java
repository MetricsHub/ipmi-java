package org.metricshub.ipmi.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReadingResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.CompactSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord;

class SensorTest {

	@Test
	void testIsCompact() {
		CompactSensorRecord record = new CompactSensorRecord();
		GetSensorReadingResponseData data = new GetSensorReadingResponseData();
		String states = "power supply 1=Transition to OK";
		assertTrue(new Sensor(record, data , states).isCompact());
		assertFalse(new Sensor(record, data , states).isFull());
	}

	@Test
	void testIsFull() {
		FullSensorRecord record = new FullSensorRecord();
		GetSensorReadingResponseData data = new GetSensorReadingResponseData();
		String states = "power supply 1=Transition to OK";
		assertFalse(new Sensor(record, data , states).isCompact());
		assertTrue(new Sensor(record, data , states).isFull());
	}

	@Test
	void testGetEntityId() {
		{
			CompactSensorRecord record = new CompactSensorRecord();
			record.setEntityId(EntityId.PowerSupply);
			GetSensorReadingResponseData data = new GetSensorReadingResponseData();
			String states = "power supply 1=Transition to OK";
			assertEquals(EntityId.PowerSupply, new Sensor(record, data, states).getEntityId());
		}

		{
			FullSensorRecord record = new FullSensorRecord();
			record.setEntityId(EntityId.PowerSupply);
			GetSensorReadingResponseData data = new GetSensorReadingResponseData();
			String states = "power supply 1=Transition to OK";
			assertEquals(EntityId.PowerSupply, new Sensor(record, data, states).getEntityId());
		}

	}

	@Test
	void testGetDeviceId() {
		{
			CompactSensorRecord record = new CompactSensorRecord();
			record.setEntityInstanceNumber((byte) 0x01);
			GetSensorReadingResponseData data = new GetSensorReadingResponseData();
			String states = "power supply 1=Transition to OK";
			assertEquals((byte) 0x01, new Sensor(record, data, states).getDeviceId());
		}

		{
			FullSensorRecord record = new FullSensorRecord();
			record.setEntityInstanceNumber((byte) 0x01);
			GetSensorReadingResponseData data = new GetSensorReadingResponseData();
			String states = "power supply 1=Transition to OK";
			assertEquals((byte) 0x01, new Sensor(record, data, states).getDeviceId());
		}
	}

	@Test
	void testGetName() {
		{
			CompactSensorRecord record = new CompactSensorRecord();
			record.setName("Base Board 1");
			GetSensorReadingResponseData data = new GetSensorReadingResponseData();
			String states = "Base Board 1=Transition to OK";
			assertEquals("Base Board 1", new Sensor(record, data, states).getName());
		}

		{
			FullSensorRecord record = new FullSensorRecord();
			record.setName("Base Board 1");
			GetSensorReadingResponseData data = new GetSensorReadingResponseData();
			String states = "Base Board 1=Transition to OK";
			assertEquals("Base Board 1", new Sensor(record, data, states).getName());
		}
	}

}
