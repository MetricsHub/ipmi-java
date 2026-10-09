package org.metricshub.ipmi.core.common;

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

import java.security.SecureRandom;

/**
 * Utility class for generating the random numbers of the RMCP+ session setup (the console random number of RAKP
 * Message 1, the first console session ID), with a {@link SecureRandom}: a predictable console random number
 * weakens the session keys.
 */
public final class Randomizer {
	private static final SecureRandom RANDOM = new SecureRandom();

	private Randomizer() {}

	/**
	 * @return Generated random {@link Integer}
	 */
	public static int getInt() {
		return RANDOM.nextInt();
	}

	/**
	 * Generates random bytes.
	 *
	 * @param length the number of bytes
	 * @return a new array of {@code length} random bytes
	 */
	public static byte[] getBytes(int length) {
		byte[] bytes = new byte[length];
		RANDOM.nextBytes(bytes);
		return bytes;
	}
}
