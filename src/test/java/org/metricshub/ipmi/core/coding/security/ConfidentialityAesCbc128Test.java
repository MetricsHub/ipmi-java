package org.metricshub.ipmi.core.coding.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

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

	/**
	 * Encrypts the given plaintext (payload data, pad and pad length, already a multiple of 16 bytes) with the key the
	 * algorithm derives from a SIK of 20 zero bytes: the first 16 bytes of K2 = HMAC-SHA1(SIK, 20 bytes 02h), IPMI 2.0
	 * section 13.32. Returns the IV followed by the ciphertext, as in an IPMI payload.
	 */
	private static byte[] encrypt(byte[] plaintext) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec(new byte[20], "HmacSHA1"));
		byte[] const2 = new byte[20];
		Arrays.fill(const2, (byte) 2);
		byte[] k2 = mac.doFinal(const2);
		byte[] iv = new byte[16];
		Arrays.fill(iv, (byte) 0x3c);
		Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Arrays.copyOf(k2, 16), "AES"), new IvParameterSpec(iv));
		byte[] encrypted = cipher.doFinal(plaintext);
		byte[] payload = Arrays.copyOf(iv, 16 + encrypted.length);
		System.arraycopy(encrypted, 0, payload, 16, encrypted.length);
		return payload;
	}

	@Test
	void decryptChecksTheConfidentialityPad() throws Exception {
		ConfidentialityAesCbc128 algorithm = new ConfidentialityAesCbc128();
		algorithm.initialize(new byte[20], new AuthenticationRakpHmacSha1());

		// 12 bytes of data, the pad 01h 02h 03h, the pad length 3
		byte[] valid = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 1, 2, 3, 3 };
		assertArrayEquals(Arrays.copyOf(valid, 12), algorithm.decrypt(encrypt(valid)));

		// pad bytes other than 01h 02h 03h
		byte[] badPad = valid.clone();
		badPad[13] = 0;
		assertThrows(IllegalArgumentException.class, () -> algorithm.decrypt(encrypt(badPad)));

		// pad length above 15
		byte[] padTooLong = new byte[32];
		padTooLong[31] = 16;
		assertThrows(IllegalArgumentException.class, () -> algorithm.decrypt(encrypt(padTooLong)));

		// pad length larger than the block
		byte[] padBeyondData = valid.clone();
		padBeyondData[15] = 15;
		assertThrows(IllegalArgumentException.class, () -> algorithm.decrypt(encrypt(padBeyondData)));

		// an IV and nothing else, or part of a block
		assertThrows(IllegalArgumentException.class, () -> algorithm.decrypt(new byte[16]));
		assertThrows(IllegalArgumentException.class, () -> algorithm.decrypt(new byte[20]));
		assertThrows(IllegalArgumentException.class, () -> algorithm.decrypt(new byte[3]));
	}
}
