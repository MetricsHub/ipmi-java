package org.metricshub.ipmi.core.coding.commands.payload;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * IPMI Java Client
 * ჻჻჻჻჻჻
 * Copyright 2023 MetricsHub
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
import org.metricshub.ipmi.core.coding.commands.ResponseData;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.coding.protocol.PayloadType;
import org.metricshub.ipmi.core.common.TypeConverter;

import java.util.Arrays;

/**
 * Wrapper for the Get Payload Instance Info command (IPMI 2.0 section 24.5): the session where an instance of a
 * payload is active.
 */
public class GetPayloadInstanceInfo extends IpmiCommandCoder {

	private final PayloadType payloadType;
	private final int payloadInstance;

	/**
	 * Creates the command.
	 *
	 * @param payloadType the payload type, not {@link PayloadType#Ipmi}
	 * @param payloadInstance the instance of the payload, from 1
	 */
	public GetPayloadInstanceInfo(PayloadType payloadType, int payloadInstance) {
		this.payloadType = payloadType;
		this.payloadInstance = payloadInstance;
	}

	@Override
	public byte getCommandCode() {
		return CommandCodes.GET_PAYLOAD_INSTANCE_INFO;
	}

	@Override
	public NetworkFunction getNetworkFunction() {
		return NetworkFunction.ApplicationRequest;
	}

	@Override
	protected IpmiPayload preparePayload(int sequenceNumber) {
		byte[] data = { TypeConverter.intToByte(payloadType.getCode()), TypeConverter.intToByte(payloadInstance) };
		return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), data, TypeConverter.intToByte(sequenceNumber));
	}

	/**
	 * Decodes the response: the session ID (LS-byte first), then 8 bytes of payload-specific information (table 24-7).
	 */
	@Override
	public ResponseData getResponseData(IpmiMessage message) throws IPMIException {
		byte[] data = validateResponse(message);
		// The whole response, before trusting its session ID
		if (data.length < 12) {
			throw new IllegalArgumentException("Invalid response length");
		}
		return new GetPayloadInstanceInfoResponseData(TypeConverter.littleEndianByteArrayToInt(Arrays.copyOf(data, 4)));
	}
}
