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
import org.metricshub.ipmi.core.coding.sol.SolCoder;
import org.metricshub.ipmi.core.connection.queue.QueueElement;

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
	 * The sequence number of the last packet of the BMC with one, and the number of the packet it acknowledged: a packet
	 * the BMC sends again has both.
	 */
	private int lastInboundSequenceNumber;
	private int lastInboundAcknowledgement;

	public SolMessageHandler(Connection connection, int timeout) throws IOException {
		super(connection, timeout, SolMessage.MIN_SEQUENCE_NUMBER, SolMessage.MAX_SEQUENCE_NUMBER);
	}

	/**
	 * The ACK-only packets go out with packet sequence number 0 and the BMC never acknowledges them (IPMI 2.0 section
	 * 15.9): they are neither queued nor numbered, so that they hold no slot of the window and leave the packet sequence
	 * numbers to the packets the BMC acknowledges, which then follow each other. The other one-way packets (the
	 * operations, whose acknowledgement nobody waits for) are numbered and hold their number until it comes or times
	 * out. A packet sent again after a timeout keeps its packet sequence number, as table 15-2 requires: the BMC takes it
	 * for the retry it is, and does not pass its characters to the server a second time.
	 */
	@Override
	protected synchronized int takeTag(PayloadCoder payloadCoder, boolean isOneWay) {
		if (isOneWay && payloadCoder instanceof SolCoder && ((SolCoder) payloadCoder).isAcknowledgeOnly()) {
			// Any tag lets the packet go: SolCoder sends 0 as the sequence number of an ACK-only packet
			return SolMessage.MIN_SEQUENCE_NUMBER;
		}
		if (payloadCoder == lastPacket && lastTag > 0) {
			int tag = getMessageQueue().add(payloadCoder, isOneWay, lastTag);
			if (tag > 0) {
				return tag;
			}
		}
		lastTag = super.takeTag(payloadCoder, isOneWay);
		lastPacket = payloadCoder;
		return lastTag;
	}

	/**
	 * Numbers the packets from 1 again, both ways, as a new activation of the payload does (ipmitool, OpenBMC).
	 */
	public synchronized void resetPacketNumbers() {
		getMessageQueue().resetSequenceNumber();
		lastPacket = null;
		lastTag = 0;
		lastInboundSequenceNumber = 0;
		lastInboundAcknowledgement = 0;
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
		boolean retry = isRetry(payload);
		boolean acknowledgement = payload.isAcknowledgeMessage()
				&& !retry
				&& handleIncomingAcknowledgeMessage(message, payload);

		// Characters, or statuses that no packet of the console waits for (an informational packet: SOL deactivated)
		if (payload.isDataCarrier() || !acknowledgement && !payload.getStatusField().getStatuses().isEmpty()) {
			handleIncomingDataMessage(payload);
		}
	}

	/**
	 * Tells whether the BMC sends its last packet again (the same non-zero sequence number and the same acknowledgement:
	 * number, ACK or NACK, and count, IPMI 2.0 table 15-2), and remembers the packet.
	 */
	private synchronized boolean isRetry(SolInboundMessage payload) {
		int sequenceNumber = payload.getSequenceNumber();
		if (sequenceNumber == 0) {
			return false;
		}
		int acknowledgement = payload.getAckNackSequenceNumber() << 16
				| (payload.getStatusField().getAckState() == SolAckState.ACK ? 0 : 1 << 8)
				| payload.getAcceptedCharacterCount();
		boolean retry = sequenceNumber == lastInboundSequenceNumber && acknowledgement == lastInboundAcknowledgement;
		lastInboundSequenceNumber = sequenceNumber;
		lastInboundAcknowledgement = acknowledgement;
		return retry;
	}

	/**
	 * Reports the acknowledgement of a queued packet, after taking the packet out of the queue when the BMC accepted
	 * characters: the queue must not report it again, as timed out or failed, meanwhile.
	 *
	 * @return whether a queued packet was acknowledged
	 */
	private boolean handleIncomingAcknowledgeMessage(Ipmiv20Message message, SolInboundMessage payload) {
		int tag = payload.getAckNackSequenceNumber();
		QueueElement element = getMessageQueue().getElement(tag);
		PayloadCoder coder = element == null ? null : element.getRequest();

		LOGGER.debug("Received message with tag " + tag);

		if (coder == null) {
			LOGGER.debug("No message tagged with " + tag + " in queue. Dropping orphan message.");
			return false;
		}

		// The packet leaves the queue when the BMC took it, wholly or partly; a NACKed one stays, to be sent again. Only
		// this element is removed: a packet sent again under the same number is another one
		if (element.isOneWay()
				|| payload.getStatusField().getAckState() == SolAckState.ACK
				|| payload.getAcceptedCharacterCount() > 0) {
			if (!getMessageQueue().remove(element)) {
				// The packet timed out meanwhile: the acknowledgement is that of the packet sent again under its number,
				// if any
				element = getMessageQueue().getElement(tag);
				if (element == null || element.getRequest() != coder || !getMessageQueue().remove(element)) {
					return false;
				}
			}
		} else if (getMessageQueue().getElement(tag) != element) {
			// The NACK of a packet that timed out meanwhile
			return false;
		}

		// Nobody waits for the acknowledgement of the operations
		if (!element.isOneWay()) {
			try {
				ResponseData responseData = coder.getResponseData(message);
				getConnection().notifyResponseListeners(getConnection().getHandle(), tag, responseData, null);
			} catch (Exception e) {
				getConnection().notifyResponseListeners(getConnection().getHandle(), tag, null, e);
			}
		}
		return true;
	}

	private void handleIncomingDataMessage(SolInboundMessage payload) {
		getConnection().notifyRequestListeners(payload);
	}

}
