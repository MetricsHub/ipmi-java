package org.metricshub.ipmi.core.api.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.metricshub.ipmi.core.api.async.messages.IpmiResponse;
import org.metricshub.ipmi.core.api.async.messages.IpmiResponseData;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatus;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.PlainMessage;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.transport.FakeRmcpPlusBmc;

class IpmiAsyncConnectorTest {

	/** Cipher suite records of suites 3 and 17, after the channel byte. */
	private static final byte[] SUITES = {
			(byte) 0xc0,
			0x03,
			0x01,
			0x41,
			(byte) 0x81,
			(byte) 0xc0,
			0x11,
			0x03,
			0x44,
			(byte) 0x81 };

	@Test
	@Timeout(30)
	void aListenerThatClosesTheConnectionOnAReplyGetsNoOtherOutcome() throws Exception {
		try (FakeRmcpPlusBmc bmc = new FakeRmcpPlusBmc(SUITES, "admin", "secret".getBytes(StandardCharsets.UTF_8), null)) {
			IpmiAsyncConnector connector = new IpmiAsyncConnector(0, 0);
			try {
				ConnectionHandle handle = connector.createConnection(bmc.getAddress(), bmc.getPort());
				CipherSuite suite = connector
						.getAvailableCipherSuites(handle)
						.stream()
						.filter(cipherSuite -> cipherSuite.getId() == 17)
						.findFirst()
						.orElseThrow(AssertionError::new);
				connector.getChannelAuthenticationCapabilities(handle, suite, PrivilegeLevel.User);
				connector.openSession(handle, "admin", "secret", null);

				List<String> outcomes = new CopyOnWriteArrayList<>();
				CountDownLatch replied = new CountDownLatch(1);
				connector.registerListener(response -> {
					outcomes.add(response.getClass().getSimpleName() + " " + response.getTag());
					if (response instanceof IpmiResponseData) {
						connector.closeConnection(handle);
						replied.countDown();
					}
				});
				int tag = connector
						.sendMessage(handle, new GetChassisStatus(IpmiVersion.V20, suite, AuthenticationType.RMCPPlus), false);
				assertTrue(replied.await(5, TimeUnit.SECONDS));
				Thread.sleep(200);
				// The request answered must not be reported again as failed by the closing connection
				assertEquals(Collections.singletonList("IpmiResponseData " + tag), outcomes);
			} finally {
				connector.tearDown();
			}
		}
	}

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
	@SuppressWarnings("deprecation")
	void theRequestsPassedWithoutConnectionReachTheListenersAsIn1202() throws Exception {
		IpmiAsyncConnector connector = new IpmiAsyncConnector(0);
		try {
			AtomicInteger notified = new AtomicInteger();
			connector.registerIncomingPayloadListener(new InboundMessageListener() {
				@Override
				public boolean isPayloadSupported(IpmiPayload payload) {
					return true;
				}

				@Override
				public void notify(IpmiPayload payload) {
					notified.incrementAndGet();
				}
			});
			connector.processRequest(new PlainMessage(new byte[0]));
			assertEquals(1, notified.get());
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
			connector.processRequest(new PlainMessage(new byte[0]), 0);
			connector.processRequest(new PlainMessage(new byte[0]), 0);
			assertTrue(notified.get() == 1, "the one-shot listener was notified " + notified.get() + " times");
		} finally {
			connector.tearDown();
		}
	}
}
