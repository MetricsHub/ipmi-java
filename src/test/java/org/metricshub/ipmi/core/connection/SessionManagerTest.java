package org.metricshub.ipmi.core.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.api.async.ConnectionHandle;
import org.metricshub.ipmi.core.api.sol.CipherSuiteSelectionHandler;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;
import org.metricshub.ipmi.core.common.PropertiesManager;
import org.metricshub.ipmi.core.transport.FakeBmc;

class SessionManagerTest {

	private static final int TIMEOUT_MS = 200;

	@Test
	void aFailedSessionClosesItsConnectionOnly() throws Exception {
		PropertiesManager properties = PropertiesManager.getInstance();
		String timeout = properties.getProperty("timeout");
		properties.setProperty("timeout", String.valueOf(TIMEOUT_MS));
		try (FakeBmc bmc = FakeBmc.silent()) {
			IpmiConnector connector = new IpmiConnector(0);
			try {
				ConnectionHandle other = connector.createConnection(bmc.getAddress(), bmc.getPort());
				CipherSuiteSelectionHandler firstSuite = suites -> suites.isEmpty() ? null : suites.get(0);
				assertThrows(
						SessionException.class,
						() -> SessionManager
								.establishSession(
										connector,
										bmc.getAddress().getHostAddress(),
										bmc.getPort(),
										"user",
										"password",
										firstSuite));

				// The failed connection (handle 1) is closed; the other one still sends and times out, instead of
				// being told that the socket is closed
				assertThrows(IllegalStateException.class, () -> connector.getTimeout(new ConnectionHandle(1, null, 0)));
				ConnectionException e = assertThrows(
						ConnectionException.class,
						() -> connector.getAvailableCipherSuites(other));
				assertEquals("Command timed out", e.getMessage());
			} finally {
				connector.tearDown();
			}
		} finally {
			properties.setProperty("timeout", timeout);
		}
	}
}
