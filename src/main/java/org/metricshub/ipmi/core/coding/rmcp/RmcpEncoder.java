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
 * Encodes RMCPMessage into RMCP packet.
 */
public final class RmcpEncoder {

    private RmcpEncoder() {
    }

    /**
     * Encodes RMCPMessage into RMCP packet.
     *
     * @param message
     *            - RMCP message to be encoded
     * @return byte data containing ready to send RMCP packet
     */
    public static byte[] encode(RmcpMessage message) {
        byte[] data = new byte[message.getData().length + 4];

        data[0] = encodeVersion(message.getVersion());

        data[1] = 0; // reserved

        data[2] = encodeSequenceNumber(message.getSequenceNumber());

        data[3] = encodeRMCPClassOfMessage(message.getClassOfMessage());

        encodeData(message.getData(), data);

        return data;
    }

    private static byte encodeVersion(RmcpVersion version) {
        switch (version) {
        case RMCP1_0:
            return RmcpConstants.RMCP_V1_0;
        default:
            throw new IllegalArgumentException("Invalid RMCP version");
        }
    }

    private static byte encodeSequenceNumber(byte sequenceNumber) {
        return sequenceNumber;
    }

    private static byte encodeRMCPClassOfMessage(
            RmcpClassOfMessage classOfMessage) {
        return TypeConverter.intToByte(classOfMessage.getCode());
    }

    /**
     * Copies data to message
     *
     * @param data
     *            - source data of RMCPMessage
     * @param message
     *            - result message
     */
    private static void encodeData(byte[] data, byte[] message) {
        System.arraycopy(data, 0, message, 4, data.length);
    }
}
