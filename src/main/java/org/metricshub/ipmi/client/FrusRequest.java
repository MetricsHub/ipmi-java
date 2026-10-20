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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Reads the FRU inventories of the BMC: those that the FRU Device Locator records of the SDR repository point at,
 * and the one of the system board.
 */
public final class FrusRequest {

	private final IpmiClient client;
	private Set<Integer> ids;
	private Duration timeout;

	FrusRequest(IpmiClient client) {
		this.client = client;
	}

	/**
	 * Reads only the FRUs with the given device IDs (0 is the system board on most BMCs), on every controller and LUN:
	 * a device ID is unique per controller and LUN only. The SDR repository is read all the same. Without argument, it
	 * reads none. A later call replaces the IDs.
	 *
	 * @param deviceIds the FRU device IDs to read
	 * @return this request
	 */
	public FrusRequest ids(int... deviceIds) {
		Objects.requireNonNull(deviceIds, "deviceIds");
		this.ids = new HashSet<Integer>();
		for (int id : deviceIds) {
			this.ids.add(id);
		}
		return this;
	}

	/**
	 * Overrides the deadline of the client for this request.
	 *
	 * @param deadline the time the request may take, session setup included
	 * @return this request
	 * @throws IllegalArgumentException when the duration is shorter than one millisecond or longer than
	 *         {@link Integer#MAX_VALUE} milliseconds
	 */
	public FrusRequest timeout(Duration deadline) {
		this.timeout = IpmiClient.checkTimeout(deadline, "timeout");
		return this;
	}

	/**
	 * Reads the selected FRUs.
	 *
	 * @return the FRUs, in the order of their locator records; a FRU that could not be read entirely is returned
	 *         with its problems
	 * @throws IpmiClientException when the session cannot be opened, the SDR repository cannot be read, the BMC stops
	 *         answering, or the deadline expires
	 */
	public FruList execute() {
		return client.readFrus(ids, timeout);
	}
}
