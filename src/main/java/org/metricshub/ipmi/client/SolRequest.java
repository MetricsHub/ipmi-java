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
 * Opens a Serial over LAN console in the session of the client (IPMI 2.0 section 15). The BMC usually requires the
 * Administrator privilege to activate the SOL payload: open the client with
 * {@code privilege(PrivilegeLevel.Administrator)}.
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
	 */
	public SolRequest timeout(Duration deadline) {
		this.timeout = IpmiClient.checkTimeout(deadline, "timeout");
		return this;
	}

	/**
	 * Activates the SOL payload and opens the console.
	 *
	 * @return the console, to close when done: closing it deactivates the payload
	 * @throws IpmiCompletionCodeException when the BMC refuses the activation, for a lack of privilege for instance
	 * @throws IpmiClientException when the BMC offers no SOL payload instance, or the activation fails
	 */
	public SolConsole open() {
		return client.openSol(timeout);
	}
}
