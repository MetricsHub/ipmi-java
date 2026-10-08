package org.metricshub.ipmi.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.transport.FakeBmc;

class IpmiClientTest {

	private static final long TIMEOUT_S = 1;

	/** The timers and the receiver end shortly after the socket is closed: how long to wait for them. */
	private static final long THREAD_EXIT_MS = 2000;

	private interface IpmiCall {
		Object run(IpmiClientConfiguration configuration) throws Exception;
	}

	@Test
	void everyCallReturnsWithinTheTimeoutAndLeavesNoThreadBehind() throws Exception {
		IpmiCall[] calls = {
				IpmiClient::getChassisStatus,
				IpmiClient::getSensors,
				IpmiClient::getFrus,
				IpmiClient::getChassisStatusAsStringResult,
				IpmiClient::getFrusAndSensorsAsStringResult };

		try (FakeBmc bmc = FakeBmc.silent()) {
			IpmiClientConfiguration configuration = new IpmiClientConfiguration(
					bmc.getAddress().getHostAddress(),
					bmc.getPort(),
					"user",
					"password".toCharArray(),
					null,
					false,
					TIMEOUT_S);

			for (IpmiCall call : calls) {
				Set<Thread> before = Thread.getAllStackTraces().keySet();
				int requestsBefore = bmc.getRequestCount();
				long start = System.nanoTime();

				assertThrows(TimeoutException.class, () -> call.run(configuration));

				long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
				assertTrue(
						elapsed >= TIMEOUT_S * 1000 && elapsed < TIMEOUT_S * 1000 + 1500,
						"elapsed " + elapsed + " ms");
				assertTrue(bmc.getRequestCount() > requestsBefore, "the BMC must have been contacted");
				assertEquals("", survivors(before), "library threads still alive after the call");
			}
		}
	}

	private static String survivors(Set<Thread> before) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(THREAD_EXIT_MS);
		Set<Thread> alive;
		do {
			alive = new HashSet<>(Thread.getAllStackTraces().keySet());
			alive.removeAll(before);
			alive.removeIf(thread -> !thread.isAlive());
			if (alive.isEmpty()) {
				return "";
			}
			Thread.sleep(20);
		} while (System.nanoTime() < deadline);
		return alive.stream().map(Thread::getName).sorted().collect(Collectors.joining(", "));
	}
}
