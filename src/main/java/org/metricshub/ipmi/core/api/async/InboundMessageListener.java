package org.metricshub.ipmi.core.api.async;

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

import org.metricshub.ipmi.core.coding.payload.IpmiPayload;

/**
 * Interface for incoming payloads.
 */
public interface InboundMessageListener {

	/**
	 * Checks if given payload is supported by this {@link InboundMessageListener} instance.
	 * This method should be called prior to invoking {@link InboundMessageListener#notify(IpmiPayload)}.
	 *
	 * @param payload
	 *        {@link IpmiPayload} instance to check
	 * @return true if payload is supported by this object, false otherwise
	 */
	boolean isPayloadSupported(IpmiPayload payload);

	/**
	 * Checks if given payload, received on the connection with the given handle, is for this
	 * {@link InboundMessageListener}. The connector calls this method; by default, the listener takes the payloads of
	 * every connection that {@link #isPayloadSupported(IpmiPayload)} supports.
	 *
	 * @param payload
	 *        {@link IpmiPayload} instance to check
	 * @param handle
	 *        the id of the connection that received the payload ({@link ConnectionHandle#getHandle()})
	 * @return true if this object handles the payload, false otherwise
	 */
	default boolean isPayloadSupported(IpmiPayload payload, int handle) {
		return isPayloadSupported(payload);
	}

	/**
	 * Notify listener about received inbound message.
	 * This method should be invoked only with payload for which
	 * {@link InboundMessageListener#isPayloadSupported(IpmiPayload)} returned true.
	 *
	 * @param payload
	 *        payload extracted from inbound message
	 */
	void notify(IpmiPayload payload);

}
