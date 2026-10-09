package org.metricshub.ipmi.core.coding.commands.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.payload.PlainMessage;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv20Message;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.coding.security.ConfidentialityNone;
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
		Rakp1 twoKey = new Rakp1(
				0x1234,
				PrivilegeLevel.User,
				username,
				"password".getBytes(StandardCharsets.UTF_8),
				bmcKey,
				SUITE_3);
		assertArrayEquals(expectedSik(twoKey, managedSystemRandom, username, bmcKey), twoKey.calculateSik(rakp2));

		// without Kg, the key is the password, in UTF-8 whatever the platform charset
		String password = "pässwörd";
		Rakp1 oneKey = new Rakp1(
				0x1234,
				PrivilegeLevel.User,
				username,
				password.getBytes(StandardCharsets.UTF_8),
				null,
				SUITE_3);
		assertArrayEquals(
				expectedSik(oneKey, managedSystemRandom, username, password.getBytes(StandardCharsets.UTF_8)),
				oneKey.calculateSik(rakp2));
	}

	private static final byte[] PASSWORD = "password".getBytes(StandardCharsets.UTF_8);

	/** RAKP Message 2 data, as decoded: the managed system random number and GUID. */
	private static Rakp1ResponseData rakp2() {
		Rakp1ResponseData rakp2 = new Rakp1ResponseData();
		byte[] managedSystemRandom = new byte[16];
		Arrays.fill(managedSystemRandom, (byte) 0xa5);
		rakp2.setManagedSystemRandomNumber(managedSystemRandom);
		byte[] guid = new byte[16];
		Arrays.fill(guid, (byte) 0x6c);
		rakp2.setManagedSystemGuid(guid);
		return rakp2;
	}

	/** A RAKP message received from the BMC, of the given payload type. */
	private static Ipmiv20Message rakpMessage(PayloadType type, byte[] payload) {
		Ipmiv20Message message = new Ipmiv20Message(new ConfidentialityNone());
		message.setPayloadType(type);
		message.setPayload(new PlainMessage(payload));
		return message;
	}

	@Test
	void anAllZeroBmcKeyMeansOneKeyLogin() throws Exception {
		// IPMI 2.0 section 13.33: a Kg of zeros is the default value, the SIK is then keyed with the password
		Rakp1ResponseData rakp2 = rakp2();
		Rakp1 rakp1 = new Rakp1(0x1234, PrivilegeLevel.User, "admin", PASSWORD, new byte[20], SUITE_3);
		assertArrayEquals(
				expectedSik(rakp1, rakp2.getManagedSystemRandomNumber(), "admin", PASSWORD),
				rakp1.calculateSik(rakp2));
	}

	@Test
	void anEmptyPasswordIsAKeyOf20ZeroBytes() throws Exception {
		Rakp1ResponseData rakp2 = rakp2();
		for (byte[] password : new byte[][] { new byte[0], null }) {
			Rakp1 rakp1 = new Rakp1(0x1234, PrivilegeLevel.User, "admin", password, null, SUITE_3);
			assertArrayEquals(
					expectedSik(rakp1, rakp2.getManagedSystemRandomNumber(), "admin", new byte[20]),
					rakp1.calculateSik(rakp2));
		}
	}

	@Test
	void aPasswordOrBmcKeyLongerThan20BytesIsRejected() {
		// IPMI 2.0 tables 22-30 and 22-35: the BMC stores 20 bytes at most, a longer key can only fail the handshake
		assertThrows(
				IllegalArgumentException.class,
				() -> new Rakp1(0x1234, PrivilegeLevel.User, "admin", new byte[21], null, SUITE_3));
		assertThrows(
				IllegalArgumentException.class,
				() -> new Rakp1(0x1234, PrivilegeLevel.User, "admin", PASSWORD, new byte[21], SUITE_3));
		new Rakp1(0x1234, PrivilegeLevel.User, "admin", new byte[20], new byte[20], SUITE_3);
	}

	@Test
	void aTruncatedRakpMessage2IsRejected() {
		Rakp1 rakp1 = new Rakp1(0x1234, PrivilegeLevel.User, "admin", PASSWORD, null, SUITE_3);
		// 40 bytes of header, then 20 bytes of HMAC-SHA1 key exchange authentication code
		for (int length : new int[] { 0, 1, 40, 59 }) {
			IllegalArgumentException e = assertThrows(
					IllegalArgumentException.class,
					() -> rakp1.getResponseData(rakpMessage(PayloadType.Rakp2, new byte[length])),
					length + " bytes");
			assertEquals("Invalid payload length", e.getMessage(), length + " bytes");
		}
	}

	@Test
	void theRakpMessage4OfRakpHmacMd5IsChecked16Bytes() throws Exception {
		// IPMI 2.0 section 13.28.3: the Integrity Check Value of RAKP-HMAC-MD5 is the whole 16-byte HMAC-MD5
		CipherSuite suite8 = new CipherSuite(
				(byte) 8,
				SecurityConstants.AA_RAKP_HMAC_MD5,
				SecurityConstants.CA_AES_CBC128,
				SecurityConstants.IA_HMAC_MD5_128);
		Rakp1ResponseData rakp2 = rakp2();
		Rakp1 rakp1 = new Rakp1(0x1234, PrivilegeLevel.User, "admin", PASSWORD, null, suite8);

		// ICV = HMAC-MD5 keyed with the SIK over Rm | SIDc | GUIDc (section 13.31)
		ByteArrayOutputStream base = new ByteArrayOutputStream();
		base.write(rakp1.getConsoleRandomNumber());
		base.write(new byte[] { 0x34, 0x12, 0, 0 });
		base.write(rakp2.getManagedSystemGuid());
		Mac mac = Mac.getInstance("HmacMD5");
		mac.init(new SecretKeySpec(rakp1.calculateSik(rakp2), "HmacMD5"));
		byte[] icv = mac.doFinal(base.toByteArray());
		assertEquals(16, icv.length);

		byte[] rakp4 = new byte[8 + icv.length];
		rakp4[4] = 0x77; // console session ID
		System.arraycopy(icv, 0, rakp4, 8, icv.length);
		Rakp3 rakp3 = new Rakp3(suite8, rakp1, rakp2);
		Rakp3ResponseData data = (Rakp3ResponseData) rakp3.getResponseData(rakpMessage(PayloadType.Rakp4, rakp4));
		assertEquals(0x77, data.getConsoleSessionId());

		// The first 12 bytes only, as HMAC-SHA1-96 would have: too short
		assertThrows(
				IllegalArgumentException.class,
				() -> rakp3.getResponseData(rakpMessage(PayloadType.Rakp4, Arrays.copyOf(rakp4, 20))));
		// The last 4 bytes wrong
		byte[] wrong = rakp4.clone();
		wrong[23] ^= 1;
		assertThrows(IllegalArgumentException.class, () -> rakp3.getResponseData(rakpMessage(PayloadType.Rakp4, wrong)));
	}
}
