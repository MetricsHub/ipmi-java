package org.metricshub.ipmi.core.coding.protocol.encoder;

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

import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv20Message;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.common.TypeConverter;

import java.security.InvalidKeyException;

/**
 * Encodes IPMI v2.0 message.
 */
public class Protocolv20Encoder extends ProtocolEncoder {
    /**
     * @param ipmiMessage
     *            - IPMI message to be encoded. Must be {@link Ipmiv20Message}.
     * @throws IllegalArgumentException
     *             when IPMI protocol version or authentication type is
     *             incorrect.
     * @throws InvalidKeyException
     *             - when initiation of the confidentiality algorithm fails
     * @see Ipmiv20Message
     */
    @Override
    public byte[] encode(IpmiMessage ipmiMessage) throws InvalidKeyException {
        if (!(ipmiMessage instanceof Ipmiv20Message)) {
            throw new IllegalArgumentException(
                    "IPMIMessage must be in 2.0 version.");
        }
        Ipmiv20Message message = (Ipmiv20Message) ipmiMessage;

        byte[] payload = message.getPayload().getEncryptedPayload();

        if (payload == null) {
            message.getPayload().encryptPayload(
                    message.getConfidentialityAlgorithm());
            payload = message.getPayload().getEncryptedPayload();
        }

        byte[] raw = new byte[getMessageLength(message)];

        if (message.getAuthenticationType() != AuthenticationType.RMCPPlus) {
            throw new IllegalArgumentException(
                    "Authentication type must be RMCP+ for IPMI v2.0");
        }

        raw[0] = encodeAuthenticationType(message.getAuthenticationType());

        int offset = 1;

        raw[offset] = encodePayloadType(message.isPayloadEncrypted(),
                message.isPayloadAuthenticated(), message.getPayloadType());

        ++offset;

        if (message.getPayloadType() == PayloadType.Oem) {
            encodeOEMIANA(message.getOemIANA(), raw, offset);
            offset += 4;

            encodeOEMPayloadId(message.getOemPayloadID(), raw, offset);
            offset += 2;
        }

        encodeSessionId(message.getSessionID(), raw, offset);
        offset += 4;

        encodeSessionSequenceNumber(message.getSessionSequenceNumber(), raw,
                offset);
        offset += 4;

        encodePayloadLength(payload.length, raw, offset);
        offset += 2;

        offset = encodePayload(payload, raw, offset);

        if (message.isPayloadAuthenticated() && message.getSessionID() != 0) {
            encodeSessionTrailer(message.getAuthCode(), raw, offset);
        }

        return raw;
    }

    /**
     * Calculates length of the IPMI message.
     *
     * @param ipmiMessage
     *            - message which length is to be calculated
     */
    private int getMessageLength(Ipmiv20Message ipmiMessage) {
        int length = 12
                + ipmiMessage.getConfidentialityAlgorithm()
                        .getConfidentialityOverheadSize(
                                ipmiMessage.getPayloadLength())
                + ipmiMessage.getPayloadLength();

        if (ipmiMessage.getPayloadType() == PayloadType.Oem) {
            length += 6;
        }

        if (ipmiMessage.isPayloadAuthenticated()
                && ipmiMessage.getSessionID() != 0) {
            if (ipmiMessage.getAuthCode() != null) {
                if ((length + ipmiMessage.getAuthCode().length + 2) % 4 != 0) {
                    length += 4 - (length + ipmiMessage.getAuthCode().length + 2) % 4;
                }
                length += ipmiMessage.getAuthCode().length;
            }
            length += 2;
        }

        return length;
    }

    private byte encodePayloadType(boolean isEncrypted,
            boolean isAuthenticated, PayloadType payloadType) {
        byte result = 0;

        if (isEncrypted) {
            result |= TypeConverter.intToByte(0x80);
        }

        if (isAuthenticated) {
            result |= TypeConverter.intToByte(0x40);
        }

        result |= TypeConverter.intToByte(payloadType.getCode());

        return result;
    }

    /**
     * Encodes OEM IANA and inserts it into message at given offset.
     *
     * @param value
     * @param message
     *            - IPMI message being created
     * @param offset
     * @throws IndexOutOfBoundsException
     *             when message is too short to hold value at given offset
     */
    private void encodeOEMIANA(int value, byte[] message, int offset) {
        encodeInt(value, message, offset);
    }

    /**
     * Encodes OEM payload ID and inserts it into message at given offset. To
     * implement manufacturer-specific OEM Payload ID encoding, override this
     * function.
     *
     * @param value
     * @param message
     *            - IPMI message being created
     * @param offset
     * @throws IndexOutOfBoundsException
     *             when message is too short to hold value at given offset
     * @throws IllegalArgumentException
     *             when value is incorrect.
     */
    protected void encodeOEMPayloadId(Object value, byte[] message, int offset) {
        byte[] oemId = null;
        try {
            oemId = (byte[]) value;
        } catch (Exception e) {
            throw new IllegalArgumentException("Value is corrupted", e);
        }
        if (oemId.length != 2) {
            throw new IllegalArgumentException("Value has invalid length");
        }
        if (oemId.length + offset > message.length) {
            throw new IndexOutOfBoundsException("Message is too short");
        }

        System.arraycopy(oemId, 0, message, offset, 2);
    }

    @Override
    protected void encodePayloadLength(int value, byte[] message, int offset) {
        byte[] payloadLength = TypeConverter.intToLittleEndianByteArray(value);
        message[offset] = payloadLength[0];
        message[offset + 1] = payloadLength[1];
    }

    /**
     * Creates session trailer. <br>
     * Encodes Authorization Code and inserts it into message. <br>
     * Adds integrity pad if needed and inserts pad length and next header
     * fields.
     *
     * @param authCode
     *            - Value of the Authorization Code
     * @param message
     *            - IPMI message being created
     * @param offset
     *            - Should point at the beginning of the session trailer.
     * @throws IndexOutOfBoundsException
     *             when message is too short to hold value at given offset
     * @return Offset pointing after Authorization Code
     */
    private int encodeSessionTrailer(final byte[] authCode, final byte[] message, final int offset) {
        int pad = 0;

        if (authCode != null && authCode.length + offset > message.length) {
            throw new IndexOutOfBoundsException("Message is too short");
        }

        if (authCode != null) {
            pad = (offset + authCode.length + 2) % 4;
        }

        if (pad > 0) {
            pad = 4 - pad;
        } else {
            pad = 0;
        }

        int currentOffset = offset;

        for (int i = 0; i < pad; ++i) {
            message[currentOffset] = TypeConverter.intToByte(0xff);
            ++currentOffset;
        }

        message[currentOffset] = TypeConverter.intToByte(pad);
        ++currentOffset;

        // Next header - reserved
        message[currentOffset] = TypeConverter.intToByte(0x07);
        ++currentOffset;
        if (authCode != null) {
            System.arraycopy(authCode, 0, message, currentOffset, authCode.length);
            currentOffset += authCode.length;
        }

        return currentOffset;
    }
}
