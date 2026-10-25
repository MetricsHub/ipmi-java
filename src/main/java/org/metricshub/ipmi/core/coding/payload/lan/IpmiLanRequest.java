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

import org.metricshub.ipmi.core.coding.commands.CommandCodes;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * A wrapper class for IPMB LAN message
 */
public class IpmiLanRequest extends IpmiLanMessage {

	/** The network function is a 6-bit field (IPMI 2.0 section 5.1). */
	private static final int MAX_NETWORK_FUNCTION_CODE = 0x3f;

	/**
	 * Builds IpmiLanRequest addressed at LUN 0.
	 *
	 * @param networkFunction
	 *        - command specific {@link NetworkFunction}
	 * @param commandCode
	 *        - command specific {@link CommandCodes}
	 * @param requestData
	 *        - command specific payload
	 * @param sequenceNumber
	 *        - used to match request and response - must be in range [0-63]
	 */
	public IpmiLanRequest(NetworkFunction networkFunction, byte commandCode, byte[] requestData, byte sequenceNumber) {
		this(networkFunction, commandCode, requestData, sequenceNumber, TypeConverter.intToByte(0));
	}

	/**
	 * Builds IpmiLanRequest.
	 *
	 * @param networkFunction
	 *        - command specific {@link NetworkFunction}
	 * @param commandCode
	 *        - command specific {@link CommandCodes}
	 * @param requestData
	 *        - command specific payload
	 * @param sequenceNumber
	 *        - used to match request and response - must be in range [0-63]
	 * @param lun
	 *        - target Logical Unit Number. Must be in range [0-3].
	 */
	public IpmiLanRequest(NetworkFunction networkFunction, byte commandCode, byte[] requestData, byte sequenceNumber,
			byte lun) {
		this(networkFunction.getCode(), commandCode, requestData, sequenceNumber, lun);
	}

	/**
	 * Builds IpmiLanRequest addressed at LUN 0, under a network function given by its code, which may be one
	 * {@link NetworkFunction} does not list: Transport (0Ch), Group Extension (2Ch: DCMI, PICMG), OEM/Group (2Eh),
	 * controller-specific OEM (30h-3Eh), IPMI 2.0 table 5-1.
	 *
	 * @param networkFunctionCode
	 *        - the network function code of the request, 00h to 3Fh
	 * @param commandCode
	 *        - the command code
	 * @param requestData
	 *        - command specific payload, null for none
	 * @param sequenceNumber
	 *        - used to match request and response - must be in range [0-63]
	 * @throws IllegalArgumentException when the code does not fit in 6 bits
	 */
	public IpmiLanRequest(int networkFunctionCode, byte commandCode, byte[] requestData, byte sequenceNumber) {
		this(networkFunctionCode, commandCode, requestData, sequenceNumber, TypeConverter.intToByte(0));
	}

	private IpmiLanRequest(int networkFunctionCode, byte commandCode, byte[] requestData, byte sequenceNumber, byte lun) {
		if (networkFunctionCode < 0 || networkFunctionCode > MAX_NETWORK_FUNCTION_CODE) {
			throw new IllegalArgumentException("Invalid network function code: " + networkFunctionCode);
		}
		if (lun < 0 || lun > 3) {
			throw new IllegalArgumentException("Invalid LUN");
		}
		setResponderAddress(IpmiLanConstants.BMC_ADDRESS);
		setNetworkFunctionCode(TypeConverter.intToByte(networkFunctionCode));
		setResponderLogicalUnitNumber(TypeConverter.intToByte(lun));
		setRequesterAddress(IpmiLanConstants.REMOTE_CONSOLE_ADDRESS);
		setRequesterLogicalUnitNumber(TypeConverter.intToByte(0));
		setSequenceNumber(sequenceNumber);
		setData(requestData);
		setCommand(commandCode);
	}

	@Override
	public int getPayloadLength() {
		int length = 7;

		if (getData() != null) {
			length += getData().length;
		}
		return length;
	}

	@Override
	public byte[] getPayloadData() {
		byte[] message = new byte[getPayloadLength()];

		message[0] = getResponderAddress();
		message[1] = TypeConverter.intToByte((getNetworkFunctionCode() << 2) | getResponderLogicalUnitNumber());
		message[2] = getChecksum1(message);
		message[3] = getRequesterAddress();
		message[4] = TypeConverter.intToByte(((getSequenceNumber() & 0x3f) << 2) | getRequesterLogicalUnitNumber()); // rqSeq/rqLUN
																																																									// (IPMI
																																																									// 2.0
																																																									// figure
																																																									// 13-4)
		message[5] = getCommand();

		if (getData() != null) {
			System.arraycopy(getData(), 0, message, 6, getData().length);
		}

		message[message.length - 1] = getChecksum2(message);

		return message;
	}

}
