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
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.AuthenticationType;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.security.CipherSuite;
import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.common.TypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiFunction;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * A wrapper class for Read FRU Data Command request. <br>
 * The command returns the specified data from the FRU Inventory Info area.
 */
public class ReadFruData extends IpmiCommandCoder {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReadFruData.class);

	/** Size of the common header (FRU spec section 8). */
	private static final int COMMON_HEADER_SIZE = 8;

	/** Size of a multirecord header (FRU spec section 16.1). */
	private static final int MULTIRECORD_HEADER_SIZE = 5;

	private static final int FRU_DEVICE_BUSY = 0x81;

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
	 *        - ID of the FRU to get info from. Must be less than 256. To
	 *        get FRU ID use {@link GetSdr} to retrieve
	 *        {@link FruDeviceLocatorRecord}.
	 * @param unit
	 *        - {@link BaseUnit} indicating if the FRU device is accessed in
	 *        {@link BaseUnit#Bytes} or {@link BaseUnit#Words}
	 * @param offset
	 *        - offset to read, in bytes (sent in words when the device is word-addressed, so it must be even then)
	 * @param countToRead
	 *        - number of bytes to read. Cannot exceed 255;
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

		// Table 34-3: the offset goes on the wire in the unit of the device, the count in bytes (as ipmitool sends it)
		this.offset = offset / unit.getSize();
		size = countToRead;
		this.fruId = fruId;
	}

	/**
	 * Initiates ReadFruData for both encoding and decoding.
	 *
	 * @param version
	 *        - IPMI version of the command.
	 * @param cipherSuite
	 *        - {@link CipherSuite} containing authentication,
	 *        confidentiality and integrity algorithms for this session.
	 * @param authenticationType
	 *        - Type of authentication used. Must be RMCPPlus for IPMI v2.0.
	 * @param fruId
	 *        - ID of the FRU to get info from. Must be less than 256. To
	 *        get FRU ID use {@link GetSdr} to retrieve
	 *        {@link FruDeviceLocatorRecord}.
	 * @param unit
	 *        - {@link BaseUnit} indicating if the FRU device is accessed in
	 *        {@link BaseUnit#Bytes} or {@link BaseUnit#Words}
	 * @param offset
	 *        - offset to read, in bytes (sent in words when the device is word-addressed, so it must be even then)
	 * @param countToRead
	 *        - number of bytes to read. Cannot exceed 255;
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

		// Table 34-3: the offset goes on the wire in the unit of the device, the count in bytes (as ipmitool sends it)
		this.offset = offset / unit.getSize();
		size = countToRead;
		this.fruId = fruId;
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
			throws NoSuchAlgorithmException,
			InvalidKeyException {
		byte[] payload = new byte[4];
		payload[0] = TypeConverter.intToByte(fruId);
		byte[] buffer = TypeConverter.intToLittleEndianByteArray(offset);
		payload[1] = buffer[0];
		payload[2] = buffer[1];
		payload[3] = TypeConverter.intToByte(size);

		return new IpmiLanRequest(
				getNetworkFunction(),
				getCommandCode(),
				payload,
				TypeConverter.intToByte(sequenceNumber));
	}

	@Override
	protected CompletionCode decodeCommandSpecificCompletionCode(int rawCode) {
		// IPMI 2.0 Table 34-3
		return rawCode == FRU_DEVICE_BUSY ? CompletionCode.Frudevicebusy : CompletionCode.Unknown;
	}

	@Override
	public ResponseData getResponseData(IpmiMessage message)
			throws IPMIException,
			NoSuchAlgorithmException,
			InvalidKeyException {

		byte[] raw = validateResponse(message);

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
	 *        - list of {@link ReadFruDataResponseData} containing FRU data
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

		if (data.length < COMMON_HEADER_SIZE) {
			throw new IllegalArgumentException("FRU data shorter than its common header: " + data.length + " byte(s)");
		}
		if (data[0] != 0x1) {
			// TODO: recognize SPD records returned by DIMM FRUs (#107)
			throw new IllegalArgumentException("Invalid format version: " + data[0]);
		}
		if (!isChecksumValid(data, 0, COMMON_HEADER_SIZE)) {
			throw new IllegalArgumentException("Invalid common header checksum");
		}

		// Offsets in multiples of 8 bytes (FRU spec section 8)
		addArea(list, data, TypeConverter.byteToInt(data[2]) * 8, "chassis", ChassisInfo::new);
		addArea(list, data, TypeConverter.byteToInt(data[3]) * 8, "board", BoardInfo::new);
		addArea(list, data, TypeConverter.byteToInt(data[4]) * 8, "product", ProductInfo::new);

		int multiRecordOffset = TypeConverter.byteToInt(data[5]) * 8;
		if (multiRecordOffset != 0) {
			addMultirecords(list, data, multiRecordOffset);
		}

		return list;
	}

	/**
	 * Decodes one of the chassis, board or product info areas, when the data read holds it: a truncated read, a
	 * bad checksum or a decoding failure costs that area, not the whole FRU.
	 */
	private static void addArea(
			List<FruRecord> list,
			byte[] data,
			int offset,
			String area,
			BiFunction<byte[], Integer, FruRecord> decoder) {
		if (offset == 0) {
			return; // area not present
		}
		if (offset + 2 > data.length) {
			LOGGER.warn("The {} info area at offset {} is beyond the {} byte(s) read: skipped", area, offset, data.length);
			return;
		}
		int length = TypeConverter.byteToInt(data[offset + 1]) * 8;
		if (offset + length > data.length) {
			LOGGER
					.warn(
							"The {} info area at offset {} is truncated ({} of {} bytes read): skipped",
							area,
							offset,
							data.length - offset,
							length);
			return;
		}
		if (!isChecksumValid(data, offset, length)) {
			LOGGER.debug("The {} info area at offset {} has an invalid checksum: decoded anyway", area, offset);
		}
		try {
			list.add(decoder.apply(data, offset));
		} catch (RuntimeException e) {
			LOGGER.warn("Cannot decode the {} info area at offset {}: {}", area, offset, e.getMessage());
		}
	}

	/**
	 * Decodes the multirecord area (FRU spec section 16): the records are length-delimited, so one the library does
	 * not model is skipped, and the record carrying the end-of-list flag is decoded too.
	 */
	private static void addMultirecords(List<FruRecord> list, byte[] data, int multiRecordOffset) {
		int offset = multiRecordOffset;
		boolean last = false;

		while (!last && offset + MULTIRECORD_HEADER_SIZE <= data.length) {
			last = (TypeConverter.byteToInt(data[offset + 1]) & 0x80) != 0;
			int length = TypeConverter.byteToInt(data[offset + 2]);

			if (offset + MULTIRECORD_HEADER_SIZE + length > data.length) {
				LOGGER.warn("The multirecord at offset {} is truncated: the rest of the multirecord area is skipped", offset);
				return;
			}
			try {
				list.add(MultiRecordInfo.populateMultiRecord(data, offset));
			} catch (RuntimeException e) {
				LOGGER
						.warn(
								"Skipping the multirecord of type 0x{} at offset {}: {}",
								Integer.toHexString(TypeConverter.byteToInt(data[offset])),
								offset,
								e.getMessage());
			}
			offset += MULTIRECORD_HEADER_SIZE + length;
		}
	}

	/**
	 * @return whether the bytes of the given range add up to zero modulo 256, as every FRU header and area must
	 */
	private static boolean isChecksumValid(byte[] data, int offset, int length) {
		int sum = 0;
		for (int i = offset; i < offset + length; i++) {
			sum += data[i];
		}
		return (sum & 0xff) == 0;
	}

}
