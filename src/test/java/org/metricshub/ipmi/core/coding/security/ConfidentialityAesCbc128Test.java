package org.metricshub.ipmi.core.coding.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class ConfidentialityAesCbc128Test {

	private static final int THREADS = 8;

	private static final int ROUNDS = 2000;

	@Test
	void encryptAndDecryptRoundTripWhenSeveralThreadsShareTheAlgorithm() throws Exception {
		ConfidentialityAesCbc128 algorithm = new ConfidentialityAesCbc128();
		algorithm.initialize(new byte[20], new AuthenticationRakpHmacSha1());

		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread[] threads = new Thread[THREADS];
		for (int i = 0; i < THREADS; i++) {
			final byte[] data = new byte[20 + i];
			Arrays.fill(data, (byte) (i + 1));
			threads[i] = new Thread(() -> {
				for (int round = 0; round < ROUNDS; round++) {
					try {
						assertArrayEquals(data, algorithm.decrypt(algorithm.encrypt(data)));
					} catch (Throwable e) {
						failure.compareAndSet(null, e);
						return;
					}
				}
			});
			threads[i].start();
		}
		for (Thread thread : threads) {
			thread.join();
		}
		assertNull(failure.get(), String.valueOf(failure.get()));
	}
}
