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

	private static byte[] conversionRecord(int units, int linearization) {
		byte[] data = EXPONENTIAL_TEMPERATURE.clone();
		data[20] = (byte) units;
		data[23] = (byte) linearization;
		// M=1, B=0, both exponents zero; retain the nominal reading and threshold bytes.
		for (int i = 25; i <= 29; i++) {
			data[i] = 0;
		}
		return data;
	}

	@Test
	void unsignedReadingsRetainTheirHighBit() {
		FullSensorRecord record = (FullSensorRecord) SensorRecord.populateSensorRecord(conversionRecord(0x00, 0x00));

		assertEquals(0, record.calcFormula(0x00), DELTA);
		assertEquals(128, record.calcFormula(0x80), DELTA);
		assertEquals(255, record.calcFormula(0xff), DELTA);
	}

	@Test
	void onesComplementReadingsIncludeNegativeZero() {
		FullSensorRecord record = (FullSensorRecord) SensorRecord.populateSensorRecord(conversionRecord(0x40, 0x00));

		assertEquals(127, record.calcFormula(0x7f), DELTA);
		assertEquals(-127, record.calcFormula(0x80), DELTA);
		assertEquals(-1, record.calcFormula(0xfe), DELTA);
		assertEquals(0, record.calcFormula(0xff), DELTA);
	}

	@Test
	void twosComplementReadingsUseTheFullSignedByteRange() {
		FullSensorRecord record = (FullSensorRecord) SensorRecord.populateSensorRecord(conversionRecord(0x80, 0x00));

		assertEquals(127, record.calcFormula(0x7f), DELTA);
		assertEquals(-128, record.calcFormula(0x80), DELTA);
		assertEquals(-1, record.calcFormula(0xff), DELTA);
	}

	@Test
	void coefficientsUseAllTenSignedBitsWithoutToleranceOrAccuracyBits() {
		byte[] data = conversionRecord(0x00, 0x00);
		data[24] = (byte) 0xff;
		data[25] = 0x44; // M=511; tolerance=4
		data[26] = 0x00;
		data[27] = (byte) 0x81; // B=-512; accuracy=1
		FullSensorRecord record = (FullSensorRecord) SensorRecord.populateSensorRecord(data);
		assertEquals(-512, record.calcFormula(0), DELTA);
		assertEquals(510, record.calcFormula(2), DELTA);

		data[24] = 0x00;
		data[25] = (byte) 0x84; // M=-512; tolerance=4
		data[26] = (byte) 0xff;
		data[27] = 0x41; // B=511; accuracy=1
		record = (FullSensorRecord) SensorRecord.populateSensorRecord(data);
		assertEquals(511, record.calcFormula(0), DELTA);
		assertEquals(-513, record.calcFormula(2), DELTA);
	}

	@Test
	void coefficientAndExponentScalingPrecedesLinearization() {
		byte[] data = conversionRecord(0x00, 0x08); // square
		data[24] = (byte) 0xfe;
		data[25] = (byte) 0xc0; // M=-2
		data[26] = (byte) 0xfd;
		data[27] = (byte) 0xc0; // B=-3
		data[29] = (byte) 0xf1; // R=-1, B exponent=1
		FullSensorRecord record = (FullSensorRecord) SensorRecord.populateSensorRecord(data);

		// IPMI 2.0 section 36.3: ((-2 * 4 - 3 * 10) / 10)^2 = 14.44.
		assertEquals(14.44, record.calcFormula(4), DELTA);
		assertEquals(11.56, record.getNominalReading(), DELTA);
		assertEquals(14.44, record.getUpperCriticalThreshold(), DELTA);

		data[23] = 0x00; // linear
		data[24] = 0x02;
		data[25] = 0x00;
		data[26] = 0x03;
		data[27] = 0x00;
		data[29] = 0x1f; // R=1, B exponent=-1
		record = (FullSensorRecord) SensorRecord.populateSensorRecord(data);
		assertEquals(83, record.calcFormula(4), DELTA); // (2 * 4 + 3 / 10) * 10
	}

	@Test
	void exponentNibblesCoverMinusEightThroughSeven() {
		byte[] data = conversionRecord(0x00, 0x00);
		data[29] = (byte) 0x80; // R=-8
		FullSensorRecord record = (FullSensorRecord) SensorRecord.populateSensorRecord(data);
		assertEquals(0.00000002, record.calcFormula(2), 1e-15);

		data[29] = 0x70; // R=7
		record = (FullSensorRecord) SensorRecord.populateSensorRecord(data);
		assertEquals(20000000, record.calcFormula(2), DELTA);

		data[24] = 0x00; // M=0 isolates the B term
		data[26] = 0x03;
		data[29] = 0x08; // B exponent=-8
		record = (FullSensorRecord) SensorRecord.populateSensorRecord(data);
		assertEquals(0.00000003, record.calcFormula(2), 1e-15);

		data[29] = 0x07; // B exponent=7
		record = (FullSensorRecord) SensorRecord.populateSensorRecord(data);
		assertEquals(30000000, record.calcFormula(2), DELTA);
	}

	@Test
	void allSupportedLinearizationsConvertReadings() {
		// IPMI 2.0 Table 43-1: fixed expected values, including negative cube and cube-root inputs.
		assertConversion(0x00, 2, 2); // linear
		assertConversion(0x01, 2, 0.6931471805599453); // natural logarithm
		assertConversion(0x02, 100, 2); // log10
		assertConversion(0x03, 8, 3); // log2
		assertConversion(0x04, 1, 2.718281828459045); // e^x
		assertConversion(0x05, 2, 100); // 10^x
		assertConversion(0x06, 3, 8); // 2^x
		assertConversion(0x07, 4, 0.25); // reciprocal
		assertConversion(0x08, 3, 9); // square
		assertConversion(0x09, 0xfe, -8); // cube of -2
		assertConversion(0x0a, 9, 3); // square root
		assertConversion(0x0b, 0xe5, -3); // cube root of -27
	}

	private static void assertConversion(int linearization, int raw, double expected) {
		FullSensorRecord record = (FullSensorRecord) SensorRecord
				.populateSensorRecord(conversionRecord(0x80, linearization));
		assertTrue(record.hasAnalogReading(), "linearization " + linearization);
		assertEquals(expected, record.calcFormula(raw), DELTA, "linearization " + linearization);
	}

	@Test
	void eachThresholdHonorsItsReadableMask() {
		double[] expectedValues = { -2, -3, -4, -5, -6, -7 };
		for (int bit = 0; bit < expectedValues.length; bit++) {
			byte[] data = conversionRecord(0x80, 0x00);
			data[18] = (byte) (1 << bit);
			for (int i = 36; i <= 41; i++) {
				data[i] = (byte) (0xf9 + i - 36); // UNR=-7 through LNC=-2
			}
			FullSensorRecord record = (FullSensorRecord) SensorRecord.populateSensorRecord(data);
			double[] thresholds = {
					record.getLowerNonCriticalThreshold(),
					record.getLowerCriticalThreshold(),
					record.getLowerNonRecoverableThreshold(),
					record.getUpperNonCriticalThreshold(),
					record.getUpperCriticalThreshold(),
					record.getUpperNonRecoverableThreshold() };
			for (int i = 0; i < thresholds.length; i++) {
				assertEquals(
						i == bit ? expectedValues[i] : Double.NaN,
						thresholds[i],
						DELTA,
						"readable mask bit " + bit + ", threshold " + i);
			}
		}
	}

	@Test
	void reservedAndOemLinearizationsHaveNoComputableReading() {
		int[] codes = { 0x0c, 0x6f, 0x7f };
		for (int code : codes) {
			FullSensorRecord record = (FullSensorRecord) SensorRecord
					.populateSensorRecord(conversionRecord(0x00, code));
			assertFalse(record.hasAnalogReading(), "linearization " + code);
			assertTrue(Double.isNaN(record.calcFormula(7)), "linearization " + code);
			assertTrue(Double.isNaN(record.getNominalReading()), "linearization " + code);
			assertTrue(Double.isNaN(record.getUpperCriticalThreshold()), "linearization " + code);
		}
	}

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
		assertTrue(Double.isNaN(record.calcFormula(7)), "the reading byte of such a record is not a value");
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
