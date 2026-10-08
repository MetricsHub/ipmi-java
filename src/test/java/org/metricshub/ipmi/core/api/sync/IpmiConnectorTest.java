package org.metricshub.ipmi.core.api.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.connection.ConnectionException;
import org.metricshub.ipmi.core.transport.FakeBmc;

class IpmiConnectorTest {

	/**
	 * RMCP header, then an RMCP+ sessionless, unauthenticated IPMI message that announces an 80-byte payload the
	 * datagram does not carry: it passes the filters of the cipher-suites step and fails to decode.
	 */
	private static final byte[] TRUNCATED_REPLY = {
			0x06,
			0x00,
			(byte) 0xff,
			0x07,
			0x06,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x00,
			0x50,
			0x00 };

	private static final int TIMEOUT_MS = 500;

	@Test
	void aBadReplyFailsTheStepAtOnceAndLeavesItRetriable() throws Exception {
		try (FakeBmc bmc = new FakeBmc(request -> TRUNCATED_REPLY)) {
			IpmiConnector connector = new IpmiConnector(0);
			try {
				ConnectionHandle handle = connector.createConnection(bmc.getAddress(), bmc.getPort());
				connector.setTimeout(handle, TIMEOUT_MS);

				Exception first = assertThrows(Exception.class, () -> connector.getAvailableCipherSuites(handle));
				assertFalse(first instanceof ConnectionException, "the decoding failure, not a timeout: " + first);
				assertEquals(1, bmc.getRequestCount(), "a reply that is not a timeout must not be sent again");

				// The state machine was rolled back: the same step can be tried again
				Exception second = assertThrows(Exception.class, () -> connector.getAvailableCipherSuites(handle));
				assertFalse(second instanceof ConnectionException, String.valueOf(second));
				assertEquals(2, bmc.getRequestCount());
			} finally {
				connector.tearDown();
			}
		}
	}
}
