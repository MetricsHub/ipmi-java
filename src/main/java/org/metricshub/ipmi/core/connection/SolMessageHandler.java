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
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.payload.sol.SolAckState;
import org.metricshub.ipmi.core.coding.payload.sol.SolInboundMessage;
import org.metricshub.ipmi.core.coding.payload.sol.SolMessage;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv20Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Implementation of {@link MessageHandler} for {@link org.metricshub.ipmi.core.coding.protocol.PayloadType#Sol}.
 */
public class SolMessageHandler extends MessageHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(SolMessageHandler.class);

	/**
	 * The last packet queued, and its tag, which is its packet sequence number.
	 */
	private PayloadCoder lastPacket;
	private int lastTag;

	/**
	 * The sequence number of the last packet of the BMC with one, read on the receiving thread only.
	 */
	private int lastInboundSequenceNumber;

	public SolMessageHandler(Connection connection, int timeout) throws IOException {
		super(connection, timeout, SolMessage.MIN_SEQUENCE_NUMBER, SolMessage.MAX_SEQUENCE_NUMBER);
	}

	/**
	 * The one-way SOL messages are the ACK-only packets, which go out with packet sequence number 0 and which the BMC
	 * never acknowledges (IPMI 2.0 section 15.9): they are neither queued nor numbered, so that they hold no slot of the
	 * window and leave the packet sequence numbers to the packets the BMC acknowledges, which then follow each other.
	 * A packet sent again after a timeout keeps its packet sequence number, as table 15-2 requires: the BMC takes it for
	 * the retry it is, and does not pass its characters to the server a second time.
	 */
	@Override
	protected synchronized int takeTag(PayloadCoder payloadCoder, boolean isOneWay) {
		if (isOneWay) {
			// Any tag lets the packet go: SolCoder sends 0 as the sequence number of an ACK-only packet
			return SolMessage.MIN_SEQUENCE_NUMBER;
		}
		if (payloadCoder == lastPacket) {
			int tag = getMessageQueue().add(payloadCoder, false, lastTag);
			if (tag > 0) {
				return tag;
			}
		}
		lastTag = super.takeTag(payloadCoder, false);
		lastPacket = payloadCoder;
		return lastTag;
	}

	/**
	 * Assuming that given message is SOL message, reads both data and acknowledge information from it,
	 * notifying registered listeners about incoming data.
	 *
	 * @param message
	 *        received IPMI message.
	 */
	@Override
	protected void handleIncomingMessageInternal(Ipmiv20Message message) {
		SolInboundMessage payload = (SolInboundMessage) message.getPayload();

		// The ACK/NACK carried by a packet that the BMC sends again was applied with the first copy: applied again, it
		// could complete a newer packet that has the same number
		boolean acknowledgement = payload.isAcknowledgeMessage()
				&& !isRetry(payload)
				&& handleIncomingAcknowledgeMessage(message, payload);

		// Characters, or statuses that no packet of the console waits for (an informational packet: SOL deactivated)
		if (payload.isDataCarrier() || !acknowledgement && !payload.getStatusField().getStatuses().isEmpty()) {
			handleIncomingDataMessage(payload);
		}
	}

	/**
	 * Tells whether the BMC sends its last packet again (the same non-zero sequence number, IPMI 2.0 table 15-2), and
	 * remembers the number of the packet.
	 */
	private boolean isRetry(SolInboundMessage payload) {
		int sequenceNumber = payload.getSequenceNumber();
		if (sequenceNumber == 0) {
			return false;
		}
		boolean retry = sequenceNumber == lastInboundSequenceNumber;
		lastInboundSequenceNumber = sequenceNumber;
		return retry;
	}

	/**
	 * Reports the acknowledgement of a queued packet.
	 *
	 * @return whether a queued packet was acknowledged
	 */
	private boolean handleIncomingAcknowledgeMessage(Ipmiv20Message message, SolInboundMessage payload) {
		PayloadCoder coder = getMessageQueue().getMessageFromQueue(payload.getAckNackSequenceNumber());
		int tag = payload.getAckNackSequenceNumber();

		LOGGER.debug("Received message with tag " + tag);

		if (coder == null) {
			LOGGER.debug("No message tagged with " + tag + " in queue. Dropping orphan message.");
			return false;
		}

		try {
			ResponseData responseData = coder.getResponseData(message);
			getConnection().notifyResponseListeners(getConnection().getHandle(), tag, responseData, null);
		} catch (Exception e) {
			getConnection().notifyResponseListeners(getConnection().getHandle(), tag, null, e);
		}

		// Remove message from queue only when it was fully or partially acknowledged. Otherwise, we want to keep it in
		// queue for further retries
		if (payload.getStatusField().getAckState() == SolAckState.ACK || payload.getAcceptedCharacterCount() > 0) {
			getMessageQueue().remove(payload.getAckNackSequenceNumber());
		}
		return true;
	}

	private void handleIncomingDataMessage(SolInboundMessage payload) {
		getConnection().notifyRequestListeners(payload);
	}

}
