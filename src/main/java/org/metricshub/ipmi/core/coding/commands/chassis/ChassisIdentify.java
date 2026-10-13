package org.metricshub.ipmi.core.coding.commands.chassis;

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
 * Wrapper for the Chassis Identify command (IPMI 2.0 section 28.5): makes the chassis identify itself, by a blinking
 * LED or a beep, for a number of seconds, indefinitely, or not any more. The request carries the interval byte, and
 * the optional force byte only when the identify is to stay on: a BMC that does not support that byte answers an
 * error completion code to it.
 */
public class ChassisIdentify extends IpmiCommandCoder<ChassisIdentifyResponseData> {

	/** The longest interval the request can carry, in seconds. */
	public static final int MAX_INTERVAL = 255;

	private final int interval;
	private final boolean force;

	/**
	 * Initiates ChassisIdentify for encoding and decoding.
	 *
	 * @param interval the identify interval in seconds, 1 to 255 (the BMC may stretch it by 20%); 0 turns the
	 *        identify off
	 * @param force whether the identify stays on indefinitely (the interval is then ignored)
	 * @throws IllegalArgumentException when the interval is not between 0 and 255
	 */
	public ChassisIdentify(int interval, boolean force) {
		if (interval < 0 || interval > MAX_INTERVAL) {
			throw new IllegalArgumentException("The identify interval must be between 0 and 255 seconds: " + interval);
		}
		this.interval = interval;
		this.force = force;
	}

	@Override
	public byte getCommandCode() {
		return CommandCodes.CHASSIS_IDENTIFY;
	}

	@Override
	public NetworkFunction getNetworkFunction() {
		return NetworkFunction.ChassisRequest;
	}

	@Override
	protected IpmiPayload preparePayload(int sequenceNumber) throws NoSuchAlgorithmException, InvalidKeyException {
		byte[] requestData = force ? new byte[] { 0, 1 } : new byte[] { TypeConverter.intToByte(interval) };
		return new IpmiLanRequest(
				getNetworkFunction(),
				getCommandCode(),
				requestData,
				TypeConverter.intToByte(sequenceNumber));
	}

	@Override
	public ChassisIdentifyResponseData getResponseData(IpmiMessage message)
			throws IPMIException,
			NoSuchAlgorithmException,
			InvalidKeyException {
		validateResponse(message);
		return new ChassisIdentifyResponseData();
	}
}
