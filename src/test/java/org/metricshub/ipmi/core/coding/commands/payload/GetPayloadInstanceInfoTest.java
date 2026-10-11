package org.metricshub.ipmi.core.coding.commands.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.metricshub.ipmi.core.coding.commands.IpmiResponses.response;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.CommandCodes;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;

class GetPayloadInstanceInfoTest {

	private final GetPayloadInstanceInfo command = new GetPayloadInstanceInfo(CipherSuite.getEmpty(), PayloadType.Sol, 1);

	@Test
	void theSessionIdIsReadLsByteFirst() throws Exception {
		// Session ID, then 8 bytes of payload-specific information (table 24-7)
		GetPayloadInstanceInfoResponseData data = (GetPayloadInstanceInfoResponseData) command
				.getResponseData(
						response(CommandCodes.GET_PAYLOAD_INSTANCE_INFO, 0x00, 0x78, 0x56, 0x34, 0x12, 1, 0, 0, 0, 0, 0, 0, 0));
		assertEquals(0x12345678, data.getSessionId());
	}

	@Test
	void aTruncatedResponseIsRejected() {
		// A session ID alone must not be trusted to deactivate a payload
		assertThrows(
				IllegalArgumentException.class,
				() -> command.getResponseData(response(CommandCodes.GET_PAYLOAD_INSTANCE_INFO, 0x00, 0x78, 0x56, 0x34, 0x12)));
	}
}
