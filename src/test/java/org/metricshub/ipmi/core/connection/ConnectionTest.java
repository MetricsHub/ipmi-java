package org.metricshub.ipmi.core.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;
import org.metricshub.ipmi.core.transport.SilentMessenger;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelAuthenticationCapabilities;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv20Message;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.sm.StateMachine;
import org.metricshub.ipmi.core.sm.actions.MessageAction;
import org.metricshub.ipmi.core.sm.states.SessionValid;
import org.metricshub.ipmi.core.transport.UdpMessage;

class ConnectionTest {

	private static final int TIMEOUT_MS = 200;

	private static final String COMMAND_TIMED_OUT = "Command timed out";

	private static Connection connect(int timeout) throws IOException {
		Connection connection = new Connection(new SilentMessenger(), 0);
		connection.setTimeout(timeout);
		connection.connect(InetAddress.getLoopbackAddress(), 623, 0);
		return connection;
	}

	@Test
	void handshakeStepTimesOutOnWallClock() throws Exception {
		Connection connection = connect(TIMEOUT_MS);
		try {
			long start = System.nanoTime();
			ConnectionException e = assertThrows(
					ConnectionException.class,
					() -> connection.getAvailableCipherSuites(1));
			long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

			assertEquals(COMMAND_TIMED_OUT, e.getMessage());
			assertTrue(elapsed >= TIMEOUT_MS && elapsed < 10 * TIMEOUT_MS, "elapsed " + elapsed + " ms");

			// The state machine is back to Uninitialized: the step can be tried again
			e = assertThrows(ConnectionException.class, () -> connection.getAvailableCipherSuites(2));
			assertEquals(COMMAND_TIMED_OUT, e.getMessage());
		} finally {
			connection.disconnect();
		}
	}

	@Test
	void handshakeStepReturnsWhenInterrupted() throws Exception {
		Connection connection = connect(60000);
		try {
			AtomicReference<Throwable> thrown = new AtomicReference<>();
			AtomicBoolean interruptedOnReturn = new AtomicBoolean();
			Thread worker = new Thread(() -> {
				try {
					connection.getAvailableCipherSuites(1);
				} catch (Throwable t) {
					thrown.set(t);
				}
				interruptedOnReturn.set(Thread.currentThread().isInterrupted());
			});
			worker.start();
			Thread.sleep(100);
			worker.interrupt();
			worker.join(5000);

			assertFalse(worker.isAlive(), "the interrupted step must return");
			assertTrue(thrown.get() instanceof InterruptedException, String.valueOf(thrown.get()));
			assertTrue(interruptedOnReturn.get(), "the interrupt flag must be restored");

			// The state machine was rolled back: the step can be tried again
			connection.setTimeout(TIMEOUT_MS);
			ConnectionException e = assertThrows(
					ConnectionException.class,
					() -> connection.getAvailableCipherSuites(2));
			assertEquals(COMMAND_TIMED_OUT, e.getMessage());
		} finally {
			connection.disconnect();
		}
	}

	@Test
	void libraryThreadsAreDaemonThreads() throws Exception {
		Set<Thread> before = Thread.getAllStackTraces().keySet();
		IpmiConnector connector = new IpmiConnector(0);
		try {
			// UDP receiver, keep-alive timer and the message queue timers
			connector.createConnection(InetAddress.getLoopbackAddress(), 623);

			Set<Thread> created = new HashSet<>(Thread.getAllStackTraces().keySet());
			created.removeAll(before);
			assertFalse(created.isEmpty(), "the connector must have started its threads");
			for (Thread thread : created) {
				assertTrue(thread.isDaemon(), thread.getName() + " must be a daemon thread");
			}
		} finally {
			connector.tearDown();
		}
	}

	/**
	 * Puts the connection in the session-open state without a handshake (there is no BMC behind the messenger).
	 */
	private static void openSession(Connection connection) throws Exception {
		Field field = Connection.class.getDeclaredField("stateMachine");
		field.setAccessible(true);
		((StateMachine) field.get(connection)).setCurrent(new SessionValid(CipherSuite.getEmpty(), 1));
	}

	@Test
	void keepAliveSendsOneMessageAndReturnsAtOnce() throws Exception {
		AtomicInteger sent = new AtomicInteger();
		Connection connection = new Connection(new SilentMessenger() {
			@Override
			public void send(UdpMessage message) {
				sent.incrementAndGet();
			}
		}, 0);
		connection.connect(InetAddress.getLoopbackAddress(), 623, 0);
		try {
			openSession(connection);
			long start = System.nanoTime();
			connection.run();
			long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			assertEquals(1, sent.get());
			assertTrue(elapsed < 500, "the keep-alive must not sleep, took " + elapsed + " ms");

			connection.disconnect();
			assertFalse(connection.isSessionValid(), "a disconnected connection has no session");
			connection.run();
			assertEquals(1, sent.get(), "a disconnected connection sends no keep-alive");
		} finally {
			connection.disconnect();
		}
	}

	@Test
	void aGetChannelAuthenticationCapabilitiesReplyReachesTheListeners() throws Exception {
		Connection connection = connect(TIMEOUT_MS);
		try {
			openSession(connection);
			AtomicInteger notifiedTag = new AtomicInteger(-1);
			AtomicReference<Object> outcome = new AtomicReference<>();
			connection.registerListener(new ConnectionListener() {
				@Override
				public void processResponse(ResponseData responseData, int handle, int tag, Exception exception) {
					notifiedTag.set(tag);
					outcome.set(responseData != null ? responseData : exception);
				}

				@Override
				public void processRequest(IpmiPayload payload) {
					// not expected
				}
			});
			GetChannelAuthenticationCapabilities request = new GetChannelAuthenticationCapabilities(
					IpmiVersion.V20,
					IpmiVersion.V20,
					CipherSuite.getEmpty(),
					PrivilegeLevel.Callback,
					(byte) 0xe);
			int tag = connection.sendMessage(request, false);
			assertTrue(tag > 0, "the request must be queued");

			// A minimal response carrying the tag of the request (rqSeq, bits 7:2 of byte 4)
			byte[] raw = { 0x20, 0x18, 0, (byte) 0x81, (byte) (tag << 2), 0x38, 0, 0 };
			raw[2] = (byte) -(raw[0] + raw[1]);
			raw[7] = (byte) -(raw[3] + raw[4] + raw[5] + raw[6]);
			Ipmiv20Message reply = new Ipmiv20Message(null);
			reply.setPayloadType(PayloadType.Ipmi);
			reply.setPayload(new IpmiLanResponse(raw));
			connection.notify(new MessageAction(reply));

			assertEquals(tag, notifiedTag.get(), "the reply must be delivered to the listeners");
			assertNotNull(outcome.get());
		} finally {
			connection.disconnect();
		}
	}

	@Test
	void theKeepAliveReplyIsDiscardedAndFreesItsTag() throws Exception {
		Connection connection = connect(TIMEOUT_MS);
		try {
			openSession(connection);
			AtomicInteger notifiedTag = new AtomicInteger(-1);
			connection.registerListener(new ConnectionListener() {
				@Override
				public void processResponse(ResponseData responseData, int handle, int tag, Exception exception) {
					notifiedTag.set(tag);
				}

				@Override
				public void processRequest(IpmiPayload payload) {
					// not expected
				}
			});
			connection.run();
			int keepAliveTag = 1; // the first tag of a new connection
			// The keep-alive reply (command 38h) is not delivered, and its tag is free again
			connection.notify(new MessageAction(reply(keepAliveTag, (byte) 0x38)));
			assertEquals(-1, notifiedTag.get(), "the keep-alive reply must not reach the listeners");

			// The same command sent by the application gets its reply
			GetChannelAuthenticationCapabilities request = new GetChannelAuthenticationCapabilities(
					IpmiVersion.V20,
					IpmiVersion.V20,
					CipherSuite.getEmpty(),
					PrivilegeLevel.Callback,
					(byte) 0xe);
			int tag = connection.sendMessage(request, false);
			assertEquals(keepAliveTag + 1, tag);
			connection.notify(new MessageAction(reply(tag, (byte) 0x38)));
			assertEquals(tag, notifiedTag.get());
		} finally {
			connection.disconnect();
		}
	}

	/** A minimal IPMI LAN response with the given tag (rqSeq, bits 7:2 of byte 4) and command. */
	private static Ipmiv20Message reply(int tag, byte command) {
		byte[] raw = { 0x20, 0x18, 0, (byte) 0x81, (byte) (tag << 2), command, 0, 0 };
		raw[2] = (byte) -(raw[0] + raw[1]);
		raw[7] = (byte) -(raw[3] + raw[4] + raw[5] + raw[6]);
		Ipmiv20Message reply = new Ipmiv20Message(null);
		reply.setPayloadType(PayloadType.Ipmi);
		reply.setPayload(new IpmiLanResponse(raw));
		return reply;
	}
}
