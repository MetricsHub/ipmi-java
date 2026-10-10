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
			// The buffer of the BMC is full three times: 600 ms of pauses, longer than the message timeout, which each
			// retry restarts
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
	void operationsAreSentWithoutCharacters() throws Exception {
		try (FakeRmcpPlusBmc bmc = solBmc(null)) {
			// The server is off: the BMC NACKs the packet, but executes the operation
			bmc.replySol(0);
			try (
					SerialOverLan sol = new SerialOverLan(
							new IpmiConnector(0, 0),
							host(bmc),
							bmc.getPort(),
							USER,
							PASSWORD,
							SUITE_17)) {
				assertTrue(sol.invokeOperations(SolOperation.Break));
				assertEquals(1, dataPackets(bmc).size(), "a NACKed operation is not sent again");
				FakeRmcpPlusBmc.SolPacket packet = dataPackets(bmc).get(0);
				assertEquals(0, packet.data.length);
				assertEquals(0x10, packet.operation);
				assertEquals(1, packet.sequenceNumber, "the BMC acknowledges a packet with a sequence number");
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
				// 2048 characters fill the buffer
				for (int i = 1; i <= 8; i++) {
					bmc.sendSol(i, 0, text(256));
				}
				bmc.sendSol(9, 0, utf8("0123456789"));
				await(() -> acks(bmc) == 9, "the console answers each packet");
				assertEquals("9/0 NACK", ack(bmc.getSolPackets().get(8)), "a Suspend NACK");

				// The read does not wait for characters that cannot arrive, and resumes the BMC once
				long start = System.nanoTime();
				assertEquals(2048, sol.readBytes(4096, 10000).length);
				assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5));
				sol.readBytes();
				sol.readBytes();
				await(() -> acks(bmc) == 10, "the console sends a Resume ACK");
				assertEquals("9/0", ack(bmc.getSolPackets().get(9)));

				bmc.sendSol(9, 0, utf8("0123456789"));
				assertEquals("0123456789", sol.readString(StandardCharsets.US_ASCII, 10, 5000));
				await(() -> acks(bmc) == 11, "the console acknowledges the packet sent again");
				assertEquals("9/10", ack(bmc.getSolPackets().get(10)));
			}
			assertEquals(Collections.emptyList(), bmc.getErrors());
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
