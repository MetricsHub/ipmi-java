package org.metricshub.ipmi.core.coding.payload.lan;

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
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * A wrapper class for IPMI LAN message
 */
public abstract class IpmiLanMessage extends IpmiPayload {
    public static final int MIN_SEQUENCE_NUMBER = 1;
    public static final int MAX_SEQUENCE_NUMBER = 63;

    private byte responderAddress;

    protected byte networkFunction;

    private byte responderLogicalUnitNumber;

    private byte requesterAddress;

    private byte requesterLogicalUnitNumber;
    
    private byte sequenceNumber;

    private byte command;

    public void setResponderAddress(byte responderAddress) {
        this.responderAddress = responderAddress;
    }

    public byte getResponderAddress() {
        return responderAddress;
    }

    public void setNetworkFunction(NetworkFunction networkFunction) {
        this.networkFunction = TypeConverter.intToByte(networkFunction.getCode());
    }

    public NetworkFunction getNetworkFunction() {
        return NetworkFunction.parseInt(TypeConverter.byteToInt(networkFunction));
    }

    public void setResponderLogicalUnitNumber(byte responderLogicalUnitNumber) {
        this.responderLogicalUnitNumber = responderLogicalUnitNumber;
    }

    public byte getResponderLogicalUnitNumber() {
        return responderLogicalUnitNumber;
    }

    public void setSequenceNumber(byte sequenceAddress) {
        this.sequenceNumber = sequenceAddress;
    }

    public byte getSequenceNumber() {
        return sequenceNumber;
    }

    public void setRequesterAddress(byte requesterAddress) {
        this.requesterAddress = requesterAddress;
    }

    public byte getRequesterAddress() {
        return requesterAddress;
    }

    public void setRequesterLogicalUnitNumber(byte requesterLogicalUnitNumber) {
        this.requesterLogicalUnitNumber = requesterLogicalUnitNumber;
    }

    public byte getRequesterLogicalUnitNumber() {
        return requesterLogicalUnitNumber;
    }

    public void setCommand(byte command) {
        this.command = command;
    }

    public byte getCommand() {
        return command;
    }
    
    /**
     * Gets expected size of LAN message in bytes.
     */
    @Override
    public abstract int getPayloadLength();
    
    /**
     * Converts IpmiLanMessage to byte array. 
     */
    @Override
    public abstract byte[] getPayloadData();
        
    protected byte getChecksum1(byte[] message) {
        int checksum = 0;
        for(int i = 0; i < 2; ++i) {
            checksum = (checksum + TypeConverter.byteToInt(message[i])) % 256;
        }
        return (byte) -TypeConverter.intToByte(checksum);
    }
    
    protected byte getChecksum2(byte[] message) {
        int checksum = 0;
        for(int i = 3; i < message.length-1; ++i) {
            checksum = ((checksum + TypeConverter.byteToInt(message[i])) % 256);
        }
        return (byte)-TypeConverter.intToByte(checksum);
    }
    
    @Override
    public byte[] getIpmiCommandData() {
        return getData();
    }
}
