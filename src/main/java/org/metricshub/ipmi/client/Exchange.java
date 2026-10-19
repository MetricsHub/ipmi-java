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

import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.commands.ResponseData;

/**
 * Sends a command in the session of the client and returns its typed reply: what the SDR and FRU readers need from
 * the session, so that they can be exercised without one.
 */
interface Exchange {

	/**
	 * Sends the command and waits for its reply.
	 *
	 * @param <R> the type of the reply
	 * @param command the command
	 * @return the reply
	 * @throws IpmiCompletionCodeException when the BMC answers with an error completion code
	 * @throws IpmiTimeoutException when the BMC does not answer after all the tries
	 * @throws IpmiClientException when the command cannot be sent
	 */
	<R extends ResponseData> R send(IpmiCommandCoder<R> command);

	/**
	 * Tells whether the session is lost: the BMC stopped answering. A command forwarded by the BMC to another
	 * controller (Send Message) that got no reply of that controller does not lose the session.
	 *
	 * @return whether the BMC stopped answering
	 */
	boolean isLost();
}
