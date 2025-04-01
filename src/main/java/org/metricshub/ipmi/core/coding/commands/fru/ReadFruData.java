package org.metricshub.ipmi.core.coding.commands.fru;

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
import org.metricshub.ipmi.core.coding.commands.fru.record.BoardInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ChassisInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.FruRecord;
import org.metricshub.ipmi.core.coding.commands.fru.record.MultiRecordInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ProductInfo;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSdr;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FruDeviceLocatorRecord;
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
import java.util.ArrayList;
import java.util.List;

/**
 * A wrapper class for Read FRU Data Command request. <br>
 * The command returns the specified data from the FRU Inventory Info area.
 */
public class ReadFruData extends IpmiCommandCoder {

    private int offset;

    private int size;

    private int fruId;

    /**
     * Initiates ReadFruData for both encoding and decoding. Sets session
     * parameters to default.
     *
     * @see IpmiCommandCoder#setSessionParameters(IpmiVersion, CipherSuite,
     *      AuthenticationType)
     * @param fruId
     *            - ID of the FRU to get info from. Must be less than 256. To
     *            get FRU ID use {@link GetSdr} to retrieve
     *            {@link FruDeviceLocatorRecord}.
     * @param unit
     *            - {@link BaseUnit} indicating if the FRU device is accessed in
     *            {@link BaseUnit#Bytes} or {@link BaseUnit#Words}
     * @param offset
     *            - offset to read in units specified by unit
     * @param countToRead
     *            - size of the area to read in unit. Cannot exceed 255;
     */
    public ReadFruData(int fruId, BaseUnit unit, int offset, int countToRead) {
        super();

        if (countToRead > 255) {
            throw new IllegalArgumentException(
                    "Count to read cannot exceed 255");
        }

        if (fruId > 255) {
            throw new IllegalArgumentException("FRU ID cannot exceed 255");
        }

        this.offset = offset * unit.getSize();

        size = countToRead * unit.getSize();

        this.fruId = fruId;
        // TODO: Check if Count To Read field is encoded in words if the FRU is
        // addressed in words (requires different server settings).
    }

    /**
     * Initiates ReadFruData for both encoding and decoding.
     *
     * @param version
     *            - IPMI version of the command.
     * @param cipherSuite
     *            - {@link CipherSuite} containing authentication,
     *            confidentiality and integrity algorithms for this session.
     * @param authenticationType
     *            - Type of authentication used. Must be RMCPPlus for IPMI v2.0.
     * @param fruId
     *            - ID of the FRU to get info from. Must be less than 256. To
     *            get FRU ID use {@link GetSdr} to retrieve
     *            {@link FruDeviceLocatorRecord}.
     * @param unit
     *            - {@link BaseUnit} indicating if the FRU device is accessed in
     *            {@link BaseUnit#Bytes} or {@link BaseUnit#Words}
     * @param offset
     *            - offset to read in units specified by unit
     * @param countToRead
     *            - size of the area to read in unit. Cannot exceed 255;
     */
    public ReadFruData(IpmiVersion version, CipherSuite cipherSuite,
            AuthenticationType authenticationType, int fruId, BaseUnit unit,
            int offset, int countToRead) {
        super(version, cipherSuite, authenticationType);

        if (countToRead > 255) {
            throw new IllegalArgumentException(
                    "Count to read cannot exceed 255");
        }

        if (fruId > 255) {
            throw new IllegalArgumentException("FRU ID cannot exceed 255");
        }

        this.offset = offset * unit.getSize();

        size = countToRead * unit.getSize();

        this.fruId = fruId;
        // TODO: Check if Count To Read field is encoded in words if the FRU is
        // addressed in words (requires different server settings).
    }

    @Override
    public byte getCommandCode() {
        return CommandCodes.READ_FRU_DATA;
    }

    @Override
    public NetworkFunction getNetworkFunction() {
        return NetworkFunction.StorageRequest;
    }

    @Override
    protected IpmiPayload preparePayload(int sequenceNumber)
            throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] payload = new byte[4];
        payload[0] = TypeConverter.intToByte(fruId);
        byte[] buffer = TypeConverter.intToLittleEndianByteArray(offset);
        payload[1] = buffer[0];
        payload[2] = buffer[1];
        payload[3] = TypeConverter.intToByte(size);

        return new IpmiLanRequest(getNetworkFunction(), getCommandCode(),
                payload, TypeConverter.intToByte(sequenceNumber));
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

        if (raw == null || raw.length < 2) {
            throw new IllegalArgumentException(
                    "Invalid response payload length");
        }

        ReadFruDataResponseData responseData = new ReadFruDataResponseData();

        int sizeFromResponse = TypeConverter.byteToInt(raw[0]);

        byte[] fruData = new byte[sizeFromResponse];

        System.arraycopy(raw, 1, fruData, 0, sizeFromResponse);

        responseData.setFruData(fruData);

        return responseData;
    }

    /**
     * Decodes {@link FruRecord}s from data provided by {@link ReadFruData}
     * command. Size of the FRU Inventory Area might exceed size of the
     * communication packet so it might come in many
     * {@link ReadFruDataResponseData} packets.
     *
     * @param fruData
     *            - list of {@link ReadFruDataResponseData} containing FRU data
     * @return list of {@link FruRecord}s containing decoded FRU data.
     */
    @SuppressWarnings("unused")
    public static List<FruRecord> decodeFruData(
            List<ReadFruDataResponseData> fruData) {

        int size = 0;

        ArrayList<FruRecord> list = new ArrayList<FruRecord>();

        for (ReadFruDataResponseData responseData : fruData) {
            size += responseData.getFruData().length;
        }

        byte[] data = new byte[size];

        int offset = 0;

        for (ReadFruDataResponseData responseData : fruData) {
            int length = responseData.getFruData().length;
            System.arraycopy(responseData.getFruData(), 0, data, offset, length);
            offset += length;
        }

        if (data[0] == 0x1) {

            int chassisOffset = TypeConverter.byteToInt(data[2]) * 8;
            int boardOffset = TypeConverter.byteToInt(data[3]) * 8;
            int productInfoOffset = TypeConverter.byteToInt(data[4]) * 8;
            int multiRecordOffset = TypeConverter.byteToInt(data[5]) * 8;

            if (chassisOffset != 0) {
                list.add(new ChassisInfo(data, chassisOffset));
            }
            if (boardOffset != 0) {
                list.add(new BoardInfo(data, boardOffset));
            }
            if (productInfoOffset != 0) {
                list.add(new ProductInfo(data, productInfoOffset));
            }
            if (multiRecordOffset != 0) {
                addMultirecords(list, data, multiRecordOffset);
            }
        } else if (false) {
            // TODO: Recognize SPD record (returned from DIMM FRUs)
        } else {
            throw new IllegalArgumentException("Invalid format version: " + data[0]);
        }

        return list;
    }

    private static void addMultirecords(ArrayList<FruRecord> list, byte[] data, int multiRecordOffset) {
        int currentMultirecordOffset = multiRecordOffset;

        while ((TypeConverter.byteToInt(data[currentMultirecordOffset + 1]) & 0x80) == 0) {
            list.add(MultiRecordInfo.populateMultiRecord(data, currentMultirecordOffset));
            currentMultirecordOffset += TypeConverter.byteToInt(data[currentMultirecordOffset + 2]) + 5;
        }
    }

}
