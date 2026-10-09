package org.metricshub.ipmi.core.api.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.api.async.messages.IpmiResponse;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.PlainMessage;

class IpmiAsyncConnectorTest {

	@Test
	void aResponseListenerMayUnregisterItselfWhileNotified() throws Exception {
		IpmiAsyncConnector connector = new IpmiAsyncConnector(0);
		try {
			ConnectionHandle handle = connector.createConnection(InetAddress.getLoopbackAddress(), 623);
			AtomicInteger notified = new AtomicInteger();
			IpmiResponseListener oneShot = new IpmiResponseListener() {
				@Override
				public void notify(IpmiResponse response) {
					connector.unregisterListener(this);
				}
			};
			connector.registerListener(oneShot);
			connector.registerListener(response -> notified.incrementAndGet());

			connector.processResponse(null, handle.getHandle(), 1, new Exception("timed out"));
			connector.processResponse(null, handle.getHandle(), 2, new Exception("timed out"));
			assertEquals(2, notified.get(), "the listener registered after the one-shot one must be notified each time");
		} finally {
			connector.tearDown();
		}
	}

	@Test
	void anInboundListenerMayUnregisterItselfWhileNotified() throws Exception {
		IpmiAsyncConnector connector = new IpmiAsyncConnector(0);
		try {
			AtomicInteger notified = new AtomicInteger();
			InboundMessageListener oneShot = new InboundMessageListener() {
				@Override
				public boolean isPayloadSupported(IpmiPayload payload) {
					return true;
				}

				@Override
				public void notify(IpmiPayload payload) {
					notified.incrementAndGet();
					connector.unregisterIncomingPayloadListener(this);
				}
			};
			connector.registerIncomingPayloadListener(oneShot);
			connector.processRequest(new PlainMessage(new byte[0]));
			connector.processRequest(new PlainMessage(new byte[0]));
			assertTrue(notified.get() == 1, "the one-shot listener was notified " + notified.get() + " times");
		} finally {
			connector.tearDown();
		}
	}
}
