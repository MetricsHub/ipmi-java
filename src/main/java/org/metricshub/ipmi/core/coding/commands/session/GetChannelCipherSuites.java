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
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanMessage;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.Ipmiv20Message;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.coding.security.ConfidentialityNone;
import org.metricshub.ipmi.core.common.TypeConverter;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Wrapper for RMCP+ Get Channel Cipher Suites command. This command can be
 * executed prior to establishing a session with the BMC.
 */
public class GetChannelCipherSuites extends IpmiCommandCoder {

    private byte channelNumber;

    private byte index;

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

    public int getChannelNumber() {
        return TypeConverter.byteToInt(channelNumber);
    }

    public void setIndex(byte index) {
        if (index > 0x3F || index < 0) {
            throw new IllegalArgumentException("Index " + index + " invalid must be (00h-3Fh).");
        }
        this.index = index;
    }

    public byte getIndex() {
        return index;
    }

    /**
     * Initiates class for decoding.
     */
    public GetChannelCipherSuites() {
        super(IpmiVersion.V20, new CipherSuite((byte) 0, (byte) 0, (byte) 0,
                (byte) 0), AuthenticationType.RMCPPlus);
    }

    /**
     * Initiates class for both encoding and decoding.
     *
     * @param channelNumber
     *            - must be 0h-Bh or Eh-Fh <br>
     *            Eh = retrieve information for channel this request was issued
     *            on
     * @param index
     *            - (00h-3Fh). 0h selects the first set of 16 cipher suites, 1h
     *            selects the next set of 16, and so on
     */
    public GetChannelCipherSuites(byte channelNumber, byte index) {
        super(IpmiVersion.V20, new CipherSuite((byte) 0, (byte) 0, (byte) 0,
                (byte) 0), AuthenticationType.RMCPPlus);
        setChannelNumber(channelNumber);
        setIndex(index);
    }

    @Override
    public IpmiMessage encodePayload(int messageSequenceNumber, int sessionSequenceNumber, int sessionId)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Ipmiv20Message message = new Ipmiv20Message(new ConfidentialityNone());

        message.setAuthenticationType(getAuthenticationType());

        message.setSessionID(0);

        message.setPayloadEncrypted(false);

        message.setPayloadAuthenticated(false);

        message.setSessionSequenceNumber(0);

        message.setPayloadType(PayloadType.Ipmi);

        message.setPayload(preparePayload(messageSequenceNumber));

        return message;
    }

    @Override
    protected IpmiLanMessage preparePayload(int sequenceNumber) {
        byte[] requestData = new byte[3];

        requestData[0] = channelNumber;

        requestData[1] = 0; // payload type = IPMI

        requestData[2] = TypeConverter.intToByte(0x80 | getIndex());

        return new IpmiLanRequest(getNetworkFunction(), getCommandCode(),
                requestData, TypeConverter.intToByte(sequenceNumber));
    }

    @Override
    public byte getCommandCode() {
        return CommandCodes.GET_CHANNEL_CIPHER_SUITES;
    }

    @Override
    public NetworkFunction getNetworkFunction() {
        return NetworkFunction.ApplicationRequest;
    }

    @Override
    public ResponseData getResponseData(IpmiMessage message) throws IPMIException, NoSuchAlgorithmException, InvalidKeyException {

        GetChannelCipherSuitesResponseData data = new GetChannelCipherSuitesResponseData();

        byte[] raw = message.getPayload().getIpmiCommandData();

        data.setChannelNumber(raw[0]);

        if (raw.length > 1) {
            byte[] cssData = new byte[raw.length - 1];

            System.arraycopy(raw, 1, cssData, 0, cssData.length);

            data.setCipherSuiteData(cssData);
        } else if(raw.length == 1) {
            data.setCipherSuiteData(new byte[0]);
        }

        return data;
    }

}
