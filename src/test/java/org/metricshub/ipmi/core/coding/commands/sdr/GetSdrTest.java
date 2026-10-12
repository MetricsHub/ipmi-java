package org.metricshub.ipmi.core.coding.commands.sdr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.metricshub.ipmi.core.coding.commands.IpmiResponses.response;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.CommandCodes;

class GetSdrTest {

	private static final GetSdr COMMAND = new GetSdr(
			0,
			1);

	@Test
	void replyWithoutRecordDataStillGivesTheNextRecordId() throws Exception {
		GetSdrResponseData data = (GetSdrResponseData) COMMAND
				.getResponseData(response(CommandCodes.GET_SDR, 0x00, 0x34, 0x12));
		assertEquals(0x1234, data.getNextRecordId());
		assertEquals(0, data.getSensorRecordData().length);
	}

	@Test
	void replyShorterThanTheNextRecordIdIsRejected() {
		assertThrows(
				IllegalArgumentException.class,
				() -> COMMAND.getResponseData(response(CommandCodes.GET_SDR, 0x00, 0x34)));
	}
}
