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

import java.time.Duration;

/**
 * Opens a Serial over LAN console (IPMI 2.0 section 15) in the session of the client or, when the BMC serves SOL on
 * another UDP port, in a session the client opens there with the same credentials, cipher suite and privilege. Most
 * BMCs require the Administrator privilege to activate the SOL payload: build the client with
 * {@code privilege(PrivilegeLevel.Administrator)}, or with {@code privilege(PrivilegeLevel.MaximumAvailable)}, which
 * raises the session to Operator, then to Administrator, as far as the account allows, when the BMC refuses the
 * activation for insufficient privilege.
 */
public final class SolRequest {

	private final IpmiClient client;
	private Duration timeout;

	SolRequest(IpmiClient client) {
		this.client = client;
	}

	/**
	 * Overrides the deadline of the client for the activation of the console.
	 *
	 * @param deadline the time the activation may take, session setup included
	 * @return this request
	 * @throws IllegalArgumentException when the duration is shorter than one millisecond or longer than
	 *         {@link Integer#MAX_VALUE} milliseconds
	 */
	public SolRequest timeout(Duration deadline) {
		this.timeout = IpmiClient.checkTimeout(deadline, "timeout");
		return this;
	}

	/**
	 * Activates the SOL payload and opens the console.
	 *
	 * @return the console, to close when done: closing it deactivates the payload
	 * @throws IpmiCompletionCodeException when the BMC refuses a command of the activation: {@code D4h} when the
	 *         session runs below the privilege the BMC requires for SOL, or a code of Activate Payload (IPMI 2.0 table
	 *         24-2)
	 * @throws IpmiAuthenticationException when the BMC refuses the login or the privilege of the client in its
	 *         session or, for a BMC that serves SOL on another UDP port, the privilege of the client in the session
	 *         there
	 * @throws IpmiConnectionException when the session of the client, or the one on the SOL port, cannot be
	 *         established
	 * @throws IpmiTimeoutException when the activation does not complete within the deadline, or the BMC stops
	 *         answering
	 * @throws IpmiClientException when every SOL payload instance is held by another live session, or the activation
	 *         fails otherwise
	 * @throws IllegalStateException when the client is closed, or was built without credentials
	 */
	public SolConsole open() {
		return client.openSol(timeout);
	}
}
