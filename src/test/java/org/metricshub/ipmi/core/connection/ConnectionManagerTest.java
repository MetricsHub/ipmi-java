package org.metricshub.ipmi.core.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

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
}
