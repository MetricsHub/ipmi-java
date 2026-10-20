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
 * The overall deadline of the operation expired, or a message of an established session got no reply after all the
 * tries. The session is then considered lost, and the next operation opens a new one, except when the deadline
 * expired while the operation was still queued behind those of other threads: the operation was never sent, and the
 * session is kept.
 * <p>
 * Two messages without reply keep the session: a command sent once
 * ({@link IpmiClient#chassisControl(org.metricshub.ipmi.core.coding.commands.chassis.PowerCommand)},
 * {@link IpmiClient#chassisIdentify(int)}, {@link IpmiClient#chassisIdentifyForever()}), where whether the BMC
 * carried the command out is unknown, and a request the BMC acknowledged and forwarded with Send Message to another
 * controller, which did not answer (it may be absent).
 * </p>
 */
public class IpmiTimeoutException extends IpmiClientException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates the exception.
	 *
	 * @param message what timed out
	 */
	public IpmiTimeoutException(String message) {
		super(message);
	}

	/**
	 * Creates the exception.
	 *
	 * @param message what timed out
	 * @param cause the failure of the protocol core
	 */
	public IpmiTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}
}
