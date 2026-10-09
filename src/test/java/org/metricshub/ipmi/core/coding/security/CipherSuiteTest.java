package org.metricshub.ipmi.core.coding.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class CipherSuiteTest {

	/** The cipher suite records of a Lenovo XCC (suites 1 to 19), after the channel byte. */
	static final String XCC = "c0 01 01 40 80 c0 02 01 41 80 c0 03 01 41 81 c0 04 01 41 82 c0 05 01 41 83 "
			+ "c0 06 02 40 80 c0 07 02 42 80 c0 08 02 42 81 c0 09 02 42 82 c0 0a 02 42 83 c0 0b 02 43 80 "
			+ "c0 0c 02 43 81 c0 0d 02 43 82 c0 0e 02 43 83 c0 0f 03 40 80 c0 10 03 44 80 c0 11 03 44 81 "
			+ "c0 12 03 44 82 c0 13 03 44 83";

	/** The cipher suite records of a Cisco IMC (UCS C240): suites 0 to 14, then malformed OEM records. */
	static final String CISCO = "c0 00 00 40 80 c0 01 01 40 80 c0 02 01 41 80 c0 03 01 41 81 c0 04 01 41 82 "
			+ "c0 05 01 41 83 c0 06 02 40 80 c0 07 02 42 80 c0 08 02 42 81 c0 09 02 42 82 c0 0a 02 42 83 "
			+ "c0 0b 02 43 80 c0 0c 02 43 81 c0 0d 02 43 82 c0 0e 02 43 83 c1 80 00 00 00 c1 c1 b1 c1 63 "
			+ "00 00 00 69 70 e8";

	static byte[] bytes(String hex) {
		String[] digits = hex.trim().split("\\s+");
		byte[] bytes = new byte[digits.length];
		for (int i = 0; i < digits.length; i++) {
			bytes[i] = (byte) Integer.parseInt(digits[i], 16);
		}
		return bytes;
	}

	private static List<Integer> ids(String hex) {
		return CipherSuite
				.getCipherSuites(bytes(hex))
				.stream()
				.map(suite -> (int) suite.getId())
				.collect(Collectors.toList());
	}

	@Test
	void standardRecordsAreDecodedWithTheirAlgorithms() {
		assertEquals(IntStream.rangeClosed(1, 19).boxed().collect(Collectors.toList()), ids(XCC));

		CipherSuite suite17 = CipherSuite.getCipherSuites(bytes(XCC)).get(16);
		assertEquals(17, suite17.getId());
		assertEquals(SecurityConstants.AA_RAKP_HMAC_SHA256, suite17.getAuthenticationAlgorithm().getCode());
		assertEquals(SecurityConstants.IA_HMAC_SHA256_128, suite17.getIntegrityAlgorithm().getCode());
		assertEquals(SecurityConstants.CA_AES_CBC128, suite17.getConfidentialityAlgorithm().getCode());
	}

	@Test
	void oemAndMalformedRecordsAreSkipped() {
		// The OEM records (C1h) carry vendor-specific suite IDs (80h and B1h here), which used to come out as -128
		// and -79 among the standard suites
		assertEquals(IntStream.rangeClosed(0, 14).boxed().collect(Collectors.toList()), ids(CISCO));
		// A valid OEM record: C1h, OEM suite ID, IANA (with bytes that look like record starts), then the tags
		assertEquals(Arrays.asList(3), ids("c1 80 c0 c1 00 01 41 81 c0 03 01 41 81"));
	}

	@Test
	void truncatedRecordsAndGarbageDoNotThrow() {
		assertEquals(Arrays.asList(), ids("c0"));
		assertEquals(Arrays.asList(), ids("c1 80 00 00"));
		assertEquals(Arrays.asList(3), ids("c0 03 01 41 81 c0 11"));
		assertEquals(Arrays.asList(3), ids("ff 12 c0 03 01 41 81"));
	}

	@Test
	void theAlgorithmsOfAStandardSuiteComeFromTheSpecificationNotFromTheRecord() {
		// The records come unauthenticated: a suite 3 whose tags say "no integrity, no encryption", or a suite 17
		// that claims xRC4, keeps the algorithms of IPMI 2.0 table 22-20
		CipherSuite suite3 = CipherSuite.getCipherSuites(bytes("c0 03 01 40 80")).get(0);
		assertEquals(SecurityConstants.AA_RAKP_HMAC_SHA1, suite3.getAuthenticationAlgorithm().getCode());
		assertEquals(SecurityConstants.IA_HMAC_SHA1_96, suite3.getIntegrityAlgorithm().getCode());
		assertEquals(SecurityConstants.CA_AES_CBC128, suite3.getConfidentialityAlgorithm().getCode());
		CipherSuite suite17 = CipherSuite.getCipherSuites(bytes("c0 11 03 44 82")).get(0);
		assertEquals(SecurityConstants.CA_AES_CBC128, suite17.getConfidentialityAlgorithm().getCode());
		// A reserved suite ID is skipped
		assertEquals(Arrays.asList(3), ids("c0 14 03 44 81 c0 03 01 41 81"));
	}

	@Test
	void isSupportedTellsTheSuitesThisLibraryImplements() {
		List<CipherSuite> suites = CipherSuite.getCipherSuites(bytes(XCC));
		List<Integer> supported = suites
				.stream()
				.filter(CipherSuite::isSupported)
				.map(suite -> (int) suite.getId())
				.collect(Collectors.toList());
		// No xRC4 (4, 5, 9, 10, 13, 14, 18, 19) and no MD5-128 integrity (11 to 14)
		assertEquals(Arrays.asList(1, 2, 3, 6, 7, 8, 15, 16, 17), supported);
		assertFalse(new CipherSuite((byte) 3, (byte) 1, (byte) 1, (byte) -1).isSupported()); // no integrity algorithm
		assertTrue(CipherSuite.getEmpty().isSupported());
	}
}
