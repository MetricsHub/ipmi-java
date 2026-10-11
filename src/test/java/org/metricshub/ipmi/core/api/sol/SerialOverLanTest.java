package org.metricshub.ipmi.core.api.sol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatus;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatusResponseData;
import org.metricshub.ipmi.core.coding.payload.sol.SolOperation;
import org.metricshub.ipmi.core.coding.payload.sol.SolStatus;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.connection.Session;
import org.metricshub.ipmi.core.connection.SessionException;
import org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc;
import org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc.Fault;

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

	/** The capacity of the buffer of a console. */
	private static final int BUFFER = 64 * 1024;

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

	@Test
	@Timeout(30)
	void aLongWriteIsSentInPacketsOfTheSizeTheBmcAccepts() throws Exception {
		// 10 characters per packet: 3 full packets and a short one
		assertWrittenInPackets(14, 35, 10, 10, 10, 5);
		// At most 255 characters per packet, which a 1-byte count acknowledges
		assertWrittenInPackets(300, 600, 255, 255, 90);
	}

	private static void assertWrittenInPackets(int payloadSize, int length, int... packetSizes) throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			bmc.enableSol(bmc.getPort(), payloadSize, ADMINISTRATOR);
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				byte[] data = text(length);
				assertTrue(sol.writeBytes(data));
				assertArrayEquals(data, bmc.getSolReceived());
				assertArrayEquals(packetSizes, dataPackets(bmc).stream().mapToInt(packet -> packet.data.length).toArray());
				// One packet after the other: 1, 2, 3...
				assertArrayEquals(
						IntStream.rangeClosed(1, packetSizes.length).toArray(),
						dataPackets(bmc).stream().mapToInt(packet -> packet.sequenceNumber).toArray());
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void theAcknowledgementsOfTheConsoleUseNoSequenceNumber() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				assertTrue(sol.writeByte((byte) 'a'));
				// 14 packets of the BMC, which the console acknowledges, used to make the next packet reuse number 1
				for (int i = 1; i <= 14; i++) {
					bmc.sendSol(i, 0, utf8("x"));
					int acknowledged = i;
					await(() -> acks(bmc) == acknowledged, "the console acknowledges packet " + i);
				}
				assertTrue(sol.writeByte((byte) 'b'));
				assertArrayEquals(
						new int[]
						{ 1, 2 },
						dataPackets(bmc).stream().mapToInt(packet -> packet.sequenceNumber).toArray());
				assertEquals("ab", new String(bmc.getSolReceived(), StandardCharsets.US_ASCII));
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aPacketWhoseAckIsLostIsSentAgainWithItsSequenceNumber() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			bmc.replySol(LOSE_ACK);
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				SerialOverLan sol = new SerialOverLan(connector, openSession(connector, bmc, null));
				assertTrue(sol.writeString("reboot\r", StandardCharsets.US_ASCII));
				assertEquals("reboot\r", new String(bmc.getSolReceived(), StandardCharsets.US_ASCII), "received once");
				assertArrayEquals(
						new int[]
						{ 1, 1 },
						dataPackets(bmc).stream().mapToInt(packet -> packet.sequenceNumber).toArray());
				sol.close();
			} finally {
				connector.tearDown();
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aPacketTheBmcCannotTakeIsSentAgainAfterAPause() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			// The buffer of the BMC is full three times: 600 ms of pauses, longer than the message timeout (see
			// MessageQueueTest for the restart of the timeout)
			bmc.replySol(0, 0, 0);
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				SerialOverLan sol = new SerialOverLan(connector, openSession(connector, bmc, null));
				long start = System.nanoTime();
				assertTrue(sol.writeString("ls\r", StandardCharsets.US_ASCII));
				assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(600), "100, 200, then 300 ms");
				assertEquals("ls\r", new String(bmc.getSolReceived(), StandardCharsets.US_ASCII));
				assertArrayEquals(
						new int[]
						{ 1, 1, 1, 1 },
						dataPackets(bmc).stream().mapToInt(packet -> packet.sequenceNumber).toArray());
				sol.close();
			} finally {
				connector.tearDown();
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aNackedPacketWhoseResendLosesItsAckIsSentAgainWithItsNumber() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			// The buffer of the BMC is full, then the BMC takes the packet but its ACK is lost
			bmc.replySol(0, LOSE_ACK);
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				SerialOverLan sol = new SerialOverLan(connector, openSession(connector, bmc, null));
				assertTrue(sol.writeString("ls\r", StandardCharsets.US_ASCII));
				assertEquals("ls\r", new String(bmc.getSolReceived(), StandardCharsets.US_ASCII), "received once");
				assertArrayEquals(
						new int[]
						{ 1, 1, 1 },
						dataPackets(bmc).stream().mapToInt(packet -> packet.sequenceNumber).toArray());
				sol.close();
			} finally {
				connector.tearDown();
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aNewActivationRetiresTheOperationsLeftWithoutAcknowledgement() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			bmc.replySol(LOSE_ACK);
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				Session session = openSession(connector, bmc, null);
				// The operations would hold their number until long after the console is opened again
				connector.setTimeout(session.getConnectionHandle(), 10000);
				SerialOverLan sol = new SerialOverLan(connector, session);
				assertTrue(sol.invokeOperations(SolOperation.FlushOutbound));
				await(() -> dataPackets(bmc).size() == 1, "the BMC receives the operations");
				sol.close();
				sol = new SerialOverLan(connector, session);
				assertTrue(sol.writeByte((byte) 'c'));
				sol.close();
				assertArrayEquals(
						new int[]
						{ 1, 1 },
						dataPackets(bmc).stream().mapToInt(packet -> packet.sequenceNumber).toArray());
			} finally {
				connector.tearDown();
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aPartialNackOfMoreThan127Characters() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			bmc.replySol(200);
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				byte[] data = text(251);
				assertTrue(sol.writeBytes(data));
				assertArrayEquals(data, bmc.getSolReceived());
				// The rest goes in the next packet
				assertArrayEquals(
						new int[]
						{ 251, 51 },
						dataPackets(bmc).stream().mapToInt(packet -> packet.data.length).toArray());
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void operationsAreSentWithoutCharactersNorWaitingForTheirAck() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			// Like many BMCs, this one does not acknowledge a packet without characters
			bmc.replySol(LOSE_ACK);
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				long start = System.nanoTime();
				assertTrue(sol.invokeOperations(SolOperation.Break));
				assertTrue(sol.writeByte((byte) 'a'));
				assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2), "the ACK of the operations is not awaited");
				await(() -> dataPackets(bmc).size() == 2, "the BMC receives both packets");
				FakeRmcpPlusBmc.SolPacket packet = dataPackets(bmc).get(0);
				assertEquals(0, packet.data.length);
				assertEquals(0x10, packet.operation);
				assertEquals(1, packet.sequenceNumber, "a sequence number, which the BMC is to acknowledge");
				assertEquals(2, dataPackets(bmc).get(1).sequenceNumber);
				assertEquals("a", new String(bmc.getSolReceived(), StandardCharsets.US_ASCII));
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void theStatusesInTheAcknowledgementOfOperationsReachTheListeners() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			bmc.setSolStatus(FakeRmcpPlusBmc.SOL_CHARACTER_TRANSFER_UNAVAILABLE);
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				List<Set<SolStatus>> events = new CopyOnWriteArrayList<>();
				sol.registerEventListener(new RecordingListener(events));
				assertTrue(sol.invokeOperations(SolOperation.FlushOutbound));
				await(() -> !events.isEmpty(), "the listener is told");
				assertEquals(EnumSet.of(SolStatus.CharacterTransferUnavailable), events.get(0));
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void lineStatesAreHeldInEveryPacketUntilReleased() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				assertTrue(sol.invokeOperations(SolOperation.CTS, SolOperation.Break));
				assertTrue(sol.writeByte((byte) 'a'));
				bmc.sendSol(1, 0, utf8("b"));
				await(() -> acks(bmc) == 1, "the console acknowledges the packet");
				assertTrue(sol.invokeOperations());
				assertTrue(sol.writeByte((byte) 'c'));
				// CTS (bit 3) and Break (bit 4), then CTS in the data and in the ACK, then nothing
				assertArrayEquals(
						new int[]
						{ 0x18, 0x08, 0x08, 0x00, 0x00 },
						bmc.getSolPackets().stream().mapToInt(packet -> packet.operation).toArray());
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void concurrentWritesDoNotMix() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			bmc.enableSol(bmc.getPort(), 14, ADMINISTRATOR);
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				String first = String.join("", Collections.nCopies(100, "a"));
				String second = String.join("", Collections.nCopies(100, "b"));
				CompletableFuture<Boolean> other = CompletableFuture
						.supplyAsync(() -> sol.writeString(second, StandardCharsets.US_ASCII));
				assertTrue(sol.writeString(first, StandardCharsets.US_ASCII));
				assertTrue(other.get(10, TimeUnit.SECONDS));
				String received = new String(bmc.getSolReceived(), StandardCharsets.US_ASCII);
				assertTrue(received.equals(first + second) || received.equals(second + first), received);
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void anInterruptedWriteKeepsTheInterruptStatus() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			bmc.replySol(LOSE_ACK);
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
				Thread writer = new Thread(() -> {
					sol.writeByte((byte) 'a');
					interrupted.complete(Thread.currentThread().isInterrupted());
				});
				writer.start();
				await(() -> bmc.getSolReceived().length == 1, "the BMC receives the character");
				writer.interrupt();
				assertTrue(interrupted.get(2, TimeUnit.SECONDS));
			}
		}
	}

	@Test
	@Timeout(30)
	void theSessionIsRaisedToThePrivilegeItWasOpenedFor() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, USER, utf8(PASSWORD), null)) {
			bmc.enableSol(bmc.getPort(), PAYLOAD_SIZE, FakeRmcpPlusBmc.OPERATOR);
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				// A session that cannot be raised above Operator: asking for Administrator fails
				Session session = openSession(connector, bmc, null, PrivilegeLevel.Operator);
				new SerialOverLan(connector, session).close();
				assertEquals(Collections.emptyList(), bmc.getErrors());
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void aLostReplyToActivatePayloadIsRecovered() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			// Get Payload Activation Status, Activate Payload (refused: the session starts at User), Set Session
			// Privilege Level, Activate Payload: its reply is lost, and the request sent again finds the payload active
			bmc.inject(4, Fault.DROP);
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				SerialOverLan sol = new SerialOverLan(connector, openSession(connector, bmc, null));
				assertTrue(bmc.isSolActive());
				assertEquals(1, bmc.getCount(NETFN_APP, FakeRmcpPlusBmc.GET_PAYLOAD_INSTANCE_INFO));
				assertEquals(1, bmc.getCount(NETFN_APP, DEACTIVATE_PAYLOAD));
				assertEquals(4, bmc.getCount(NETFN_APP, FakeRmcpPlusBmc.ACTIVATE_PAYLOAD));
				assertTrue(sol.writeString("ls\r", StandardCharsets.US_ASCII));
				sol.close();
				assertEquals(Collections.emptyList(), bmc.getErrors());
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void aPayloadActiveInAnotherSessionIsLeftToIt() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			bmc.handle(NETFN_APP, FakeRmcpPlusBmc.ACTIVATE_PAYLOAD, data -> new byte[] { (byte) 0x80 });
			bmc
					.handle(
							NETFN_APP,
							FakeRmcpPlusBmc.GET_PAYLOAD_INSTANCE_INFO,
							data -> new byte[]
							{ 0x00, 0x12, 0x34, 0x56, 0x78, 0, 0, 0, 0, 0, 0, 0, 0 });
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				Session session = openSession(connector, bmc, null);
				assertThrows(SOLException.class, () -> new SerialOverLan(connector, session));
				assertEquals(0, bmc.getCount(NETFN_APP, DEACTIVATE_PAYLOAD), "the console of the other session stays");
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void aPacketTheBmcSendsAgainIsReadOnce() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				// The ACK of the console is lost twice, and the BMC appends characters the second time
				bmc.sendSol(3, 0, utf8("abc"));
				bmc.sendSol(3, 0, utf8("abc"));
				bmc.sendSol(3, 0, utf8("abcde"));
				await(() -> acks(bmc) == 3, "the console acknowledges each copy");
				assertEquals("abcde", sol.readString(StandardCharsets.US_ASCII));
				assertEquals(
						Arrays.asList("3/3", "3/3", "3/5"),
						bmc.getSolPackets().stream().map(SerialOverLanTest::ack).collect(Collectors.toList()));
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aFullBufferSuspendsTheBmcUntilItIsRead() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				int packets = fillBuffer(bmc);
				int suspended = packets % 15 + 1;
				bmc.sendSol(suspended, 0, utf8("0123456789"));
				await(() -> acks(bmc) == packets + 1, "the console answers each packet");
				assertEquals(suspended + "/0 NACK", ack(bmc.getSolPackets().get(packets)), "a Suspend NACK");

				// The read does not wait for characters that cannot arrive, and resumes the BMC
				long start = System.nanoTime();
				assertEquals(BUFFER, sol.readBytes(2 * BUFFER, 10000).length);
				assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5));
				await(() -> resumes(bmc) == 1, "the console sends a Resume ACK");

				// The BMC does not send the packet again (the Resume ACK was lost): a later read resumes it again
				Thread.sleep(150);
				sol.readBytes();
				await(() -> resumes(bmc) == 2, "the console sends the Resume ACK again");

				bmc.sendSol(suspended, 0, utf8("0123456789"));
				assertEquals("0123456789", sol.readString(StandardCharsets.US_ASCII, 10, 5000));
				await(
						() -> bmc.getSolPackets().stream().anyMatch(packet -> (suspended + "/10").equals(ack(packet))),
						"the console acknowledges the packet sent again");
				Thread.sleep(150);
				sol.readBytes();
				Thread.sleep(50);
				assertEquals(2, resumes(bmc), "no Resume ACK once the packet is accepted");
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aRetryWithAppendedCharactersIsResumedOnceTheyFit() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				int packets = fillBuffer(bmc);
				int last = (packets - 1) % 15 + 1;
				int lastLength = BUFFER - (packets - 1) * 248;
				// The BMC sends its last packet again with 10 characters more, which do not fit
				bmc.sendSol(last, 0, text(lastLength + 10));
				await(() -> acks(bmc) == packets + 1, "the console answers the packet");
				assertEquals(last + "/0 NACK", ack(bmc.getSolPackets().get(packets)), "a Suspend NACK");
				// A late copy of the packet, without the new characters, does not end the suspension
				bmc.sendSol(last, 0, text(lastLength));
				await(() -> acks(bmc) == packets + 2, "the console acknowledges the late copy");
				// Room for the 10 new characters: the BMC is resumed
				assertEquals(10, sol.readBytes(10).length);
				await(() -> resumes(bmc) == 1, "the console sends a Resume ACK");
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aStatusIsReportedAtOnceWhenTheCharactersOfItsPacketMustWait() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				List<Set<SolStatus>> events = new CopyOnWriteArrayList<>();
				sol.registerEventListener(new RecordingListener(events));
				int packets = fillBuffer(bmc);
				int next = packets % 15 + 1;
				bmc.sendSol(next, FakeRmcpPlusBmc.SOL_BREAK, text(10));
				await(() -> !events.isEmpty(), "the listener is told before the characters fit");
				assertEquals(next + "/0 NACK", ack(bmc.getSolPackets().get(packets)), "a Suspend NACK");

				// The packet sent again once resumed: its status is not reported again
				sol.readBytes(10);
				bmc.sendSol(next, FakeRmcpPlusBmc.SOL_BREAK, text(10));
				await(
						() -> bmc.getSolPackets().stream().anyMatch(packet -> (next + "/10").equals(ack(packet))),
						"the console accepts the packet sent again");
				assertEquals(Collections.singletonList(EnumSet.of(SolStatus.Break)), events);
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aStatusNewInACopyOfAPacketIsReported() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				List<Set<SolStatus>> events = new CopyOnWriteArrayList<>();
				sol.registerEventListener(new RecordingListener(events));
				// The ACK of the console is lost, and the BMC appends characters, and a break, to its packet
				bmc.sendSol(3, 0, utf8("abc"));
				bmc.sendSol(3, FakeRmcpPlusBmc.SOL_BREAK, utf8("abcde"));
				bmc.sendSol(3, FakeRmcpPlusBmc.SOL_BREAK, utf8("abcde"));
				await(() -> acks(bmc) == 3, "the console acknowledges each copy");
				assertEquals(Collections.singletonList(EnumSet.of(SolStatus.Break)), events, "reported once");
				assertEquals("abcde", sol.readString(StandardCharsets.US_ASCII));
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void theAcknowledgementOfAWriteReportsTheLineStatesOfItsPacket() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				List<Set<SolOperation>> operations = new CopyOnWriteArrayList<>();
				sol.registerEventListener(new RecordingListener(new CopyOnWriteArrayList<>()) {
					@Override
					public void processResponseEvent(
							Set<SolStatus> statuses,
							byte[] correspondingRequestData,
							Set<SolOperation> correspondingRequestOperations) {
						operations.add(correspondingRequestOperations);
					}
				});
				assertTrue(sol.invokeOperations(SolOperation.CTS));
				bmc.setSolStatus(FakeRmcpPlusBmc.SOL_CHARACTER_TRANSFER_UNAVAILABLE);
				assertTrue(sol.writeByte((byte) 'a'));
				assertEquals(Collections.singletonList(EnumSet.of(SolOperation.CTS)), operations);
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aSecondConsoleThatCannotActivateLeavesTheFirstOneWorking() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				Session session = openSession(connector, bmc, null);
				SerialOverLan sol = new SerialOverLan(connector, session);
				assertTrue(sol.writeByte((byte) 'a'));
				// A BMC with an instance free, which refuses it: the payload is active in another session
				bmc
						.handle(
								NETFN_APP,
								FakeRmcpPlusBmc.GET_PAYLOAD_ACTIVATION_STATUS,
								data -> new byte[]
								{ 0x00, 0x02, 0x01, 0x00 });
				bmc.handle(NETFN_APP, FakeRmcpPlusBmc.ACTIVATE_PAYLOAD, data -> new byte[] { (byte) 0x80 });
				bmc
						.handle(
								NETFN_APP,
								FakeRmcpPlusBmc.GET_PAYLOAD_INSTANCE_INFO,
								data -> new byte[]
								{ 0x00, 0x12, 0x34, 0x56, 0x78, 0, 0, 0, 0, 0, 0, 0, 0 });
				assertThrows(SOLException.class, () -> new SerialOverLan(connector, session));
				// The first console goes on numbering its packets
				assertTrue(sol.writeByte((byte) 'b'));
				assertEquals("ab", new String(bmc.getSolReceived(), StandardCharsets.US_ASCII));
				assertArrayEquals(
						new int[]
						{ 1, 2 },
						dataPackets(bmc).stream().mapToInt(packet -> packet.sequenceNumber).toArray());
				sol.close();
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void aLateShorterCopyOfAPacketDoesNotDuplicateItsCharacters() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				// A retry with 2 more characters overtakes the first copy, and the BMC sends it again
				bmc.sendSol(3, 0, utf8("abcde"));
				bmc.sendSol(3, 0, utf8("abc"));
				bmc.sendSol(3, 0, utf8("abcde"));
				await(() -> acks(bmc) == 3, "the console acknowledges each copy");
				assertEquals("abcde", sol.readString(StandardCharsets.US_ASCII));
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aReadReturnsOnceTheBufferIsFull() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				// The capacity of the buffer exactly, and nothing more
				fillBuffer(bmc);
				long start = System.nanoTime();
				assertEquals(BUFFER, sol.readBytes(2 * BUFFER, 10000).length);
				assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5));
			}
		}
	}

	@Test
	@Timeout(30)
	void aConsoleOpenedAgainOnTheSameSessionNumbersItsPacketsFrom1() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				Session session = openSession(connector, bmc, null);
				SerialOverLan sol = new SerialOverLan(connector, session);
				assertTrue(sol.writeByte((byte) 'a'));
				assertTrue(sol.writeByte((byte) 'b'));
				sol.close();
				sol = new SerialOverLan(connector, session);
				assertTrue(sol.writeByte((byte) 'c'));
				sol.close();
				assertArrayEquals(
						new int[]
						{ 1, 2, 1 },
						dataPackets(bmc).stream().mapToInt(packet -> packet.sequenceNumber).toArray());
				assertEquals(Collections.emptyList(), bmc.getErrors());
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void anActivationWithoutReplyIsUndoneInTheSessionOfTheApplication() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			// Get Payload Activation Status, Activate Payload (refused at User), Set Session Privilege Level, then the 4
			// tries of Activate Payload: the first one activates the payload, and no reply comes back
			for (int request = 4; request <= 7; request++) {
				bmc.inject(request, Fault.DROP);
			}
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				Session session = openSession(connector, bmc, null);
				assertThrows(SOLException.class, () -> new SerialOverLan(connector, session));
				assertFalse(bmc.isSolActive(), "the payload active in the session of the application is deactivated");
				// The next console can open
				new SerialOverLan(connector, session).close();
				assertEquals(Collections.emptyList(), bmc.getErrors());
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void aStatusWithoutCharactersIsAcknowledgedAndReportedOnce() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				List<Set<SolStatus>> events = new CopyOnWriteArrayList<>();
				// A listener that unregisters itself must not keep the next one from being notified
				sol.registerEventListener(new RecordingListener(events) {
					@Override
					public void processRequestEvent(Set<SolStatus> statuses) {
						super.processRequestEvent(statuses);
						sol.unregisterEventListener(this);
					}
				});
				sol.registerEventListener(new RecordingListener(events));
				bmc.sendSol(5, FakeRmcpPlusBmc.SOL_BREAK, new byte[0]);
				bmc.sendSol(5, FakeRmcpPlusBmc.SOL_BREAK, new byte[0]);
				await(() -> acks(bmc) == 2, "the console acknowledges each copy");
				assertEquals(
						Arrays.asList(EnumSet.of(SolStatus.Break), EnumSet.of(SolStatus.Break)),
						events,
						"each listener is told once");
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
		}
	}

	@Test
	@Timeout(30)
	void aStatusInAnInformationalPacketReachesTheListeners() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				List<Set<SolStatus>> events = new CopyOnWriteArrayList<>();
				sol.registerEventListener(new RecordingListener(events));
				// Another session deactivated SOL: the BMC says so in a packet without sequence number
				bmc.sendSol(0, FakeRmcpPlusBmc.SOL_NACK | FakeRmcpPlusBmc.SOL_DEACTIVATED, new byte[0]);
				await(() -> !events.isEmpty(), "the listener is told");
				assertEquals(EnumSet.of(SolStatus.SolDeactivated), events.get(0));
				assertEquals(0, acks(bmc), "a packet without sequence number is not acknowledged");
			}
		}
	}

	/**
	 * Records the statuses of {@link #processRequestEvent(Set)}.
	 */
	private static class RecordingListener implements SolEventListener {
		private final List<Set<SolStatus>> events;

		RecordingListener(List<Set<SolStatus>> events) {
			this.events = events;
		}

		@Override
		public void processRequestEvent(Set<SolStatus> statuses) {
			events.add(statuses);
		}

		@Override
		public void processResponseEvent(
				Set<SolStatus> statuses,
				byte[] correspondingRequestData,
				Set<SolOperation> correspondingRequestOperations) {
			// not expected
		}
	}

	/**
	 * @return the acknowledgement of an ACK-only packet, as "sequence number/accepted characters", with " NACK"
	 */
	private static String ack(FakeRmcpPlusBmc.SolPacket packet) {
		return packet.ackSequenceNumber + "/" + packet.acceptedCharacters + (packet.isNack() ? " NACK" : "");
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
		return openSession(connector, bmc, kg, PrivilegeLevel.Administrator);
	}

	/**
	 * Opens a session of suite 17 that may be raised to the given privilege.
	 */
	private static Session openSession(
			IpmiConnector connector,
			FakeRmcpPlusBmc bmc,
			byte[] kg,
			PrivilegeLevel privilegeLevel)
			throws Exception {
		ConnectionHandle handle = connector.createConnection(bmc.getAddress(), bmc.getPort());
		connector.setTimeout(handle, TIMEOUT_MS);
		CipherSuite suite = SUITE_17.choose(connector.getAvailableCipherSuites(handle));
		connector.getChannelAuthenticationCapabilities(handle, suite, privilegeLevel);
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
	 * @return the packets of the console that the BMC acknowledges (those with a sequence number)
	 */
	private static List<FakeRmcpPlusBmc.SolPacket> dataPackets(FakeRmcpPlusBmc bmc) {
		return bmc.getSolPackets().stream().filter(packet -> packet.sequenceNumber != 0).collect(Collectors.toList());
	}

	/**
	 * @return printable characters, all different within 26
	 */
	private static byte[] text(int length) {
		byte[] text = new byte[length];
		for (int i = 0; i < length; i++) {
			text[i] = (byte) ('a' + i % 26);
		}
		return text;
	}

	/**
	 * Fills the buffer of the console with packets of the BMC of at most 248 characters, like those of real BMCs, 16 at
	 * a time: a longer burst of datagrams could overflow the socket.
	 *
	 * @return the number of packets sent, numbered from 1 to 15 in a loop
	 */
	private static int fillBuffer(FakeRmcpPlusBmc bmc) throws Exception {
		int packets = 0;
		for (int sent = 0; sent < BUFFER; sent += 248) {
			bmc.sendSol(packets % 15 + 1, 0, text(Math.min(248, BUFFER - sent)));
			packets++;
			if (packets % 16 == 0 || sent + 248 >= BUFFER) {
				int expected = packets;
				await(() -> acks(bmc) == expected, "the console acknowledges packet " + expected);
			}
		}
		return packets;
	}

	/**
	 * @return how many Resume ACKs (ACK of 0 characters of a packet) the BMC received
	 */
	private static long resumes(FakeRmcpPlusBmc bmc) {
		return bmc
				.getSolPackets()
				.stream()
				.filter(packet -> packet.sequenceNumber == 0 && packet.ackSequenceNumber != 0)
				.filter(packet -> !packet.isNack() && packet.acceptedCharacters == 0)
				.count();
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
