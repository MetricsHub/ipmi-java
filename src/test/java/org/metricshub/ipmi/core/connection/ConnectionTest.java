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
}
