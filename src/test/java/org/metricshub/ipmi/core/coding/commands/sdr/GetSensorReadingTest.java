package org.metricshub.ipmi.core.coding.commands.sdr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.ipmi.core.coding.commands.IpmiResponses.response;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.sdr.record.ReadingType;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;

class GetSensorReadingTest {

	private static final GetSensorReading COMMAND = new GetSensorReading(
			IpmiVersion.V20,
			CipherSuite.getEmpty(),
			AuthenticationType.RMCPPlus,
			5);

	private static final int THRESHOLD_TYPE = 1;

	private static final byte GET_SENSOR_READING = 0x2d;

	private static GetSensorReadingResponseData decode(int... data) throws Exception {
		return (GetSensorReadingResponseData) COMMAND
				.getResponseData(response(GET_SENSOR_READING, 0x00, data));
	}

	@Test
	void upperThresholdCrossingIsReportedAsSuch() throws Exception {
		// Table 35-15: byte 2 bit 6 scanning enabled, byte 3 bit 3 "at or above upper non-critical"
		GetSensorReadingResponseData data = decode(0x64, 0x40, 0x08);

		assertEquals(100, data.getPlainSensorReading(), 0);
		assertTrue(data.isSensorStateValid());
		assertTrue(data.isScanningEnabled());
		assertEquals(SensorState.AboveUpperNonCritical, data.getSensorState());
		assertEquals(
				Arrays.asList(ReadingType.UpperNonCriticalGoingHigh),
				data.getStatesAsserted(SensorType.Temperature, THRESHOLD_TYPE));
	}

	@Test
	void everyThresholdBitMapsToItsEvent() throws Exception {
		// LNC (bit 0) and UC (bit 4) at the same time
		GetSensorReadingResponseData data = decode(0x64, 0x40, 0x11);

		assertEquals(SensorState.AboveUpperCritical, data.getSensorState());
		assertEquals(
				Arrays.asList(ReadingType.LowerNonCriticalGoingLow, ReadingType.UpperCriticalGoingHigh),
				data.getStatesAsserted(SensorType.Temperature, THRESHOLD_TYPE));
	}

	@Test
	void unavailableReadingAndDisabledScanningAreExposed() throws Exception {
		// bit 5: reading/state unavailable
		GetSensorReadingResponseData unavailable = decode(0x00, 0x60, 0x00);
		assertFalse(unavailable.isSensorStateValid());
		assertTrue(unavailable.isScanningEnabled());

		GetSensorReadingResponseData notScanned = decode(0x00, 0x00, 0x00);
		assertTrue(notScanned.isSensorStateValid());
		assertFalse(notScanned.isScanningEnabled());
	}

	@Test
	void sensorStateIsTheMostSevereThresholdCrossed() {
		assertEquals(SensorState.Ok, SensorState.parseInt(0x00));
		assertEquals(SensorState.BelowLowerNonCritical, SensorState.parseInt(0x01));
		assertEquals(SensorState.BelowLowerCritical, SensorState.parseInt(0x03));
		assertEquals(SensorState.BelowLowerNonRecoverable, SensorState.parseInt(0x07));
		assertEquals(SensorState.AboveUpperNonCritical, SensorState.parseInt(0x08));
		assertEquals(SensorState.AboveUpperCritical, SensorState.parseInt(0x18));
		assertEquals(SensorState.AboveUpperNonRecoverable, SensorState.parseInt(0x3f));
	}
}
