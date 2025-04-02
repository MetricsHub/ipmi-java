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
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Wrapper for Activate Payload response
 */
public abstract class ActivatePayloadResponseData implements ResponseData {

    /**
     *  Maximum size of a payload data field from remote console to BMC.
     *  Excludes size of confidentiality header and trailer fields, if any.
     */
    private int inboundPayloadSize;

    /**
     *  Maximum size of a payload data field from BMC to remote console.
     *  Excludes size of confidentiality header and trailer fields, if any.
     */
    private int outboundPayloadSize;

    /**
     * UDP port number that payload can be transferred over.
     */
    private int payloadUdpPortNumber;

    /**
     * Payload VLAN number. FFFFh if VLAN addressing is not used.
     */
    private int payloadVlanNumber;

    /**
     * Set auxiliary information data
     *
     * @param auxilaryInformationData auxiliary information in bytes
     */
    public abstract void setAuxilaryInformationData(byte[] auxilaryInformationData);

    /**
     * Get inbound payload size
     *
     * @return int value
     */
    public int getInboundPayloadSize() {
        return inboundPayloadSize;
    }

    /**
     * Set inbound payload size
     *
     * @param inboundPayloadSizeData byte array of inbound payload size data
     */
    public void setInboundPayloadSize(byte[] inboundPayloadSizeData) {
        this.inboundPayloadSize = TypeConverter.littleEndianWordToInt(inboundPayloadSizeData);
    }

    /**
     * Get outbound payload size
     *
     * @return int value
     */
    public int getOutboundPayloadSize() {
        return outboundPayloadSize;
    }

    /**
     * Set outbound payload size
     *
     * @param outboundPayloadSizeData byte array of outbound payload size data
     */
    public void setOutboundPayloadSize(byte[] outboundPayloadSizeData) {
        this.outboundPayloadSize = TypeConverter.littleEndianWordToInt(outboundPayloadSizeData);
    }

    /**
     * Get payload udp port number
     *
     * @return Udp port number as int value
     */
    public int getPayloadUdpPortNumber() {
        return payloadUdpPortNumber;
    }

    /**
     * Set payload udp port number
     *
     * @param payloadUdpPortNumberData byte array of payload udp port number data
     */
    public void setPayloadUdpPortNumber(byte[] payloadUdpPortNumberData) {
        this.payloadUdpPortNumber = TypeConverter.littleEndianWordToInt(payloadUdpPortNumberData);
    }

    /**
     * Get payload vlan number
     *
     * @return vlan number data as int
     */
    public int getPayloadVlanNumber() {
        return payloadVlanNumber;
    }

    /**
     * Set payload vlan number
     *
     * @param payloadVlanNumberData byte array of payload vlan number data
     */
    public void setPayloadVlanNumber(byte[] payloadVlanNumberData) {
        this.payloadVlanNumber = TypeConverter.littleEndianWordToInt(payloadVlanNumberData);
    }

}
