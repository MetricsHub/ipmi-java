package org.metricshub.ipmi.core.api.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.api.async.IpmiAsyncConnector;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatus;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.PropertiesManager;
import org.metricshub.ipmi.core.connection.Connection;
import org.metricshub.ipmi.core.connection.ConnectionException;
import org.metricshub.ipmi.core.connection.ConnectionManager;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.states.SessionValid;
import org.metricshub.ipmi.core.transport.FakeBmc;

class IpmiConnectorTest {

	/**
	 * RMCP header, then an RMCP+ sessionless, unauthenticated IPMI message that announces an 80-byte payload the
	 * datagram does not carry: it passes the filters of the cipher-suites step and fails to decode.
	 */
	private static final byte[] TRUNCATED_REPLY = {
			0x06,
			0x00,
			(byte) 0xff,
			0x07,
			0x06,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x50,
			0x00 };

	private static final int TIMEOUT_MS = 500;

	/** The session ID of the console in the sessions the tests fake. */
	private static final int SESSION_ID = 1;

	/** Offset of the IPMI payload in an RMCP+ datagram of cipher suite 0. */
	private static final int PAYLOAD_OFFSET = 16;

	/** Get Chassis Status response data: power on, no last power event, no chassis state flag. */
	private static final byte[] CHASSIS_STATUS = { 0x01, 0x00, 0x00 };

	/**
	 * Long enough for any pause before a resend to show: a random pause of up to one hour either shows in the gap or
	 * trips the timeout of the test, where a resend without pause comes within the message timeout plus one tick of
	 * the queue timer.
	 */
	private static final String LONG_IDLE_TIME = "3600000";

	@Test
	void aBadReplyFailsTheStepAtOnceAndLeavesItRetriable() throws Exception {
		try (FakeBmc bmc = new FakeBmc(request -> TRUNCATED_REPLY)) {
			IpmiConnector connector = new IpmiConnector(0);
			try {
				ConnectionHandle handle = connector.createConnection(bmc.getAddress(), bmc.getPort());
				connector.setTimeout(handle, TIMEOUT_MS);

				Exception first = assertThrows(Exception.class, () -> connector.getAvailableCipherSuites(handle));
				assertFalse(first instanceof ConnectionException, "the decoding failure, not a timeout: " + first);
				assertEquals(1, bmc.getRequestCount(), "a reply that is not a timeout must not be sent again");

				// The state machine was rolled back: the same step can be tried again
				Exception second = assertThrows(Exception.class, () -> connector.getAvailableCipherSuites(handle));
				assertFalse(second instanceof ConnectionException, String.valueOf(second));
				assertEquals(2, bmc.getRequestCount());
			} finally {
				connector.tearDown();
			}
		}
	}

	@Test
	@Timeout(30)
	void aLostReplyIsSentAgainWithoutPause() throws Exception {
		// The BMC drops the first request and answers the second one
		long gapMs = gapBetweenTwoTries(tries -> tries == 1 ? null : 0x00);
		assertTrue(gapMs < TIMEOUT_MS + 1500, "the request was sent again after " + gapMs + " ms");
	}

	@Test
	@Timeout(30)
	void aTimeoutOnTheBmcSideIsSentAgainWithoutPause() throws Exception {
		// C3h, "Timeout while processing command": the BMC already waited for the device it could not reach
		long gapMs = gapBetweenTwoTries(tries -> tries == 1 ? 0xc3 : 0x00);
		assertTrue(gapMs < 1000, "the request was sent again after " + gapMs + " ms");
	}

	/**
	 * Sends a Get Chassis Status in a faked session to a BMC that answers each try with the given completion code (or
	 * not at all, for null), and returns the time between the first two tries.
	 */
	private static long gapBetweenTwoTries(Function<Integer, Integer> completionCodeOfTry) throws Exception {
		PropertiesManager properties = PropertiesManager.getInstance();
		String idleTime = properties.getProperty("idleTime");
		properties.setProperty("idleTime", LONG_IDLE_TIME);
		List<Long> arrivals = new CopyOnWriteArrayList<>();
		try (FakeBmc bmc = new FakeBmc(request -> {
			arrivals.add(System.nanoTime());
			Integer completionCode = completionCodeOfTry.apply(arrivals.size());
			return completionCode == null ? null : sessionReply(request, completionCode, CHASSIS_STATUS, arrivals.size());
		})) {
			// No keep-alive: the BMC sees the requests of the test only
			IpmiConnector connector = new IpmiConnector(0, 0);
			try {
				ConnectionHandle handle = connector
						.createConnection(bmc.getAddress(), bmc.getPort(), CipherSuite.getEmpty(), PrivilegeLevel.User);
				connector.setTimeout(handle, TIMEOUT_MS);
				openSession(connector, handle);

				assertNotNull(
						connector
								.sendMessage(
										handle,
										new GetChassisStatus(IpmiVersion.V20, CipherSuite.getEmpty(), AuthenticationType.RMCPPlus)));
				assertEquals(2, arrivals.size(), "the request must be sent twice");
				return TimeUnit.NANOSECONDS.toMillis(arrivals.get(1) - arrivals.get(0));
			} finally {
				connector.tearDown();
			}
		} finally {
			properties.setProperty("idleTime", idleTime);
		}
	}

	/** Puts the connection in a session of cipher suite 0, as if the handshake had succeeded. */
	private static void openSession(IpmiConnector connector, ConnectionHandle handle) throws Exception {
		IpmiAsyncConnector asyncConnector = (IpmiAsyncConnector) field(IpmiConnector.class, "asyncConnector")
				.get(connector);
		ConnectionManager manager = (ConnectionManager) field(IpmiAsyncConnector.class, "connectionManager")
				.get(asyncConnector);
		Connection connection = manager.getConnection(handle.getHandle());
		((StateMachine) field(Connection.class, "stateMachine").get(connection))
				.setCurrent(new SessionValid(CipherSuite.getEmpty(), SESSION_ID));
	}

	private static Field field(Class<?> type, String name) throws NoSuchFieldException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	/**
	 * The reply of a BMC to an IPMI request sent in a session of cipher suite 0 (neither authenticated nor
	 * encrypted): same tag and command, response network function, given completion code and data, and the given
	 * session sequence number (a BMC numbers its replies 1, 2, 3...: a number received twice is dropped).
	 */
	private static byte[] sessionReply(byte[] request, int completionCode, byte[] data, int sequenceNumber) {
		byte[] payload = new byte[8 + data.length];
		payload[0] = (byte) 0x81;
		payload[1] = (byte) ((((request[PAYLOAD_OFFSET + 1] & 0xff) >> 2) + 1) << 2);
		payload[2] = (byte) -(payload[0] + payload[1]);
		payload[3] = 0x20;
		payload[4] = request[PAYLOAD_OFFSET + 4];
		payload[5] = request[PAYLOAD_OFFSET + 5];
		payload[6] = (byte) completionCode;
		System.arraycopy(data, 0, payload, 7, data.length);
		byte checksum = 0;
		for (int i = 3; i < payload.length - 1; i++) {
			checksum += payload[i];
		}
		payload[payload.length - 1] = (byte) -checksum;

		byte[] reply = new byte[PAYLOAD_OFFSET + payload.length];
		byte[] header = {
				0x06,
				0x00,
				(byte) 0xff,
				0x07,
				0x06,
				0x00,
				SESSION_ID,
				0x00,
				0x00,
				0x00,
				(byte) sequenceNumber,
				0x00,
				0x00,
				0x00,
				(byte) payload.length,
				0x00 };
		System.arraycopy(header, 0, reply, 0, PAYLOAD_OFFSET);
		System.arraycopy(payload, 0, reply, PAYLOAD_OFFSET, payload.length);
		return reply;
	}
}
