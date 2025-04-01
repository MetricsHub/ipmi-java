package org.metricshub.ipmi.core.coding.commands.chassis;

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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.metricshub.ipmi.core.coding.commands.CommandCodes;
import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanMessage;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanResponse;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Wrapper class for Get Chassis Status request.
 */
public class GetChassisStatus extends IpmiCommandCoder {

    /**
     * Initiates GetChassisStatus for encoding and decoding.
     *
     * @param version
     *            - IPMI version of the command.
     * @param cipherSuite
     *            - {@link CipherSuite} containing authentication,
     *            confidentiality and integrity algorithms for this session.
     * @param authenticationType
     *            - Type of authentication used. Must be RMCPPlus for IPMI v2.0.
     */
    public GetChassisStatus(IpmiVersion version, CipherSuite cipherSuite,
            AuthenticationType authenticationType) {
        super(version, cipherSuite, authenticationType);

        if (version == IpmiVersion.V20
                && authenticationType != AuthenticationType.RMCPPlus) {
            throw new IllegalArgumentException(
                    "Authentication Type must be RMCPPlus for IPMI v2.0 messages");
        }
    }

    @Override
    protected IpmiLanMessage preparePayload(int sequenceNumber) {
        return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), null,
                TypeConverter.intToByte(sequenceNumber));
    }

    @Override
    public byte getCommandCode() {
        return CommandCodes.GET_CHASSIS_STATUS;
    }

    @Override
    public NetworkFunction getNetworkFunction() {
        return NetworkFunction.ChassisRequest;
    }

    @Override
    public ResponseData getResponseData(IpmiMessage message) throws IPMIException, NoSuchAlgorithmException, InvalidKeyException {
        if (!isCommandResponse(message)) {
            throw new IllegalArgumentException(
                    "This is not a response for Get Chassis Status command");
        }
        if (!(message.getPayload() instanceof IpmiLanResponse)) {
            throw new IllegalArgumentException("Invalid response payload");
        }
        if (((IpmiLanResponse) message.getPayload()).getCompletionCode() != CompletionCode.Ok) {
            throw new IPMIException(
                    ((IpmiLanResponse) message.getPayload())
                            .getCompletionCode());
        }

        byte[] raw = message.getPayload().getIpmiCommandData();

        if (raw == null || (raw.length != 3 && raw.length != 4)) {
            throw new IllegalArgumentException(
                    "Invalid response payload length");
        }

        GetChassisStatusResponseData responseData = new GetChassisStatusResponseData();

        responseData.setCurrentPowerState(raw[0]);
        responseData.setLastPowerEvent(raw[1]);
        responseData.setMiscChassisState(raw[2]);

        if (raw.length == 4) {
            responseData.setFrontPanelButtonCapabilities(raw[3]);
        }

        return responseData;
    }

}
