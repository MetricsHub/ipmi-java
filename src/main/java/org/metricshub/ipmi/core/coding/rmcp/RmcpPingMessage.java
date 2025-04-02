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
 * A wrapper class for ASF ping message.
 */
public class RmcpPingMessage extends RmcpMessage {
    /**
     * Prepares a ready to send ASF ping message.
     *
     * @param sequenceNumber
     *            Used for pairing request with response. Can't be 255.
     * @throws IllegalArgumentException
     *             when sequence number is 255.
     */
    public RmcpPingMessage(int sequenceNumber) {
        super();
        if (sequenceNumber > 254 || sequenceNumber < 0) {
            throw new IllegalArgumentException(
                    "Sequence number must be in range 0-254");
        }
        setVersion(RmcpVersion.RMCP1_0);
        setClassOfMessage(RmcpClassOfMessage.Asf);
        setData(preparePingMessage(sequenceNumber));
    }

    private byte[] preparePingMessage(int sequenceNumber) {
        byte[] message = new byte[8];

        // set IANA Enterprise Number
        System.arraycopy(TypeConverter.intToByteArray(RmcpConstants.ASFIANA),
                0, message, 0, 4);

        // set message type as presence ping
        message[4] = RmcpConstants.PRESENCE_PING;

        // set message tag (ASF version of a sequence number)
        message[5] = TypeConverter.intToByte(sequenceNumber);

        // reserved
        message[6] = 0;

        // data length
        message[7] = 0;

        return message;
    }
}
