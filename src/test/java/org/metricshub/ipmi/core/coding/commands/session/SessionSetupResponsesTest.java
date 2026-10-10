package org.metricshub.ipmi.core.coding.commands.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.IpmiResponses;
import org.metricshub.ipmi.core.coding.payload.PlainMessage;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv20Message;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.coding.security.ConfidentialityNone;

class SessionSetupResponsesTest {

	private static boolean ipmiv20Support(int authenticationTypes, int extendedCapabilities) throws Exception {
		GetChannelAuthenticationCapabilitiesResponseData data = (GetChannelAuthenticationCapabilitiesResponseData) new GetChannelAuthenticationCapabilities()
				.getResponseData(
						IpmiResponses
								.response((byte) 0x38, 0x00, 0x01, authenticationTypes, 0x00, extendedCapabilities, 0, 0, 0, 0));
		return data.isIpmiv20Support();
	}

	@Test
	void ipmiV20SupportIsReadFromTheExtendedCapabilities() throws Exception {
		// IPMI 2.0 table 22-15: byte 3 bit 7 says the extended capabilities are there, byte 5 bit 1 is the v2.0 support
		assertTrue(ipmiv20Support(0x84, 0x03));
		assertTrue(ipmiv20Support(0x80, 0x02));
		assertFalse(ipmiv20Support(0x84, 0x01), "a channel with IPMI v1.5 connections only");
		assertFalse(ipmiv20Support(0x04, 0x00), "no extended capabilities");
	}

	private static Ipmiv20Message openSessionResponse(byte[] payload) {
		Ipmiv20Message message = new Ipmiv20Message(new ConfidentialityNone());
		message.setPayloadType(PayloadType.RmcpOpenSessionResponse);
		message.setPayload(new PlainMessage(payload));
		return message;
	}

	@Test
	void anOpenSessionResponseTooShortIsRejected() {
		OpenSession openSession = new OpenSession(CipherSuite.getEmpty());
		for (int length : new int[] { 0, 1, 35 }) {
			assertThrows(
					IllegalArgumentException.class,
					() -> openSession.getResponseData(openSessionResponse(new byte[length])),
					length + " bytes");
		}
	}

	@Test
	void theReservedBitsOfTheAlgorithmsAreIgnored() throws Exception {
		// IPMI 2.0 table 13-9: the algorithm is in bits 5:0
		byte[] payload = new byte[36];
		payload[16] = (byte) 0xc3;
		payload[24] = (byte) 0x44;
		payload[32] = (byte) 0x81;

		OpenSessionResponseData data = (OpenSessionResponseData) new OpenSession(CipherSuite.getEmpty())
				.getResponseData(openSessionResponse(payload));

		assertEquals(3, data.getAuthenticationAlgorithm());
		assertEquals(4, data.getIntegrityAlgorithm());
		assertEquals(1, data.getConfidentialityAlgorithm());
	}
}
