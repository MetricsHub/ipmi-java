package org.metricshub.ipmi.core.coding.payload.sol;

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
import org.metricshub.ipmi.core.common.MessageComposer;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Implementation of {@link IpmiPayload} for SOL (Serial over LAN) messages.
 */
public abstract class SolMessage extends IpmiPayload {

    public static final byte MIN_SEQUENCE_NUMBER = 1;
    public static final byte MAX_SEQUENCE_NUMBER = 15;

    private static final int PACKET_SEQUENCE_NUMBER_LENGTH = 1;
    private static final int PACKET_ACK_SEQUENCE_NUMBER_LENGTH = 1;
    private static final int ACCEPTED_CHARACTERS_LENGTH = 1;
    private static final int OPERATION_STATUS_LENGTH = 1;

    public static final int PAYLOAD_HEADER_LENGTH =
            PACKET_SEQUENCE_NUMBER_LENGTH +
            PACKET_ACK_SEQUENCE_NUMBER_LENGTH +
            ACCEPTED_CHARACTERS_LENGTH +
            OPERATION_STATUS_LENGTH;

    /**
     * Sequence number of this packet.
     */
    private final byte sequenceNumber;

    /**
     * Sequence number of packet being ACKd/NACKd by this packet.
     */
    private final byte ackNackSequenceNumber;

    /**
     * Number of characters being accepted from ACKd/NACKd packet.
     */
    private final byte acceptedCharacterCount;

    /**
     * Operation to invoke or status of previously send packet.
     */
    private final byte operationStatus;

    protected SolMessage(byte sequenceNumber, byte ackNackSequenceNumber, byte acceptedCharacterCount, byte operationStatus) {
        this.sequenceNumber = trimSequenceNumber(sequenceNumber);
        this.ackNackSequenceNumber = trimSequenceNumber(ackNackSequenceNumber);
        this.acceptedCharacterCount = acceptedCharacterCount;
        this.operationStatus = operationStatus;
    }

    /**
     * Trims given sequence number to max allowed value for sequence numbers, applying MAX_SEQUENCE_NUMBER mask on it.
     *
     * @param sequenceNumber
     *          Sequence number before trim
     * @return trimmed sequence number
     */
    private byte trimSequenceNumber(byte sequenceNumber) {
        return TypeConverter.intToByte(sequenceNumber & MAX_SEQUENCE_NUMBER);
    }

    @Override
    public byte[] getPayloadData() {
        return MessageComposer.get(getPayloadLength())
            .appendField(sequenceNumber)
            .appendField(ackNackSequenceNumber)
            .appendField(acceptedCharacterCount)
            .appendField(operationStatus)
            .appendField(getData())
            .getMessage();
    }

    @Override
    public int getPayloadLength() {
        return PAYLOAD_HEADER_LENGTH + getData().length;
    }

    @Override
    public byte[] getIpmiCommandData() {
        return getData();
    }

    @Override
    public byte[] getData() {
        byte[] data = super.getData();

        if (data == null) {
            return new byte[0];
        }

        return data;
    }

    public byte getSequenceNumber() {
        return sequenceNumber;
    }

    public byte getAckNackSequenceNumber() {
        return ackNackSequenceNumber;
    }

    public byte getAcceptedCharacterCount() {
        return acceptedCharacterCount;
    }

    /**
     * Checks if given message carries some data (or operation/status).
     */
    public boolean isDataCarrier() {
        return sequenceNumber != 0 || getData().length > 0;
    }

    /**
     * Checks if given {@link SolMessage} is an ACK/NACK for some other message.
     */
    public boolean isAcknowledgeMessage() {
        return ackNackSequenceNumber != 0;
    }
}
