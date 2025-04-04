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
 * A wrapper class for RMCP message.
 */
public class RmcpMessage {
    private RmcpVersion version;
    private byte sequenceNumber;
    private RmcpClassOfMessage classOfMessage;
    private byte[] data;

    public RmcpMessage() {
        setSequenceNumber(0xff);
    }

    public void setVersion(RmcpVersion version) {
        this.version = version;
    }

    public RmcpVersion getVersion() {
        return version;
    }

    /**
     * Set RMCP sequence number. Must be 0-254 if ACK is desired, 255 if no ACK is desired.
     * @param sequenceNumber
     */
    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = TypeConverter.intToByte(sequenceNumber);
    }

    public byte getSequenceNumber() {
        return sequenceNumber;
    }

    public int getIntSequenceNumber() {
        return TypeConverter.byteToInt(sequenceNumber);
    }

    public void setClassOfMessage(RmcpClassOfMessage classOfMessage) {
        this.classOfMessage = classOfMessage;
    }

    public RmcpClassOfMessage getClassOfMessage() {
        return classOfMessage;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }
}
