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
 * The connection to the BMC failed for a network reason: unknown host, socket error, no reply to the session setup
 * after all the tries (wrong host, UDP port filtered, IPMI over LAN disabled, an IPMI 1.5-only BMC with
 * {@link IpmiClient.Builder#cipherSuite(int)}), or the connection closed while an operation ran, which loses the
 * session. Also thrown when the session of a Serial over LAN console that the BMC serves on another UDP port cannot be
 * opened.
 */
public class IpmiConnectionException extends IpmiClientException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates the exception.
	 *
	 * @param message what failed
	 * @param cause the failure of the protocol core
	 */
	public IpmiConnectionException(String message, Throwable cause) {
		super(message, cause);
	}
}
