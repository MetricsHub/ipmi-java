package org.metricshub.ipmi.core.coding.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.metricshub.ipmi.core.coding.commands.IpmiResponses.response;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.fru.BaseUnit;
import org.metricshub.ipmi.core.coding.commands.fru.ReadFruData;
import org.metricshub.ipmi.core.coding.commands.sdr.ReserveSdrRepository;
import org.metricshub.ipmi.core.coding.commands.sdr.ReserveSdrRepositoryResponseData;
import org.metricshub.ipmi.core.coding.commands.session.CloseSession;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelCipherSuites;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;

class IpmiCommandCoderTest {

	private static final byte CLOSE_SESSION = 0x3c;

	private static final ReserveSdrRepository COMMAND = new ReserveSdrRepository(
			IpmiVersion.V20,
			CipherSuite.getEmpty(),
			AuthenticationType.RMCPPlus);

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
		assertEquals(0xc5, e.getRawCode());
	}

	@Test
	void responseToAnotherCommandIsRejected() {
		IllegalArgumentException e = assertThrows(
				IllegalArgumentException.class,
				() -> COMMAND.getResponseData(response(CommandCodes.GET_SDR, 0x00, 0x34, 0x12)));
		assertEquals("This is not a response for ReserveSdrRepository command", e.getMessage());
	}

	@Test
	void oemAndCommandSpecificCodesAreReportedWithTheirRawValue() {
		IPMIException oem = assertThrows(
				IPMIException.class,
				() -> COMMAND.getResponseData(response(CommandCodes.RESERVE_SDR_REPOSITORY, 0x8a)));
		assertEquals(CompletionCode.Unknown, oem.getCompletionCode());
		assertEquals(0x8a, oem.getRawCode());
		assertEquals("OEM completion code 0x8A.", oem.getMessage());

		// 0Dh is "Unauthorized name" for RAKP only: for an IPMI command it is a command-specific code
		IPMIException specific = assertThrows(
				IPMIException.class,
				() -> COMMAND.getResponseData(response(CommandCodes.RESERVE_SDR_REPOSITORY, 0x0d)));
		assertEquals(CompletionCode.Unknown, specific.getCompletionCode());
		assertEquals("Command-specific completion code 0x0D.", specific.getMessage());

		IPMIException reserved = assertThrows(
				IPMIException.class,
				() -> COMMAND.getResponseData(response(CommandCodes.RESERVE_SDR_REPOSITORY, 0xd9)));
		assertEquals("Reserved completion code 0xD9.", reserved.getMessage());
	}

	@Test
	void readFruDataKnowsItsBusyCode() {
		ReadFruData readFruData = new ReadFruData(
				IpmiVersion.V20,
				CipherSuite.getEmpty(),
				AuthenticationType.RMCPPlus,
				0,
				BaseUnit.Bytes,
				0,
				16);
		IPMIException e = assertThrows(
				IPMIException.class,
				() -> readFruData.getResponseData(response(CommandCodes.READ_FRU_DATA, 0x81)));
		assertEquals(CompletionCode.Frudevicebusy, e.getCompletionCode());
	}

	@Test
	void closeSessionKnowsItsSessionCodes() {
		CloseSession closeSession = new CloseSession(
				IpmiVersion.V20,
				CipherSuite.getEmpty(),
				AuthenticationType.RMCPPlus,
				0x1234);
		IPMIException invalidId = assertThrows(
				IPMIException.class,
				() -> closeSession.getResponseData(response(CLOSE_SESSION, 0x87)));
		assertEquals(CompletionCode.InvalidSessionId, invalidId.getCompletionCode());
		IPMIException invalidHandle = assertThrows(
				IPMIException.class,
				() -> closeSession.getResponseData(response(CLOSE_SESSION, 0x88)));
		assertEquals(CompletionCode.InvalidSessionHandle, invalidHandle.getCompletionCode());
	}

	@Test
	void getChannelCipherSuitesChecksTheCompletionCode() {
		GetChannelCipherSuites command = new GetChannelCipherSuites((byte) 0x0e, (byte) 0);
		IPMIException e = assertThrows(
				IPMIException.class,
				() -> command.getResponseData(response(CommandCodes.GET_CHANNEL_CIPHER_SUITES, 0xcc)));
		assertEquals(CompletionCode.InvalidData, e.getCompletionCode());
	}
}
