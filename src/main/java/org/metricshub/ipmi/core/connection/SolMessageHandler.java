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

    private static final Logger logger = LoggerFactory.getLogger(SolMessageHandler.class);

    public SolMessageHandler(Connection connection, int timeout) throws IOException {
        super(connection, timeout, SolMessage.MIN_SEQUENCE_NUMBER, SolMessage.MAX_SEQUENCE_NUMBER);
    }

    /**
     * Assuming that given message is SOL message, reads both data and acknowledge information from it,
     * notifying registered listeners about incoming data.
     *
     * @param message
     *          received IPMI message.
     */
    @Override
    protected void handleIncomingMessageInternal(Ipmiv20Message message) {
        SolInboundMessage payload = (SolInboundMessage) message.getPayload();

        if (payload.isAcknowledgeMessage()) {
            handleIncomingAcknowledgeMessage(message, payload);
        }

        if (payload.isDataCarrier()) {
            handleIncomingDataMessage(payload);
        }
    }

    private void handleIncomingAcknowledgeMessage(Ipmiv20Message message, SolInboundMessage payload) {
        PayloadCoder coder = messageQueue.getMessageFromQueue(payload.getAckNackSequenceNumber());
        int tag = payload.getAckNackSequenceNumber();

        logger.debug("Received message with tag " + tag);

        if (coder == null) {
            logger.debug("No message tagged with " + tag + " in queue. Dropping orphan message.");
            return;
        }

        try {
            ResponseData responseData = coder.getResponseData(message);
            connection.notifyResponseListeners(connection.getHandle(), tag, responseData, null);
        } catch (Exception e) {
            connection.notifyResponseListeners(connection.getHandle(), tag, null, e);
        }

        // Remove message from queue only when it was fully or partially acknowledged. Otherwise, we want to keep it in queue for further retries
        if (payload.getStatusField().getAckState() == SolAckState.ACK || payload.getAcceptedCharacterCount() > 0) {
            messageQueue.remove(payload.getAckNackSequenceNumber());
        }
    }

    private void handleIncomingDataMessage(SolInboundMessage payload) {
        connection.notifyRequestListeners(payload);
    }

}
