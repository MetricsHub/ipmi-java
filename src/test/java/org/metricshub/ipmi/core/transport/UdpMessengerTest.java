package org.metricshub.ipmi.core.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class UdpMessengerTest {

	@Test
	void testReceivesDatagramsLargerThan512Bytes() throws Exception {
		byte[] reply = new byte[1000];
		Arrays.fill(reply, (byte) 0x5A);
		BlockingQueue<byte[]> received = new ArrayBlockingQueue<>(1);
		UdpMessenger messenger = new UdpMessenger(0, InetAddress.getLoopbackAddress());
		try (FakeBmc bmc = new FakeBmc(request -> reply)) {
			messenger.register(message -> received.offer(message.getMessage()));
			UdpMessage request = new UdpMessage();
			request.setAddress(bmc.getAddress());
			request.setPort(bmc.getPort());
			request.setMessage(new byte[] { 1 });
			messenger.send(request);

			byte[] message = received.poll(5, TimeUnit.SECONDS);
			assertNotNull(message, "no reply received");
			assertArrayEquals(reply, message, "the reply must not be truncated");
		} finally {
			messenger.closeConnection();
		}
	}
}
