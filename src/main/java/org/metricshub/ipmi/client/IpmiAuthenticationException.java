package org.metricshub.ipmi.client;

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

import org.metricshub.ipmi.core.coding.payload.CompletionCode;

/**
 * The BMC refused the login: unknown user name, wrong password or BMC key, role or privilege refused.
 */
public class IpmiAuthenticationException extends IpmiClientException {

	private static final long serialVersionUID = 1L;

	/** The status code the BMC sent, null when the console side rejected the login. */
	private final CompletionCode statusCode;

	/**
	 * Creates the exception for a login the console side rejected (the password or BMC key does not match).
	 *
	 * @param message what failed
	 * @param cause the failure of the protocol core
	 */
	public IpmiAuthenticationException(String message, Throwable cause) {
		this(message, null, cause);
	}

	/**
	 * Creates the exception for a login the BMC rejected with a status code.
	 *
	 * @param message what failed
	 * @param statusCode the RAKP status code or completion code the BMC sent, null when it sent none
	 * @param cause the failure of the protocol core
	 */
	public IpmiAuthenticationException(String message, CompletionCode statusCode, Throwable cause) {
		super(message, cause);
		this.statusCode = statusCode;
	}

	/**
	 * Returns the status code the BMC sent when it refused the login: the status code of its Open Session or RAKP
	 * reply (IPMI 2.0 table 13-15: unauthorized name, unauthorized role, invalid integrity check value...), or the
	 * completion code of the Set Session Privilege Level it refused right after the login.
	 *
	 * @return the status code: the Open Session or RAKP status code of a refused login, the completion code of a
	 *         refused Set Session Privilege Level ({@link CompletionCode#Unknown} for its {@code 80h} and {@code 81h},
	 *         {@code InsufficientPrivilege} for {@code D4h}), or null when the login failed on the console side, which
	 *         checks the key exchange codes of the BMC against the password and the BMC key
	 */
	public CompletionCode statusCode() {
		return statusCode;
	}
}
