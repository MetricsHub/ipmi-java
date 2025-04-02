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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.metricshub.ipmi.core.coding.commands.session.Rakp1;

/**
 * Interface for authentication algorithms. All classes extending this one must
 * have a parameterless constructor.
 */
public abstract class AuthenticationAlgorithm {

	private final Mac mac;

	/**
	 * Constructs an authentication algorithm.
	 */
	protected AuthenticationAlgorithm(String algorithmName) {
		this(CipherSuite.newMacInstance(algorithmName));
	}

	/**
	 * Constructs an authentication algorithm with the provided MAC.
	 *
	 * @param mac the MAC instance to use
	 */
	private AuthenticationAlgorithm(Mac mac) {
		this.mac = mac;
	}

	/**
	 * @return algorithm-specific code
	 */
	public abstract byte getCode();

	/**
	 * @return length of the key for the RAKP2 message
	 */
	public abstract int getKeyLength();

	/**
	 * @return length of the integrity check base for RAKP4 message
	 */
	public abstract int getIntegrityCheckBaseLength();

	/**
	 * Checks value of the Key Exchange Authentication Code in RAKP messages
	 *
	 * @param data     - The base for authentication algorithm. Depends on RAKP
	 *                 Message.
	 * @param key      - the Key Exchange Authentication Code to check.
	 * @param password - password of the user establishing a session
	 * @return True if authentication check was successful, false otherwise.
	 * @throws NoSuchAlgorithmException when initiation of the algorithm fails
	 * @throws InvalidKeyException      when creating of the algorithm key fails
	 */
	public boolean checkKeyExchangeAuthenticationCode(byte[] data, byte[] key, String password)
			throws NoSuchAlgorithmException, InvalidKeyException {
		byte[] check = getKeyExchangeAuthenticationCode(data, password);
		return Arrays.equals(check, key);
	}

	/**
	 * Calculates value of the Key Exchange Authentication Code in RAKP messages
	 *
	 * @param data     - The base for authentication algorithm. Depends on RAKP
	 *                 Message.
	 * @param password - password of the user establishing a session
	 * @throws NoSuchAlgorithmException when initiation of the algorithm fails
	 * @throws InvalidKeyException      when creating of the algorithm key fails
	 */
	public byte[] getKeyExchangeAuthenticationCode(byte[] data, String password)
			throws NoSuchAlgorithmException, InvalidKeyException {

		final byte[] key = password.getBytes();

		SecretKeySpec sKey = new SecretKeySpec(key, getAlgorithmName());
		mac.init(sKey);

		return mac.doFinal(data);
	}

	/**
	 * Validates Integrity Check Value in RAKP Message 4.
	 *
	 * @param data      - The base for authentication algorithm.
	 * @param reference - The Integrity Check Value to validate.
	 * @param sik       - The Session Integrity Key generated on base of RAKP
	 *                  Messages 1 and 2.
	 * @see Rakp1#calculateSik(org.metricshub.ipmi.core.coding.commands.session.Rakp1ResponseData)
	 * @return True if integrity check was successful, false otherwise.
	 * @throws NoSuchAlgorithmException when initiation of the algorithm fails
	 * @throws InvalidKeyException      when creating of the algorithm key fails
	 */
	public boolean doIntegrityCheck(byte[] data, byte[] reference, byte[] sik)
			throws InvalidKeyException, NoSuchAlgorithmException {

		SecretKeySpec sKey = new SecretKeySpec(sik, getAlgorithmName());
		mac.init(sKey);
		
		final int integrityCheckLength = getIntegrityCheckBaseLength();
		final byte[] result = new byte[integrityCheckLength];

		System.arraycopy(mac.doFinal(data), 0, result, 0, integrityCheckLength);

		return Arrays.equals(result, reference);
	}

	/**
	 * @return the name of the algorithm as a {@code String}.
	 */
	public abstract String getAlgorithmName();
}
