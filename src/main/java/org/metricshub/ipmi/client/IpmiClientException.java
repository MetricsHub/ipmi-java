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

/**
 * A failure of an {@link IpmiClient} operation. The subclasses tell what to act on: the BMC refused the login
 * ({@link IpmiAuthenticationException}), the session could not be established for a network reason
 * ({@link IpmiConnectionException}), the operation or a message of the session got no reply in time
 * ({@link IpmiTimeoutException}), or the BMC answered a command with an error completion code
 * ({@link IpmiCompletionCodeException}). The base class covers the rest: no cipher suite in common, a BMC without IPMI
 * 2.0 (RMCP+), handshake replies that do not match the request, an interrupted operation (the interrupt status is
 * restored).
 * <p>
 * The checked exception of the protocol core that caused the failure, when there is one, is the
 * {@link #getCause()}.
 * </p>
 */
public class IpmiClientException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates the exception.
	 *
	 * @param message what failed
	 */
	public IpmiClientException(String message) {
		super(message);
	}

	/**
	 * Creates the exception.
	 *
	 * @param message what failed
	 * @param cause the failure of the protocol core
	 */
	public IpmiClientException(String message, Throwable cause) {
		super(message, cause);
	}
}
