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

import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv15Message;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.rmcp.RmcpMessage;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Decodes IPMI v1.5 session header and retrieves encrypted payload.
 */
public class Protocolv15Decoder extends ProtocolDecoder {

    public Protocolv15Decoder() {
        super();
    }

    /**
     * Decodes IPMI v1.5 message fields.
     *
     * @param rmcpMessage
     *            - RMCP message to decode.
     * @return decoded message
     * @see Ipmiv15Message
     * @throws IllegalArgumentException
     *             when delivered RMCP message does not contain encapsulated
     *             IPMI message.
     */
    @Override
    public IpmiMessage decode(RmcpMessage rmcpMessage) {
        Ipmiv15Message message = new Ipmiv15Message();

        byte[] raw = rmcpMessage.getData();

        message.setAuthenticationType(decodeAuthenticationType(raw[0]));

        int offset = 1;

        message.setSessionSequenceNumber(decodeSessionSequenceNumber(raw,
                offset));

        offset += 4;

        message.setSessionID(decodeSessionID(raw, offset));

        offset += 4;

        if (message.getAuthenticationType() != AuthenticationType.None) {
            message.setAuthCode(decodeAuthCode(raw, offset));
            offset += 16;
        }

        int payloadLength = decodePayloadLength(raw, offset);

        message.setPayloadLength(payloadLength);
        ++offset;

        message.setPayload(decodePayload(raw, offset, payloadLength,
                message.getConfidentialityAlgorithm(), PayloadType.Ipmi));

        return message;
    }

    /**
     * Decodes authentication code.
     *
     * @param rawMessage
     *            - Byte array holding whole message data.
     * @param offset
     *            - Offset to authentication code in header.
     * @return authentication code.
     */
    private byte[] decodeAuthCode(byte[] rawMessage, int offset) {
        byte[] authCode = new byte[16];

        System.arraycopy(rawMessage, offset, authCode, 0, 16);

        return authCode;
    }

    @Override
    protected int decodePayloadLength(byte[] rawData, int offset) {
        return TypeConverter.byteToInt(rawData[offset]);
    }
}
