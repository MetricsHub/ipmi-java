package org.metricshub.ipmi.core.connection.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.PayloadCoder;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelAuthenticationCapabilities;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanMessage;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.connection.Connection;
import org.metricshub.ipmi.core.connection.ConnectionListener;
import org.metricshub.ipmi.core.transport.SilentMessenger;

class MessageQueueTest {

	private static final int TIMEOUT_MS = 100;

	/** Longer than the timeout plus the 500 ms cleaning period of the queue timer. */
	private static final int TIMER_TICK_MS = 800;

	private static final int WINDOW_SIZE = 8;

	private final Connection connection = new Connection(new SilentMessenger(), 0);

	/** "tag:message" of every timeout reported to the listeners. */
	private final List<String> reported = new CopyOnWriteArrayList<>();

	private MessageQueue newQueue() {
		return new MessageQueue(
				connection,
				TIMEOUT_MS,
				IpmiLanMessage.MIN_SEQUENCE_NUMBER,
				IpmiLanMessage.MAX_SEQUENCE_NUMBER);
	}

	private static PayloadCoder request() {
		return new GetChannelAuthenticationCapabilities(IpmiVersion.V20, IpmiVersion.V20, CipherSuite.getEmpty());
	}

	private ConnectionListener recorder() {
		return new ConnectionListener() {
			@Override
			public void processResponse(ResponseData responseData, int handle, int tag, Exception exception) {
				reported.add(tag + ":" + exception.getMessage());
			}

			@Override
			public void processRequest(IpmiPayload payload) {
				// not used
			}
		};
	}

	@Test
	void timedOutMessageLeavesTheQueueAtOnceAndIsReportedOnce() throws Exception {
		MessageQueue queue = newQueue();
		try {
			connection.registerListener(recorder());

			int tag = queue.add(request());
			assertTrue(queue.containsId(tag));

			Thread.sleep(TIMEOUT_MS + 50);
			queue.run();

			assertEquals(Collections.singletonList(tag + ":Message timed out"), reported);
			assertFalse(queue.containsId(tag), "a timed-out message must not linger in the queue");

			// The window is free: a full window of new messages is accepted without waiting
			for (int i = 0; i < WINDOW_SIZE; i++) {
				assertTrue(queue.add(request()) > 0, "add " + i);
			}
			queue.run();
			assertEquals(1, reported.size(), "a timeout must be reported once");
		} finally {
			queue.tearDown();
		}
	}

	@Test
	void timerSurvivesAListenerThatThrows() throws Exception {
		MessageQueue queue = newQueue();
		try {
			connection.registerListener(recorder());
			connection.registerListener(new ConnectionListener() {
				@Override
				public void processResponse(ResponseData responseData, int handle, int tag, Exception exception) {
					throw new IllegalStateException("listener failure");
				}

				@Override
				public void processRequest(IpmiPayload payload) {
					// not used
				}
			});

			int first = queue.add(request());
			Thread.sleep(TIMER_TICK_MS); // expired by the timer thread, whose notification throws

			int second = queue.add(request());
			Thread.sleep(TIMER_TICK_MS); // only a live timer thread can expire this one

			assertEquals(Arrays.asList(first + ":Message timed out", second + ":Message timed out"), reported);
		} finally {
			queue.tearDown();
		}
	}

	@Test
	void oneWaySequenceNumbersSkipTheQueuedTags() {
		MessageQueue queue = newQueue();
		try {
			int queued = queue.add(request());
			assertTrue(queued > 0);
			// Twice around the 63-value sequence space
			for (int i = 0; i < 2 * IpmiLanMessage.MAX_SEQUENCE_NUMBER; i++) {
				int sequenceNumber = queue.getSequenceNumber();
				assertTrue(sequenceNumber != queued, "a one-way message took the tag of the queued request");
				assertTrue(
						sequenceNumber >= IpmiLanMessage.MIN_SEQUENCE_NUMBER
								&& sequenceNumber <= IpmiLanMessage.MAX_SEQUENCE_NUMBER,
						"out of range: " + sequenceNumber);
			}
		} finally {
			queue.tearDown();
		}
	}
}
