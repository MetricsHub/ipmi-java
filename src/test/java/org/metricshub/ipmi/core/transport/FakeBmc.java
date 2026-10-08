package org.metricshub.ipmi.core.transport;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * A UDP endpoint on the loopback interface that stands in for a BMC: every datagram it receives is handed to a
 * responder whose return value is sent back (or dropped when null).
 */
public class FakeBmc implements AutoCloseable {

	private final DatagramSocket socket;
	private final Function<byte[], byte[]> responder;
	private final AtomicInteger requests = new AtomicInteger();

	/**
	 * @param responder computes the reply to a request, or returns null to drop it
	 * @throws SocketException when no loopback port is free
	 */
	public FakeBmc(Function<byte[], byte[]> responder) throws SocketException {
		this.responder = responder;
		socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
		Thread thread = new Thread(this::serve, "fake-bmc");
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * @return a BMC that never answers
	 * @throws SocketException when no loopback port is free
	 */
	public static FakeBmc silent() throws SocketException {
		return new FakeBmc(request -> null);
	}

	/**
	 * @return the loopback address the fake BMC listens on
	 */
	public InetAddress getAddress() {
		return socket.getLocalAddress();
	}

	/**
	 * @return the UDP port the fake BMC listens on
	 */
	public int getPort() {
		return socket.getLocalPort();
	}

	/**
	 * @return how many datagrams were received so far
	 */
	public int getRequestCount() {
		return requests.get();
	}

	private void serve() {
		byte[] buffer = new byte[1024];
		while (!socket.isClosed()) {
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			try {
				socket.receive(packet);
				requests.incrementAndGet();
				byte[] reply = responder.apply(Arrays.copyOf(packet.getData(), packet.getLength()));
				if (reply != null) {
					socket.send(new DatagramPacket(reply, reply.length, packet.getSocketAddress()));
				}
			} catch (IOException e) {
				return; // closed
			}
		}
	}

	@Override
	public void close() {
		socket.close();
	}
}
