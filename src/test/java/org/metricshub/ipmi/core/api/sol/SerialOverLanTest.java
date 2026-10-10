package org.metricshub.ipmi.core.api.sol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.ADMINISTRATOR;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.DEACTIVATE_PAYLOAD;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.LOSE_ACK;
import static org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.NETFN_APP;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatus;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatusResponseData;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.connection.Session;
import org.metricshub.ipmi.core.connection.SessionException;
import org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc;

/**
 * Serial over LAN consoles against {@link FakeRmcpPlusBmc}, a BMC written from the IPMI 2.0 specification, over
 * loopback UDP.
 */
class SerialOverLanTest {

	/** Cipher suite records of a Lenovo XCC (suites 1 to 19), after the channel byte. */
	private static final byte[] SUITES = bytes(
			"c0 01 01 40 80 c0 02 01 41 80 c0 03 01 41 81 c0 04 01 41 82 c0 05 01 41 83 c0 06 02 40 80 c0 07 02 42 80"
					+ " c0 08 02 42 81 c0 09 02 42 82 c0 0a 02 42 83 c0 0b 02 43 80 c0 0c 02 43 81 c0 0d 02 43 82 c0 0e 02 43 83"
					+ " c0 0f 03 40 80 c0 10 03 44 80 c0 11 03 44 81 c0 12 03 44 82 c0 13 03 44 83");

	private static final String USER = "admin";
	private static final String PASSWORD = "secret";

	/** A Kg whose bytes are not all valid in any charset. */
	private static final byte[] KG = bytes("80 ff 01 fe 7f 00 c3 28 a0 a1 e2 82 ff ff 00 01 02 03 04 05");

	/** The inbound payload size of most BMCs: 251 bytes of characters per packet. */
	private static final int PAYLOAD_SIZE = 255;

	private static final int TIMEOUT_MS = 500;

	private static final CipherSuiteSelectionHandler SUITE_17 = suites -> suites
			.stream()
			.filter(suite -> suite.getId() == 17)
			.findFirst()
			.orElse(null);

	@Test
	@Timeout(30)
	void aConsoleThatOpensItsSessionClosesItAndTearsDownTheConnector() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			IpmiConnector connector = new IpmiConnector(0, 0);
			SerialOverLan sol = new SerialOverLan(connector, host(bmc), bmc.getPort(), USER, PASSWORD, SUITE_17);
			assertTrue(bmc.isSolActive());
			assertTrue(sol.writeString("ls\r", StandardCharsets.US_ASCII));
			await(() -> bmc.getSolReceived().length == 3, "the BMC receives the characters");

			sol.close();
			assertFalse(bmc.isSolActive(), "the payload is deactivated");
			awaitClose(bmc);
			assertTornDown(connector);
			sol.close(); // twice is harmless
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aTwoKeyLoginConsole() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(KG)) {
			IpmiConnector connector = new IpmiConnector(0, 0);
			new SerialOverLan(connector, host(bmc), bmc.getPort(), USER, PASSWORD, KG, SUITE_17).close();
			awaitClose(bmc);
			assertTornDown(connector);
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aConsoleThatCannotOpenItsSessionTearsDownTheConnector() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(KG)) {
			IpmiConnector connector = new IpmiConnector(0, 0);
			// No BMC key: the integrity check of RAKP Message 4 fails
			assertThrows(
					SessionException.class,
					() -> new SerialOverLan(connector, host(bmc), bmc.getPort(), USER, PASSWORD, SUITE_17));
			assertTornDown(connector);
		}
	}

	@Test
	@Timeout(30)
	void aConsoleThatCannotActivateThePayloadClosesItsSession() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			bmc.handle(NETFN_APP, FakeRmcpPlusBmc.ACTIVATE_PAYLOAD, data -> new byte[] { (byte) 0x81 });
			IpmiConnector connector = new IpmiConnector(0, 0);
			assertThrows(
					SOLException.class,
					() -> new SerialOverLan(connector, host(bmc), bmc.getPort(), USER, PASSWORD, SUITE_17));
			awaitClose(bmc);
			assertTornDown(connector);
		}
	}

	@Test
	@Timeout(30)
	void aPayloadSizeWithoutRoomForCharactersFailsTheActivation() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			bmc.enableSol(bmc.getPort(), 4, ADMINISTRATOR);
			IpmiConnector connector = new IpmiConnector(0, 0);
			SOLException e = assertThrows(
					SOLException.class,
					() -> new SerialOverLan(connector, host(bmc), bmc.getPort(), USER, PASSWORD, SUITE_17));
			assertTrue(e.getMessage().contains("payload size of 4 bytes"), e.getMessage());
			awaitClose(bmc);
		}
	}

	@Test
	@Timeout(30)
	void aFailedActivationInTheSessionOfTheApplicationLeavesTheSessionOpen() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			bmc.enableSol(bmc.getPort(), 4, ADMINISTRATOR);
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				Session session = openSession(connector, bmc, null);
				assertThrows(SOLException.class, () -> new SerialOverLan(connector, session));
				assertFalse(bmc.isSolActive(), "the payload activated in the session of the application is deactivated");
				assertChassisStatus(connector, session.getConnectionHandle());
				assertFalse(bmc.isSessionClosed());
				connector.closeSession(session.getConnectionHandle());
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void closingReleasesTheSessionWhenTheDeactivationFails() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			IpmiConnector connector = new IpmiConnector(0, 0);
			SerialOverLan sol = new SerialOverLan(connector, host(bmc), bmc.getPort(), USER, PASSWORD, SUITE_17);
			bmc.handle(NETFN_APP, DEACTIVATE_PAYLOAD, data -> new byte[] { (byte) 0xcc });
			assertThrows(IOException.class, sol::close);
			awaitClose(bmc);
			assertTornDown(connector);
		}
	}

	@Test
	@Timeout(30)
	void aConsoleOnAnotherPortClosesBothItsSessions() throws Exception {
		try (
				FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), KG);
				FakeRmcpPlusBmc solPort = solBmc(KG)) {
			bmc.enableSol(solPort.getPort(), PAYLOAD_SIZE, ADMINISTRATOR);
			IpmiConnector connector = new IpmiConnector(0, 0);
			SerialOverLan sol = new SerialOverLan(connector, host(bmc), bmc.getPort(), USER, PASSWORD, KG, SUITE_17);
			assertFalse(bmc.isSolActive());
			assertTrue(solPort.isSolActive(), "the payload is activated in the session on its port");
			assertTrue(sol.writeString("ls\r", StandardCharsets.US_ASCII));

			sol.close();
			awaitClose(solPort);
			awaitClose(bmc);
			assertTornDown(connector);
			assertEquals(Collections.emptyList(), bmc.getErrors());
			assertEquals(Collections.emptyList(), solPort.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aConsoleOnAnotherPortLeavesTheSessionOfTheApplicationWorking() throws Exception {
		try (
				FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), KG);
				FakeRmcpPlusBmc solPort = solBmc(KG)) {
			bmc.enableSol(solPort.getPort(), PAYLOAD_SIZE, ADMINISTRATOR);
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				Session session = openSession(connector, bmc, KG);
				// The session on the payload port is opened with the BMC key of the session of the application
				SerialOverLan sol = new SerialOverLan(connector, session);
				assertTrue(solPort.isSolActive());
				// The session on the payload port has its own keys
				assertChassisStatus(connector, session.getConnectionHandle());

				sol.close();
				awaitClose(solPort);
				assertFalse(bmc.isSessionClosed(), "the session of the application stays open");
				assertChassisStatus(connector, session.getConnectionHandle());
				connector.closeSession(session.getConnectionHandle());
				awaitClose(bmc);
				assertEquals(Collections.emptyList(), bmc.getErrors());
				assertEquals(Collections.emptyList(), solPort.getErrors());
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void eachConsoleOfAConnectorReceivesTheDataOfItsOwnBmc() throws Exception {
		try (FakeRmcpPlusBmc first = solBmc(null); FakeRmcpPlusBmc second = solBmc(null)) {
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				Session firstSession = openSession(connector, first, null);
				SerialOverLan firstSol = new SerialOverLan(connector, firstSession);
				SerialOverLan secondSol = new SerialOverLan(connector, openSession(connector, second, null));

				first.sendSol(1, 0, utf8("login: "));
				assertEquals("login: ", firstSol.readString(StandardCharsets.US_ASCII, 7, 5000));
				assertEquals("", secondSol.readString(StandardCharsets.US_ASCII));
				await(() -> acks(first) == 1, "the first BMC gets its ACK");
				assertEquals(0, acks(second), "the second BMC gets no ACK of packets it did not send");

				// A console closed and opened again on the same session: only the new one acknowledges
				firstSol.close();
				firstSol = new SerialOverLan(connector, firstSession);
				first.sendSol(2, 0, utf8("again"));
				assertEquals("again", firstSol.readString(StandardCharsets.US_ASCII, 5, 5000));
				await(() -> acks(first) == 2, "the first BMC gets its ACK");
				Thread.sleep(200);
				assertEquals(2, acks(first), "the closed console no longer acknowledges");

				firstSol.close();
				secondSol.close();
				assertEquals(Collections.emptyList(), first.getErrors());
				assertEquals(Collections.emptyList(), second.getErrors());
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void aWriteWaitingForItsAckReturnsWhenTheConsoleIsClosed() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			bmc.replySol(LOSE_ACK);
			IpmiConnector connector = new IpmiConnector(0, 0);
			SerialOverLan sol = new SerialOverLan(connector, host(bmc), bmc.getPort(), USER, PASSWORD, SUITE_17);
			CompletableFuture<Boolean> write = CompletableFuture
					.supplyAsync(() -> sol.writeString("ls\r", StandardCharsets.US_ASCII));
			await(() -> bmc.getSolReceived().length == 3, "the BMC receives the characters");
			sol.close();
			// The message timeout is 5 s: the write must not wait for it
			assertFalse(write.get(2, TimeUnit.SECONDS));
		}
	}

	@Test
	@Timeout(30)
	void closingAConnectionForgetsItsSession() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				Session session = openSession(connector, bmc, null);
				ConnectionHandle handle = session.getConnectionHandle();
				assertEquals(session, connector.getExistingSessionForCriteria(bmc.getAddress(), bmc.getPort(), USER));
				connector.closeConnection(handle);
				assertNull(connector.getExistingSessionForCriteria(bmc.getAddress(), bmc.getPort(), USER));
			} finally {
				connector.tearDown();
			}
		}
	}

	/**
	 * A BMC that serves SOL on its own port.
	 */
	private static FakeRmcpPlusBmc solBmc(byte[] kg) throws IOException {
		FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), kg);
		bmc.enableSol(bmc.getPort(), PAYLOAD_SIZE, ADMINISTRATOR);
		return bmc;
	}

	/**
	 * Opens a session of suite 17 that may be raised to Administrator, as an application would.
	 */
	private static Session openSession(IpmiConnector connector, FakeRmcpPlusBmc bmc, byte[] kg) throws Exception {
		ConnectionHandle handle = connector.createConnection(bmc.getAddress(), bmc.getPort());
		connector.setTimeout(handle, TIMEOUT_MS);
		CipherSuite suite = SUITE_17.choose(connector.getAvailableCipherSuites(handle));
		connector.getChannelAuthenticationCapabilities(handle, suite, PrivilegeLevel.Administrator);
		return connector.openSession(handle, USER, PASSWORD, kg);
	}

	private static void assertChassisStatus(IpmiConnector connector, ConnectionHandle handle) throws Exception {
		GetChassisStatusResponseData status = (GetChassisStatusResponseData) connector
				.sendMessage(
						handle,
						new GetChassisStatus(IpmiVersion.V20, handle.getCipherSuite(), AuthenticationType.RMCPPlus));
		assertEquals(FakeRmcpPlusBmc.CHASSIS_STATUS[0], status.getCurrentPowerState());
	}

	private static void assertTornDown(IpmiConnector connector) {
		assertThrows(IllegalStateException.class, () -> connector.createConnection(InetAddress.getLoopbackAddress()));
	}

	/**
	 * @return how many ACK-only packets the BMC received
	 */
	private static long acks(FakeRmcpPlusBmc bmc) {
		return bmc.getSolPackets().stream().filter(packet -> packet.sequenceNumber == 0).count();
	}

	private static void awaitClose(FakeRmcpPlusBmc bmc) throws InterruptedException {
		await(bmc::isSessionClosed, "the BMC must receive a valid Close Session");
	}

	private static void await(BooleanSupplier condition, String message) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}
		assertTrue(condition.getAsBoolean(), message);
	}

	private static String host(FakeRmcpPlusBmc bmc) {
		return bmc.getAddress().getHostAddress();
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
