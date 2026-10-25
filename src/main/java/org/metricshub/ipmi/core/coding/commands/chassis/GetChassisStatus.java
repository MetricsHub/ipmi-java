package org.metricshub.ipmi.core.coding.commands.chassis;

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
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanMessage;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Wrapper class for Get Chassis Status request.
 */
public class GetChassisStatus extends IpmiCommandCoder<GetChassisStatusResponseData> {

	/**
	 * Initiates GetChassisStatus for encoding and decoding.
	 */
	public GetChassisStatus() {}

	@Override
	protected IpmiLanMessage preparePayload(int sequenceNumber) {
		return new IpmiLanRequest(getNetworkFunction(), getCommandCode(), null, TypeConverter.intToByte(sequenceNumber));
	}

	@Override
	public byte getCommandCode() {
		return CommandCodes.GET_CHASSIS_STATUS;
	}

	@Override
	public NetworkFunction getNetworkFunction() {
		return NetworkFunction.ChassisRequest;
	}

	@Override
	public GetChassisStatusResponseData getResponseData(IpmiMessage message)
			throws IPMIException,
			NoSuchAlgorithmException,
			InvalidKeyException {
		byte[] raw = validateResponse(message);

		if (raw == null || (raw.length != 3 && raw.length != 4)) {
			throw new IllegalArgumentException("Invalid response payload length");
		}

		GetChassisStatusResponseData responseData = new GetChassisStatusResponseData();

		responseData.setCurrentPowerState(raw[0]);
		responseData.setLastPowerEvent(raw[1]);
		responseData.setMiscChassisState(raw[2]);

		if (raw.length == 4) {
			responseData.setFrontPanelButtonCapabilities(raw[3]);
		}

		return responseData;
	}

}
