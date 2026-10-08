package org.metricshub.ipmi.core.coding.commands.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.coding.security.SecurityConstants;

class Rakp1Test {

	private static final CipherSuite SUITE_3 = new CipherSuite(
			(byte) 3,
			SecurityConstants.AA_RAKP_HMAC_SHA1,
			SecurityConstants.CA_AES_CBC128,
			SecurityConstants.IA_HMAC_SHA1_96);

	/**
	 * SIK = HMAC-SHA1 keyed with Kg (or the password) over Rc | Rm | RoleM | ULengthM | UNameM (IPMI 2.0 section
	 * 13.31), computed independently of the library.
	 */
	private static byte[] expectedSik(Rakp1 rakp1, byte[] managedSystemRandom, String username, byte[] key)
			throws Exception {
		ByteArrayOutputStream base = new ByteArrayOutputStream();
		base.write(rakp1.getConsoleRandomNumber());
		base.write(managedSystemRandom);
		base.write(0x12); // User privilege level (2h), name-only lookup (10h)
		byte[] name = username.getBytes(StandardCharsets.UTF_8);
		base.write(name.length);
		base.write(name);
		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec(key, "HmacSHA1"));
		return mac.doFinal(base.toByteArray());
	}

	@Test
	void calculateSikUsesRawBmcKeyAndUtf8Credentials() throws Exception {
		Rakp1ResponseData rakp2 = new Rakp1ResponseData();
		byte[] managedSystemRandom = new byte[16];
		Arrays.fill(managedSystemRandom, (byte) 0xa5);
		rakp2.setManagedSystemRandomNumber(managedSystemRandom);
		String username = "usér";

		// Kg bytes of 80h and above used to go through new String(key).getBytes() and come out mangled
		byte[] bmcKey = { (byte) 0x80, (byte) 0xff, 0x01, (byte) 0xc3 };
		Rakp1 twoKey = new Rakp1(0x1234, PrivilegeLevel.User, username, "password", bmcKey, SUITE_3);
		assertArrayEquals(expectedSik(twoKey, managedSystemRandom, username, bmcKey), twoKey.calculateSik(rakp2));

		// without Kg, the key is the password, in UTF-8 whatever the platform charset
		String password = "pässwörd";
		Rakp1 oneKey = new Rakp1(0x1234, PrivilegeLevel.User, username, password, null, SUITE_3);
		assertArrayEquals(
				expectedSik(oneKey, managedSystemRandom, username, password.getBytes(StandardCharsets.UTF_8)),
				oneKey.calculateSik(rakp2));
	}
}
