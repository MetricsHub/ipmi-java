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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.metricshub.ipmi.core.coding.commands.CommandCodes;
import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * A wrapper class for Get FRU Inventory Area Info Command.
 */
public class GetFruInventoryAreaInfo extends IpmiCommandCoder<GetFruInventoryAreaInfoResponseData> {

	private int fruId;

	private final byte lun;

	/**
	 * Initiates GetFruInventoryAreaInfo for both encoding and decoding, addressed to LUN 0.
	 *
	 * @param fruId
	 *        - ID of the FRU to get info from. Must be less than 256.
	 */
	public GetFruInventoryAreaInfo(int fruId) {
		this(fruId, 0);
	}

	/**
	 * Initiates GetFruInventoryAreaInfo for both encoding and decoding.
	 *
	 * @param fruId
	 *        - ID of the FRU to get info from. Must be less than 256.
	 * @param lun the LUN of the FRU device, the access LUN of its FRU Device Locator record (IPMI 2.0 table 43-7),
	 *        0 to 3
	 * @throws IllegalArgumentException when the FRU ID exceeds 255 or the LUN is not 0 to 3
	 */
	public GetFruInventoryAreaInfo(int fruId, int lun) {

		if (fruId > 255) {
			throw new IllegalArgumentException("FRU ID cannot exceed 255");
		}

		this.fruId = fruId;
		this.lun = checkLun(lun);
	}

	@Override
	public byte getCommandCode() {
		return CommandCodes.GET_FRU_INVENTORY_AREA_INFO;
	}

	@Override
	public NetworkFunction getNetworkFunction() {
		return NetworkFunction.StorageRequest;
	}

	@Override
	protected IpmiPayload preparePayload(int sequenceNumber) throws NoSuchAlgorithmException, InvalidKeyException {
		byte[] payload = new byte[] { TypeConverter.intToByte(fruId) };
		return new IpmiLanRequest(
				getNetworkFunction(),
				getCommandCode(),
				payload,
				TypeConverter.intToByte(sequenceNumber),
				lun);
	}

	@Override
	public GetFruInventoryAreaInfoResponseData getResponseData(IpmiMessage message)
			throws IPMIException,
			NoSuchAlgorithmException,
			InvalidKeyException {

		byte[] raw = validateResponse(message);

		if (raw == null || raw.length != 3) {
			throw new IllegalArgumentException("Invalid response payload length");
		}

		GetFruInventoryAreaInfoResponseData responseData = new GetFruInventoryAreaInfoResponseData();

		byte[] buffer = new byte[4];

		buffer[0] = raw[0];
		buffer[1] = raw[1];
		buffer[2] = 0;
		buffer[3] = 0;

		responseData.setFruInventoryAreaSize(TypeConverter.littleEndianByteArrayToInt(buffer));

		responseData.setFruUnit(BaseUnit.parseInt(TypeConverter.byteToInt(raw[2]) & 0x1));

		return responseData;
	}
}
