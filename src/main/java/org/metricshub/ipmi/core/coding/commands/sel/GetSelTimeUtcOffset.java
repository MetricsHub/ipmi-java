package org.metricshub.ipmi.core.coding.commands.sel;

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
 * Wrapper for the Get SEL Time UTC Offset command (IPMI 2.0 revision 1.1, section 31.11a): the offset from UTC of the
 * SEL time, for a BMC that keeps its SEL time as local time. A BMC without the command (C1h) keeps it in UTC.
 */
public class GetSelTimeUtcOffset extends IpmiCommandCoder<GetSelTimeUtcOffsetResponseData> {

	private static final int RESPONSE_LENGTH = 2;

	/**
	 * Initiates GetSelTimeUtcOffset for both encoding and decoding.
	 */
	public GetSelTimeUtcOffset() {}

	@Override
	public byte getCommandCode() {
		return CommandCodes.GET_SEL_TIME_UTC_OFFSET;
	}

	@Override
	public NetworkFunction getNetworkFunction() {
		return NetworkFunction.StorageRequest;
	}

	@Override
	protected IpmiPayload preparePayload(int sequenceNumber) throws NoSuchAlgorithmException, InvalidKeyException {
		return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), null, TypeConverter.intToByte(sequenceNumber));
	}

	@Override
	public GetSelTimeUtcOffsetResponseData getResponseData(IpmiMessage message)
			throws IPMIException,
			NoSuchAlgorithmException,
			InvalidKeyException {
		byte[] raw = validateResponse(message);

		if (raw == null || raw.length != RESPONSE_LENGTH) {
			throw new IllegalArgumentException("Invalid response payload length: " + (raw != null ? raw.length : "null"));
		}

		GetSelTimeUtcOffsetResponseData responseData = new GetSelTimeUtcOffsetResponseData();
		// A signed 16-bit number of minutes, LS byte first
		responseData.setOffsetMinutes((short) ((raw[0] & 0xff) | (raw[1] & 0xff) << 8));
		return responseData;
	}
}
