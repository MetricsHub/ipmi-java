package org.metricshub.ipmi.core.coding.commands.sdr.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FullSensorRecordTest {

	private static final double DELTA = 1e-6;

	/**
	 * A full sensor record (IPMI 2.0 Table 43-1) whose body starts at byte 10; bytes 5-9 hold the sensor key and
	 * entity.
	 */
	private static byte[] fullRecord(int... body) {
		int[] head = { 0x42, 0x00, 0x51, 0x01, 0, 0x40, 0x00, 0x01, 0x07, 0x01 };
		byte[] result = new byte[head.length + body.length];
		for (int i = 0; i < head.length; i++) {
			result[i] = (byte) head[i];
		}
		for (int i = 0; i < body.length; i++) {
			result[head.length + i] = (byte) body[i];
		}
		result[4] = (byte) (result.length - 5);
		return result;
	}

	// @formatter:off
	private static final byte[] EXPONENTIAL_TEMPERATURE = fullRecord(
			0x00, // 10: initialization: "init sensor type" (bit 2) clear, which has nothing to do with thresholds
			0x24, // 11: capabilities: thresholds readable
			0x01, 0x01, // 12-13: temperature sensor, threshold reading type
			0x00, 0x00, 0x00, 0x00, 0x38, 0x00, // 14-19: masks: UNR, UC and UNC readable, lower thresholds not
			0x00, 0x01, 0x00, // 20-22: unsigned, degrees C, no modifier unit
			0x04, // 23: linearization e^x
			0x01, 0x04, 0x00, 0x01, 0x08, 0x00, // 24-29: M=1, tolerance 4, B=0, accuracy 1 x 10^2, R exp 0, B exp 0
			0x00, // 30: analog characteristics
			0x02, 0x00, 0x00, 0x00, 0x00, // 31-35: nominal 2, normal max/min, sensor max/min
			0x05, 0x04, 0x03, 0x00, 0x00, 0x00, // 36-41: UNR 5, UC 4, UNC 3, lower thresholds unreadable
			0x00, 0x00, 0x00, 0x00, 0x00, // 42-46: hysteresis, reserved, OEM
			0xc3, 'C', 'P', 'U'); // 47-50: 8-bit ASCII ID string

	private static final byte[] NO_THRESHOLDS_NO_READING = fullRecord(
			0x04, // 10: initialization: "init sensor type" set, which is not "thresholds present"
			0x20, // 11: capabilities: no thresholds (bits [3:2] = 00b)
			0x01, 0x01, // 12-13: temperature sensor, threshold reading type
			0x00, 0x00, 0x00, 0x00, 0x3f, 0x00, // 14-19: masks claim every threshold readable
			0xc0, 0x01, 0x00, // 20-22: no analog reading (data format 11b), degrees C
			0x00, // 23: linear
			0x01, 0x00, 0x00, 0x00, 0x00, 0x00, // 24-29: M=1
			0x00, // 30
			0x00, 0x00, 0x00, 0x00, 0x00, // 31-35
			0x05, 0x04, 0x03, 0x02, 0x01, 0x00, // 36-41: threshold bytes that must be ignored
			0x00, 0x00, 0x00, 0x00, 0x00, // 42-46
			0xc3, 'C', 'P', 'U'); // 47-50
	// @formatter:on

	@Test
	void thresholdsAreGatedOnByte12AndLinearizedLikeTheReading() {
		FullSensorRecord record = assertInstanceOf(
				FullSensorRecord.class,
				SensorRecord.populateSensorRecord(EXPONENTIAL_TEMPERATURE));

		assertTrue(record.hasAnalogReading());
		assertEquals(Math.exp(2), record.getNominalReading(), DELTA);
		assertEquals(Math.exp(5), record.getUpperNonRecoverableThreshold(), DELTA);
		assertEquals(Math.exp(4), record.getUpperCriticalThreshold(), DELTA);
		assertEquals(Math.exp(3), record.getUpperNonCriticalThreshold(), DELTA);
		assertTrue(Double.isNaN(record.getLowerNonRecoverableThreshold()), "an unreadable threshold is NaN");
		assertTrue(Double.isNaN(record.getLowerCriticalThreshold()));
		assertTrue(Double.isNaN(record.getLowerNonCriticalThreshold()));
		assertEquals(Math.exp(7), record.calcFormula(7), DELTA);
	}

	@Test
	void accuracyExponentAndToleranceAreDecoded() {
		FullSensorRecord record = (FullSensorRecord) SensorRecord.populateSensorRecord(EXPONENTIAL_TEMPERATURE);

		// accuracy 1 (in 1/100 %) x 10^2
		assertEquals(0.01, record.getAccuracy(), DELTA);
		// tolerance 4 half raw counts: 4 / 2 x |M| x 10^R
		assertEquals(2.0, record.getTolerance(), DELTA);
	}

	@Test
	void recordWithoutThresholdsOrAnalogReadingSaysSo() {
		FullSensorRecord record = (FullSensorRecord) SensorRecord.populateSensorRecord(NO_THRESHOLDS_NO_READING);

		assertFalse(record.hasAnalogReading());
		assertTrue(Double.isNaN(record.getUpperNonRecoverableThreshold()));
		assertTrue(Double.isNaN(record.getUpperCriticalThreshold()));
		assertTrue(Double.isNaN(record.getUpperNonCriticalThreshold()));
		assertTrue(Double.isNaN(record.getLowerNonRecoverableThreshold()));
		assertTrue(Double.isNaN(record.getLowerCriticalThreshold()));
		assertTrue(Double.isNaN(record.getLowerNonCriticalThreshold()));
	}

	@Test
	void nonLinearSensorHasNoComputableReading() {
		byte[] nonLinear = EXPONENTIAL_TEMPERATURE.clone();
		nonLinear[23] = 0x70; // linearization: non-linear, the factors hold at the nominal reading only

		FullSensorRecord record = (FullSensorRecord) SensorRecord.populateSensorRecord(nonLinear);

		assertFalse(record.hasAnalogReading());
		assertTrue(Double.isNaN(record.calcFormula(7)));
		assertTrue(Double.isNaN(record.getUpperCriticalThreshold()));
	}

	@Test
	void reservedUnitValuesDoNotThrow() {
		assertEquals(RateUnit.None, RateUnit.parseInt(7));
		assertEquals(ModifierUnitUsage.None, ModifierUnitUsage.parseInt(3));
	}
}
