package org.metricshub.ipmi.core.connection;

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

import org.metricshub.ipmi.core.api.async.ConnectionHandle;

/**
 * Single opened IPMI session handle. It contains all information necessary to distinguish session among others.
 */
public class Session {

	private final int sessionId;
	private final int managedSystemSessionId;
	private final ConnectionHandle connectionHandle;

	public Session(int sessionId, ConnectionHandle connectionHandle) {
		this(sessionId, 0, connectionHandle);
	}

	/**
	 * @param sessionId the remote console session ID
	 * @param managedSystemSessionId the managed system session ID, which the BMC generated
	 * @param connectionHandle the connection of the session
	 */
	public Session(int sessionId, int managedSystemSessionId, ConnectionHandle connectionHandle) {
		this.sessionId = sessionId;
		this.managedSystemSessionId = managedSystemSessionId;
		this.connectionHandle = connectionHandle;
	}

	public int getSessionId() {
		return sessionId;
	}

	/**
	 * @return the managed system session ID, which the BMC generated and reports in its commands (Get Payload Instance
	 *         Info), or 0 when unknown
	 */
	public int getManagedSystemSessionId() {
		return managedSystemSessionId;
	}

	public ConnectionHandle getConnectionHandle() {
		return connectionHandle;
	}

}
