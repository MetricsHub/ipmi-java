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
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.common.TypeConverter;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.metricshub.ipmi.core.coding.commands.CommandCodes.GET_CHANNEL_PAYLOAD_SUPPORT;

/**
 * A wrapper class for Get Channel Payload Support Command.
 */
public class GetChannelPayloadSupport extends IpmiCommandCoder<GetChannelPayloadSupportResponseData> {

	private byte channelNumber;

	/**
	 * Initiates class for decoding.
	 */
	public GetChannelPayloadSupport() {}

	/**
	 * Initiates class for both encoding and decoding.
	 *
	 * @param channelNumber
	 *        - must be 0h-Bh or Eh-Fh <br>
	 *        Eh = retrieve information for channel this request was issued
	 *        on
	 */
	public GetChannelPayloadSupport(byte channelNumber) {
		setChannelNumber(channelNumber);
	}

	public byte getChannelNumber() {
		return channelNumber;
	}

	/**
	 * Sets the channel number that will be put into IPMI command.
	 *
	 * @param channelNumber
	 *        - must be 0h-Bh or Eh-Fh <br>
	 *        Eh = retrieve information for channel this request was issued
	 *        on
	 * @throws IllegalArgumentException
	 */
	public void setChannelNumber(int channelNumber) {
		if (channelNumber < 0 || channelNumber > 0xF || channelNumber == 0xC || channelNumber == 0xD) {
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

		return new IpmiLanRequest(
				getNetworkFunction(),
				getCommandCode(),
				requestData,
				TypeConverter.intToByte(sequenceNumber));
	}

	@Override
	public GetChannelPayloadSupportResponseData getResponseData(IpmiMessage message)
			throws IPMIException,
			NoSuchAlgorithmException,
			InvalidKeyException {
		byte[] responseData = validateResponse(message);

		// Standard payloads 0-7 and 8-15, session setup payloads 0-7 and 8-15, OEM payloads 0-7 and 8-15, reserved
		// (IPMI 2.0 table 24-10)
		if (responseData.length < 8) {
			throw new IllegalArgumentException("Invalid response length");
		}

		GetChannelPayloadSupportResponseData data = new GetChannelPayloadSupportResponseData();

		data.setStandardPayloads(responseData[0]);
		data.setSessionSetupPayloads(responseData[2]);
		data.setOemPayloads(responseData[4]);

		return data;
	}

}
