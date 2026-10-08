package org.metricshub.ipmi.core.transport;

/**
 * A {@link Messenger} that sends nothing and never delivers anything: stands in for a BMC that does not answer.
 */
public class SilentMessenger implements Messenger {

	@Override
	public void send(UdpMessage message) {
		// dropped
	}

	@Override
	public void register(UdpListener listener) {
		// nothing will ever be delivered
	}

	@Override
	public void unregister(UdpListener listener) {
		// nothing to do
	}

	@Override
	public void closeConnection() {
		// nothing to do
	}
}
