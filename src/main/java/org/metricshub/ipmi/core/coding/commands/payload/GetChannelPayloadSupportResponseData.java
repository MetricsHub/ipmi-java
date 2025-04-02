package org.metricshub.ipmi.core.coding.commands.payload;

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
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.common.TypeConverter;

import java.util.HashSet;
import java.util.Set;

/**
 * Wrapper for Get Channel Payload Support response
 */
public class GetChannelPayloadSupportResponseData implements ResponseData {

    /**
     * Byte that carries information about supported Standard Payload Types.
     */
    private byte standardPayloads;

    /**
     * Byte that carries information about supported Session Setup Payload Types.
     */
    private byte sessionSetupPayloads;

    /**
     * Byte that carries information about supported OEM Payload Types.
     */
    private byte oemPayloads;

    /**
     * Set of supported {@link PayloadType}s.
     */
    private Set<PayloadType> supportedPayloads;

    public void setStandardPayloads(byte standardPayloads) {
        this.standardPayloads = standardPayloads;
    }

    public void setSessionSetupPayloads(byte sessionSetupPayloads) {
        this.sessionSetupPayloads = sessionSetupPayloads;
    }

    public void setOemPayloads(byte oemPayloads) {
        this.oemPayloads = oemPayloads;
    }

    public Set<PayloadType> getSupportedPayloads() {
        if (supportedPayloads == null) {
            initializeSupportedPayloads();
        }

        return supportedPayloads;
    }

    /**
     * Reads raw data from standardPayloads, sessionSetupPayloads and oemPayloads bytes and converts it to the {@link Set}
     * of supported {@link PayloadType}s
     */
    private void initializeSupportedPayloads() {
        supportedPayloads = new HashSet<PayloadType>();

        lookForGivenPayloads(standardPayloads, PayloadType.Ipmi, PayloadType.Sol, PayloadType.Oem);
        lookForGivenPayloads(sessionSetupPayloads, PayloadType.RmcpOpenSessionRequest, PayloadType.RmcpOpenSessionResponse,
                PayloadType.Rakp1, PayloadType.Rakp2, PayloadType.Rakp3, PayloadType.Rakp4);
        lookForGivenPayloads(oemPayloads, PayloadType.Oem0, PayloadType.Oem1, PayloadType.Oem2,
                PayloadType.Oem3, PayloadType.Oem4, PayloadType.Oem5, PayloadType.Oem6, PayloadType.Oem7);
    }

    /**
     * Checks if given payload types are enabled into given byte. If some of them is, adds this type to the output supportedPayloads.
     *
     * @param payloadsByte
     *          - byte carrying information about specific enabled payload types
     * @param types
     *          - array of types that we want to search in given byte
     */
    private void lookForGivenPayloads(byte payloadsByte, PayloadType... types) {
        for (int i = 0; i < types.length; i++) {
            if (TypeConverter.isBitSetOnPosition(i, payloadsByte)) {
                supportedPayloads.add(types[i]);
            }
        }
    }

}
