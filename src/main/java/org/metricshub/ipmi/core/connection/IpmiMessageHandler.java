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
import org.metricshub.ipmi.core.coding.commands.session.GetChannelAuthenticationCapabilities;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanMessage;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv20Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Default implementation of {@link MessageHandler} for {@link IpmiLanMessage}s.
 */
public class IpmiMessageHandler extends MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(IpmiMessageHandler.class);

    public IpmiMessageHandler(Connection connection, int timeout) throws IOException {
        super(connection, timeout, IpmiLanMessage.MIN_SEQUENCE_NUMBER, IpmiLanMessage.MAX_SEQUENCE_NUMBER);
    }

    /**
     * If message is of type {@link IpmiLanMessage}, finds corresponding request message and extracts response data from it,
     * using data carried in this message.
     *
     * @param message
     *          received IPMI message
     */
    @Override
    protected void handleIncomingMessageInternal(Ipmiv20Message message) {
        if (message.getPayload() instanceof IpmiLanMessage) {
            IpmiLanMessage lanMessagePayload = (IpmiLanMessage) message.getPayload();

            PayloadCoder coder = messageQueue.getMessageFromQueue(lanMessagePayload.getSequenceNumber());
            int tag = lanMessagePayload.getSequenceNumber();

            logger.debug("Received message with tag " + tag);

            if (coder == null) {
                logger.debug("No message tagged with " + tag
                        + " in queue. Dropping orphan message.");
                return;
            }

            if (coder.getClass() == GetChannelAuthenticationCapabilities.class) {
                messageQueue.remove(tag);
            } else {

                try {
                    ResponseData responseData = coder.getResponseData(message);
                    connection.notifyResponseListeners(connection.getHandle(), tag, responseData, null);
                } catch (Exception e) {
                    connection.notifyResponseListeners(connection.getHandle(), tag, null, e);
                }
                messageQueue.remove(lanMessagePayload.getSequenceNumber());
            }
        }
    }

}
