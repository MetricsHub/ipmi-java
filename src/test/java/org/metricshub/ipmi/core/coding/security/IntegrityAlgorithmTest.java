package org.metricshub.ipmi.core.coding.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class IntegrityAlgorithmTest {

	private static final int THREADS = 8;

	private static final int ROUNDS = 2000;

	@Test
	void generateAuthCodeIsStableWhenSeveralThreadsShareTheAlgorithm() throws Exception {
		IntegrityAlgorithm algorithm = new IntegrityHmacSha1_96();
		algorithm.initialize(new byte[20]);
		byte[][] bases = new byte[THREADS][40];
		byte[][] expected = new byte[THREADS][];
		for (int i = 0; i < THREADS; i++) {
			Arrays.fill(bases[i], (byte) (i + 1));
			expected[i] = algorithm.generateAuthCode(bases[i]);
		}

		AtomicReference<AssertionError> failure = new AtomicReference<>();
		Thread[] threads = new Thread[THREADS];
		for (int i = 0; i < THREADS; i++) {
			final int id = i;
			threads[i] = new Thread(() -> {
				for (int round = 0; round < ROUNDS; round++) {
					try {
						assertArrayEquals(expected[id], algorithm.generateAuthCode(bases[id]), "thread " + id);
					} catch (AssertionError e) {
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
