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

/**
 * Class representing RAKP-None integrity algorithm
 */
public class IntegrityNone extends IntegrityAlgorithm {

	/**
	 * Initiates the IntegrityNone algorithm
	 */
	public IntegrityNone() {
		super("");
	}

	@Override
	public void initialize(byte[] sik) throws InvalidKeyException {
		this.sik = sik;
	}

	@Override
	public byte getCode() {
		return SecurityConstants.IA_NONE;
	}

	@Override
	public byte[] generateAuthCode(byte[] base) {
		return null;
	}

	@Override
	public String getAlgorithmName() {
		return "";
	}

	@Override
	public int getAuthCodeLength() {
		return 0;
	}

}
