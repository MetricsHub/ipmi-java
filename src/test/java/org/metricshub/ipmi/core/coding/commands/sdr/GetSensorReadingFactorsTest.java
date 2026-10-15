package org.metricshub.ipmi.core.coding.commands.sdr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.metricshub.ipmi.core.coding.commands.IpmiResponses.response;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorReadingFactors;

class GetSensorReadingFactorsTest {

	private static final GetSensorReadingFactors COMMAND = new GetSensorReadingFactors(5, 0x9a);

	private static final byte GET_SENSOR_READING_FACTORS = 0x23;

	@Test
	void factorsAreDecodedLikeThoseOfTheFullSensorRecord() throws Exception {
		// Table 35-5: next reading A0h; M=-2 (10 bits), tolerance 4; B=3 (10 bits), accuracy 1; accuracy exponent 2;
		// R exponent -1, B exponent 1
		GetSensorReadingFactorsResponseData data = COMMAND
				.getResponseData(response(GET_SENSOR_READING_FACTORS, 0x00, 0xa0, 0xfe, 0xc4, 0x03, 0x01, 0x08, 0xf1));

		assertEquals(0xa0, data.getNextReading());
		SensorReadingFactors factors = data.getFactors();
		assertEquals((-2 * 4 + 3 * 10) / 10.0, factors.convert(4), 1e-9);
		assertEquals(4 / 2.0 * 2 / 10, factors.getTolerance(), 1e-9);
		assertEquals(0.01, factors.getAccuracy(), 1e-9);
	}

	@Test
	void aShortReplyIsRejected() {
		assertThrows(
				IllegalArgumentException.class,
				() -> COMMAND.getResponseData(response(GET_SENSOR_READING_FACTORS, 0x00, 0xa0, 0x01, 0x00)));
	}
}
