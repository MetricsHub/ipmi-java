package org.metricshub.ipmi.core.coding.rmcp;

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

import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Decodes RMCP packet from raw data.
 */
public final class RmcpDecoder {

    private RmcpDecoder() {
    }

    /**
     * Decodes the RMCP packet from raw data.
     * @param rawMessage
     * - packet data in a byte form
     * @return RMCPMessage
     * @throws IllegalArgumentException
     * - occurs when message is too short or contains invalid data
     */
    public static RmcpMessage decode(byte[] rawMessage) {
        RmcpMessage message = new RmcpMessage();

        if (rawMessage.length < 4) {
            throw new IllegalArgumentException("Message is corrupted");
        }

        message.setVersion(decodeVersion(rawMessage[0]));

        // byte 1 is reserved

        message.setSequenceNumber(decodeSequenceNumber(rawMessage[2]));

        message.setClassOfMessage(decodeClassOfMessage(rawMessage[3]));

        message.setData(decodeData(rawMessage));

        return message;
    }

    private static RmcpVersion decodeVersion(byte version) {
        if (version == RmcpConstants.RMCP_V1_0) {
            return RmcpVersion.RMCP1_0;
        }

        throw new IllegalArgumentException("Illegal RMCP version");
    }

    private static int decodeSequenceNumber(byte sequenceNumber) {
        return TypeConverter.byteToInt(sequenceNumber);
    }

    private static RmcpClassOfMessage decodeClassOfMessage(byte classOfMessage) {
        return RmcpClassOfMessage.parseInt(TypeConverter.byteToInt(classOfMessage) & 0x9f); 
        // bits 5 and 6 are reserved so we need to get rid of them
    }

    private static byte[] decodeData(byte[] rawMessage) {
        byte[] data = new byte[rawMessage.length - 4];
        System.arraycopy(rawMessage, 4, data, 0, data.length);
        return data;
    }

}
