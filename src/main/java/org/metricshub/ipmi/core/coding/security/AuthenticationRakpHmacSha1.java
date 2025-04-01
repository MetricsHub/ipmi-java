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
 * RAKP-HMAC-SHA1 authentication algorithm.
 */
public class AuthenticationRakpHmacSha1 extends AuthenticationAlgorithm {
	
	private static final String ALGORITHM_NAME = "HmacSHA1";
	
	/**
	 * Initiates RAKP-HMAC-SHA1 authentication algorithm.
	 */
	public AuthenticationRakpHmacSha1() {
		super(ALGORITHM_NAME);
	}

	@Override
	public byte getCode() {
		return SecurityConstants.AA_RAKP_HMAC_SHA1;
	}

	@Override
	public int getKeyLength() {
		return 20;
	}

	@Override
	public int getIntegrityCheckBaseLength() {
		return 12;
	}

	@Override
	public String getAlgorithmName() {
		return ALGORITHM_NAME;
	}

}
