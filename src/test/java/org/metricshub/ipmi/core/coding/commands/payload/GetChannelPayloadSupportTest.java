package org.metricshub.ipmi.core.coding.commands.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.metricshub.ipmi.core.coding.commands.IpmiResponses.response;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.CommandCodes;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;

class GetChannelPayloadSupportTest {

	@Test
	void eachBitmapIsReadAtItsOffset() throws Exception {
		// Standard payloads IPMI and SOL, 8-15 none, session setup payloads 0-5, 8-15 none, OEM none (table 24-10)
		GetChannelPayloadSupportResponseData data = (GetChannelPayloadSupportResponseData) new GetChannelPayloadSupport()
				.getResponseData(response(CommandCodes.GET_CHANNEL_PAYLOAD_SUPPORT, 0x00, 0x03, 0x00, 0x3f, 0, 0, 0, 0, 0));
		assertEquals(
				EnumSet
						.of(
								PayloadType.Ipmi,
								PayloadType.Sol,
								PayloadType.RmcpOpenSessionRequest,
								PayloadType.RmcpOpenSessionResponse,
								PayloadType.Rakp1,
								PayloadType.Rakp2,
								PayloadType.Rakp3,
								PayloadType.Rakp4),
				data.getSupportedPayloads());
	}

	@Test
	void aShortResponseIsRejected() {
		GetChannelPayloadSupport command = new GetChannelPayloadSupport();
		assertThrows(
				IllegalArgumentException.class,
				() -> command
						.getResponseData(
								response(CommandCodes.GET_CHANNEL_PAYLOAD_SUPPORT, 0x00, 0x03, 0x00, 0x3f, 0, 0, 0, 0)));
	}
}
