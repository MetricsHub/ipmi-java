package org.metricshub.ipmi.core.coding.security;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 MetricsHub
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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

// Name mirrors the IPMI 2.0 integrity algorithm name (MD5-128), like the HMAC-xxx-nn classes
// CHECKSTYLE.OFF: TypeName
/**
 * MD5-128 integrity algorithm (IPMI 2.0 section 13.28.4): a plain MD5 of the password around the message,
 * {@code AuthCode = MD5(password + AuthType/Format ... Next Header + password)}, where the password is the user's key
 * (Kuid) zero-padded to 20 bytes as the BMC stores it. Unlike the HMAC algorithms, it is not keyed with the Session
 * Integrity Key: {@link #initialize(byte[])} takes the password.
 */
public class IntegrityMd5_128 extends IntegrityAlgorithm {
	// CHECKSTYLE.ON: TypeName

	public static final String ALGORITHM_NAME = "MD5";

	private static final int AUTH_CODE_LENGTH = 16;

	private static final int PASSWORD_LENGTH = 20;

	private final MessageDigest md5;

	/**
	 * Initiates the MD5-128 integrity algorithm.
	 */
	public IntegrityMd5_128() {
		super("");
		try {
			md5 = MessageDigest.getInstance(ALGORITHM_NAME);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException("Algorithm " + ALGORITHM_NAME + " is not available", e);
		}
	}

	/**
	 * Keys the algorithm with the user's password (not the Session Integrity Key), zero-padded to 20 bytes.
	 *
	 * @param password the password of the user, as sent to the BMC; null for an empty password
	 */
	@Override
	public synchronized void initialize(byte[] password) {
		setSik(Arrays.copyOf(password == null ? new byte[0] : password, PASSWORD_LENGTH));
	}

	@Override
	public synchronized byte[] generateAuthCode(byte[] base) {
		byte[] password = getSik();
		if (password == null) {
			throw new NullPointerException("Algorithm not initialized.");
		}
		byte[] data = base[base.length - 2] == 0 ? injectIntegrityPad(base, AUTH_CODE_LENGTH) : base;
		md5.reset();
		md5.update(password);
		md5.update(data);
		md5.update(password);
		return md5.digest();
	}

	@Override
	public byte getCode() {
		return SecurityConstants.IA_MD5_128;
	}

	@Override
	public String getAlgorithmName() {
		return ALGORITHM_NAME;
	}

	@Override
	public int getAuthCodeLength() {
		return AUTH_CODE_LENGTH;
	}
}
