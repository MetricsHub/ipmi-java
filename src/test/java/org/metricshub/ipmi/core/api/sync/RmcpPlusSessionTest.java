package org.metricshub.ipmi.core.api.sync;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.GET_CHANNEL_AUTHENTICATION_CAPABILITIES;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.GET_CHANNEL_CIPHER_SUITES;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.GET_CHASSIS_STATUS;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.IPMI_MESSAGE;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.NETFN_APP;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.NETFN_CHASSIS;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.SET_SESSION_PRIVILEGE_LEVEL;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.metricshub.ipmi.client.IpmiClient;
import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatus;
import org.metricshub.ipmi.core.coding.commands.chassis.ChassisIdentify;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatusResponseData;
import org.metricshub.ipmi.core.coding.commands.session.GetSessionInfo;
import org.metricshub.ipmi.core.coding.commands.session.SetSessionPrivilegeLevel;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.connection.Ipmi20UnsupportedException;
import org.metricshub.ipmi.core.connection.SessionException;
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
			try (
					IpmiClient client = IpmiClient
							.builder(bmc.getAddress().getHostAddress())
							.port(bmc.getPort())
							.credentials(USER, PASSWORD.toCharArray())
							.timeout(Duration.ofSeconds(20))
							.build()) {
				assertChassisStatus(client.chassisStatus().data());
				assertArrayEquals(algorithms, bmc.getAlgorithms());
			}
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
				assertEquals(1, bmc.getCount(NETFN_APP, GET_CHANNEL_AUTHENTICATION_CAPABILITIES), "one per discovery");
				assertEquals(Collections.emptyList(), bmc.getErrors());
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void openSessionRaisesOperatorAndAdministratorRightAfterTheHandshake() throws Exception {
		PrivilegeLevel[] levels = { PrivilegeLevel.User, PrivilegeLevel.Operator, PrivilegeLevel.Administrator };
		for (int i = 0; i < levels.length; i++) {
			try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(XCC_SUITES, USER, utf8(PASSWORD), null)) {
				IpmiConnector connector = new IpmiConnector(0, 0);
				try {
					ConnectionHandle handle = openSession(connector, bmc, 17, PASSWORD, null, levels[i]);
					int raises = levels[i] == PrivilegeLevel.User ? 0 : 1;
					assertEquals(raises, bmc.getSessionRequests(), levels[i] + ": the first request of the session");
					assertEquals(raises, bmc.getCount(NETFN_APP, SET_SESSION_PRIVILEGE_LEVEL), levels[i].toString());

					// The present level of the session, as the BMC reports it (the fake starts every session at User)
					int sessionId = connector
							.getExistingSessionForCriteria(bmc.getAddress(), bmc.getPort(), USER)
							.getManagedSystemSessionId();
					assertEquals(i + 2, connector.sendMessage(handle, new GetSessionInfo(sessionId)).getPrivilegeLevel());

					if (levels[i] == PrivilegeLevel.User) {
						IPMIException refused = assertThrows(
								IPMIException.class,
								() -> connector.sendMessage(handle, new ChassisIdentify(0, false)));
						assertEquals(CompletionCode.InsufficentPrivilege, refused.getCompletionCode());
					} else {
						connector.sendMessage(handle, new ChassisIdentify(0, false));
						assertArrayEquals(new byte[] { 0 }, bmc.getChassisIdentify());
					}
					assertEquals(Collections.emptyList(), bmc.getErrors());
				} finally {
					connector.tearDown();
				}
			}
		}
	}

	@Test
	@Timeout(30)
	void aRefusedPrivilegeFailsOpenSessionAndClosesTheSession() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(XCC_SUITES, USER, utf8(PASSWORD), null)) {
			// Refused once (80h: the level is not available for this user), then accepted
			AtomicInteger calls = new AtomicInteger();
			bmc
					.handle(
							NETFN_APP,
							SET_SESSION_PRIVILEGE_LEVEL,
							data -> calls.getAndIncrement() == 0 ? new byte[]
							{ (byte) 0x80 } : new byte[] { 0x00, 0x04 });
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				ConnectionHandle handle = connector
						.createConnection(bmc.getAddress(), bmc.getPort(), CipherSuite.forId(17), PrivilegeLevel.Administrator);
				connector.setTimeout(handle, TIMEOUT_MS);
				SessionException refused = assertThrows(
						SessionException.class,
						() -> connector.openSession(handle, USER, PASSWORD, null));
				assertTrue(refused.getMessage().contains("Administrator privilege"), refused.getMessage());
				assertTrue(refused.getMessage().contains("not available for this user"), refused.getMessage());
				assertTrue(refused.getMessage().contains("(completion code 80h)"), refused.getMessage());
				assertEquals(0x80, ((IPMIException) refused.getCause()).getRawCode());
				assertEquals(1, bmc.getCount(NETFN_APP, SET_SESSION_PRIVILEGE_LEVEL), "a refusal is not sent again");
				awaitClose(bmc);
				assertEquals(null, connector.getExistingSessionForCriteria(bmc.getAddress(), bmc.getPort(), USER));

				// The connection is ready for another login
				connector.openSession(handle, USER, PASSWORD, null);
				assertChassisStatus(getChassisStatus(connector, handle));
				assertEquals(Collections.emptyList(), bmc.getErrors());
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void openSessionSetsAMaximumAvailableSessionToUser() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(XCC_SUITES, USER, utf8(PASSWORD), null)) {
			// A BMC that starts the session at the requested maximum, and an account limited to Operator
			bmc.startsAtMaximum(true);
			bmc.privilegeLimit(FakeRmcpPlusBmc.OPERATOR);
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				ConnectionHandle handle = openSession(connector, bmc, 17, PASSWORD, null, PrivilegeLevel.MaximumAvailable);
				assertEquals(2, bmc.getCount(FakeRmcpPlusBmc.RAKP_1), "Administrator refused to the account, then Operator");
				assertEquals(1, bmc.getCount(NETFN_APP, SET_SESSION_PRIVILEGE_LEVEL), "set to User after the handshake");
				assertEquals(FakeRmcpPlusBmc.USER, bmc.getPrivilege());
				int sessionId = connector
						.getExistingSessionForCriteria(bmc.getAddress(), bmc.getPort(), USER)
						.getManagedSystemSessionId();
				assertEquals(2, connector.sendMessage(handle, new GetSessionInfo(sessionId)).getPrivilegeLevel());

				// The caller raises it when a command needs more, up to the limit of the account
				connector.sendMessage(handle, new SetSessionPrivilegeLevel(PrivilegeLevel.Operator));
				assertEquals(FakeRmcpPlusBmc.OPERATOR, bmc.getPrivilege());
				IPMIException refused = assertThrows(
						IPMIException.class,
						() -> connector.sendMessage(handle, new SetSessionPrivilegeLevel(PrivilegeLevel.Administrator)));
				assertEquals(SetSessionPrivilegeLevel.LEVEL_ABOVE_LIMIT, refused.getRawCode());
				assertEquals(Collections.emptyList(), bmc.getErrors());
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void aLostReplyToSetSessionPrivilegeLevelIsSentAgain() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(XCC_SUITES, USER, utf8(PASSWORD), null)) {
			bmc.inject(1, Fault.DROP);
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				ConnectionHandle handle = openSession(connector, bmc, 17, PASSWORD, null, PrivilegeLevel.Operator);
				assertEquals(2, bmc.getCount(NETFN_APP, SET_SESSION_PRIVILEGE_LEVEL));
				connector.sendMessage(handle, new ChassisIdentify(0, false));
				assertEquals(Collections.emptyList(), bmc.getErrors());
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void anIpmi15OnlyBmcIsRefusedWithoutAnyRmcpPlusPacket() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(XCC_SUITES, USER, utf8(PASSWORD), null)) {
			bmc.inject(Fault.IPMI_1_5_ONLY);
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				ConnectionHandle handle = connector.createConnection(bmc.getAddress(), bmc.getPort());
				connector.setTimeout(handle, TIMEOUT_MS);
				for (int call = 1; call <= 2; call++) {
					Ipmi20UnsupportedException refused = assertThrows(
							Ipmi20UnsupportedException.class,
							() -> connector.getAvailableCipherSuites(handle));
					assertTrue(refused.getMessage().contains("does not support IPMI 2.0"), refused.getMessage());
					// The answer is not sent again, and the state was rolled back for another try
					assertEquals(call, bmc.getCount(NETFN_APP, GET_CHANNEL_AUTHENTICATION_CAPABILITIES));
				}
				assertEquals(0, bmc.getCount(IPMI_MESSAGE), "no RMCP+ request");
				assertEquals(Collections.emptyList(), bmc.getErrors());
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void aRefusedCipherSuiteListFailsAtOnceAndLeavesTheStepRetriable() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(XCC_SUITES, USER, utf8(PASSWORD), null)) {
			bmc.handle(NETFN_APP, GET_CHANNEL_CIPHER_SUITES, data -> new byte[] { (byte) 0xc1 });
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				ConnectionHandle handle = connector.createConnection(bmc.getAddress(), bmc.getPort());
				connector.setTimeout(handle, TIMEOUT_MS);
				for (int call = 1; call <= 2; call++) {
					assertThrows(IPMIException.class, () -> connector.getAvailableCipherSuites(handle));
					assertEquals(call, bmc.getCount(NETFN_APP, GET_CHANNEL_CIPHER_SUITES), "C1h is not sent again");
					assertEquals(call, bmc.getCount(NETFN_APP, GET_CHANNEL_AUTHENTICATION_CAPABILITIES));
				}
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
		return openSession(connector, bmc, suiteId, password, kg, PrivilegeLevel.User);
	}

	/**
	 * The same handshake, at the given privilege.
	 */
	private static ConnectionHandle openSession(
			IpmiConnector connector,
			FakeRmcpPlusBmc bmc,
			int suiteId,
			String password,
			byte[] kg,
			PrivilegeLevel level)
			throws Exception {
		ConnectionHandle handle = connector.createConnection(bmc.getAddress(), bmc.getPort());
		connector.setTimeout(handle, TIMEOUT_MS);
		CipherSuite suite = connector
				.getAvailableCipherSuites(handle)
				.stream()
				.filter(cipherSuite -> cipherSuite.getId() == suiteId)
				.findFirst()
				.orElseThrow(() -> new AssertionError("no suite " + suiteId));
		connector.getChannelAuthenticationCapabilities(handle, suite, level);
		connector.openSession(handle, USER, password, kg);
		return handle;
	}

	private static GetChassisStatusResponseData getChassisStatus(IpmiConnector connector, ConnectionHandle handle)
			throws Exception {
		return (GetChassisStatusResponseData) connector
				.sendMessage(
						handle,
						new GetChassisStatus());
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
