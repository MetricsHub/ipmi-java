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

/**
 * RAKP-None authentication algorithm.
 */
public class AuthenticationRakpNone extends AuthenticationAlgorithm {

	/**
     * Constructs an instance of the RAKP-None authentication algorithm.
     */
	public AuthenticationRakpNone() {
		super("");
	}

	@Override
	public byte getCode() {
		return SecurityConstants.AA_RAKP_NONE;
	}

	/**
	 * Checks value of the Key Exchange Authentication Code in RAKP messages using
	 * the RAKP-None algorithm.
	 */
	@Override
	public boolean checkKeyExchangeAuthenticationCode(byte[] data, byte[] key, String password) {
		return true;
	}

	/**
	 * Calculates value of the Key Exchange Authentication Code in RAKP messages
	 * using the RAKP-None algorithm.
	 */
	@Override
	public byte[] getKeyExchangeAuthenticationCode(byte[] data, String password) {
		return new byte[0];
	}

	/**
	 * Performs Integrity Check in RAKP 4 message using the RAKP-None algorithm.
	 */
	@Override
	public boolean doIntegrityCheck(byte[] data, byte[] reference, byte[] sik) {
		return true;
	}

	@Override
	public int getKeyLength() {
		return 0;
	}

	@Override
	public int getIntegrityCheckBaseLength() {
		return 0;
	}

	@Override
	public String getAlgorithmName() {
		return "";
	}
}
