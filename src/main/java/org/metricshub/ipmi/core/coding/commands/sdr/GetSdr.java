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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

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

/**
 * Wrapper for Get SDR command.
 */
public class GetSdr extends IpmiCommandCoder {

    private int reservationId;

    private int recordId;

    private int offset;

    private int bytesToRead;

    /**
     * Initiates GetSdr for both encoding and decoding. 'Offset info record' and
     * 'bytes to read' fields are set to read whole record.
     *
     * @param version
     *            - IPMI version of the command.
     * @param cipherSuite
     *            - {@link CipherSuite} containing authentication,
     *            confidentiality and integrity algorithms for this session.
     * @param authenticationType
     *            - Type of authentication used. Must be RMCPPlus for IPMI v2.0.
     * @param reservationId
     *            - SDR reservation ID received via {@link ReserveSdrRepository}
     *            command
     * @param recordId
     *            - ID of the record to get
     */
    public GetSdr(IpmiVersion version, CipherSuite cipherSuite,
            AuthenticationType authenticationType, int reservationId,
            int recordId) {
        this(version, cipherSuite, authenticationType, reservationId, recordId,
                0, 0xFF);
    }

    /**
     * Initiates GetSdr for both encoding and decoding.
     *
     * @param version
     *            - IPMI version of the command.
     * @param cipherSuite
     *            - {@link CipherSuite} containing authentication,
     *            confidentiality and integrity algorithms for this session.
     * @param authenticationType
     *            - Type of authentication used. Must be RMCPPlus for IPMI v2.0.
     * @param reservationId
     *            - SDR reservation ID received via {@link ReserveSdrRepository}
     *            command
     * @param recordId
     *            - ID of the record to get
     * @param offset
     *            - the offset into record, at which reading should be started
     * @param bytesToRead
     *            - number of bytes to read
     */
    public GetSdr(IpmiVersion version, CipherSuite cipherSuite,
            AuthenticationType authenticationType, int reservationId,
            int recordId, int offset, int bytesToRead) {
        super(version, cipherSuite, authenticationType);
        this.recordId = recordId;
        this.reservationId = reservationId;
        this.offset = offset;
        this.bytesToRead = bytesToRead;
    }

    @Override
    public byte getCommandCode() {
        return CommandCodes.GET_SDR;
    }

    @Override
    public NetworkFunction getNetworkFunction() {
        return NetworkFunction.StorageRequest;
    }

    @Override
    protected IpmiPayload preparePayload(int sequenceNumber)
            throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] payload = new byte[6];

        byte[] buffer = TypeConverter.intToByteArray(reservationId);

        payload[0] = buffer[3];
        payload[1] = buffer[2]; // reservation ID

        buffer = TypeConverter.intToByteArray(recordId);

        payload[2] = buffer[3];
        payload[3] = buffer[2]; // record ID

        payload[4] = TypeConverter.intToByte(offset);
        payload[5] = TypeConverter.intToByte(bytesToRead);

        return new IpmiLanRequest(getNetworkFunction(), getCommandCode(),
                payload, TypeConverter.intToByte(sequenceNumber));
    }

    @Override
    public ResponseData getResponseData(IpmiMessage message) throws IPMIException, NoSuchAlgorithmException, InvalidKeyException {
        if (!isCommandResponse(message)) {
            throw new IllegalArgumentException(
                    "This is not a response for Get SDR command");
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

        if (raw == null || raw.length < 3) {
            throw new IllegalArgumentException(
                    "Invalid response payload length");
        }

        GetSdrResponseData responseData = new GetSdrResponseData();

        byte[] buffer = new byte[4];

        buffer[0] = raw[0];
        buffer[1] = raw[1];
        buffer[2] = 0;
        buffer[3] = 0;

        responseData.setNextRecordId(TypeConverter
                .littleEndianByteArrayToInt(buffer));

        byte[] recordData = new byte[raw.length - 2];

        System.arraycopy(raw, 2, recordData, 0, recordData.length);

        responseData.setSensorRecordData(recordData);

        return responseData;
    }

}
