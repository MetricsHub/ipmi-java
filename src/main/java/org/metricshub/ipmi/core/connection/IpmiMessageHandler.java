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

import org.metricshub.ipmi.core.coding.PayloadCoder;
import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.commands.payload.ActivatePayload;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanMessage;
import org.metricshub.ipmi.core.coding.payload.sol.SolMessage;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv20Message;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.connection.queue.QueueElement;

import java.io.IOException;

/**
 * Default implementation of {@link MessageHandler} for {@link IpmiLanMessage}s.
 */
public class IpmiMessageHandler extends MessageHandler {

	public IpmiMessageHandler(Connection connection, int timeout) throws IOException {
		// The tags start above the SOL packet sequence numbers (1 to 15): the replies of both payload types are reported
		// to the same listeners with their connection and tag only, which must not be ambiguous
		super(connection, timeout, SolMessage.MAX_SEQUENCE_NUMBER + 1, IpmiLanMessage.MAX_SEQUENCE_NUMBER);
	}

	/**
	 * If message is of type {@link IpmiLanMessage}, finds corresponding request message and extracts response data from
	 * it,
	 * using data carried in this message.
	 *
	 * @param message
	 *        received IPMI message
	 */
	@Override
	protected void handleIncomingMessageInternal(Ipmiv20Message message) {
		if (message.getPayload() instanceof IpmiLanMessage) {
			IpmiLanMessage lanMessagePayload = (IpmiLanMessage) message.getPayload();

			int tag = lanMessagePayload.getSequenceNumber();
			QueueElement element = getMessageQueue().getElement(tag);
			PayloadCoder coder = element == null ? null : element.getRequest();

			if (coder == null) {
				return;
			}

			// A late reply to a request that timed out may carry the tag of a newer queued request: the reply of a
			// request answers the command of that request, and the request stays queued
			if (coder instanceof IpmiCommandCoder && !((IpmiCommandCoder<?>) coder).answers(lanMessagePayload)) {
				return;
			}

			// Out of the queue before the listeners hear of the reply, so that the queue cannot report the request as timed
			// out or failed afterwards; when it did so already, the reply comes too late. Nobody waits for the reply of a
			// one-way message (the keep-alive): it only frees the tag
			if (!getMessageQueue().remove(element) || element.isOneWay()) {
				return;
			}

			try {
				ResponseData responseData = coder.getResponseData(message);
				if (coder instanceof ActivatePayload && ((ActivatePayload) coder).getPayloadType() == PayloadType.Sol) {
					// A new activation, confirmed: its SOL packets are numbered from 1 again, before the console sends any
					getConnection().resetSolPacketNumbers();
				}
				getConnection().notifyResponseListeners(getConnection().getHandle(), tag, responseData, null);
			} catch (Exception e) {
				getConnection().notifyResponseListeners(getConnection().getHandle(), tag, null, e);
			}
		}
	}

}
