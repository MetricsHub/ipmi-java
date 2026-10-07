package org.metricshub.ipmi.core.coding.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.sdr.ReserveSdrRepository;
import org.metricshub.ipmi.core.coding.commands.sdr.ReserveSdrRepositoryResponseData;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv15Message;
import org.metricshub.ipmi.core.coding.security.CipherSuite;

class IpmiCommandCoderTest {

	private static final ReserveSdrRepository COMMAND = new ReserveSdrRepository(
			IpmiVersion.V20,
			CipherSuite.getEmpty(),
			AuthenticationType.RMCPPlus);

	/**
	 * Build a message wrapping an IPMI LAN response (IPMI 2.0 table 13-5) from the BMC to remote console software.
	 */
	private static IpmiMessage response(byte command, int completionCode, int... data) {
		byte[] raw = new byte[8 + data.length];
		raw[0] = (byte) 0x81; // requester address
		raw[1] = (byte) 0x2c; // storage response network function, LUN 0
		raw[2] = (byte) -(0x81 + 0x2c); // checksum 1
		raw[3] = 0x20; // responder address
		raw[4] = 0x04; // sequence number 1, LUN 0
		raw[5] = command;
		raw[6] = (byte) completionCode;
		for (int i = 0; i < data.length; i++) {
			raw[7 + i] = (byte) data[i];
		}
		int checksum = 0;
		for (int i = 3; i < raw.length - 1; i++) {
			checksum += raw[i];
		}
		raw[raw.length - 1] = (byte) -checksum; // checksum 2

		IpmiMessage message = new Ipmiv15Message();
		message.setPayload(new IpmiLanResponse(raw));
		return message;
	}

	@Test
	void successfulResponseIsDecoded() throws Exception {
		ReserveSdrRepositoryResponseData data = (ReserveSdrRepositoryResponseData) COMMAND
				.getResponseData(response(CommandCodes.RESERVE_SDR_REPOSITORY, 0x00, 0x34, 0x12));

		assertEquals(0x1234, data.getReservationId());
	}

	@Test
	void errorCompletionCodeIsThrown() {
		IPMIException e = assertThrows(
				IPMIException.class,
				() -> COMMAND.getResponseData(response(CommandCodes.RESERVE_SDR_REPOSITORY, 0xc5)));

		assertEquals(CompletionCode.ReservationCanceled, e.getCompletionCode());
	}

	@Test
	void responseToAnotherCommandIsRejected() {
		IllegalArgumentException e = assertThrows(
				IllegalArgumentException.class,
				() -> COMMAND.getResponseData(response(CommandCodes.GET_SDR, 0x00, 0x34, 0x12)));

		assertEquals("This is not a response for ReserveSdrRepository command", e.getMessage());
	}
}
