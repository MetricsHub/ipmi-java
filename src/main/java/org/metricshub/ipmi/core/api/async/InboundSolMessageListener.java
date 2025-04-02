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

import org.metricshub.ipmi.core.api.sol.SolEventListener;
import org.metricshub.ipmi.core.api.sync.IpmiConnector;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.sol.SolAckState;
import org.metricshub.ipmi.core.coding.payload.sol.SolInboundMessage;
import org.metricshub.ipmi.core.coding.payload.sol.SolStatus;
import org.metricshub.ipmi.core.coding.sol.SolCoder;
import org.metricshub.ipmi.core.common.ByteBuffer;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener for inbound SOL messages sent by remote system to this application.
 */
public class InboundSolMessageListener implements InboundMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(InboundSolMessageListener.class);

    static final int BUFFER_CAPACITY = 2048;

    private final ByteBuffer buffer = new ByteBuffer(BUFFER_CAPACITY);
    private final IpmiConnector connector;
    private final ConnectionHandle connectionHandle;
    private final List<SolEventListener> eventListeners;

    private SolInboundMessage waitingMessage;

    public InboundSolMessageListener(IpmiConnector connector, ConnectionHandle connectionHandle,
                                     List<SolEventListener> eventListeners) {
        this.connector = connector;
        this.connectionHandle = connectionHandle;
        this.eventListeners = eventListeners;
    }

    @Override
    public boolean isPayloadSupported(IpmiPayload payload) {
        return payload instanceof SolInboundMessage;
    }

    @Override
    public void notify(IpmiPayload payload) {
        if (isPayloadSupported(payload)) {
            SolInboundMessage solPayload = (SolInboundMessage) payload;

            byte[] characterData = solPayload.getData();

            if (messageHasCharacterData(characterData)) {
                processCharacterData(solPayload, characterData);
            }

            Set<SolStatus> statuses = solPayload.getStatusField().getStatuses();

            if (messageHasStatuses(statuses)) {
                processStatuses(statuses);
            }
        }
    }

    private void processCharacterData(SolInboundMessage solPayload, byte[] characterData) {
        if (noSpaceForDataInBuffer(characterData)) {
            sendNack(solPayload);
        } else {
            buffer.write(characterData);

            sendAck(solPayload, characterData);
        }
    }

    private void processStatuses(Set<SolStatus> statuses) {
        for (SolEventListener listener : eventListeners) {
            listener.processRequestEvent(statuses);
        }
    }

    private boolean noSpaceForDataInBuffer(byte[] characterData) {
        return characterData.length > buffer.remainingSpace();
    }

    private boolean messageHasCharacterData(byte[] characterData) {
        return characterData != null && characterData.length > 0;
    }

    private boolean messageHasStatuses(Set<SolStatus> statuses) {
        return statuses != null && !statuses.isEmpty();
    }

    private void sendNack(SolInboundMessage solPayload) {
        if (solPayload.getSequenceNumber() != 0) {
            try {
                SolCoder solNack = new SolCoder(solPayload.getSequenceNumber(), (byte) 0,
                        SolAckState.NACK, connectionHandle.getCipherSuite());
                connector.sendOneWayMessage(connectionHandle, solNack);

                synchronized (buffer) {
                    waitingMessage = solPayload;
                }
            } catch (Exception e) {
                logger.error("Could not send NACK for packet " + solPayload.getSequenceNumber(), e);
            }
        }
    }

    private void sendAck(SolInboundMessage solPayload, byte[] characterData) {
        if (solPayload.getSequenceNumber() != 0) {
            try {
                SolCoder solAck = new SolCoder(solPayload.getSequenceNumber(), (byte) characterData.length,
                        SolAckState.ACK, connectionHandle.getCipherSuite());
                connector.sendOneWayMessage(connectionHandle, solAck);

                synchronized (buffer) {
                    waitingMessage = null;
                }
            } catch (Exception e) {
                logger.error("Could not send ACK for packet " + solPayload.getSequenceNumber(), e);
            }
        }
    }

    private void sendResumeAck(SolInboundMessage solPayload) {
        try {
            SolCoder solResumeAck = new SolCoder(solPayload.getSequenceNumber(), (byte) 0,
                    SolAckState.ACK, connectionHandle.getCipherSuite());
            connector.sendOneWayMessage(connectionHandle, solResumeAck);
        } catch (Exception e) {
            logger.error("Could not send Resume ACK for packet " + solPayload.getSequenceNumber(), e);
        }
    }

    /**
     * Attempts to read given number of bytes from this {@link ByteBuffer}.
     * If buffer currently contains less bytes than requested, this method reads only available number of bytes.
     *
     * @param numberOfBytes
     *          requested number of bytes to read
     * @return actual bytes that could be read from this buffer.
     */
    public byte[] readBytes(int numberOfBytes) {
        byte[] result = buffer.read(numberOfBytes);

        synchronized (buffer) {
            if (waitingMessage != null && waitingMessage.getData().length <= buffer.remainingSpace()) {
                sendResumeAck(waitingMessage);
            }
        }

        return result;
    }

    /**
     * Returns number of incoming bytes that are available to read from the buffer.
     *
     * @return number of available bytes to read
     */
    public int getAvailableBytesCount() {
        return buffer.size();
    }
}
