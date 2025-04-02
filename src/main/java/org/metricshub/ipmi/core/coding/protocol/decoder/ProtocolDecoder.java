package org.metricshub.ipmi.core.coding.protocol.decoder;

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
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.metricshub.ipmi.core.coding.payload.sol.SolInboundMessage;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.rmcp.RmcpClassOfMessage;
import org.metricshub.ipmi.core.coding.rmcp.RmcpMessage;
import org.metricshub.ipmi.core.coding.security.ConfidentialityAlgorithm;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Decodes IPMI session header and retrieves encrypted payload. Payload must be
 * IPMI LAN format message.
 */
public abstract class ProtocolDecoder implements IpmiDecoder {

    public ProtocolDecoder() {

    }

    /**
     * Decodes IPMI message version independent fields.
     *
     * @param rmcpMessage
     *            - RMCP message to decode.
     * @param message
     *            - A reference to message being decoded.
     * @param sequenceNumberOffset
     *            - Protocol version specific offset to Session Sequence Number
     *            field in the header of the IPMI message.
     * @param payloadLengthOffset
     *            - Protocol version specific offset to IPMI Payload Length
     *            field in the header of the IPMI message.
     * @param payloadLengthLength
     *            - Length of the payload length field.
     * @see IpmiMessage
     * @return Offset to the session trailer.
     * @throws IllegalArgumentException
     *             when delivered RMCP message does not contain encapsulated
     *             IPMI message.
     *
     * @deprecated this method is obolete. use {@link ProtocolDecoder#decode(RmcpMessage)} instead
     */
    @Deprecated
    protected int decode(RmcpMessage rmcpMessage, IpmiMessage message,
            int sequenceNumberOffset, int payloadLengthOffset,
            int payloadLengthLength) {

        if (rmcpMessage.getClassOfMessage() != RmcpClassOfMessage.Ipmi) {
            throw new IllegalArgumentException("This is not an IPMI message");
        }

        byte[] raw = rmcpMessage.getData();

        message.setAuthenticationType(decodeAuthenticationType(raw[0]));

        message.setSessionSequenceNumber(decodeSessionSequenceNumber(raw,
                sequenceNumberOffset));

        message.setPayloadLength(decodePayloadLength(raw, payloadLengthOffset));

        message.setPayload(decodePayload(raw, payloadLengthOffset
                + payloadLengthLength, message.getPayloadLength(),
                message.getConfidentialityAlgorithm(), PayloadType.Ipmi));

        return payloadLengthOffset + payloadLengthLength
                + message.getPayloadLength();
    }

    protected static AuthenticationType decodeAuthenticationType(byte authenticationType) {
        authenticationType &= TypeConverter.intToByte(0x0f);

        return AuthenticationType.parseInt(TypeConverter
                .byteToInt(authenticationType));
    }

    /**
     * Decodes {@link AuthenticationType} of the message so that the version of
     * the IPMI protocol could be determined.
     *
     * @param message
     *            - RMCP message to decode.
     * @return {@link AuthenticationType} of the message.
     */
    public static AuthenticationType decodeAuthenticationType(
            RmcpMessage message) {
        return decodeAuthenticationType(message.getData()[0]);
    }

    /**
     * Decodes int in a little endian convention from raw message at given
     * offset
     *
     * @param rawMessage
     *            - Raw message to be decoded
     * @param offset
     * @return Decoded integer
     */
    protected static int decodeInt(byte[] rawMessage, int offset) {
        byte[] result = new byte[4];

        System.arraycopy(rawMessage, offset, result, 0, 4);

        return TypeConverter.littleEndianByteArrayToInt(result);
    }

    /**
     * Decodes session sequence number.
     *
     * @param rawMessage
     *            - Byte array holding whole message data.
     * @param offset
     *            - Offset to session sequence number in header.
     * @return Session Sequence number.
     */
    protected int decodeSessionSequenceNumber(byte[] rawMessage, int offset) {
        return decodeInt(rawMessage, offset);
    }

    /**
     * Decodes session ID.
     *
     * @param rawMessage
     *            - Byte array holding whole message data.
     * @param offset
     *            - Offset to session ID in header.
     * @return Session ID.
     */
    protected static int decodeSessionID(byte[] rawMessage, int offset) {
        return decodeInt(rawMessage, offset);
    }

    /**
     * Decodes payload length.
     *
     * @param rawData
     *            - Byte array holding whole message data.
     * @param offset
     *            - Offset to payload length in header.
     * @return payload length.
     */
    protected abstract int decodePayloadLength(byte[] rawData, int offset);

    /**
     * Decodes payload.
     *
     * @param rawData
     *            - Byte array holding whole message data.
     * @param offset
     *            - Offset to payload.
     * @param length
     *            - Length of the payload.
     * @param confidentialityAlgorithm
     *            - {@link ConfidentialityAlgorithm} required to decrypt
     *            payload.
     * @return Payload decoded into {@link IpmiLanResponse}.
     */
    protected IpmiPayload decodePayload(byte[] rawData, int offset, int length,
            ConfidentialityAlgorithm confidentialityAlgorithm, PayloadType payloadType) {
        byte[] payload = null;
        if (length > 0) {
            payload = new byte[length];

            System.arraycopy(rawData, offset, payload, 0, length);

            payload = confidentialityAlgorithm.decrypt(payload);
        }

        if (payloadType == PayloadType.Sol) {
            return new SolInboundMessage(payload);
        } else {
            return new IpmiLanResponse(payload);
        }
    }
}
