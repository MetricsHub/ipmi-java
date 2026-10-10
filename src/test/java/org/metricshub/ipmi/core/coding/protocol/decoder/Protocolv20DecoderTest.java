package org.metricshub.ipmi.core.coding.protocol.decoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.Encoder;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReading;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv20Message;
import org.metricshub.ipmi.core.coding.protocol.encoder.Protocolv20Encoder;
import org.metricshub.ipmi.core.coding.rmcp.RmcpDecoder;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.coding.security.SecurityConstants;

class Protocolv20DecoderTest {

	private static final int SESSION_ID = 0x1234;

	/** Offset of the IPMI session header in a datagram: after the RMCP header. */
	private static final int SESSION_HEADER = 4;

	/**
	 * Cipher suites 3 (HMAC-SHA1-96 integrity, AES-CBC-128 confidentiality) and 16 (HMAC-SHA256-128 integrity, no
	 * confidentiality), keyed as after a handshake.
	 */
	private static List<CipherSuite> suites() throws Exception {
		byte[] sik = new byte[32];
		Arrays.fill(sik, (byte) 0x5a);
		CipherSuite suite3 = new CipherSuite(
				(byte) 3,
				SecurityConstants.AA_RAKP_HMAC_SHA1,
				SecurityConstants.CA_AES_CBC128,
				SecurityConstants.IA_HMAC_SHA1_96);
		suite3.initializeAlgorithms(Arrays.copyOf(sik, 20));
		CipherSuite suite16 = new CipherSuite(
				(byte) 16,
				SecurityConstants.AA_RAKP_HMAC_SHA256,
				SecurityConstants.CA_NONE,
				SecurityConstants.IA_HMAC_SHA256_128);
		suite16.initializeAlgorithms(sik);
		return Arrays.asList(suite3, suite16);
	}

	/**
	 * A signed (and, with suite 3, encrypted) in-session datagram. A Get Sensor Reading request, with its single data
	 * byte, is laid
	 * out like an IPMI LAN response, so that the decoder can parse it back.
	 */
	private static byte[] signedDatagram(CipherSuite suite) throws Exception {
		return Encoder
				.encode(
						new Protocolv20Encoder(),
						new GetSensorReading(IpmiVersion.V20, suite, AuthenticationType.RMCPPlus, 0x42),
						5,
						7,
						SESSION_ID);
	}

	private static Ipmiv20Message decode(CipherSuite suite, byte[] datagram) throws Exception {
		return (Ipmiv20Message) new Protocolv20Decoder(suite).decode(RmcpDecoder.decode(datagram));
	}

	@Test
	void aSignedMessageIsDecoded() throws Exception {
		for (CipherSuite suite : suites()) {
			assertDecoded(suite);
		}
	}

	private static void assertDecoded(CipherSuite suite) throws Exception {
		Ipmiv20Message message = decode(suite, signedDatagram(suite));

		assertEquals(SESSION_ID, message.getSessionID());
		assertEquals(7, message.getSessionSequenceNumber());
		assertTrue(message.isPayloadAuthenticated());
		assertEquals(suite.getId() == 3, message.isPayloadEncrypted());
		IpmiLanResponse payload = (IpmiLanResponse) message.getPayload();
		assertEquals(0x2d, payload.getCommand()); // Get Sensor Reading
		assertEquals(0x42, payload.getRawCompletionCode()); // the sensor number, in the place of the code
	}

	@Test
	void anyModifiedByteFailsTheIntegrityCheck() throws Exception {
		for (CipherSuite suite : suites()) {
			byte[] datagram = signedDatagram(suite);

			// Every byte of the session header, the payload, the integrity pad and the AuthCode is covered
			for (int i = SESSION_HEADER; i < datagram.length; i++) {
				byte[] tampered = datagram.clone();
				tampered[i] ^= 0x01;
				assertThrows(
						IllegalArgumentException.class,
						() -> decode(suite, tampered),
						"suite " + suite.getId() + ", byte " + i + " was modified");
			}
		}
	}

	@Test
	void anUnauthenticatedMessageIsRejectedInASessionWithIntegrity() throws Exception {
		CipherSuite suite = suites().get(0);
		byte[] datagram = signedDatagram(suite);

		// Clear the "authenticated" bit and remove the session trailer, which the MAC no longer protects
		int payloadLength = (datagram[SESSION_HEADER + 10] & 0xff) | (datagram[SESSION_HEADER + 11] & 0xff) << 8;
		byte[] unsigned = Arrays.copyOf(datagram, SESSION_HEADER + 12 + payloadLength);
		unsigned[SESSION_HEADER + 1] &= ~0x40;

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> decode(suite, unsigned));
		assertTrue(e.getMessage().contains("Unauthenticated"), e.getMessage());
	}

	@Test
	void aTruncatedMessageIsRejectedWithoutIndexingPastItsEnd() throws Exception {
		CipherSuite suite = suites().get(0);
		byte[] datagram = signedDatagram(suite);

		for (int length = 0; length < datagram.length; length++) {
			byte[] truncated = Arrays.copyOf(datagram, length);
			assertThrows(IllegalArgumentException.class, () -> decode(suite, truncated), length + " bytes");
		}
	}

	@Test
	void aPayloadLengthBeyondTheMessageIsRejected() throws Exception {
		CipherSuite suite = suites().get(0);
		byte[] datagram = signedDatagram(suite);
		datagram[SESSION_HEADER + 11] = 0x7f; // payload length of 32 kB

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> decode(suite, datagram));
		assertTrue(e.getMessage().contains("truncated"), e.getMessage());
	}
}
