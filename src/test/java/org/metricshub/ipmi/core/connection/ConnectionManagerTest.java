package org.metricshub.ipmi.core.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.net.InetAddress;
import org.metricshub.ipmi.core.transport.SilentMessenger;

class ConnectionManagerTest {

	private static final int TAG_COUNT = 60;

	@Test
	void generateSessionlessTagKeepsWaitingWhenInterruptedOnSaturatedPool() throws Exception {
		int[] tags = new int[TAG_COUNT];
		try {
			for (int i = 0; i < TAG_COUNT; i++) {
				tags[i] = ConnectionManager.generateSessionlessTag();
			}

			AtomicInteger got = new AtomicInteger(-1);
			AtomicBoolean interruptedOnReturn = new AtomicBoolean();
			Thread waiter = new Thread(() -> {
				got.set(ConnectionManager.generateSessionlessTag());
				interruptedOnReturn.set(Thread.currentThread().isInterrupted());
			});
			waiter.start();
			waiter.interrupt();
			Thread.sleep(100);
			assertTrue(waiter.isAlive(), "the interrupted request must keep waiting for a free tag");

			ConnectionManager.freeTag(tags[0]);
			waiter.join(5000);
			assertFalse(waiter.isAlive(), "the request must complete once a tag is freed");
			assertEquals(tags[0], got.get());
			assertTrue(interruptedOnReturn.get(), "the interrupt flag must be restored on return");
		} finally {
			for (int tag : tags) {
				ConnectionManager.freeTag(tag);
			}
		}
	}

	@Test
	void pingPeriodComesFromThePropertiesOnlyWhenNotGiven() throws Exception {
		assertPingPeriod(30000, new ConnectionManager(0));
		assertPingPeriod(30000, new ConnectionManager(0, -1));
		assertPingPeriod(12345, new ConnectionManager(0, 12345));
		assertPingPeriod(0, new ConnectionManager(0, 0));
	}

	private static void assertPingPeriod(long expected, ConnectionManager manager) {
		try {
			assertEquals(expected, manager.getPingPeriod());
		} finally {
			manager.close();
		}
	}

	@Test
	void sessionlessTagsStayReservedWhenAnotherManagerIsCreated() {
		int reserved = ConnectionManager.generateSessionlessTag();
		int[] others = new int[TAG_COUNT - 1];
		try {
			new ConnectionManager(new SilentMessenger()).close();
			for (int i = 0; i < others.length; i++) {
				others[i] = ConnectionManager.generateSessionlessTag();
				assertNotEquals(reserved, others[i], "a tag reserved before the second manager was handed out again");
			}
		} finally {
			ConnectionManager.freeTag(reserved);
			for (int tag : others) {
				ConnectionManager.freeTag(tag);
			}
		}
	}

	@Test
	void everyCreateConnectionOverloadReturnsTheHandleOfTheConnection() throws Exception {
		ConnectionManager manager = new ConnectionManager(new SilentMessenger());
		InetAddress bmc = InetAddress.getLoopbackAddress();
		try {
			int[] handles = {
					manager.createConnection(bmc, 623),
					manager.createConnection(bmc, 623, 0),
					manager.createConnection(bmc, 623, 0, true),
					manager.createConnection(bmc, 623, true) };
			for (int i = 0; i < handles.length; i++) {
				assertEquals(i, handles[i]);
				assertEquals(i, manager.getConnection(i).getHandle(), "the connection must carry its own handle");
			}
		} finally {
			manager.close();
		}
	}

	@Test
	void closeConnectionReleasesTheConnectionAndKeepsTheOtherHandles() throws Exception {
		ConnectionManager manager = new ConnectionManager(new SilentMessenger());
		InetAddress bmc = InetAddress.getLoopbackAddress();
		try {
			int first = manager.createConnection(bmc, 623);
			int second = manager.createConnection(bmc, 623);
			manager.closeConnection(first);
			assertThrows(IllegalStateException.class, () -> manager.getConnection(first));
			assertEquals(second, manager.getConnection(second).getHandle());
			assertTrue(manager.getConnection(second).isActive());
			manager.closeConnection(first); // closing twice is harmless
			assertEquals(2, manager.createConnection(bmc, 623), "a released handle is not reused");
		} finally {
			manager.close();
		}
	}

	@Test
	void everyOperationOnAReleasedHandleFailsTheSameWay() throws Exception {
		ConnectionManager manager = new ConnectionManager(new SilentMessenger());
		try {
			int handle = manager.createConnection(InetAddress.getLoopbackAddress(), 623);
			manager.closeConnection(handle);
			assertThrows(IllegalStateException.class, () -> manager.getAvailableCipherSuites(handle));
			assertThrows(
					IllegalStateException.class,
					() -> manager.getChannelAuthenticationCapabilities(handle, null, null));
			assertThrows(IllegalStateException.class, () -> manager.startSession(handle, null, null, "", new byte[0], null));
			assertThrows(IllegalStateException.class, () -> manager.registerListener(handle, null));
			assertThrows(IllegalStateException.class, () -> manager.getConnection(handle));
		} finally {
			manager.close();
		}
	}

	@Test
	void aClosedManagerCreatesNoConnection() throws Exception {
		ConnectionManager manager = new ConnectionManager(new SilentMessenger());
		int handle = manager.createConnection(InetAddress.getLoopbackAddress(), 623);
		manager.close();
		assertFalse(manager.getConnection(handle).isActive(), "close() disconnects the connections");
		assertThrows(
				IllegalStateException.class,
				() -> manager.createConnection(InetAddress.getLoopbackAddress(), 623));
	}
}
