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

import org.metricshub.ipmi.core.coding.payload.CompletionCode;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * A wrapper class for IPMB response message.
 */
public class IpmiLanResponse extends IpmiLanMessage {

	private static final int GENERIC_CODES_START = 0xC0;

	private CompletionCode completionCode;

	private int rawCompletionCode;

	/**
	 * Decodes the completion code. Only 00h and the generic codes (C0h-FFh, IPMI 2.0 Table 5-2) have a meaning
	 * common to every command; an OEM (01h-7Eh) or command-specific (80h-BEh) code is {@link CompletionCode#Unknown}
	 * here, and the command coder may give it its own meaning.
	 *
	 * @param completionCode the completion code byte of the response
	 */
	public void setCompletionCode(byte completionCode) {
		rawCompletionCode = TypeConverter.byteToInt(completionCode);
		this.completionCode = rawCompletionCode == 0 || rawCompletionCode >= GENERIC_CODES_START ?
				CompletionCode.parseInt(rawCompletionCode) : CompletionCode.Unknown;
	}

	public CompletionCode getCompletionCode() {
		return completionCode;
	}

	/**
	 * @return the completion code byte as the BMC sent it
	 */
	public int getRawCompletionCode() {
		return rawCompletionCode;
	}

	/**
	 * Builds IPMI LAN response message from byte array.
	 *
	 * @param rawData
	 * @throws IllegalArgumentException
	 *         when checksum is corrupted
	 */
	public IpmiLanResponse(byte[] rawData) {
		setRequesterAddress(rawData[0]);
		setNetworkFunctionCode(
				TypeConverter
						.intToByte(
								(TypeConverter
										.byteToInt(rawData[1])
										& 0xfC) >> 2));
		setRequesterLogicalUnitNumber(
				TypeConverter
						.intToByte(
								TypeConverter
										.byteToInt(rawData[1])
										& 0x03));
		if (rawData[2] != getChecksum1(rawData)) {
			throw new IllegalArgumentException("Checksum 1 failed");
		}
		setResponderAddress(rawData[3]);
		setSequenceNumber(
				TypeConverter
						.intToByte(
								(TypeConverter
										.byteToInt(rawData[4])
										& 0xfC) >> 2));
		setResponderLogicalUnitNumber(
				TypeConverter
						.intToByte(
								TypeConverter
										.byteToInt(rawData[4])
										& 0x03));
		setCommand(rawData[5]);
		setCompletionCode(rawData[6]);

		if (rawData.length > 8) {
			byte[] data = new byte[rawData.length - 8];

			System.arraycopy(rawData, 7, data, 0, rawData.length - 8);

			setData(data);
		}

		if (rawData[rawData.length - 1] != getChecksum2(rawData)) {
			throw new IllegalArgumentException("Checksum 2 failed");
		}
	}

	@Override
	public int getPayloadLength() {
		int length = 8;
		if (getData() != null) {
			length += getData().length;
		}
		return length;
	}

	/**
	 * @deprecated LAN response does not hve payload data
	 */
	@Override
	@Deprecated
	public byte[] getPayloadData() {
		return null;
	}

}
