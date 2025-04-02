package org.metricshub.ipmi.core.coding.commands.sdr;

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
 * A wrapper class for Get SDR Repository Info Command. <br>
 * This command returns the SDR command version for the SDR Repository. It also
 * returns a timestamp for when the last ADD, DELETE, or CLEAR occurred.
 */
public class GetSdrRepositoryInfo extends IpmiCommandCoder {

    /**
     * Initiates GetSdrRepositoryInfo for both encoding and decoding.
     *
     * @param version
     *            - IPMI version of the command.
     * @param cipherSuite
     *            - {@link CipherSuite} containing authentication,
     *            confidentiality and integrity algorithms for this session.
     * @param authenticationType
     *            - Type of authentication used. Must be RMCPPlus for IPMI v2.0.
     */
    public GetSdrRepositoryInfo(IpmiVersion version, CipherSuite cipherSuite,
            AuthenticationType authenticationType) {
        super(version, cipherSuite, authenticationType);
    }

    @Override
    public byte getCommandCode() {
        return CommandCodes.GET_SDR_REPOSITORY_INFO;
    }

    @Override
    public NetworkFunction getNetworkFunction() {
        return NetworkFunction.StorageRequest;
    }

    @Override
    protected IpmiPayload preparePayload(int sequenceNumber)
            throws NoSuchAlgorithmException, InvalidKeyException {
        return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), null,
                TypeConverter.intToByte(sequenceNumber));
    }

    @Override
    public ResponseData getResponseData(IpmiMessage message) throws IPMIException, NoSuchAlgorithmException, InvalidKeyException {
        if (!isCommandResponse(message)) {
            throw new IllegalArgumentException(
                    "This is not a response for Get SDR Repository Info command");
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

        if (raw == null || raw.length != 14) {
            throw new IllegalArgumentException(
                    "Invalid response payload length");
        }

        GetSdrRepositoryInfoResponseData responseData = new GetSdrRepositoryInfoResponseData();

        responseData.setSdrVersion(TypeConverter
                .littleEndianBcdByteToInt(raw[0]));

        byte[] buffer = new byte[4];

        buffer[0] = raw[1];
        buffer[1] = raw[2];
        buffer[2] = 0;
        buffer[3] = 0;

        responseData.setRecordCount(TypeConverter
                .littleEndianByteArrayToInt(buffer));

        System.arraycopy(raw, 5, buffer, 0, 4);

        responseData.setAddTimestamp(TypeConverter
                .littleEndianByteArrayToInt(buffer));

        System.arraycopy(raw, 9, buffer, 0, 4);

        responseData.setDelTimestamp(TypeConverter
                .littleEndianByteArrayToInt(buffer));

        responseData
                .setReserveSupported((TypeConverter.byteToInt(raw[13]) & 0x2) != 0);

        return responseData;
    }

}
