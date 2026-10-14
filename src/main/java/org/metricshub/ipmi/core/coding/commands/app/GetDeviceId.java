package org.metricshub.ipmi.core.coding.commands.app;

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
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanMessage;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Wrapper for the Get Device ID command (IPMI 2.0 section 20.1): the identity of the controller, its firmware and
 * the functions it provides. It has no request data; the reply is a {@link GetDeviceIdResponseData}.
 */
public class GetDeviceId extends IpmiCommandCoder<GetDeviceIdResponseData> {

	/**
	 * Initiates GetDeviceId for encoding and decoding.
	 */
	public GetDeviceId() {}

	@Override
	protected IpmiLanMessage preparePayload(int sequenceNumber) {
		return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), null, TypeConverter.intToByte(sequenceNumber));
	}

	@Override
	public byte getCommandCode() {
		return CommandCodes.GET_DEVICE_ID;
	}

	@Override
	public NetworkFunction getNetworkFunction() {
		return NetworkFunction.ApplicationRequest;
	}

	@Override
	public GetDeviceIdResponseData getResponseData(IpmiMessage message) throws IPMIException {
		return new GetDeviceIdResponseData(validateResponse(message));
	}
}
