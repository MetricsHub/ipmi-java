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
import org.metricshub.ipmi.core.coding.protocol.Ipmiv15Message;
import org.metricshub.ipmi.core.common.TypeConverter;

import java.security.InvalidKeyException;

/**
 * Encodes IPMI v1.5 message
 */
public class Protocolv15Encoder extends ProtocolEncoder {

    /**
     * @param ipmiMessage
     *            - IPMI message to be encoded. Must be {@link Ipmiv15Message}.
     * @throws InvalidKeyException
     *             - when initiation of the confidentiality algorithm fails
     * @throws IllegalArgumentException
     *             when IPMI protocol version is incorrect.
     * @see Ipmiv15Message
     */
    @Override
    public byte[] encode(IpmiMessage ipmiMessage) throws InvalidKeyException {
        if (!(ipmiMessage instanceof Ipmiv15Message)) {
            throw new IllegalArgumentException(
                    "IPMIMessage must be in 1.5 version.");
        }
        Ipmiv15Message message = (Ipmiv15Message) ipmiMessage;

        byte[] raw = new byte[getMessageLength(message)];

        raw[0] = encodeAuthenticationType(message.getAuthenticationType());

        int offset = 1;

        encodeSessionSequenceNumber(message.getSessionSequenceNumber(), raw,
                offset);
        offset += 4;

        encodeSessionId(message.getSessionID(), raw, offset);
        offset += 4;

        if (message.getAuthenticationType() != AuthenticationType.None) {
            encodeAuthenticationCode(message.getAuthCode(), raw, offset);
            offset += message.getAuthCode().length;
        }

        byte[] payload = message.getPayload().getEncryptedPayload();

        if (payload == null) {
            message.getPayload().encryptPayload(
                    message.getConfidentialityAlgorithm());
            payload = message.getPayload().getEncryptedPayload();
        }

        encodePayloadLength(payload.length, raw, offset);
        ++offset;

        offset = encodePayload(payload, raw, offset);

        encodeSessionTrailer(raw, offset);

        return raw;
    }

    private int getMessageLength(IpmiMessage ipmiMessage) {
        int length = 11
                + ipmiMessage.getConfidentialityAlgorithm()
                        .getConfidentialityOverheadSize(
                                ipmiMessage.getPayloadLength())
                + ipmiMessage.getPayloadLength();

        if (ipmiMessage.getAuthenticationType() != AuthenticationType.None) {
            length += 16;
        }

        return length;
    }

    /**
     * Inserts authentication code into message at given offset.
     *
     * @param authCode
     * @param message
     *            - message being encoded
     * @param offset
     * @throws IndexOutOfBoundsException
     *             when message is too short to hold value at given offset
     */
    private void encodeAuthenticationCode(byte[] authCode, byte[] message, int offset) {
        if (authCode.length + offset > message.length) {
            throw new IndexOutOfBoundsException("Message is too short");
        }
        System.arraycopy(authCode, 0, message, offset, authCode.length);
    }

    @Override
    protected void encodePayloadLength(int value, byte[] message, int offset) {
        message[offset] = TypeConverter.intToByte(value);
    }

    /**
     * Creates session trailer. <br>
     * Creates an inserts Legacy PAD.
     *
     * @param message
     *            - IPMI message being created
     * @param offset
     *            - Should point at the beginning of the session trailer.
     * @throws IndexOutOfBoundsException
     *             when message is too short to hold value at given offset
     * @return Offset pointing after Authorization Code
     */
    private int encodeSessionTrailer(byte[] message, int offset) {
        if (1 + offset > message.length) {
            throw new IndexOutOfBoundsException("Message is too short");
        }

        message[offset] = 0;

        return offset + 1;
    }
}
