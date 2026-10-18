package org.metricshub.ipmi.core.coding.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.metricshub.ipmi.core.coding.security.CipherSuiteTest.bytes;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

// CHECKSTYLE.OFF: TypeName
class IntegrityMd5_128Test {
	// CHECKSTYLE.ON: TypeName

	/** AuthType/Format (06h), an RMCP+ header and payload, Pad Length 0, Next Header 07h: 24 bytes, no pad needed. */
	private static final byte[] BASE = bytes("06 c0 01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13 14 00 07");

	/** MD5("password" zero-padded to 20 bytes + BASE + the same password), computed with Python's hashlib. */
	private static final byte[] AUTH_CODE = bytes("d1 27 35 fd 17 3a b0 d5 54 96 3a e5 c3 2f 91 6b");

	@Test
	void authCodeIsTheMd5OfThePaddedPasswordAroundTheMessage() {
		IntegrityMd5_128 algorithm = new IntegrityMd5_128();
		algorithm.initialize("password".getBytes(StandardCharsets.US_ASCII));
		assertArrayEquals(AUTH_CODE, algorithm.generateAuthCode(BASE));
		assertArrayEquals(AUTH_CODE, algorithm.generateAuthCode(BASE), "the digest is reset between messages");
		assertEquals(16, algorithm.getAuthCodeLength());
		assertEquals(SecurityConstants.IA_MD5_128, algorithm.getCode());
		// The BMC stores the password zero-padded to 20 bytes: the padded password gives the same code
		IntegrityMd5_128 padded = new IntegrityMd5_128();
		padded.initialize(Arrays.copyOf("password".getBytes(StandardCharsets.US_ASCII), 20));
		assertArrayEquals(AUTH_CODE, padded.generateAuthCode(BASE));
	}

	@Test
	void integrityPadIsInjectedWhenTheBaseHasNone() {
		// 22 bytes + 16 of AuthCode = 38: two FFh pad bytes make it a multiple of 4, and Pad Length becomes 2
		byte[] unpadded = bytes("06 c0 01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f 10 11 12 00 07");
		byte[] paddedBase = bytes("06 c0 01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f 10 11 12 ff ff 02 07");
		IntegrityMd5_128 algorithm = new IntegrityMd5_128();
		algorithm.initialize(new byte[0]);
		assertArrayEquals(algorithm.generateAuthCode(paddedBase), algorithm.generateAuthCode(unpadded));
	}

	@Test
	void anEmptyPasswordIsTwentyZeroBytes() {
		IntegrityMd5_128 withNull = new IntegrityMd5_128();
		withNull.initialize(null);
		IntegrityMd5_128 withZeros = new IntegrityMd5_128();
		withZeros.initialize(new byte[20]);
		assertArrayEquals(withZeros.generateAuthCode(BASE), withNull.generateAuthCode(BASE));
		assertThrows(NullPointerException.class, () -> new IntegrityMd5_128().generateAuthCode(BASE));
	}

	@Test
	void theSuiteKeysMd5WithThePasswordAndTheHmacSuitesWithTheSik() throws Exception {
		byte[] sik = new byte[20];
		Arrays.fill(sik, (byte) 0x5a);
		byte[] password = "password".getBytes(StandardCharsets.US_ASCII);
		CipherSuite suite11 = CipherSuite.forId(11);
		suite11.initializeAlgorithms(sik, password);
		assertArrayEquals(AUTH_CODE, suite11.getIntegrityAlgorithm().generateAuthCode(BASE));
		CipherSuite suite7 = CipherSuite.forId(7);
		suite7.initializeAlgorithms(sik, password);
		IntegrityHmacMd5_128 hmac = new IntegrityHmacMd5_128();
		hmac.initialize(sik);
		assertArrayEquals(hmac.generateAuthCode(BASE), suite7.getIntegrityAlgorithm().generateAuthCode(BASE));
	}
}
