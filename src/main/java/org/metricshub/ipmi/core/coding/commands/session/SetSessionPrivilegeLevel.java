package org.metricshub.ipmi.core.coding.commands.session;

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

import org.metricshub.ipmi.core.coding.commands.CommandCodes;
import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.PrivilegeLevel;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.TypeConverter;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Wrapper class for Set Session Privilege Level command
 */
public class SetSessionPrivilegeLevel extends IpmiCommandCoder {

    private PrivilegeLevel privilegeLevel;

    /**
     * Initiates {@link SetSessionPrivilegeLevel} for encoding and decoding
     * @param version
     * - IPMI version of the command.
     * @param cipherSuite
     * - {@link CipherSuite} containing authentication, confidentiality and integrity algorithms for this session.
     * @param authenticationType
     * - Type of authentication used. Must be RMCPPlus for IPMI v2.0.
     * @param privilegeLevel
     * - Requested {@link PrivilegeLevel} to acquire. Can not be higher than level declared during starting session.
     */
    public SetSessionPrivilegeLevel(IpmiVersion version, CipherSuite cipherSuite,
            AuthenticationType authenticationType, PrivilegeLevel privilegeLevel) {
        super(version, cipherSuite, authenticationType);
        this.privilegeLevel = privilegeLevel;
    }

    @Override
    public byte getCommandCode() {
        return CommandCodes.SET_SESSION_PRIVILEGE_LEVEL;
    }

    @Override
    public NetworkFunction getNetworkFunction() {
        return NetworkFunction.ApplicationRequest;
    }

    @Override
    protected IpmiPayload preparePayload(int sequenceNumber) throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] requestData = new byte[1];

        requestData[0] = TypeConverter.intToByte(getRequestedPrivilegeLevelEncoded());

        return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), requestData,
                TypeConverter.intToByte(sequenceNumber));
    }

    @Override
    public ResponseData getResponseData(IpmiMessage message) throws IPMIException,
            NoSuchAlgorithmException, InvalidKeyException {
        if (!isCommandResponse(message)) {
            throw new IllegalArgumentException("This is not a response for Get SEL Entry command");
        }
        if (!(message.getPayload() instanceof IpmiLanResponse)) {
            throw new IllegalArgumentException("Invalid response payload");
        }
        if (((IpmiLanResponse) message.getPayload()).getCompletionCode() != CompletionCode.Ok) {
            throw new IPMIException(((IpmiLanResponse) message.getPayload()).getCompletionCode());
        }

        return new SetSessionPrivilegeLevelResponseData();
    }

    private byte getRequestedPrivilegeLevelEncoded() {
        switch (privilegeLevel) {
        case MaximumAvailable:
            return 0;
        case Callback:
            return TypeConverter.intToByte(0x1);
        case User:
            return TypeConverter.intToByte(0x2);
        case Operator:
            return TypeConverter.intToByte(0x3);
        case Administrator:
            return TypeConverter.intToByte(0x4);
        default:
            throw new IllegalArgumentException("Invalid privilege level");
        }
    }
}
