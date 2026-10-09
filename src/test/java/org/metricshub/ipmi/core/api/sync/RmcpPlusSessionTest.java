package org.metricshub.ipmi.core.api.sync;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.GET_CHASSIS_STATUS;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.NETFN_CHASSIS;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.client.IpmiClientConfiguration;
import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatus;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatusResponseData;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc;
import org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.Fault;

/**
 * End-to-end tests of RMCP+ sessions against {@link FakeRmcpPlusBmc}, a BMC written from the IPMI 2.0 specification
 * independently of the coders of the library, over loopback UDP.
 */
class RmcpPlusSessionTest {

	/** Cipher suite records of a Lenovo XCC (suites 1 to 19), after the channel byte. */
	private static final byte[] XCC_SUITES = bytes(
			"c0 01 01 40 80 c0 02 01 41 80 c0 03 01 41 81 c0 04 01 41 82 c0 05 01 41 83 c0 06 02 40 80 c0 07 02 42 80"
					+ " c0 08 02 42 81 c0 09 02 42 82 c0 0a 02 42 83 c0 0b 02 43 80 c0 0c 02 43 81 c0 0d 02 43 82 c0 0e 02 43 83"
					+ " c0 0f 03 40 80 c0 10 03 44 80 c0 11 03 44 81 c0 12 03 44 82 c0 13 03 44 83");

	/**
	 * Cipher suite records of a Cisco IMC (suites 0 to 14, then malformed OEM records), after the channel byte: 91
	 * bytes, so that the list ends with a short chunk.
	 */
	private static final byte[] CISCO_SUITES = bytes(
			"c0 00 00 40 80 c0 01 01 40 80 c0 02 01 41 80 c0 03 01 41 81 c0 04 01 41 82 c0 05 01 41 83 c0 06 02 40 80"
					+ " c0 07 02 42 80 c0 08 02 42 81 c0 09 02 42 82 c0 0a 02 42 83 c0 0b 02 43 80 c0 0c 02 43 81 c0 0d 02 43 82"
					+ " c0 0e 02 43 83 c1 80 00 00 00 c1 c1 b1 c1 63 00 00 00 69 70 e8");

	/** Authentication, integrity and confidentiality algorithms of cipher suite 17. */
	private static final int[] SUITE_17 = { 3, 4, 1 };

	/** Authentication, integrity and confidentiality algorithms of cipher suite 3. */
	private static final int[] SUITE_3 = { 1, 1, 1 };

	private static final String USER = "admin";
	private static final String PASSWORD = "secret";

	/** A Kg whose bytes are not all valid in any charset. */
	private static final byte[] KG = bytes("80 ff 01 fe 7f 00 c3 28 a0 a1 e2 82 ff ff 00 01 02 03 04 05");

	private static final int TIMEOUT_MS = 300;

	@Test
	@Timeout(30)
	void ipmiClientOpensSuite17OnALenovoXcc() throws Exception {
		assertIpmiClientSession(XCC_SUITES, SUITE_17);
	}

	@Test
	@Timeout(30)
	void ipmiClientOpensSuite3OnACiscoImcWithMalformedOemRecords() throws Exception {
		assertIpmiClientSession(CISCO_SUITES, SUITE_3);
	}

	/**
	 * The whole flow of {@link IpmiClient}: cipher suites and the choice of the client, authentication capabilities,
	 * session, Get Chassis Status, Close Session.
	 */
	private static void assertIpmiClientSession(byte[] suites, int[] algorithms) throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(suites, USER, utf8(PASSWORD), null)) {
			IpmiClientConfiguration configuration = new IpmiClientConfiguration(
					bmc.getAddress().getHostAddress(),
					bmc.getPort(),
					USER,
					PASSWORD.toCharArray(),
					null,
					false,
					20);
			assertChassisStatus(IpmiClient.getChassisStatus(configuration));
			assertArrayEquals(algorithms, bmc.getAlgorithms());
			awaitClose(bmc);
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aListOfCipherSuitesEndingWithAnEmptyChunkIsRead() throws Exception {
		// 96 bytes: six full chunks of 16 bytes, then an empty one
		byte[] suites = Arrays.copyOf(CISCO_SUITES, 96);
		System.arraycopy(bytes("c1 00 00 00 00"), 0, suites, 91, 5);
		assertIpmiClientSession(suites, SUITE_3);
	}

	@Test
	@Timeout(30)
	void twoKeyLoginWithAKgOfBytesAbove80h() throws Exception {
		assertSession(PASSWORD, KG, KG, 17);
		assertSession(PASSWORD, KG, KG, 3);
	}

	@Test
	@Timeout(30)
	void anAllZeroKgIsAOneKeyLogin() throws Exception {
		assertSession(PASSWORD, null, new byte[20], 17);
	}

	@Test
	@Timeout(30)
	void anEmptyPasswordIsAKeyOfZeros() throws Exception {
		assertSession("", null, null, 17);
	}

	/**
	 * Opens a session of the given suite, sends Get Chassis Status and closes the session.
	 */
	private static void assertSession(String password, byte[] bmcKg, byte[] consoleKg, int suite) throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(XCC_SUITES, USER, utf8(password), bmcKg)) {
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				ConnectionHandle handle = openSession(connector, bmc, suite, password, consoleKg);
				assertChassisStatus(getChassisStatus(connector, handle));
				connector.closeSession(handle);
				awaitClose(bmc);
				assertEquals(Collections.emptyList(), bmc.getErrors());
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void aReplyWithABadAuthCodeIsDroppedAndTheRequestSentAgain() throws Exception {
		assertReplyDropped(1, Fault.FLIP_AUTH_CODE);
	}

	@Test
	@Timeout(30)
	void aReplyWithATamperedPayloadIsDroppedAndTheRequestSentAgain() throws Exception {
		assertReplyDropped(1, Fault.FLIP_PAYLOAD);
	}

	@Test
	@Timeout(30)
	void anUnauthenticatedReplyInTheSessionIsDroppedAndTheRequestSentAgain() throws Exception {
		assertReplyDropped(1, Fault.PLAINTEXT);
	}

	@Test
	@Timeout(30)
	void aReplyWithASequenceNumberReceivedAlreadyIsDroppedAndTheRequestSentAgain() throws Exception {
		assertReplyDropped(2, Fault.REUSED_SEQUENCE_NUMBER);
	}

	@Test
	@Timeout(30)
	void aLostReplyIsSentAgain() throws Exception {
		// The control of the tests above: what they expect of a dropped reply
		assertReplyDropped(1, Fault.DROP);
	}

	/**
	 * Sends Get Chassis Status until the given request, whose reply has the given fault: the library must drop that
	 * reply and send the request again, which the BMC answers properly.
	 */
	private static void assertReplyDropped(int request, Fault fault) throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(XCC_SUITES, USER, utf8(PASSWORD), null)) {
			bmc.inject(request, fault);
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				ConnectionHandle handle = openSession(connector, bmc, 17, PASSWORD, null);
				for (int i = 0; i < request; i++) {
					assertChassisStatus(getChassisStatus(connector, handle));
				}
				assertEquals(request + 1, bmc.getCount(NETFN_CHASSIS, GET_CHASSIS_STATUS), "the faulty reply was dropped");
				assertEquals(Collections.emptyList(), bmc.getErrors());
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void anOpenSessionResponseWithAnotherIntegrityAlgorithmFails() throws Exception {
		assertHandshakeFails(Fault.OPEN_SESSION_INTEGRITY_NONE, "Open Session Response");
	}

	@Test
	@Timeout(30)
	void anOpenSessionResponseWithoutEncryptionOrWithAnotherAuthenticationFails() throws Exception {
		assertHandshakeFails(Fault.OPEN_SESSION_CONFIDENTIALITY_NONE, "Open Session Response");
		assertHandshakeFails(Fault.OPEN_SESSION_OTHER_AUTHENTICATION, "Open Session Response");
	}

	@Test
	@Timeout(30)
	void anOpenSessionResponseForAnotherConsoleSessionFails() throws Exception {
		assertHandshakeFails(Fault.OPEN_SESSION_OTHER_CONSOLE_SESSION_ID, "Open Session Response");
	}

	@Test
	@Timeout(30)
	void anOpenSessionResponseWithANullManagedSystemSessionIdFails() throws Exception {
		assertHandshakeFails(Fault.OPEN_SESSION_NULL_MANAGED_SYSTEM_SESSION_ID, "Open Session Response");
	}

	@Test
	@Timeout(30)
	void aRakp2ForAnotherConsoleSessionFails() throws Exception {
		assertHandshakeFails(Fault.RAKP_2_OTHER_CONSOLE_SESSION_ID, "RAKP Message 2");
	}

	@Test
	@Timeout(30)
	void aRakp4ForAnotherConsoleSessionFails() throws Exception {
		assertHandshakeFails(Fault.RAKP_4_OTHER_CONSOLE_SESSION_ID, "RAKP Message 4");
	}

	private static void assertHandshakeFails(Fault fault, String reply) throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(XCC_SUITES, USER, utf8(PASSWORD), null)) {
			bmc.inject(fault);
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				IllegalArgumentException e = assertThrows(
						IllegalArgumentException.class,
						() -> openSession(connector, bmc, 17, PASSWORD, null));
				assertEquals(reply + " does not match the request", e.getMessage());
				// The BMC would answer the same: the handshake is not tried again
				assertEquals(1, bmc.getCount(FakeRmcpPlusBmc.OPEN_SESSION_REQUEST));
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void aWrongPasswordFailsAtRakp2WithoutRetry() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(XCC_SUITES, USER, utf8(PASSWORD), null)) {
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				IllegalArgumentException e = assertThrows(
						IllegalArgumentException.class,
						() -> openSession(connector, bmc, 17, "wrong", null));
				assertEquals("Authentication check failed", e.getMessage());
				assertEquals(1, bmc.getCount(FakeRmcpPlusBmc.RAKP_1), "the credentials must not be sent again");
				assertEquals(0, bmc.getCount(FakeRmcpPlusBmc.RAKP_3));
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void aPasswordLongerThan20BytesFailsBeforeAnythingIsSent() throws Exception {
		// 11 characters, 22 bytes in UTF-8
		String password = "ééééééééééé";
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(XCC_SUITES, USER, utf8(PASSWORD), null)) {
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				ConnectionHandle handle = connector.createConnection(bmc.getAddress(), bmc.getPort());
				connector.setTimeout(handle, TIMEOUT_MS);
				CipherSuite suite = connector
						.getAvailableCipherSuites(handle)
						.stream()
						.filter(cipherSuite -> cipherSuite.getId() == 17)
						.findFirst()
						.orElseThrow(AssertionError::new);
				connector.getChannelAuthenticationCapabilities(handle, suite, PrivilegeLevel.User);

				IllegalArgumentException e = assertThrows(
						IllegalArgumentException.class,
						() -> connector.openSession(handle, USER, password, null));
				assertEquals("Password is too long. Its length cannot exceed 20 bytes", e.getMessage());
				assertEquals(0, bmc.getCount(FakeRmcpPlusBmc.OPEN_SESSION_REQUEST), "nothing must reach the BMC");

				// The handle is still usable
				connector.openSession(handle, USER, PASSWORD, null);
				assertChassisStatus(getChassisStatus(connector, handle));
				connector.closeSession(handle);
				awaitClose(bmc);
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void aSessionReopenedOnTheSameConnectionGetsItsReplies() throws Exception {
		// The BMC numbers the messages of each session from 1: the replies of the second session must not be taken
		// for replays of the first one
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(XCC_SUITES, USER, utf8(PASSWORD), null)) {
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				ConnectionHandle handle = openSession(connector, bmc, 17, PASSWORD, null);
				for (int i = 0; i < 3; i++) {
					assertChassisStatus(getChassisStatus(connector, handle));
				}
				connector.closeSession(handle);
				awaitClose(bmc);

				connector.openSession(handle, USER, PASSWORD, null);
				assertChassisStatus(getChassisStatus(connector, handle));
				assertEquals(4, bmc.getCount(FakeRmcpPlusBmc.NETFN_CHASSIS, FakeRmcpPlusBmc.GET_CHASSIS_STATUS));
				assertEquals(Collections.emptyList(), bmc.getErrors());
			} finally {
				connector.tearDown();
			}
		}
	}

	/**
	 * The handshake of {@link IpmiClient} with the suite of the given ID: cipher suites, authentication capabilities,
	 * session.
	 */
	private static ConnectionHandle openSession(
			IpmiConnector connector,
			FakeRmcpPlusBmc bmc,
			int suiteId,
			String password,
			byte[] kg)
			throws Exception {
		ConnectionHandle handle = connector.createConnection(bmc.getAddress(), bmc.getPort());
		connector.setTimeout(handle, TIMEOUT_MS);
		CipherSuite suite = connector
				.getAvailableCipherSuites(handle)
				.stream()
				.filter(cipherSuite -> cipherSuite.getId() == suiteId)
				.findFirst()
				.orElseThrow(() -> new AssertionError("no suite " + suiteId));
		connector.getChannelAuthenticationCapabilities(handle, suite, PrivilegeLevel.User);
		connector.openSession(handle, USER, password, kg);
		return handle;
	}

	private static GetChassisStatusResponseData getChassisStatus(IpmiConnector connector, ConnectionHandle handle)
			throws Exception {
		return (GetChassisStatusResponseData) connector
				.sendMessage(
						handle,
						new GetChassisStatus(IpmiVersion.V20, handle.getCipherSuite(), AuthenticationType.RMCPPlus));
	}

	private static void assertChassisStatus(GetChassisStatusResponseData status) {
		assertEquals(FakeRmcpPlusBmc.CHASSIS_STATUS[0], status.getCurrentPowerState());
		assertEquals(FakeRmcpPlusBmc.CHASSIS_STATUS[1], status.getLastPowerEvent());
		assertEquals(FakeRmcpPlusBmc.CHASSIS_STATUS[2], status.getMiscChassisState());
	}

	/** Close Session is not answered: waits for the BMC to receive it. */
	private static void awaitClose(FakeRmcpPlusBmc bmc) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!bmc.isSessionClosed() && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}
		assertTrue(bmc.isSessionClosed(), "the BMC must receive a valid Close Session");
	}

	private static byte[] utf8(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] bytes(String hex) {
		String[] values = hex.split(" ");
		byte[] bytes = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			bytes[i] = (byte) Integer.parseInt(values[i], 16);
		}
		return bytes;
	}
}
