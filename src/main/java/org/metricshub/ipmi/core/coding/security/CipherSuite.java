package org.metricshub.ipmi.core.coding.security;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 Verax Systems, MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import org.metricshub.ipmi.core.coding.commands.session.GetChannelCipherSuites;
import org.metricshub.ipmi.core.coding.commands.session.GetChannelCipherSuitesResponseData;
import org.metricshub.ipmi.core.common.TypeConverter;

import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Provides cipher suite (authentication, confidentiality and integrity
 * algorithms used during the session).
 */
public class CipherSuite {

	/**
	 * Message of the exceptions about algorithms this library does not implement.
	 */
	public static final String NOT_YET_IMPLEMENTED_MESSAGE = "Not yet implemented.";

	/**
	 * The authentication, confidentiality and integrity algorithms of the standard cipher suites 0 to 19 (IPMI 2.0
	 * table 22-20).
	 */
	private static final byte[][] STANDARD_SUITES = {
			{ SecurityConstants.AA_RAKP_NONE, SecurityConstants.CA_NONE, SecurityConstants.IA_NONE },
			{ SecurityConstants.AA_RAKP_HMAC_SHA1, SecurityConstants.CA_NONE, SecurityConstants.IA_NONE },
			{ SecurityConstants.AA_RAKP_HMAC_SHA1, SecurityConstants.CA_NONE, SecurityConstants.IA_HMAC_SHA1_96 },
			{ SecurityConstants.AA_RAKP_HMAC_SHA1, SecurityConstants.CA_AES_CBC128, SecurityConstants.IA_HMAC_SHA1_96 },
			{ SecurityConstants.AA_RAKP_HMAC_SHA1, SecurityConstants.CA_XRC4_128, SecurityConstants.IA_HMAC_SHA1_96 },
			{ SecurityConstants.AA_RAKP_HMAC_SHA1, SecurityConstants.CA_XRC4_40, SecurityConstants.IA_HMAC_SHA1_96 },
			{ SecurityConstants.AA_RAKP_HMAC_MD5, SecurityConstants.CA_NONE, SecurityConstants.IA_NONE },
			{ SecurityConstants.AA_RAKP_HMAC_MD5, SecurityConstants.CA_NONE, SecurityConstants.IA_HMAC_MD5_128 },
			{ SecurityConstants.AA_RAKP_HMAC_MD5, SecurityConstants.CA_AES_CBC128, SecurityConstants.IA_HMAC_MD5_128 },
			{ SecurityConstants.AA_RAKP_HMAC_MD5, SecurityConstants.CA_XRC4_128, SecurityConstants.IA_HMAC_MD5_128 },
			{ SecurityConstants.AA_RAKP_HMAC_MD5, SecurityConstants.CA_XRC4_40, SecurityConstants.IA_HMAC_MD5_128 },
			{ SecurityConstants.AA_RAKP_HMAC_MD5, SecurityConstants.CA_NONE, SecurityConstants.IA_MD5_128 },
			{ SecurityConstants.AA_RAKP_HMAC_MD5, SecurityConstants.CA_AES_CBC128, SecurityConstants.IA_MD5_128 },
			{ SecurityConstants.AA_RAKP_HMAC_MD5, SecurityConstants.CA_XRC4_128, SecurityConstants.IA_MD5_128 },
			{ SecurityConstants.AA_RAKP_HMAC_MD5, SecurityConstants.CA_XRC4_40, SecurityConstants.IA_MD5_128 },
			{ SecurityConstants.AA_RAKP_HMAC_SHA256, SecurityConstants.CA_NONE, SecurityConstants.IA_NONE },
			{ SecurityConstants.AA_RAKP_HMAC_SHA256, SecurityConstants.CA_NONE, SecurityConstants.IA_HMAC_SHA256_128 },
			{ SecurityConstants.AA_RAKP_HMAC_SHA256, SecurityConstants.CA_AES_CBC128, SecurityConstants.IA_HMAC_SHA256_128 },
			{ SecurityConstants.AA_RAKP_HMAC_SHA256, SecurityConstants.CA_XRC4_128, SecurityConstants.IA_HMAC_SHA256_128 },
			{ SecurityConstants.AA_RAKP_HMAC_SHA256, SecurityConstants.CA_XRC4_40, SecurityConstants.IA_HMAC_SHA256_128 } };

	private byte id;

	private byte authenticationAlgorithm;
	private byte integrityAlgorithm;
	private byte confidentialityAlgorithm;

	private AuthenticationAlgorithm aa;
	private ConfidentialityAlgorithm ca;
	private IntegrityAlgorithm ia;

	/**
	 * @return the ID of the cipher suite (IPMI 2.0 table 22-20)
	 */
	public byte getId() {
		return id;
	}

	/**
	 * Creates a cipher suite.
	 *
	 * @param id the ID of the cipher suite
	 * @param authenticationAlgorithm the code of the authentication algorithm ({@link SecurityConstants})
	 * @param confidentialityAlgorithm the code of the confidentiality algorithm
	 * @param integrityAlgorithm the code of the integrity algorithm
	 */
	public CipherSuite(byte id, byte authenticationAlgorithm,
			byte confidentialityAlgorithm, byte integrityAlgorithm) {
		this.id = id;
		this.authenticationAlgorithm = authenticationAlgorithm;
		this.confidentialityAlgorithm = confidentialityAlgorithm;
		this.integrityAlgorithm = integrityAlgorithm;
	}

	/**
	 * Initializes algorithms contained in this {@link CipherSuite}.
	 *
	 * @param sik
	 *        - Session Integrity Key calculated during the opening of the
	 *        session or user password if 'one-key' logins are enabled.
	 * @throws IllegalArgumentException
	 *         - when an algorithm of the suite is not implemented
	 * @throws InvalidKeyException
	 *         - when initiation of the algorithm fails
	 * @throws NoSuchAlgorithmException
	 *         - when initiation of the algorithm fails
	 * @throws NoSuchPaddingException
	 *         - when initiation of the algorithm fails
	 */
	public void initializeAlgorithms(byte[] sik)
			throws InvalidKeyException,
			NoSuchAlgorithmException,
			NoSuchPaddingException {
		getIntegrityAlgorithm().initialize(sik);
		getConfidentialityAlgorithm().initialize(sik, getAuthenticationAlgorithm());
	}

	/**
	 * Returns instance of AuthenticationAlgorithm class.
	 *
	 * @return the authentication algorithm of the suite
	 * @throws IllegalArgumentException when authentication algorithm code is
	 *         incorrect.
	 */
	public AuthenticationAlgorithm getAuthenticationAlgorithm() {
		if (aa != null && aa.getCode() != authenticationAlgorithm) {
			throw new IllegalArgumentException("Invalid authentication algorithm code");
		}
		switch (authenticationAlgorithm) {
		case SecurityConstants.AA_RAKP_NONE:
			return instantiateAuthenticationAlgorithm(AuthenticationRakpNone::new);
		case SecurityConstants.AA_RAKP_HMAC_SHA1:
			return instantiateAuthenticationAlgorithm(AuthenticationRakpHmacSha1::new);
		case SecurityConstants.AA_RAKP_HMAC_MD5:
			return instantiateAuthenticationAlgorithm(AuthenticationRakpHmacMd5::new);
		case SecurityConstants.AA_RAKP_HMAC_SHA256:
			return instantiateAuthenticationAlgorithm(AuthenticationRakpHmacSha256::new);
		default:
			throw new IllegalArgumentException("Invalid authentication algorithm.");
		}
	}

	/**
	 * Returns instance of IntegrityAlgorithm class.
	 *
	 * @return the integrity algorithm of the suite
	 * @throws IllegalArgumentException when integrity algorithm code is incorrect.
	 */
	public IntegrityAlgorithm getIntegrityAlgorithm() {
		if (ia != null && ia.getCode() != integrityAlgorithm) {
			throw new IllegalArgumentException("Invalid integrity algorithm code");
		}
		switch (integrityAlgorithm) {
		case SecurityConstants.IA_NONE:
			return instantiateIntegrityAlgorithm(IntegrityNone::new);
		case SecurityConstants.IA_HMAC_SHA1_96:
			return instantiateIntegrityAlgorithm(IntegrityHmacSha1_96::new);
		case SecurityConstants.IA_MD5_128:
			// TODO: MD5-128
			throw new IllegalArgumentException("Integrity algorithm MD5-128 is not yet implemented.");
		case SecurityConstants.IA_HMAC_MD5_128:
			return instantiateIntegrityAlgorithm(IntegrityHmacMd5_128::new);
		case SecurityConstants.IA_HMAC_SHA256_128:
			return instantiateIntegrityAlgorithm(IntegrityHmacSha256_128::new);
		default:
			throw new IllegalArgumentException("Invalid integrity algorithm.");
		}
	}

	/**
	 * Returns instance of ConfidentialityAlgorithm class.
	 *
	 * @return the confidentiality algorithm of the suite
	 * @throws IllegalArgumentException
	 *         when confidentiality algorithm code is incorrect.
	 */
	public ConfidentialityAlgorithm getConfidentialityAlgorithm() {
		if (ca != null && ca.getCode() != confidentialityAlgorithm) {
			throw new IllegalArgumentException(
					"Invalid confidentiality algorithm code");
		}
		switch (confidentialityAlgorithm) {
		case SecurityConstants.CA_NONE:
			if (ca == null) {
				ca = new ConfidentialityNone();
			}
			return ca;
		case SecurityConstants.CA_AES_CBC128:
			if (ca == null) {
				ca = new ConfidentialityAesCbc128();
			}
			return ca;
		case SecurityConstants.CA_XRC4_40:
			// TODO: XRc4-40
			throw new IllegalArgumentException("Confidentiality algorithm XRC4-40 is not yet implemented.");
		case SecurityConstants.CA_XRC4_128:
			// TODO: XRc4-128
			throw new IllegalArgumentException("Confidentiality algorithm XRC4-128 is not yet implemented.");
		default:
			throw new IllegalArgumentException(
					"Invalid confidentiality algorithm.");

		}
	}

	/**
	 * Builds Cipher Suites collection from raw data received by
	 * {@link GetChannelCipherSuites} commands. Cannot be executed in
	 * {@link GetChannelCipherSuitesResponseData} since data comes in 16-byte
	 * packets and is fragmented.
	 * <p>
	 * A standard record (IPMI 2.0 table 22-19) is C0h, the suite ID, then the algorithm tags. The algorithms of the
	 * standard suites 0 to 19 are those of IPMI 2.0 table 22-20, whatever the tags say, as in ipmitool and FreeIPMI:
	 * the records come unauthenticated, and a suite 17 whose tags were stripped must not become a suite without
	 * integrity. OEM records (C1h, the OEM suite ID, a 3-byte IANA, then the tags), whose suite IDs are vendor-specific,
	 * records with a reserved suite ID, and malformed or truncated records are skipped.
	 * </p>
	 *
	 * @param bytes
	 *        - concatenated Cipher Suite Records received by
	 *        {@link GetChannelCipherSuites} commands.
	 * @return list of the standard Cipher Suites offered by BMC, in the BMC's order (including suites whose
	 *         algorithms this library does not implement, see {@link #isSupported()})
	 */
	public static List<CipherSuite> getCipherSuites(byte[] bytes) {
		ArrayList<CipherSuite> suites = new ArrayList<CipherSuite>();

		int offset = 0;

		while (offset < bytes.length) {
			int start = TypeConverter.byteToInt(bytes[offset]);
			if (start != 0xC0 && start != 0xC1) {
				++offset; // not the start of a record: resynchronize on the next one
				continue;
			}
			int tags = offset + (start == 0xC0 ? 2 : 5);
			if (tags >= bytes.length) {
				break; // truncated record
			}
			int id = TypeConverter.byteToInt(bytes[offset + 1]);
			// The tags end at the next record: their bits 7:6 are never 11b
			offset = tags;
			while (offset < bytes.length && (bytes[offset] & 0xC0) != 0xC0) {
				++offset;
			}
			if (start == 0xC0 && id < STANDARD_SUITES.length) {
				byte[] algorithms = STANDARD_SUITES[id];
				suites.add(new CipherSuite((byte) id, algorithms[0], algorithms[1], algorithms[2]));
			}
		}

		return suites;
	}

	/**
	 * Tells whether this library implements the algorithms of this suite. The suites listed by
	 * {@link #getCipherSuites(byte[])} include the ones it does not implement (MD5-128 integrity, xRC4
	 * confidentiality), which fail when the session is opened.
	 *
	 * @return true when the authentication, integrity and confidentiality algorithms are all implemented
	 */
	public boolean isSupported() {
		try {
			getAuthenticationAlgorithm();
			getIntegrityAlgorithm();
			getConfidentialityAlgorithm();
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * @return {@link CipherSuite} with algorithms set to
	 *         {@link AuthenticationRakpNone}, {@link ConfidentialityNone} and
	 *         {@link IntegrityNone}.
	 */
	public static CipherSuite getEmpty() {
		return new CipherSuite((byte) 0, (byte) 0, (byte) 0, (byte) 0);
	}

	/**
	 * Creates an instance of AuthenticationAlgorithm.
	 *
	 * @param supplier constructor of the algorithm
	 */
	private AuthenticationAlgorithm instantiateAuthenticationAlgorithm(
			final Supplier<AuthenticationAlgorithm> constructor) {
		if (aa == null) {
			aa = constructor.get();
		}
		return aa;
	}

	/**
	 * Creates an instance of IntegrityAlgorithm.
	 *
	 * @param supplier constructor of the algorithm
	 */
	private IntegrityAlgorithm instantiateIntegrityAlgorithm(final Supplier<IntegrityAlgorithm> constructor) {
		if (ia == null) {
			ia = constructor.get();
		}
		return ia;
	}

	/**
	 * Constructs a Mac object that implements the given MAC algorithm.
	 *
	 * @param algorithmName the name of the algorithm to use
	 * @return The Mac object that implements the specified MAC algorithm.
	 */
	public static Mac newMacInstance(final String algorithmName) {
		if (algorithmName == null || algorithmName.trim().isEmpty()) {
			return null;
		}
		try {
			return Mac.getInstance(algorithmName);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException("Algorithm " + algorithmName + " is not available", e);
		}
	}
}
