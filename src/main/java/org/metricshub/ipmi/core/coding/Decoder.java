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

import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.protocol.decoder.IpmiDecoder;
import org.metricshub.ipmi.core.coding.protocol.decoder.PlainCommandv20Decoder;
import org.metricshub.ipmi.core.coding.protocol.decoder.Protocolv15Decoder;
import org.metricshub.ipmi.core.coding.protocol.decoder.Protocolv20Decoder;
import org.metricshub.ipmi.core.coding.rmcp.RmcpDecoder;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Decodes RMCP packet into {@link ResponseData}.
 */
public final class Decoder {

    /**
     * Decodes raw data into {@link ResponseData} - a wrapper class for
     * message-specific response data.
     *
     * @param data
     *            - raw RMCP packet to be decoded
     * @param protocolDecoder
     *            - instance of {@link IpmiDecoder} class for decoding of the
     *            IPMI session header and (if present) IPMI LAN packet. If IPMI
     *            LAN packet is present, {@link Protocolv15Decoder} or
     *            {@link Protocolv20Decoder} should be used (depending on IPMI
     *            protocol version used). Otherwise,
     *            {@link PlainCommandv20Decoder} should be used.
     * @param payloadCoder
     *            - instance of {@link PayloadCoder} class used for wrapping
     *            payload into message-dependent {@link ResponseData} object.
     * @return {@link ResponseData}
     * @throws IPMIException
     *             when request to the server fails.
     * @see CompletionCode
     * @throws IllegalArgumentException
     *             when data is corrupted
     * @throws NoSuchAlgorithmException
     *             - when authentication, confidentiality or integrity algorithm
     *             fails.
     * @throws InvalidKeyException
     *             when creating of the authentication algorithm key fails
     */
    public static ResponseData decode(byte[] data, IpmiDecoder protocolDecoder,
            PayloadCoder payloadCoder) throws IPMIException, NoSuchAlgorithmException, InvalidKeyException {
        return payloadCoder.getResponseData(protocolDecoder.decode(RmcpDecoder
                .decode(data)));
    }

    private Decoder() {
    }
}
