package org.metricshub.ipmi.core.api.sync;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.api.async.messages.IpmiError;
import org.metricshub.ipmi.core.api.async.messages.IpmiResponse;
import org.metricshub.ipmi.core.api.async.messages.IpmiResponseData;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.connection.ConnectionException;

class MessageListenerTest {

	private static final int TAG = 5;
	private static final ConnectionHandle HANDLE = new ConnectionHandle(0, InetAddress.getLoopbackAddress(), 623);

	@Test
	void retryWaitsForTheReplyOfTheResentMessage() throws Exception {
		MessageListener listener = new MessageListener(HANDLE);
		ResponseData data = new ResponseData() {};

		// First try: the message times out
		deliverLater(listener, new IpmiError(new ConnectionException("Message timed out"), TAG, HANDLE));
		assertThrows(ConnectionException.class, () -> listener.waitForAnswer(TAG));

		// Retry with the same tag: the reply of the resent message must be returned, not the stale error
		deliverLater(listener, new IpmiResponseData(data, TAG, HANDLE));
		assertSame(data, listener.waitForAnswer(TAG));
	}

	@Test
	void waitForAnswerReturnsWhenInterrupted() throws Exception {
		MessageListener listener = new MessageListener(HANDLE);
		AtomicReference<Throwable> thrown = new AtomicReference<>();
		Thread waiter = new Thread(() -> {
			try {
				listener.waitForAnswer(TAG);
			} catch (Throwable t) {
				thrown.set(t);
			}
		});
		waiter.start();
		Thread.sleep(100);
		waiter.interrupt();
		waiter.join(5000);
		assertFalse(waiter.isAlive(), "the waiting thread must return when interrupted");
		assertTrue(thrown.get() instanceof InterruptedException, String.valueOf(thrown.get()));
	}

	private static void deliverLater(MessageListener listener, IpmiResponse response) {
		Thread deliverer = new Thread(() -> {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				return;
			}
			listener.notify(response);
		});
		deliverer.setDaemon(true);
		deliverer.start();
	}
}
