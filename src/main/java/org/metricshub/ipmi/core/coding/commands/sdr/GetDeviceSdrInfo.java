package org.metricshub.ipmi.core.coding.commands.sdr;

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

import org.metricshub.ipmi.core.coding.commands.IpmiCommandCoder;
import org.metricshub.ipmi.core.coding.payload.IpmiPayload;
import org.metricshub.ipmi.core.coding.payload.lan.IPMIException;
import org.metricshub.ipmi.core.coding.payload.lan.IpmiLanRequest;
import org.metricshub.ipmi.core.coding.payload.lan.NetworkFunction;
import org.metricshub.ipmi.core.coding.protocol.IpmiMessage;
import org.metricshub.ipmi.core.common.TypeConverter;

/**
 * Get Device SDR Info (IPMI 2.0 section 35.2): how many SDRs a Sensor Device holds itself, or how many sensors it
 * implements at the LUN the request is addressed to.
 */
public class GetDeviceSdrInfo extends IpmiCommandCoder<GetDeviceSdrInfoResponseData> {

	/** Length of the response data of a static sensor population: the count and the flags. */
	private static final int STATIC_LENGTH = 2;

	/** Length of the response data with the sensor population change indicator of a dynamic population. */
	private static final int DYNAMIC_LENGTH = 6;

	private final boolean sdrCount;

	private final byte lun;

	/**
	 * Creates the command, addressed to LUN 0.
	 *
	 * @param sdrCount true for the number of SDRs of the device, false for the number of sensors at LUN 0
	 */
	public GetDeviceSdrInfo(boolean sdrCount) {
		this(sdrCount, 0);
	}

	/**
	 * Creates the command, addressed to a LUN of the device: the sensor count is the one of that LUN.
	 *
	 * @param sdrCount true for the number of SDRs of the device, false for the number of sensors at the LUN
	 * @param lun the LUN, 0 to 3 (the response flags list the LUNs that have sensors)
	 * @throws IllegalArgumentException when the LUN is not 0 to 3
	 */
	public GetDeviceSdrInfo(boolean sdrCount, int lun) {
		this.sdrCount = sdrCount;
		this.lun = checkLun(lun);
	}

	@Override
	public byte getCommandCode() {
		return 0x20;
	}

	@Override
	public NetworkFunction getNetworkFunction() {
		return NetworkFunction.SensorRequest;
	}

	@Override
	protected IpmiPayload preparePayload(int sequenceNumber) throws NoSuchAlgorithmException, InvalidKeyException {
		return new IpmiLanRequest(
				getNetworkFunction(),
				getCommandCode(),
				new byte[]
				{ (byte) (sdrCount ? 1 : 0) },
				TypeConverter.intToByte(sequenceNumber),
				lun);
	}

	@Override
	public GetDeviceSdrInfoResponseData getResponseData(IpmiMessage message)
			throws IPMIException,
			NoSuchAlgorithmException,
			InvalidKeyException {
		byte[] raw = validateResponse(message);
		if (raw == null || raw.length < STATIC_LENGTH) {
			throw new IllegalArgumentException("Invalid response payload length");
		}
		int flags = TypeConverter.byteToInt(raw[1]);
		boolean dynamic = (flags & 0x80) != 0;
		if (dynamic && raw.length < DYNAMIC_LENGTH) {
			throw new IllegalArgumentException(
					"Invalid response payload length: a dynamic population has a change indicator");
		}
		Long changeIndicator = null;
		if (dynamic) {
			changeIndicator = TypeConverter.littleEndianByteArrayToInt(new byte[] { raw[2], raw[3], raw[4], raw[5] })
					& 0xffffffffL;
		}
		return new GetDeviceSdrInfoResponseData(TypeConverter.byteToInt(raw[0]), dynamic, flags & 0x0f, changeIndicator);
	}
}
