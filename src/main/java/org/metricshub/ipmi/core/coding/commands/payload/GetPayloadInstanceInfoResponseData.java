package org.metricshub.ipmi.core.coding.commands.payload;

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

import org.metricshub.ipmi.core.coding.commands.ResponseData;

/**
 * Response of the Get Payload Instance Info command (IPMI 2.0 table 24-7).
 */
public class GetPayloadInstanceInfoResponseData implements ResponseData {

	private final int sessionId;

	/**
	 * @param sessionId the managed system session ID of the session where the instance is active, 0 when inactive
	 */
	public GetPayloadInstanceInfoResponseData(int sessionId) {
		this.sessionId = sessionId;
	}

	/**
	 * @return the managed system session ID (the ID the BMC generated) of the session where the instance is active, or
	 *         0 when the instance is not active
	 */
	public int getSessionId() {
		return sessionId;
	}
}
