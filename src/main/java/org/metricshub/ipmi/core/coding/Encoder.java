package org.metricshub.ipmi.core.coding;

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

import org.metricshub.ipmi.core.coding.protocol.decoder.Protocolv15Decoder;
import org.metricshub.ipmi.core.coding.protocol.decoder.Protocolv20Decoder;
import org.metricshub.ipmi.core.coding.protocol.encoder.IpmiEncoder;
import org.metricshub.ipmi.core.coding.rmcp.RmcpEncoder;
import org.metricshub.ipmi.core.coding.rmcp.RmcpIpmiMessage;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Creates RMCP packet containing encrypted IPMI command from IPMICommand
 * wrapper class or raw byte data.
 */
public final class Encoder {

    /**
     * Encodes IPMI command specified by payloadCoder into byte array raw data.
     *
     * @param protcolEncoder
     *            - instance of {@link IpmiEncoder} class for encoding of the
     *            IPMI session header. {@link Protocolv15Decoder} or
     *            {@link Protocolv20Decoder} should be used (depending on IPMI
     *            protocol version used).
     * @param payloadCoder
     *            - instance of {@link PayloadCoder} class used for building
     *            IPMI message payload.
     * @param messageSequenceNumber
     *            - A generated sequence number used for matching request and
     *            response. If IPMI message is sent in a session, it is used as
     *            a Session Sequence Number. For all IPMI messages,
     *            messageSequenceNumber is used as a IPMI LAN Message sequence
     *            number and as an IPMI payload message tag.
     * @param sessionId
     *            - ID of the managed system's session message is being sent in.
     *            For sessionless commands should be set to 0.
     * @return encoded IPMI command
     * @throws NoSuchAlgorithmException
     *             - when authentication, confidentiality or integrity algorithm
     *             fails.
     * @throws InvalidKeyException
     *             - when creating of the algorithm key fails
     */
    public static byte[] encode(IpmiEncoder protcolEncoder, PayloadCoder payloadCoder,
                                int messageSequenceNumber, int sessionSequenceNumber, int sessionId)
            throws NoSuchAlgorithmException, InvalidKeyException {
        return RmcpEncoder
                .encode(new RmcpIpmiMessage(protcolEncoder.encode(payloadCoder
                        .encodePayload(messageSequenceNumber, sessionSequenceNumber, sessionId))));
    }

    private Encoder() {
    }
}
