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

import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.commands.IpmiVersion;
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

import static org.metricshub.ipmi.core.coding.commands.CommandCodes.GET_CHANNEL_PAYLOAD_SUPPORT;

/**
 * A wrapper class for Get Channel Payload Support Command.
 */
public class GetChannelPayloadSupport extends IpmiCommandCoder {

    private byte channelNumber;

    /**
     * Initiates class for decoding.
     */
    public GetChannelPayloadSupport() {
        super(IpmiVersion.V20, new CipherSuite((byte) 0, (byte) 0, (byte) 0, (byte) 0), AuthenticationType.RMCPPlus);
    }

    /**
     * Initiates class for both encoding and decoding.
     *
     * @param channelNumber
     *            - must be 0h-Bh or Eh-Fh <br>
     *            Eh = retrieve information for channel this request was issued
     *            on
     */
    public GetChannelPayloadSupport(byte channelNumber) {
        super(IpmiVersion.V20, new CipherSuite((byte) 0, (byte) 0, (byte) 0, (byte) 0), AuthenticationType.RMCPPlus);
        setChannelNumber(channelNumber);
    }

    /**
     * Initiates class for both encoding and decoding.
     *
     * @param channelNumber
     *            - must be 0h-Bh or Eh-Fh <br>
     *            Eh = retrieve information for channel this request was issued
     *            on
     * @param cipherSuite
     *            - {@link CipherSuite} containing authentication,
     *            confidentiality and integrity algorithms for this session.
     * @param authenticationType
     *            - Type of authentication used. Must be RMCPPlus for IPMI v2.0.
     */
    public GetChannelPayloadSupport(byte channelNumber, CipherSuite cipherSuite, AuthenticationType authenticationType) {
        super(IpmiVersion.V20, cipherSuite, authenticationType);
        setChannelNumber(channelNumber);
    }

    public byte getChannelNumber() {
        return channelNumber;
    }

    /**
     * Sets the channel number that will be put into IPMI command.
     *
     * @param channelNumber
     *            - must be 0h-Bh or Eh-Fh <br>
     *            Eh = retrieve information for channel this request was issued
     *            on
     * @throws IllegalArgumentException
     */
    public void setChannelNumber(int channelNumber) {
        if (channelNumber < 0 || channelNumber > 0xF || channelNumber == 0xC
                || channelNumber == 0xD) {
            throw new IllegalArgumentException("Invalid channel number");
        }
        this.channelNumber = TypeConverter.intToByte(channelNumber);
    }

    @Override
    public byte getCommandCode() {
        return GET_CHANNEL_PAYLOAD_SUPPORT;
    }

    @Override
    public NetworkFunction getNetworkFunction() {
        return NetworkFunction.ApplicationRequest;
    }

    @Override
    protected IpmiPayload preparePayload(int sequenceNumber) throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] requestData = new byte[1];

        requestData[0] = channelNumber;

        return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), requestData, TypeConverter.intToByte(sequenceNumber));
    }

    @Override
    public ResponseData getResponseData(IpmiMessage message) throws IPMIException, NoSuchAlgorithmException, InvalidKeyException {
        if (!isCommandResponse(message)) {
            throw new IllegalArgumentException("This is not a response for Get Payload Info command");
        }

        if (!(message.getPayload() instanceof IpmiLanResponse)) {
            throw new IllegalArgumentException("Invalid response payload");
        }

        if (((IpmiLanResponse) message.getPayload()).getCompletionCode() != CompletionCode.Ok) {
            throw new IPMIException(((IpmiLanResponse) message.getPayload()).getCompletionCode());
        }

        GetChannelPayloadSupportResponseData data = new GetChannelPayloadSupportResponseData();

        byte[] responseData = message.getPayload().getData();

        data.setStandardPayloads(responseData[0]);
        data.setSessionSetupPayloads(responseData[1]);
        data.setOemPayloads(responseData[2]);

        return data;
    }

}
